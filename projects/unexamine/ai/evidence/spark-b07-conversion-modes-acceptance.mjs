/* 本地转化模式验收：复用现有服务和隔离浏览器，故障只作用于指定新建来源，finally 恢复。 */
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { connect } from './spark-browser-qa.mjs'
const run = promisify(execFile)
const area = 'projects/unexamine/ai/evidence/'
const file = area + 'spark-b07-conversion-modes-facts.json'
const source = 'module_49d10b5a8b', target = 'module_4513ff83a1'
const phase = process.argv[2]
const sql = async query => {
  const result = await run('docker', ['exec','unexamine-mysql-1','sh','-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"','readback',query], { encoding: 'utf8' })
  return result.stdout.trim() ? result.stdout.trim().split('\n').map(line => line.replace(/\r$/, '').split('\t')) : []
}
const api = async (method, path, body, token, headers = {}) => {
  const response = await fetch('http://localhost:18080' + path, { method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body) })
  const value = await response.json()
  assert(response.ok, `HTTP ${response.status}: ${value.code}: ${value.message}`)
  return value.data
}
const bootstrap = await fs.readFile('projects/unexamine/backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java', 'utf8')
const platform = await api('POST','/api/auth/login',{username:bootstrap.match(/default-admin\.username:([^}]+)/)[1],password:bootstrap.match(/default-admin\.initial-password:([^}]+)/)[1]})
const context = await api('GET','/api/auth/me',undefined,platform.accessToken)
const entry = await api('POST','/api/systems/19/enter',{},platform.accessToken)
const token = entry.tokens.accessToken
const request = (method,path,body,headers) => api(method,path,body,token,headers)
let facts = await fs.readFile(file,'utf8').then(JSON.parse).catch(() => ({}))
const save = async () => { facts.checked_at = new Date().toISOString(); await fs.writeFile(file, JSON.stringify(facts,null,2)+'\n') }
const binding = async () => (await request('GET','/api/flow-bindings?moduleId=30&triggerEvent=CONVERT')).currentTenant.find(b => b.status === 'ACTIVE')
const b = await connect()
await b.command('Page.navigate',{url:'http://localhost:15173/'})
await b.wait("location.origin==='http://localhost:15173'")
const system = {...context,systemId:19,tenantId:entry.tenantId,memberId:entry.systemMemberId,tenantMemberId:entry.tenantMemberId,
  systemName:entry.systemName,tenantName:entry.tenantName,roleIds:entry.roleIds,permissions:entry.permissions,dataScopes:entry.dataScopes,
  contextRevision:entry.contextRevision,redrawScopes:entry.redrawScopes,tenantSwitchContext:entry.tenantSwitchContext}
