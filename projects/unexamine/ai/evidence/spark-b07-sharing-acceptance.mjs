/* 共享真实验收：独立多租户验收系统，公共 API 准备，原生页面授权/编辑/撤销；不复制正文。 */
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import {execFile} from 'node:child_process'
import {promisify} from 'node:util'
import {connect} from './spark-browser-qa.mjs'
const area='projects/unexamine/ai/evidence/',file=area+'spark-b07-sharing-facts.json',run=promisify(execFile)
const phase=process.argv[2],facts=await fs.readFile(file,'utf8').then(JSON.parse).catch(()=>({}))
const save=()=>fs.writeFile(file,JSON.stringify({...facts,checked_at:new Date().toISOString()},null,2)+'\n')
const api=async(token,method,path,body,headers={})=>{
 const r=await fetch('http://localhost:18080'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...headers},body:body===undefined?undefined:JSON.stringify(body)})
 return {http:r.status,...await r.json()}
}
const req=async(token,...args)=>{const r=await api(token,...args);assert(r.http>=200&&r.http<300,`${r.http}: ${r.code}: ${r.message}`);return r.data}
const init=await fs.readFile('projects/unexamine/backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java','utf8')
const platform=await req(null,'POST','/api/auth/login',{username:init.match(/default-admin\.username:([^}]+)/)[1],password:init.match(/default-admin\.initial-password:([^}]+)/)[1]})
const context=await req(platform.accessToken,'GET','/api/auth/me')
const enter=async tenant=>req(platform.accessToken,'POST',`/api/systems/${facts.system.systemId}${tenant?'/tenants/'+tenant:''}/enter`,{})
const sql=async query=>{const r=await run('docker',['exec','unexamine-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N unexamine -e "$1"','readback',query]);return r.stdout.trim()?r.stdout.trim().split('\n').map(l=>l.replace(/\r$/,'').split('\t')):[]}
const base=()=>`/api/runtime/modules/${facts.module.code}/records`
const ui=async(b,entry,record)=>{
 await b.command('Page.navigate',{url:'http://localhost:15173/'})
 await b.wait("location.origin==='http://localhost:15173'")
 const system={...context,systemId:facts.system.systemId,tenantId:entry.tenantId,memberId:entry.systemMemberId,tenantMemberId:entry.tenantMemberId,systemName:entry.systemName,tenantName:entry.tenantName,roleIds:entry.roleIds,permissions:entry.permissions,dataScopes:entry.dataScopes,contextRevision:entry.contextRevision,redrawScopes:entry.redrawScopes,tenantSwitchContext:entry.tenantSwitchContext}
 await b.evaluate(`sessionStorage.setItem('unexamine.platform.tokens',${JSON.stringify(JSON.stringify(platform))});sessionStorage.setItem('unexamine.platform.context',${JSON.stringify(JSON.stringify(context))});sessionStorage.setItem('unexamine.system.tokens',${JSON.stringify(JSON.stringify(entry.tokens))});sessionStorage.setItem('unexamine.system.context',${JSON.stringify(JSON.stringify(system))});true`)
 await b.command('Page.navigate',{url:`http://localhost:15173/systems/${facts.system.systemId}?workspace=runtime&module=${facts.module.code}${record?'&recordId='+record:''}`})
 await b.wait("document.body.innerText.includes('共享验收业务')")
 if(record)await b.wait("Boolean(document.querySelector('.ant-drawer-body'))")
}
if(phase==='prepare'){
 facts.system ||= await req(platform.accessToken,'POST','/api/systems',{code:'spark_sharing_acceptance',name:'共享运行验收中心',tenantMode:'MULTI'});await save()
 const entry=await enter(),token=entry.tokens.accessToken
 facts.source_tenant_id=entry.tenantId;facts.member_id=entry.systemMemberId;await save()
 facts.target ||= await req(token,'POST','/api/admin/system/tenants',{code:'collaborator',name:'协作工作空间'});await save()
 if(!facts.module){const draft=await req(token,'POST','/api/admin/module-config/modules',{groupName:'共享验收',code:'spark_shared_business',name:'共享验收业务'});facts.module={id:draft.module.id,code:draft.module.code};await save()}
 if(!facts.field){facts.field=await req(token,'POST',`/api/admin/module-config/modules/${facts.module.id}/fields`,{code:'subject',name:'业务名称',fieldType:'TEXT',required:true,uniqueValue:false,searchable:true,sortOrder:0,config:{}});await save()}
 const draft=await req(token,'GET',`/api/admin/module-config/modules/${facts.module.id}/draft`),action=draft.actions.find(a=>a.code==='SHARE')
 if(action.location!=='DETAIL_HEADER')await req(token,'PUT',`/api/admin/module-config/modules/${facts.module.id}/actions/SHARE`,{location:'DETAIL_HEADER',version:action.version,config:JSON.parse(action.configJson)})
 const check=await req(token,'GET',`/api/admin/module-config/modules/${facts.module.id}/publication-check`);assert(check.valid,JSON.stringify(check.issues))
 facts.publication=await req(token,'POST',`/api/admin/module-config/modules/${facts.module.id}/publish`,{expectedDraftRevision:check.draftRevision});await save()
 facts.record ||= await req(token,'POST',base(),{title:'来源租户·共享业务',status:'ACTIVE',ownerMemberId:entry.systemMemberId,participantMemberIds:[],fields:{subject:'来源租户·共享业务'}});await save()
 const target=await enter(facts.target.id)
 facts.own ||= await req(target.tokens.accessToken,'POST',base(),{title:'协作租户·自有业务',status:'ACTIVE',ownerMemberId:target.systemMemberId,participantMemberIds:[],fields:{subject:'协作租户·自有业务'}});await save()
 console.log('Isolated MULTI system/two tenants/one module/two records created through current APIs.')
}else if(phase==='readonly'){
 assert(!facts.readonly,'Default-readonly acceptance already completed')
 const source=await enter(),target=await enter(facts.target.id),b=await connect()
 try{
  const existing=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}/shares`)
  const active=existing.find(g=>g.targetTenantId===facts.target.id&&g.status==='ACTIVE')
  if(!active){const before=await req(target.tokens.accessToken,'GET',base()+'?tenantScope=SHARED');assert.equal(before.records.length,0)}
  await ui(b,source,facts.record.id);await b.button('共享给租户');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('额外允许动作')")
  const named=await b.evaluate("document.querySelector('.ant-modal-content').innerText");assert(named.includes('协作工作空间'))
  if(!active)await b.button('授权共享');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('长期有效')")
  const grants=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}/shares`);facts.grant=grants.find(g=>g.targetTenantId===facts.target.id);assert.deepEqual(facts.grant.allowedActions,['LIST','DETAIL']);await save()
  await b.screenshot(area+'spark-b07-sharing-default-grant.png')
  await ui(b,target)
  await b.wait("document.body.innerText.includes('来源租户·共享业务')&&document.body.innerText.includes('协作租户·自有业务')")
  assert(await b.evaluate("[...document.querySelectorAll('tbody tr')].find(e=>e.innerText.includes('来源租户·共享业务'))?.querySelector('small')?.innerText.includes('主工作空间')"))
  const scope=label=>b.click(`[...document.querySelectorAll('.ant-segmented-item')].find(e=>e.textContent===${JSON.stringify(label)})`)
  await scope('当前工作空间');await b.wait("document.querySelector('tbody')?.innerText.includes('协作租户·自有业务')&&!document.querySelector('tbody')?.innerText.includes('来源租户·共享业务')")
  await scope('共享给我');await b.wait("document.querySelector('tbody')?.innerText.includes('来源租户·共享业务')&&!document.querySelector('tbody')?.innerText.includes('协作租户·自有业务')")
  await b.click("[...document.querySelectorAll('tbody tr')].find(e=>e.innerText.includes('来源租户·共享业务'))?.querySelector('.record-title')")
  await b.wait("document.querySelector('.ant-drawer-body')?.innerText.includes('共享状态')")
  assert(!await b.evaluate("[...document.querySelectorAll('.ant-drawer button')].some(e=>e.getClientRects().length&&e.textContent.replace(/\\s/g,'')==='编辑')"))
  const rejected=await api(target.tokens.accessToken,'PUT',base()+`/${facts.record.id}`,{title:'不应写入',status:'ACTIVE',ownerMemberId:source.systemMemberId,participantMemberIds:[],version:facts.record.version,fields:{subject:'不应写入'}},{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()});assert.equal(rejected.http,404)
  assert.deepEqual(await sql('SELECT COUNT(*),MIN(tenant_id) FROM biz_record WHERE id='+facts.record.id),[['1',String(source.tenantId)]])
  const data=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}`);assert.equal(data.title,facts.record.title)
  facts.readonly={grant_id:facts.grant.id,ui_named_target:true,all_own_shared_filters:true,source_label:true,no_editor_for_readonly:true,forged_update_http:rejected.http,one_source_no_copy:true}
  await b.screenshot(area+'spark-b07-sharing-readonly-detail.png');await save()
 }finally{b.close()}
 console.log('Real named default share/filter/source detail; forged UPDATE denied, source unchanged/no copy.')
}else if(phase==='update'){
 assert(facts.readonly&&!facts.update,'Run readonly first; do not repeat accepted write')
 const source=await enter(),target=await enter(facts.target.id),b=await connect()
 try{
  await ui(b,source,facts.record.id);await b.button('共享给租户');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('额外允许动作')")
  const grantsBefore=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}/shares`)
  if(!grantsBefore.find(g=>g.targetTenantId===facts.target.id)?.allowedActions.includes('UPDATE')){
   await b.click("[...document.querySelectorAll('.ant-checkbox-wrapper')].find(e=>e.textContent==='编辑')")
   await b.button('授权共享')
  }
  await b.wait("[...document.querySelectorAll('.ant-modal-content tbody tr')].some(e=>e.innerText.includes('编辑'))")
  const grants=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}/shares`);facts.grant=grants.find(g=>g.targetTenantId===facts.target.id);assert.deepEqual(facts.grant.allowedActions,['LIST','DETAIL','UPDATE']);await save()
  const accepted=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}`)
  if(accepted.fields.subject!=='协作租户·获权编辑'){
   await ui(b,target,facts.record.id);await b.button('编辑');await b.wait("[...document.querySelectorAll('label')].some(e=>e.textContent==='业务名称')")
   await b.fill("[...document.querySelectorAll('.ant-form-item')].find(e=>e.querySelector('label')?.textContent==='业务名称')?.querySelector('input')",'协作租户·获权编辑')
   assert.equal(await b.evaluate("[...document.querySelectorAll('.ant-form-item')].find(e=>e.querySelector('label')?.textContent==='业务名称')?.querySelector('input')?.value"),'协作租户·获权编辑')
   await b.button('保存');await b.wait("![...document.querySelectorAll('.ant-drawer')].some(e=>e.getClientRects().length&&e.innerText.includes('编辑共享验收业务'))")
  }
  const data=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}`);assert.equal(data.fields.subject,'协作租户·获权编辑');assert.equal(data.dataTenantId,source.tenantId)
  const forged={title:data.title,status:data.status,ownerMemberId:data.ownerMemberId,participantMemberIds:data.participantMemberIds,version:data.version,fields:{subject:''}}
  const invalid=await api(target.tokens.accessToken,'PUT',base()+`/${facts.record.id}`,forged,{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()});assert.equal(invalid.http,422)
  const reshare=await api(target.tokens.accessToken,'POST',base()+`/${facts.record.id}/shares`,{targetTenantId:source.tenantId,allowedActions:[]});assert.equal(reshare.http,404)
  const cross=await api(source.tokens.accessToken,'POST',base()+`/${facts.record.id}/shares`,{targetTenantId:21,allowedActions:[]});assert.equal(cross.http,422);assert.equal(cross.code,'SHARE_TARGET_INVALID')
  const audit=JSON.parse((await sql("SELECT detail_json FROM audit_event WHERE event_code='BUSINESS_RECORD_UPDATED' AND object_id='"+facts.record.id+"' ORDER BY id DESC LIMIT 1"))[0][0])
  assert.equal(audit.actorTenantId,target.tenantId);assert.equal(audit.dataTenantId,source.tenantId);assert.equal(audit.shareId,facts.grant.id)
  facts.update={source_id:data.id,source_version:data.version,source_tenant:data.dataTenantId,ui_named_edit:true,invalid_source_required_http:invalid.http,reshare_http:reshare.http,cross_system_http:cross.http,audit_actor_and_data_tenants:true,audit}
  await b.screenshot(area+'spark-b07-sharing-granted-edit.png');await save()
 }finally{b.close()}
 console.log('Real granted shared edit stays on source; source required validation/re-share/cross-system restrictions and tenant audit passed.')
}else if(phase==='revoke'){
 assert(facts.update&&!facts.revoke,'Run granted edit first; do not repeat revoke')
 const source=await enter(),target=await enter(facts.target.id),b=await connect()
 const before=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}`)
 const usage=await sql('SELECT COUNT(*) FROM biz_tenant_share_usage WHERE share_id='+facts.grant.id)
 try{
  await ui(b,source,facts.record.id);await b.button('共享给租户');await b.wait("[...document.querySelectorAll('.ant-modal-content button')].some(e=>e.textContent.replace(/\\s/g,'')==='撤销')")
  await b.button('撤销');await b.button('确认撤销');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('已撤销')")
  const denied=await api(target.tokens.accessToken,'GET',base()+`/${facts.record.id}`);assert.equal(denied.http,404)
  const list=await req(target.tokens.accessToken,'GET',base()+'?tenantScope=SHARED');assert.equal(list.records.length,0)
  const after=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}`);assert.deepEqual(after.fields,before.fields);assert.equal(after.version,before.version)
  const grants=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}/shares`);assert.equal(grants.find(g=>g.id===facts.grant.id).status,'REVOKED')
  const finalUsage=await sql('SELECT COUNT(*) FROM biz_tenant_share_usage WHERE share_id='+facts.grant.id);assert(Number(finalUsage[0][0])>=Number(usage[0][0]))
  facts.revoke={ui_revoke:true,immediate_detail_http:denied.http,shared_list_empty:true,source_fields_version_unchanged:true,grant_history_retained:true,usage_history_retained:true}
  await b.screenshot(area+'spark-b07-sharing-revoked.png');await ui(b,target);await b.wait("document.querySelector('tbody')?.innerText.includes('协作租户·自有业务')&&!document.querySelector('tbody')?.innerText.includes('来源租户·共享业务')");await save()
 }finally{b.close()}
 console.log('Real revoke immediately removes recipient access/list relation; source and sharing/usage history remain.')
}else if(phase==='conversion-prepare'){
 const source=await enter(),token=source.tokens.accessToken,targetEntry=await enter(facts.target.id)
 if(!facts.conversion_target){const draft=await req(token,'POST','/api/admin/module-config/modules',{groupName:'共享验收',code:'spark_shared_target',name:'共享转化目标'});facts.conversion_target={id:draft.module.id,code:draft.module.code};await save()}
 if(!facts.target_field){facts.target_field=await req(token,'POST',`/api/admin/module-config/modules/${facts.conversion_target.id}/fields`,{code:'subject',name:'业务名称',fieldType:'TEXT',required:true,uniqueValue:true,searchable:true,sortOrder:0,config:{}});await save()}
 const publishModule=async id=>{const check=await req(token,'GET',`/api/admin/module-config/modules/${id}/publication-check`);assert(check.valid,JSON.stringify(check.issues));return req(token,'POST',`/api/admin/module-config/modules/${id}/publish`,{expectedDraftRevision:check.draftRevision})}
 facts.target_publication=await publishModule(facts.conversion_target.id);await save()
 const draft=await req(token,'GET',`/api/admin/module-config/modules/${facts.module.id}/draft`),action=draft.actions.find(a=>a.code==='CONVERT')
 await req(token,'PUT',`/api/admin/module-config/modules/${facts.module.id}/actions/CONVERT`,{location:'BATCH',version:action.version,config:{targets:[{moduleCode:facts.conversion_target.code,fieldMappings:{subject:'subject'}}]}})
 facts.conversion_publication=await publishModule(facts.module.id);await save()
 facts.conversion_source ||= await req(token,'POST',base(),{title:'显式共享·转化业务',status:'ACTIVE',ownerMemberId:source.systemMemberId,participantMemberIds:[],fields:{subject:'显式共享·转化业务'}});await save()
 const grants=await req(token,'GET',base()+`/${facts.conversion_source.id}/shares`)
 facts.conversion_grant=grants.find(g=>g.targetTenantId===facts.target.id&&g.status==='ACTIVE')||await req(token,'POST',base()+`/${facts.conversion_source.id}/shares`,{targetTenantId:facts.target.id,allowedActions:['CONVERT']});await save()
 const preview=await api(targetEntry.tokens.accessToken,'POST',base()+`/${facts.conversion_source.id}/conversion-preview`,{version:facts.conversion_source.version,targetModuleCode:facts.conversion_target.code})
 facts.shared_conversion_before={http:preview.http,code:preview.code,message:preview.message};await save()
 console.log('Current explicit-share conversion preview: '+preview.http+' '+preview.code)
}else if(phase==='flow-prepare'){
 const source=await enter(),target=await enter(facts.target.id)
 facts.flows ||= {}
 for(const [side,entry,name] of [['source',source,'来源租户生效流程'],['target',target,'协作租户覆盖流程']]){
  const token=entry.tokens.accessToken
  facts.flows[side] ||= await req(token,'POST','/api/flows',{code:'spark_share_'+side,name,description:'验收：区分共享业务使用来源绑定而非操作者当前租户覆盖。'});await save()
  const flow=await req(token,'GET',`/api/flows/${facts.flows[side].id}`)
  if(!flow.currentVersionId){
   await req(token,'PUT',`/api/flows/${flow.id}/draft`,{expectedVersion:flow.version,nodes:[{nodeKey:'start',nodeType:'START',name:'开始',positionX:100,positionY:100,config:{}},{nodeKey:'end',nodeType:'END',name:'结束',positionX:300,positionY:100,config:{}}],edges:[{edgeKey:'start_end',sourceNodeKey:'start',targetNodeKey:'end',priorityOrder:0,config:{}}]})
   const check=await req(token,'GET',`/api/flows/${flow.id}/publication-check`);assert(check.valid,JSON.stringify(check.issues))
   facts.flows[side].publication=await req(token,'POST',`/api/flows/${flow.id}/publish`,{expectedDraftRevision:check.draftRevision,changeSummary:'验收共享来源与当前租户绑定选择',simulationVariables:{}});await save()
  }
  const current=(await req(token,'GET',`/api/flow-bindings?moduleId=${facts.module.id}&triggerEvent=CONVERT`)).currentTenant.find(b=>b.status==='ACTIVE')
  facts.flows[side].binding=current||await req(token,'POST','/api/flow-bindings/publish',{moduleId:facts.module.id,triggerEvent:'CONVERT',flowId:flow.id,executionMode:side==='source'?'AFTER_EXECUTION':'FLOW_ONLY',priorityOrder:0,replaceExisting:true});await save()
 }
 console.log('Different source/recipient published CONVERT Flow bindings prepared via current APIs.')
}else if(phase==='conversion'){
 assert(!facts.conversion,'Accepted conversion cannot be repeated')
 const source=await enter(),target=await enter(facts.target.id),b=await connect()
 const path=base()+`/${facts.conversion_source.id}`,body={version:facts.conversion_source.version,targetModuleCode:facts.conversion_target.code}
 const existing=await sql('SELECT id FROM biz_record_conversion WHERE source_record_id='+facts.conversion_source.id)
 const preview=await req(target.tokens.accessToken,'POST',path+'/conversion-preview',body)
 if(!existing.length)assert(preview.executable,JSON.stringify(preview.issues));else assert(preview.alreadyConverted)
 try{
  if(!existing.length){
  await ui(b,target)
  await b.wait("document.querySelector('tbody')?.innerText.includes('显式共享·转化业务')")
  await b.click("[...document.querySelectorAll('tbody tr')].find(e=>e.innerText.includes('显式共享·转化业务'))?.querySelector('input[type=checkbox]')")
  await b.button('转化记录');await b.button('预览全部映射');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('映射可执行')")
  await b.button('确认批量转化')
  }
  await b.wait("[...document.querySelectorAll('.ant-modal-content tbody tr')].some(e=>e.innerText.includes('显式共享·转化业务')&&e.innerText.includes('已转化'))")
  const markers=await sql('SELECT c.id,c.tenant_id,r.target_record_id FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+facts.conversion_source.id)
  assert.equal(markers.length,1);assert.equal(Number(markers[0][1]),source.tenantId)
  const record=await req(target.tokens.accessToken,'GET',`/api/runtime/modules/${facts.conversion_target.code}/records/${markers[0][2]}`)
  assert.equal(record.dataTenantId,target.tenantId);assert.equal(record.fields.subject,facts.conversion_source.fields.subject)
  let rows=[]
  for(let i=0;i<40;i++){rows=await sql('SELECT id,tenant_id,flow_id,flow_version_id,HEX(business_snapshot_json) FROM flow_instance WHERE system_id='+facts.system.systemId+" AND JSON_EXTRACT(business_snapshot_json,'$.targetRecordId')="+facts.conversion_source.id);if(rows.length)break;await new Promise(r=>setTimeout(r,400))}
  assert.equal(rows.length,1);assert.equal(Number(rows[0][1]),source.tenantId);assert.equal(Number(rows[0][2]),facts.flows.source.id)
  const snapshot=JSON.parse(Buffer.from(rows[0][4],'hex').toString('utf8'))
  assert.equal(snapshot.actorTenantId,target.tenantId);assert.equal(snapshot.dataTenantId,source.tenantId)
  const stored=await sql("SELECT HEX(response_json),idempotency_key FROM core_idempotency_record WHERE id="+snapshot.actionId)
  const original=JSON.parse(Buffer.from(stored[0][0],'hex').toString('utf8'))
  const replay=await req(target.tokens.accessToken,'POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':stored[0][1]});assert(replay.replayed);assert.equal(replay.value.targetRecord.id,record.id)
  const second=await api(target.tokens.accessToken,'POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()});assert.equal(second.http,409);assert.equal(second.code,'RECORD_ALREADY_CONVERTED')
  assert.deepEqual(await sql('SELECT c.id,c.tenant_id,r.target_record_id FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+facts.conversion_source.id),markers)
  facts.conversion={source_id:facts.conversion_source.id,target_id:record.id,preview_executable:true,ui_batch_converted:true,marker_on_source_tenant:true,target_on_explicit_share_recipient:true,flow_id:Number(rows[0][0]),flow_definition_id:Number(rows[0][2]),source_flow_not_recipient_override:true,flow_snapshot_actor_and_data_tenants:true,original_request_replay_same_target:true,second_conversion_http:second.http,receipt_mode:original.mode}
  await b.screenshot(area+'spark-b07-sharing-conversion.png');await save()
 }finally{b.close()}
 console.log('Real shared batch conversion/owner marker/recipient target/source effective Flow/original replay/permanent uniqueness passed.')
}else if(phase==='own-regression'){
 assert(!facts.own_regression,'Own-conversion regression already accepted')
 const source=await enter(),target=await enter(facts.target.id)
 facts.own_conversion_source ||= await req(source.tokens.accessToken,'POST',base(),{title:'本租户转化·回归验收',status:'ACTIVE',ownerMemberId:source.systemMemberId,participantMemberIds:[],fields:{subject:'本租户转化·回归验收'}});await save()
 const path=base()+`/${facts.own_conversion_source.id}`,body={version:facts.own_conversion_source.version,targetModuleCode:facts.conversion_target.code}
 const absent=await api(target.tokens.accessToken,'POST',path+'/conversion-preview',body);assert.equal(absent.http,404)
 await req(source.tokens.accessToken,'POST',path+'/shares',{targetTenantId:target.tenantId,allowedActions:[]})
 const readonly=await api(target.tokens.accessToken,'POST',path+'/conversion-preview',body);assert.equal(readonly.http,404)
 const writeDenied=await api(target.tokens.accessToken,'POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()});assert.equal(writeDenied.http,404)
 assert.deepEqual(await sql('SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id='+facts.own_conversion_source.id),[['0']])
 const foreign=await api(source.tokens.accessToken,'POST',path+'/conversion-preview',{...body,targetModuleCode:'module_4513ff83a1'});assert.equal(foreign.http,404)
 const preview=await req(source.tokens.accessToken,'POST',path+'/conversion-preview',body);assert(preview.executable)
 const key=crypto.randomUUID(),result=await req(source.tokens.accessToken,'POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':key})
 assert.equal(result.mode,'AFTER_EXECUTION');assert.equal(result.value.targetRecord.dataTenantId,source.tenantId)
 const replay=await req(source.tokens.accessToken,'POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':key});assert(replay.replayed);assert.equal(replay.value.targetRecord.id,result.value.targetRecord.id)
 facts.own_regression={source_id:facts.own_conversion_source.id,target_id:result.value.targetRecord.id,current_own_source_lock_success:true,target_stays_current_tenant:true,original_replay_same_target:true,no_share_http:absent.http,readonly_share_preview_http:readonly.http,readonly_share_write_http:writeDenied.http,denied_no_marker:true,cross_system_module_http:foreign.http};await save()
 console.log('Current own conversion/replay still works; no share/readonly share/cross-system module denied before marker creation.')
}else if(phase==='audit-readback'){
 const source=await enter(),record=await req(source.tokens.accessToken,'GET',base()+`/${facts.record.id}`)
 const audit=JSON.parse((await sql("SELECT detail_json FROM audit_event WHERE event_code='BUSINESS_RECORD_UPDATED' AND object_id='"+facts.record.id+"' ORDER BY id DESC LIMIT 1"))[0][0])
 assert.equal(audit.afterFields.subject,record.fields.subject)
 facts.update.audit=audit;facts.update.utf8_audit_matches_source=true;await save()
 console.log('UTF-8 audit body matches the source API readback; earlier client-character-set rendering corrected.')
}else throw Error('Unknown phase')