await b.evaluate(`sessionStorage.setItem('unexamine.platform.tokens',${JSON.stringify(JSON.stringify(platform))});sessionStorage.setItem('unexamine.platform.context',${JSON.stringify(JSON.stringify(context))});sessionStorage.setItem('unexamine.system.tokens',${JSON.stringify(JSON.stringify(entry.tokens))});sessionStorage.setItem('unexamine.system.context',${JSON.stringify(JSON.stringify(system))});true`)
const adminUrl = `http://localhost:15173/systems/19/admin?section=flow&module=${source}&moduleTask=FLOW&flowSection=bindings&triggerEvent=CONVERT`
const go = async url => { await b.command('Page.navigate',{url}); await b.wait("document.readyState==='complete'") }
const openBinding = async () => {
  await go(adminUrl)
  await b.wait("document.body.innerText.includes('配置模块 Flow') || document.body.innerText.includes('执行方式')")
  if (!await b.evaluate("document.body.innerText.includes('执行方式')")) await b.button('配置模块 Flow')
  await b.wait("document.body.innerText.includes('执行方式')")
  await b.wait("[...document.querySelectorAll('button')].some(el=>el.textContent.replace(/\\s/g,'')==='发布动作配置'&&!el.disabled)&&document.body.innerText.includes('转化时')")
}
const inspect = async () => console.log(await b.evaluate("document.body.innerText"))
const select = async (label, option) => {
  await b.click(`[...document.querySelectorAll('.ant-form-item')].find(el=>el.querySelector('label')?.textContent===${JSON.stringify(label)})?.querySelector('.ant-select-selector')`)
  const choice = `[...document.querySelectorAll('.ant-select-item-option-content')].find(el=>el.getClientRects().length&&el.textContent===${JSON.stringify(option)})`
  await b.wait(`Boolean(${choice})`); await new Promise(r=>setTimeout(r,300)); await b.click(choice)
  await b.wait(`[...document.querySelectorAll('.ant-form-item')].find(el=>el.querySelector('label')?.textContent===${JSON.stringify(label)})?.querySelector('.ant-select-selection-item')?.textContent===${JSON.stringify(option)}`)
}
const latest = async id => {
  const result = await sql("SELECT HEX(response_json) FROM core_idempotency_record WHERE id=(SELECT aggregate_id FROM core_event_outbox WHERE aggregate_type='BUSINESS_ACTION' AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.action.actionCode'))='CONVERT' AND JSON_EXTRACT(payload_json,'$.action.recordId')="+id+" ORDER BY id DESC LIMIT 1)")
  assert.equal(result.length,1)
  return JSON.parse(Buffer.from(result[0][0],'hex').toString('utf8'))
}
const markers = id => sql('SELECT c.id,c.status,c.version,r.target_record_id,c.finished_at>=c.created_at FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+id)
const conversionAudits = id => sql("SELECT COUNT(*) FROM audit_event WHERE system_id=19 AND tenant_id=21 AND object_type='BUSINESS_RECORD' AND event_code='BUSINESS_RECORD_CONVERTED' AND result_code='SUCCESS' AND object_id='"+id+"'")
const startCount = actionId => sql("SELECT COUNT(*) FROM flow_action WHERE action_code='START' AND idempotency_key='business-action:"+actionId+"'")
const replay = result => request('POST',`/api/runtime/modules/${source}/records/${result.sourceId}/convert`,{version:0,targetModuleCode:target},{Prefer:'return=action','Idempotency-Key':result.idempotencyKey})
const runtimeSubmit = async record => {
  await go(`http://localhost:15173/systems/19?workspace=runtime&module=${source}`)
  const row = `[...document.querySelectorAll('tbody tr')].find(el=>el.innerText.includes(${JSON.stringify(record.title)}))`
  await b.wait(`Boolean(${row})`); await b.click(`${row}?.querySelector('input[type=checkbox]')`)
  await b.button('转化记录'); await b.wait("Boolean(document.querySelector('.ant-modal-content'))")
  await b.button('预览全部映射')
  await b.wait("[...document.querySelectorAll('button')].some(el=>el.textContent.replace(/\\s/g,'')==='确认批量转化'&&!el.disabled)")
  await b.button('确认批量转化')
  await b.wait("[...document.querySelectorAll('button')].some(el=>el.textContent.replace(/\\s/g,'')==='刷新处理结果'&&!el.disabled)")
}
try {
  if (phase === 'init') {
    assert(!facts.original_binding,'Fixtures already exist; no duplicate creation')
    facts.original_binding = await binding()
    facts.historical_flows = await sql('SELECT id,flow_version_id,status,MD5(business_snapshot_json) FROM flow_instance WHERE id<=50 ORDER BY id')
    const config = await request('GET',`/api/runtime/modules/${source}/configuration`)
    const fields = Object.fromEntries(config.configuration.fields.map(f=>[f.name,f.code]))
    facts.records = {}
    for (const [label,title] of [['after','转化模式验收·执行后流程'],['only','转化模式验收·仅流程']]) {
      facts.records[label] = await request('POST',`/api/runtime/modules/${source}/records`,{title,status:'ACTIVE',ownerMemberId:entry.systemMemberId,departmentId:3,participantMemberIds:[],
        fields:{[fields['巡检项目']]:title,[fields['仪表读数']]:3,[fields['正常运行']]:true,[fields['巡检备注']]:title+'客户'}})
    }
    await save(); await openBinding(); await inspect()
  } else if (phase === 'inspect') {
    await openBinding(); console.log(await b.evaluate('location.href')); await inspect()
    console.log(await b.evaluate("[...document.querySelectorAll('.ant-select')].map((el,index)=>({index,text:el.innerText}))"))
  } else if (phase === 'after' || phase === 'only') {
    assert(!facts[phase], 'This mode already verified; do not resubmit')
    const record = facts.records[phase]
    const mode = phase==='after'?'AFTER_EXECUTION':'FLOW_ONLY'
    const previousMode = (await binding()).executionMode
    await openBinding()
    await select('执行方式',phase==='after'?'保存后启动流程':'仅启动流程')
    await b.button('发布动作配置')
    if (previousMode !== mode) {
      await b.wait("document.body.innerText.includes('停用旧绑定并发布')")
      await b.button('停用旧绑定并发布')
    }
    for(let i=0;i<30;i++) {if((await binding()).executionMode===mode)break;await new Promise(r=>setTimeout(r,150))}
    const published = await binding(); assert.equal(published.executionMode,mode)
    assert.deepEqual(published.condition,facts.original_binding.condition)
    assert.equal(published.flowVersionId,facts.original_binding.flowVersionId)
    await b.screenshot(area+`spark-b07-conversion-modes-${phase}-binding.png`)
    let failureInstalled = false
    try {
      if (phase==='after') {
        // 故障仅作用于本次新建来源，模拟流程投递暂时失败；不影响其他实例或改动历史快照。
        assert.deepEqual(await sql("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='unexamine' AND TRIGGER_NAME='spark_b07_conversion_flow_failure'"),[['0']])
        await sql("DELIMITER $$\nCREATE TRIGGER spark_b07_conversion_flow_failure BEFORE INSERT ON flow_instance FOR EACH ROW BEGIN IF NEW.system_id=19 AND NEW.tenant_id=21 AND NEW.business_type='MODULE:"+source+"' AND NEW.business_id='"+record.id+"' AND JSON_UNQUOTE(JSON_EXTRACT(NEW.business_snapshot_json,'$.executionMode'))='AFTER_EXECUTION' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='指定验收来源的流程启动暂时失败'; END IF; END$$\nDELIMITER ;")
        failureInstalled = true
      }
      await runtimeSubmit(record)
      const result = await latest(record.id); result.sourceId = record.id
      assert.equal(result.mode,mode)
      const delivery = await request('GET',`/api/runtime/modules/${source}/records/action-results/${result.idempotencyRecordId}`)
      const modal = await b.evaluate("document.querySelector('.ant-modal-content').innerText")
      const stored = await markers(record.id)
      if (phase==='after') {
        assert.equal(delivery.status,'RETRYING'); assert.equal(delivery.businessStatus,'COMPLETED')
        assert.equal(stored.length,1); assert.deepEqual(stored[0].slice(1,3),['SUCCEEDED','1']); assert.equal(stored[0][4],'1')
        assert.deepEqual(await conversionAudits(record.id),[['1']]); assert(result.value.targetRecord)
        assert(modal.includes('转化已完成')&&modal.includes('自动重试')&&modal.includes('已转化'))
        facts.after_failed_delivery = {source_id:record.id,target_id:result.value.targetRecord.id,receipt_id:result.idempotencyRecordId,
          business_status:delivery.businessStatus,delivery_status:delivery.status,outbox:await sql('SELECT status,retry_count FROM core_event_outbox WHERE id='+result.outboxId),
          one_atomic_marker_result_audit:true,ui_message:modal}
        await b.screenshot(area+'spark-b07-conversion-modes-after-retrying.png')
        await save()
        await sql('DROP TRIGGER spark_b07_conversion_flow_failure'); failureInstalled = false
        let recovered
        for(let i=0;i<60;i++){recovered=await request('GET',`/api/runtime/modules/${source}/records/action-results/${result.idempotencyRecordId}`);if(recovered.status==='STARTED')break;await new Promise(r=>setTimeout(r,250))}
        assert.equal(recovered.status,'STARTED'); assert.equal(recovered.businessStatus,'COMPLETED')
        await b.button('刷新处理结果'); await b.wait("document.querySelector('.ant-modal-content').innerText.includes('流程已发起')")
        const instance = await request('GET',`/api/flow-runtime/instances/${recovered.flowInstanceId}`)
        assert.equal(instance.flowVersionId,published.flowVersionId)
        const original = await replay(result); assert(original.replayed); assert.equal(original.value.targetRecord.id,result.value.targetRecord.id)
        assert.deepEqual(await markers(record.id),stored); assert.deepEqual(await conversionAudits(record.id),[['1']]); assert.deepEqual(await startCount(result.idempotencyRecordId),[['1']])
        facts.after={source_id:record.id,target_id:result.value.targetRecord.id,receipt_id:result.idempotencyRecordId,
          flow_id:recovered.flowInstanceId,flow_version_id:instance.flowVersionId,recovered_delivery:recovered.status,
          original_replay_same_target:true,one_flow_start:true,ui_message:await b.evaluate("document.querySelector('.ant-modal-content').innerText")}
      } else {
        assert.equal(result.value,null); assert.equal(result.status,'FLOW_STARTED'); assert.equal(delivery.status,'STARTED')
        assert.deepEqual(stored,[]); assert.deepEqual(await conversionAudits(record.id),[['0']]); assert.deepEqual(await startCount(result.idempotencyRecordId),[['1']])
        assert(modal.includes('仅发起流程')&&modal.includes('业务数据未变更')&&!modal.includes('只重试失败项'))
        const before = await request('GET',`/api/runtime/modules/${source}/records/${record.id}`)
        assert.deepEqual(before,record)
        const original = await replay(result); assert(original.replayed); assert.equal(original.value,null); assert.equal(original.flowInstanceId,result.flowInstanceId)
        assert.deepEqual(await startCount(result.idempotencyRecordId),[['1']]); assert.deepEqual(await markers(record.id),[])
        facts.only={source_id:record.id,receipt_id:result.idempotencyRecordId,flow_id:result.flowInstanceId,
          no_target_marker_success_audit:true,source_unchanged:true,one_flow_start:true,original_replay_same_flow:true,ui_message:modal}
      }
      await b.screenshot(area+`spark-b07-conversion-modes-${phase}-result.png`); await save()
      console.log(`${mode}: real visual publish/submit, receipt/replay and independent DB assertions passed.`)
    } finally { if(failureInstalled)await sql('DROP TRIGGER spark_b07_conversion_flow_failure') }
  } else if (phase === 'regression') {
    for(const label of ['after','only']){
      const result=await latest(facts.records[label].id);result.sourceId=facts.records[label].id
      const original=await replay(result),delivery=await request('GET',`/api/runtime/modules/${source}/records/action-results/${result.idempotencyRecordId}`)
      assert(original.replayed);assert.equal(original.mode,label==='after'?'AFTER_EXECUTION':'FLOW_ONLY');assert.equal(delivery.status,'STARTED')
      if(label==='after'){assert.equal(original.value.targetRecord.id,facts.after.target_id);assert.equal((await markers(result.sourceId)).length,1)}
      else{assert.equal(original.value,null);assert.deepEqual(await markers(result.sourceId),[]);assert.deepEqual(await conversionAudits(result.sourceId),[['0']])}
      assert.deepEqual(await startCount(result.idempotencyRecordId),[['1']])
    }
    assert.deepEqual(await sql("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='unexamine' AND TRIGGER_NAME='spark_b07_conversion_flow_failure'"),[['0']])
    facts.current_backend_original_replays_passed=true;await save();console.log('Both conversion modes retain original replay/results on current backend; no failure trigger remains.')
  } else if (phase === 'restore') {
    const old = facts.original_binding
    const restored = await request('POST','/api/flow-bindings/publish',{moduleId:30,triggerEvent:'CONVERT',flowId:old.flowId,executionMode:old.executionMode,
      priorityOrder:old.priorityOrder,condition:old.condition,conditionExpression:old.conditionExpression,mutuallyExclusive:old.mutuallyExclusive,replaceExisting:true})
    assert.equal(restored.flowVersionId,old.flowVersionId); assert.deepEqual(restored.condition,old.condition)
    assert.deepEqual(await sql('SELECT id,flow_version_id,status,MD5(business_snapshot_json) FROM flow_instance WHERE id<=50 ORDER BY id'),facts.historical_flows)
    facts.restored_binding = restored; facts.historical_flows_unchanged = true; await save()
    console.log('Original semantic binding restored; historical flows unchanged.')
  } else throw new Error('Unknown phase')
} finally { b.close() }
