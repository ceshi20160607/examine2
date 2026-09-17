/* 当前字段映射真实验收：仅新建验收元数据和记录，经公共 API 与页面执行，读回并恢复临时配置。 */
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { connect } from './spark-browser-qa.mjs'
const run = promisify(execFile), area='projects/unexamine/ai/evidence/', file=area+'spark-b07-mapping-facts.json'
const phase=process.argv[2]
const api=async(method,path,body,token,headers={})=>{
 const r=await fetch('http://localhost:18080'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...headers},body:body===undefined?undefined:JSON.stringify(body)})
 const value=await r.json();return {http:r.status,...value}
}
const bootstrap=await fs.readFile('projects/unexamine/backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java','utf8')
const login=await api('POST','/api/auth/login',{username:bootstrap.match(/default-admin\.username:([^}]+)/)[1],password:bootstrap.match(/default-admin\.initial-password:([^}]+)/)[1]})
const platform=login.data, context=(await api('GET','/api/auth/me',undefined,platform.accessToken)).data
const entry=(await api('POST','/api/systems/19/enter',{},platform.accessToken)).data
const call=(method,path,body,headers)=>api(method,path,body,entry.tokens.accessToken,headers)
const request=async(...args)=>{const value=await call(...args);assert(value.http>=200&&value.http<300,`${value.http}: ${value.code}: ${value.message}`);return value.data}
const sql=async query=>{const r=await run('docker',['exec','unexamine-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine -e "$1"','readback',query]);return r.stdout.trim()?r.stdout.trim().split('\n').map(l=>l.replace(/\r$/,'').split('\t')):[]}
let facts=await fs.readFile(file,'utf8').then(JSON.parse).catch(()=>({}))
const save=()=>fs.writeFile(file,JSON.stringify({...facts,checked_at:new Date().toISOString()},null,2)+'\n')
const publish=async id=>{const check=await request('GET',`/api/admin/module-config/modules/${id}/publication-check`);assert(check.valid,JSON.stringify(check.issues));return request('POST',`/api/admin/module-config/modules/${id}/publish`,{expectedDraftRevision:check.draftRevision})}
if(phase==='prepare'){
 assert(!facts.dictionary,'Fixtures already exist')
 const d=await request('POST','/api/admin/dictionaries',{code:'spark_mapping_status',name:'映射验收进度',hierarchical:false})
 const item=await request('POST',`/api/admin/dictionaries/${d.dictionary.id}/items`,{code:'blue',label:'待跟进',sortOrder:0})
 const check=await request('GET',`/api/admin/dictionaries/${d.dictionary.id}/publication-check`)
 await request('POST',`/api/admin/dictionaries/${d.dictionary.id}/publish`,{expectedDraftRevision:check.draftRevision})
 facts.dictionary={id:d.dictionary.id,item};facts.modules={}
 for(const [side,name]of[['source','字段映射验收来源'],['target','字段映射验收目标']]){
  const draft=await request('POST','/api/admin/module-config/modules',{groupName:'字段映射验收',code:'spark_mapping_'+side,name})
  const id=draft.module.id;facts.modules[side]={id,code:draft.module.code,fields:{}}
  const definitions=[['subject','业务名称','TEXT',true,{}],['stage','跟进阶段','SINGLE_SELECT',true,side==='source'?{options:[{value:'blue',label:'待跟进'},{value:'green',label:'已联系'}]}:{}],
   ['visit','预约日期','DATE',false,{}],['memo','业务描述','RICH_TEXT',false,{}]]
  for(const[code,fieldName,fieldType,required,config]of definitions){
   const field=await request('POST',`/api/admin/module-config/modules/${id}/fields`,{code,name:fieldName,fieldType,required,uniqueValue:side==='target'&&code==='subject',searchable:false,sortOrder:Object.keys(facts.modules[side].fields).length*10,config,...(side==='target'&&code==='stage'?{dictionaryId:d.dictionary.id}:{})})
   facts.modules[side].fields[code]=field
  }
  if(side==='target')await publish(id)
 }
 const source=facts.modules.source,target=facts.modules.target
 const draft=await request('GET',`/api/admin/module-config/modules/${source.id}/draft`)
 const action=draft.actions.find(a=>a.code==='CONVERT')
 await request('PUT',`/api/admin/module-config/modules/${source.id}/actions/CONVERT`,{location:'BATCH',version:action.version,config:{targets:[{moduleCode:target.code,fieldMappings:{subject:'subject',stage:'stage',visit:'visit',memo:'memo'}}]}})
 facts.source_publication=await publish(source.id)
 facts.record=await request('POST',`/api/runtime/modules/${source.code}/records`,{title:'字典映射验收·正常',status:'ACTIVE',ownerMemberId:entry.systemMemberId,departmentId:3,participantMemberIds:[],fields:{subject:'字典映射验收·正常',stage:'blue',visit:'2026-10-01',memo:'<p>跟进业务说明</p>'}})
 await save()
 console.log('Isolated current metadata modules/dictionary/source prepared.')
}else if(phase==='live-retry'){
 assert(!facts.live_retry,'Current-rule live batch already accepted')
 const source=facts.modules.source,target=facts.modules.target
 const values={...facts.typed_source.fields,subject:'实时规则批量验收·正常'}
 facts.live_source ||= await request('POST',`/api/runtime/modules/${source.code}/records`,{title:values.subject,status:'ACTIVE',ownerMemberId:entry.systemMemberId,departmentId:3,participantMemberIds:[],fields:values})
 await save()
 const setGreen=async status=>{const draft=await request('GET',`/api/admin/dictionaries/${facts.dictionary.id}`),item=draft.items.find(i=>i.code==='green');await request('PUT',`/api/admin/dictionaries/${facts.dictionary.id}/items/${item.id}`,{label:item.label,color:item.color,sortOrder:item.sortOrder,parentId:item.parentId,status,version:item.version});const check=await request('GET',`/api/admin/dictionaries/${facts.dictionary.id}/publication-check`);return request('POST',`/api/admin/dictionaries/${facts.dictionary.id}/publish`,{expectedDraftRevision:check.draftRevision})}
 const b=await connect();let disabled=false
 try{
  await b.command('Page.navigate',{url:'http://localhost:15173/'})
  await b.wait("location.origin==='http://localhost:15173'")
  const system={...context,systemId:19,tenantId:entry.tenantId,memberId:entry.systemMemberId,tenantMemberId:entry.tenantMemberId,systemName:entry.systemName,tenantName:entry.tenantName,roleIds:entry.roleIds,permissions:entry.permissions,dataScopes:entry.dataScopes,contextRevision:entry.contextRevision,redrawScopes:entry.redrawScopes,tenantSwitchContext:entry.tenantSwitchContext}
  await b.evaluate(`sessionStorage.setItem('unexamine.platform.tokens',${JSON.stringify(JSON.stringify(platform))});sessionStorage.setItem('unexamine.platform.context',${JSON.stringify(JSON.stringify(context))});sessionStorage.setItem('unexamine.system.tokens',${JSON.stringify(JSON.stringify(entry.tokens))});sessionStorage.setItem('unexamine.system.context',${JSON.stringify(JSON.stringify(system))});true`)
  await b.command('Page.navigate',{url:`http://localhost:15173/systems/19?workspace=runtime&module=${source.code}`})
  for(const record of [facts.live_source,facts.rule_records['未使用字典选项停用']]){
   const row=`[...document.querySelectorAll('tbody tr')].find(e=>e.innerText.includes(${JSON.stringify(record.title)}))`
   await b.wait(`Boolean(${row})`);await b.click(`${row}?.querySelector('input[type=checkbox]')`)
  }
  await b.button('转化记录');await b.wait("Boolean(document.querySelector('.ant-modal-content'))")
  await b.button('预览全部映射');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('映射可执行')")
  // 预览之后配置变化，提交必须再次校验；不以旧的客户端预览放行。
  await setGreen('DISABLED');disabled=true
  await b.button('确认批量转化');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('字典选项不存在或已停用')")
  const failedText=await b.evaluate("document.querySelector('.ant-modal-content').innerText")
  assert(failedText.includes('已转化')&&failedText.includes('失败')&&!failedText.includes('服务处理失败'))
  const failed=facts.rule_records['未使用字典选项停用']
  assert.deepEqual(await sql('SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id='+failed.id),[['0']])
  const success=await sql('SELECT c.id,r.target_record_id FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+facts.live_source.id)
  assert.equal(success.length,1);await b.screenshot(area+'spark-b07-mapping-live-rule-rejected.png')
  await setGreen('ACTIVE');disabled=false
  await b.button('刷新失败项并预览');await b.wait("[...document.querySelectorAll('button')].some(e=>e.textContent.replace(/\\s/g,'')==='只重试失败项'&&!e.disabled)")
  await b.button('只重试失败项');await b.wait("![...document.querySelectorAll('button')].some(e=>e.textContent.replace(/\\s/g,'')==='只重试失败项')")
  const retry=await sql('SELECT c.id,r.target_record_id FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+failed.id)
  assert.equal(retry.length,1);assert.deepEqual(await sql('SELECT c.id,r.target_record_id FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+facts.live_source.id),success)
  const result=await request('GET',`/api/runtime/modules/${target.code}/records/${retry[0][1]}`);assert.equal(result.fields.stage,'green')
  facts.live_retry={sources:[facts.live_source.id,failed.id],targets:[Number(success[0][1]),Number(retry[0][1])],configuration_changed_after_preview:true,
   readable_partial_rejection:true,only_failed_retried:true,success_marker_unchanged:true,retry_target_dictionary_value:result.fields.stage}
  await b.screenshot(area+'spark-b07-mapping-live-rule-retry.png');await save()
 }finally{if(disabled)await setGreen('ACTIVE');b.close()}
 console.log('Real batch rejects changed target dictionary after preview; named failed-only retry preserves successful target.')
}else if(phase==='detail'){
 const directory=await request('GET','/api/system-directory')
 const record=await request('GET',`/api/runtime/modules/${facts.modules.target.code}/records/${facts.typed.target_id}`)
 const member=directory.people.find(person=>person.systemMemberId===record.fields.contact)
 assert(member,'Mapped member must resolve by system membership')
 const b=await connect()
 try{
  await b.command('Page.navigate',{url:`http://localhost:15173/systems/19?workspace=runtime&module=${facts.modules.target.code}&recordId=${facts.typed.target_id}`})
  await b.wait("document.body.innerText.includes('类型映射的业务说明')&&document.body.innerText.includes('扩展信息')")
  await b.wait(`document.querySelector('.ant-drawer-body')?.innerText.includes('经办成员\\n复制\\n'+${JSON.stringify(member.displayName)})`)
  await b.wait("document.querySelector('.ant-drawer-body')?.innerText.includes('关联来源\\n复制\\n字典映射验收·正常')")
  await b.screenshot(area+'spark-b07-mapping-target-detail.png')
  facts.final_detail={record_id:facts.typed.target_id,current_typed_schema:true,member_display_name:member.displayName,member_value_key:'systemMemberId',reference_record_label:true,actual_visible_business_values:await b.evaluate("[...document.querySelectorAll('.ant-drawer-body')].find(e=>e.getClientRects().length)?.innerText")}
  await b.button('编辑')
  await b.wait(`[...document.querySelectorAll('.ant-form-item')].some(e=>e.querySelector('label')?.textContent==='经办成员'&&e.innerText.includes(${JSON.stringify(member.displayName)}))`)
  facts.final_detail.member_editor_selected_name=true
  await b.button('取消');await save()
 }finally{b.close()}
 console.log('Current typed target detail opened and stable screenshot captured.')
}else if(phase==='before'||phase==='after'||phase==='positive'){
 const source=facts.modules.source,target=facts.modules.target,path=`/api/runtime/modules/${source.code}/records/${facts.record.id}`
 const preview=await request('POST',path+'/conversion-preview',{version:facts.record.version,targetModuleCode:target.code})
 facts[phase+'_preview']=preview
 if(phase==='before'){assert(!preview.executable);assert(preview.issues.some(i=>i.includes('跟进阶段')&&i.includes('可用选项')))}
 await save()
 if(phase==='before'){console.log('Real published dictionary conversion rejected: '+preview.issues.join('; '));process.exit(0)}
 assert(preview.executable,JSON.stringify(preview.issues))
 const b=await connect()
 try{
  await b.command('Page.navigate',{url:'http://localhost:15173/'})
  await b.wait("location.origin==='http://localhost:15173'")
  const system={...context,systemId:19,tenantId:entry.tenantId,memberId:entry.systemMemberId,tenantMemberId:entry.tenantMemberId,systemName:entry.systemName,tenantName:entry.tenantName,roleIds:entry.roleIds,permissions:entry.permissions,dataScopes:entry.dataScopes,contextRevision:entry.contextRevision,redrawScopes:entry.redrawScopes,tenantSwitchContext:entry.tenantSwitchContext}
  await b.evaluate(`sessionStorage.setItem('unexamine.platform.tokens',${JSON.stringify(JSON.stringify(platform))});sessionStorage.setItem('unexamine.platform.context',${JSON.stringify(JSON.stringify(context))});sessionStorage.setItem('unexamine.system.tokens',${JSON.stringify(JSON.stringify(entry.tokens))});sessionStorage.setItem('unexamine.system.context',${JSON.stringify(JSON.stringify(system))});true`)
  await b.command('Page.navigate',{url:`http://localhost:15173/systems/19?workspace=runtime&module=${target.code}`})
  await b.wait("[...document.querySelectorAll('button')].some(e=>e.textContent.replace(/\\s/g,'')==='新建字段映射验收目标')")
  await b.button('新建字段映射验收目标');await b.wait("[...document.querySelectorAll('label')].some(e=>e.textContent==='跟进阶段')")
  await b.click("[...document.querySelectorAll('.ant-form-item')].find(e=>e.querySelector('label')?.textContent==='跟进阶段')?.querySelector('.ant-select-selector')")
  await b.wait("Boolean(document.querySelector('.ant-select-dropdown'))")
  await new Promise(r=>setTimeout(r,300))
  const options=await b.evaluate("[...document.querySelectorAll('.ant-select-dropdown .ant-select-item-option-content')].filter(e=>e.getClientRects().length).map(e=>e.textContent)")
  facts[phase+'_target_form_options']=options
  if(phase==='before')assert.equal(options.length,0);else assert.deepEqual(options,['待跟进'])
  await b.screenshot(area+`spark-b07-mapping-dictionary-${phase}.png`)
  if(phase==='positive'){
   assert(!facts.positive,'Do not repeat saved fixture actions')
   let direct=(await request('GET',`/api/runtime/modules/${target.code}/records?page=1&pageSize=20`)).records.find(r=>r.title==='字典字段验收·页面新建')
   if(!direct){
    await b.click("[...document.querySelectorAll('.ant-select-item-option-content')].find(e=>e.getClientRects().length&&e.textContent==='待跟进')")
    await b.fill("[...document.querySelectorAll('.ant-form-item')].find(e=>e.querySelector('label')?.textContent==='业务名称')?.querySelector('input')",'字典字段验收·页面新建')
    await b.fill("[...document.querySelectorAll('.ant-form-item')].find(e=>e.querySelector('label')?.textContent==='预约日期')?.querySelector('input')",'2026-10-02')
    await b.button('保存')
    await b.wait("!document.querySelector('.ant-drawer-open') && document.body.innerText.includes('字典字段验收·页面新建')")
    direct=(await request('GET',`/api/runtime/modules/${target.code}/records?page=1&pageSize=20`)).records.find(r=>r.title==='字典字段验收·页面新建')
   }else{
    await b.command('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape'})
    await b.command('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape'})
    await b.button('取消')
   }
   assert.equal(direct.fields.stage,'blue');assert.equal(direct.fields.visit,'2026-10-02')
   await b.command('Page.navigate',{url:`http://localhost:15173/systems/19?workspace=runtime&module=${source.code}`})
   const row=`[...document.querySelectorAll('tbody tr')].find(e=>e.innerText.includes(${JSON.stringify(facts.record.title)}))`
   await b.wait(`Boolean(${row})`);await b.click(`${row}?.querySelector('input[type=checkbox]')`)
   await b.button('转化记录');await b.wait("Boolean(document.querySelector('.ant-modal-content'))")
   await b.button('预览全部映射')
   await b.wait("[...document.querySelectorAll('button')].some(e=>e.textContent.replace(/\\s/g,'')==='确认批量转化'&&!e.disabled)")
   await b.button('确认批量转化');await b.wait("document.querySelector('.ant-modal-content')?.innerText.includes('转化已完成')")
   const marker=await sql('SELECT c.id,c.status,r.target_record_id FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+facts.record.id)
   assert.equal(marker.length,1);assert.equal(marker[0][1],'SUCCEEDED')
   const converted=await request('GET',`/api/runtime/modules/${target.code}/records/${marker[0][2]}`)
   for(const code of ['subject','stage','visit','memo'])assert.deepEqual(converted.fields[code],facts.record.fields[code])
   await b.button('打开目标');await b.wait("document.body.innerText.includes('跟进业务说明')&&document.body.innerText.includes('待跟进')")
   assert(await b.evaluate(`location.search.includes('recordId=${converted.id}')`))
   await b.screenshot(area+'spark-b07-mapping-target-detail.png')
   facts.positive={direct_target_id:direct.id,direct_dictionary_value:direct.fields.stage,direct_visit:direct.fields.visit,
    converted_target_id:converted.id,mapped_fields:converted.fields,target_version:converted.version,
    one_atomic_marker_result:true,real_target_detail_named_label:true}
  }
 }finally{b.close()}
 await save();console.log(`${phase}: target dictionary mapping and real form options checked.`)
}else if(phase==='null-before'||phase==='null-after'){
 const source=facts.modules.source,target=facts.modules.target
 if(phase==='null-before'){
  assert(!facts.null_source,'NULL fixture already exists')
  for(const side of[target,source]){
   const field=await request('POST',`/api/admin/module-config/modules/${side.id}/fields`,{code:'blank',name:'明确空值',fieldType:'JSON',required:false,uniqueValue:false,searchable:false,sortOrder:250,config:{jsonSchema:{type:'NULL'}}})
   side.fields.blank=field
   if(side===source){
    const draft=await request('GET',`/api/admin/module-config/modules/${source.id}/draft`),action=draft.actions.find(a=>a.code==='CONVERT')
    await request('PUT',`/api/admin/module-config/modules/${source.id}/actions/CONVERT`,{location:'BATCH',version:action.version,config:{targets:[{moduleCode:target.code,fieldMappings:Object.fromEntries(Object.keys(source.fields).map(c=>[c,c]))}]}})
   }
   await publish(side.id)
  }
  facts.null_source=await request('POST',`/api/runtime/modules/${source.code}/records`,{title:'明确空值映射验收',status:'ACTIVE',ownerMemberId:entry.systemMemberId,departmentId:3,participantMemberIds:[],fields:{subject:'明确空值映射验收',stage:'blue',visit:'2026-10-04',blank:null}})
  assert(Object.hasOwn(facts.null_source.fields,'blank'));assert.equal(facts.null_source.fields.blank,null)
  await save()
 }
 const path=`/api/runtime/modules/${source.code}/records/${facts.null_source.id}`,body={version:0,targetModuleCode:target.code}
 const preview=await request('POST',path+'/conversion-preview',body),mapping=preview.mappings.find(m=>m.targetFieldCode==='blank')
 assert(mapping,JSON.stringify(preview.mappings))
 if(phase==='null-before'){assert.equal(mapping.status,'EMPTY');facts.null_before={source_id:facts.null_source.id,source_explicit_null:true,preview_mapping:mapping}}
 else{
  assert.equal(mapping.status,'READY');assert.equal(mapping.value,null)
  const result=await request('POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()})
  const converted=await request('GET',`/api/runtime/modules/${target.code}/records/${result.value.targetRecord.id}`)
  assert(Object.hasOwn(converted.fields,'blank'));assert.equal(converted.fields.blank,null)
  facts.null_after={source_id:facts.null_source.id,target_id:converted.id,explicit_null_preserved:true,preview_mapping:mapping}
 }
 await save();console.log(`${phase}: explicit JSON NULL mapping checked.`)
}else if(phase==='typed'){
 assert(!facts.typed,'Typed mapping already verified; no duplicate conversion')
 const source=facts.modules.source,target=facts.modules.target,dictionaryId=facts.dictionary.id
 if(!facts.hierarchical_dictionary){
  const created=await request('POST','/api/admin/dictionaries',{code:'spark_mapping_category',name:'映射验收业务分类',hierarchical:true}),id=created.dictionary.id
  const root=await request('POST',`/api/admin/dictionaries/${id}/items`,{code:'service',label:'客户服务',sortOrder:0})
  const leaf=await request('POST',`/api/admin/dictionaries/${id}/items`,{code:'visit',label:'客户回访',parentId:root.id,sortOrder:0})
  const check=await request('GET',`/api/admin/dictionaries/${id}/publication-check`)
  await request('POST',`/api/admin/dictionaries/${id}/publish`,{expectedDraftRevision:check.draftRevision})
  facts.hierarchical_dictionary={id,root,leaf};await save()
 }
 const definitions=[['notes','补充说明','MULTILINE_TEXT',{},'第一行\n第二行'],['phone','联系电话','PHONE',{},'13800138000'],
  ['email','联系邮箱','EMAIL',{},'qa@example.invalid'],['website','业务链接','URL',{},'https://example.com'],['identity','身份编码','ID_CARD',{},'11010519491231002X'],
  ['amount','业务数量','NUMBER',{},5],['budget','预算金额','MONEY',{currency:'CNY',precision:2},120.5],['scheduled','预约时间','DATETIME',{},'2026-10-01T09:30:00'],
  ['enabled','是否有效','BOOLEAN',{},true],['contact','经办成员','MEMBER',{},entry.systemMemberId],['department','所属部门','DEPARTMENT',{},3],
  ['related','关联来源','REFERENCE',{},facts.record.id],['state','业务状态','STATUS',{options:[{value:'open',label:'处理中'},{value:'closed',label:'已结束'}]},'open'],
  ['category','业务分类','CASCADE',{maxDepth:2,allowIntermediate:false,saveMode:'PATH'},[facts.hierarchical_dictionary.root.id,facts.hierarchical_dictionary.leaf.id]],
  ['extra','扩展信息','JSON',{jsonSchema:{type:'OBJECT',properties:[{key:'说明',required:true,schema:{type:'STRING'}}]}},{说明:'业务附加信息'}]]
 for(const side of[source,target]){
  let draft=await request('GET',`/api/admin/module-config/modules/${side.id}/draft`)
  for(const[code,name,fieldType,config]of definitions){
   let field=draft.fields.find(f=>f.code===code)
   if(!field)field=await request('POST',`/api/admin/module-config/modules/${side.id}/fields`,{code,name,fieldType,required:false,searchable:false,uniqueValue:false,sortOrder:Object.keys(side.fields).length*10,config,
    ...(fieldType==='REFERENCE'?{referenceModuleId:source.id}:{}),...(fieldType==='CASCADE'?{dictionaryId:facts.hierarchical_dictionary.id}:{})})
   side.fields[code]=field
  }
  if(side===target)facts.typed_target_publication=await publish(side.id)
 }
 const draft=await request('GET',`/api/admin/module-config/modules/${source.id}/draft`),action=draft.actions.find(a=>a.code==='CONVERT')
 await request('PUT',`/api/admin/module-config/modules/${source.id}/actions/CONVERT`,{location:'BATCH',version:action.version,config:{targets:[{moduleCode:target.code,fieldMappings:Object.fromEntries(Object.keys(source.fields).map(code=>[code,code]))}]}})
 await publish(source.id)
 const values={subject:'类型映射验收·正常',stage:'blue',visit:'2026-10-03',memo:'<p>类型映射的业务说明</p>',...Object.fromEntries(definitions.map(([code,,,,value])=>[code,value]))}
 const record=await request('POST',`/api/runtime/modules/${source.code}/records`,{title:values.subject,status:'ACTIVE',ownerMemberId:entry.systemMemberId,departmentId:3,participantMemberIds:[],fields:values})
 facts.typed_source=record;await save()
 const path=`/api/runtime/modules/${source.code}/records/${record.id}`,body={version:0,targetModuleCode:target.code},key=crypto.randomUUID()
 const preview=await request('POST',path+'/conversion-preview',body);assert(preview.executable,JSON.stringify(preview.issues));assert.equal(preview.mappings.length,Object.keys(values).length)
 const result=await request('POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':key});assert.equal(result.mode,'DIRECT')
 const converted=await request('GET',`/api/runtime/modules/${target.code}/records/${result.value.targetRecord.id}`)
 for(const code of Object.keys(values))assert.deepEqual(converted.fields[code],record.fields[code],code)
 const original=await request('POST',path+'/convert',body,{Prefer:'return=action','Idempotency-Key':key});assert(original.replayed);assert.equal(original.value.targetRecord.id,converted.id)
 assert.deepEqual(await sql('SELECT COUNT(*) FROM biz_record_conversion c JOIN biz_record_conversion_result r ON r.conversion_id=c.id WHERE c.source_record_id='+record.id),[['1']])
 facts.typed={source_id:record.id,target_id:converted.id,receipt_id:result.idempotencyRecordId,field_types:[...new Set(Object.values(source.fields).map(f=>f.fieldType))],mapped_count:Object.keys(values).length,
  stored_fields:converted.fields,one_marker_result:true,original_replay_same_target:true}
 await save();console.log(`${facts.typed.field_types.length} supported field families mapped/read back through one public action.`)
}else if(phase==='rules'){
 assert(!facts.rules,'Current-rule acceptance already completed')
 const source=facts.modules.source,target=facts.modules.target
 facts.rule_records ||= {};facts.rule_results ||= {}
 const make=async(label,changes={})=>{
  if(facts.rule_records[label])return facts.rule_records[label]
  const fields={...facts.typed_source.fields,subject:'当前规则验收·'+label,...changes}
  const record=await request('POST',`/api/runtime/modules/${source.code}/records`,{title:'当前规则验收·'+label,status:'ACTIVE',ownerMemberId:entry.systemMemberId,departmentId:3,participantMemberIds:[],fields})
  facts.rule_records[label]=record;await save();return record
 }
 const change=async(code,changes)=>{
  const draft=await request('GET',`/api/admin/module-config/modules/${target.id}/draft`),field=draft.fields.find(f=>f.code===code)
  return request('PUT',`/api/admin/module-config/modules/${target.id}/fields/${field.id}`,{name:field.name,required:field.required,uniqueValue:field.uniqueValue,searchable:field.searchable,dictionaryId:field.dictionaryId,referenceModuleId:field.referenceModuleId,sortOrder:field.sortOrder,status:field.status,config:JSON.parse(field.configJson),version:field.version,...changes})
 }
 const preview=record=>request('POST',`/api/runtime/modules/${source.code}/records/${record.id}/conversion-preview`,{version:record.version,targetModuleCode:target.code})
 const failure=async(record,label,message)=>{
  const planned=await preview(record);assert(!planned.executable);assert(planned.issues.some(i=>i.includes(message)),JSON.stringify(planned.issues))
  const count=await sql('SELECT COUNT(*) FROM biz_record WHERE module_id='+target.id)
  const rejected=await call('POST',`/api/runtime/modules/${source.code}/records/${record.id}/convert`,{version:record.version,targetModuleCode:target.code},{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()})
  assert.equal(rejected.http,409);assert.equal(rejected.code,'CONVERSION_MAPPING_INVALID')
  assert.deepEqual(await sql('SELECT COUNT(*) FROM biz_record_conversion WHERE source_record_id='+record.id),[['0']])
  assert.deepEqual(await sql('SELECT COUNT(*) FROM biz_record WHERE module_id='+target.id),count)
  assert.deepEqual(await sql("SELECT COUNT(*) FROM audit_event WHERE object_type='BUSINESS_RECORD' AND object_id='"+record.id+"' AND event_code='BUSINESS_RECORD_CONVERTED' AND result_code='SUCCESS'"),[['0']])
  facts.rule_results[label]={source_id:record.id,http:rejected.http,code:rejected.code,issues:planned.issues,no_target_marker_success_audit:true};await save()
 }
 const publishDictionary=async()=>{const check=await request('GET',`/api/admin/dictionaries/${facts.dictionary.id}/publication-check`);assert(check.valid,JSON.stringify(check.issues));return request('POST',`/api/admin/dictionaries/${facts.dictionary.id}/publish`,{expectedDraftRevision:check.draftRevision})}
 const option=async(code,status)=>{const draft=await request('GET',`/api/admin/dictionaries/${facts.dictionary.id}`),item=draft.items.find(i=>i.code===code);await request('PUT',`/api/admin/dictionaries/${facts.dictionary.id}/items/${item.id}`,{label:item.label,color:item.color,sortOrder:item.sortOrder,parentId:item.parentId,status,version:item.version});return publishDictionary()}
 const missing=await make('缺少预约日期',{visit:null}),unique=await make('重复业务名称',{subject:facts.typed_source.fields.subject})
 const disabled=await make('字典选项停用'),state=await make('状态选项停用')
 const reference=await make('关联目标'),dangling=await make('关联目标失效',{related:reference.id})
 facts.target_before_rules ||= await request('GET',`/api/admin/module-config/modules/${target.id}/published`);await save()
 let dateChanged=false,statusChanged=false,blueChanged=false
 try{
  if(!facts.rule_results.unique)await failure(unique,'unique','业务名称')
  if(!facts.corrected_required_retry){
  await change('visit',{required:true});dateChanged=true
  assert((await preview(missing)).executable,'Unpublished draft leaked into runtime')
  facts.required_target_publication=await publish(target.id)
  await failure(missing,'required_current_publication','预约日期')
  const updated=await request('PUT',`/api/runtime/modules/${source.code}/records/${missing.id}`,{title:missing.title,status:missing.status,ownerMemberId:missing.ownerMemberId,departmentId:missing.departmentId,participantMemberIds:missing.participantMemberIds,version:missing.version,fields:{...missing.fields,visit:'2026-10-05'}},{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()})
  assert.equal(updated.mode,'DIRECT');assert.equal(updated.value.version,missing.version+1)
  const retried=await request('POST',`/api/runtime/modules/${source.code}/records/${missing.id}/convert`,{version:updated.value.version,targetModuleCode:target.code},{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()})
  assert.equal(retried.value.targetRecord.fields.visit,'2026-10-05');facts.corrected_required_retry={source_id:missing.id,source_version:updated.value.version,target_id:retried.value.targetRecord.id}
  await save()
  }
  if(!facts.green_item){facts.green_item=await request('POST',`/api/admin/dictionaries/${facts.dictionary.id}/items`,{code:'green',label:'已联系',sortOrder:10});await save()}
  await publishDictionary()
  const oldDictionary=await request('GET',`/api/admin/dictionaries/${facts.dictionary.id}`),oldBlue=oldDictionary.items.find(i=>i.code==='blue')
  const used=await call('PUT',`/api/admin/dictionaries/${facts.dictionary.id}/items/${oldBlue.id}`,{label:oldBlue.label,color:oldBlue.color,sortOrder:oldBlue.sortOrder,parentId:oldBlue.parentId,status:'DISABLED',version:oldBlue.version})
  assert.equal(used.http,422);assert.equal(used.code,'DICTIONARY_ITEM_IN_USE');facts.dictionary_in_use_rejection={http:used.http,code:used.code}
  const unused=await make('未使用字典选项停用',{stage:'green'});assert((await preview(unused)).executable)
  await option('green','DISABLED');blueChanged=true
  await failure(unused,'dictionary_current_publication','跟进阶段')
  await option('green','ACTIVE');blueChanged=false
  await change('state',{config:{options:[{value:'open',label:'处理中',status:'DISABLED'},{value:'closed',label:'已结束',status:'ACTIVE'}]}});statusChanged=true
  await publish(target.id);await failure(state,'status_current_publication','业务状态')
  await change('state',{config:{options:[{value:'open',label:'处理中'},{value:'closed',label:'已结束'}]}});await publish(target.id);statusChanged=false
  const cascade=await make('级联映射值无效',{amount:999999})
  const setCategoryMapping=async code=>{const draft=await request('GET',`/api/admin/module-config/modules/${source.id}/draft`),action=draft.actions.find(a=>a.code==='CONVERT'),config=JSON.parse(action.configJson);config.targets[0].fieldMappings.category=code;await request('PUT',`/api/admin/module-config/modules/${source.id}/actions/CONVERT`,{location:action.location,version:action.version,config});return publish(source.id)}
  try{await setCategoryMapping('amount');await failure(cascade,'cascade_invalid_value','业务分类')}
  finally{await setCategoryMapping('category')}
  assert((await preview(dangling)).executable)
  const removed=await request('POST',`/api/runtime/modules/${source.code}/records/${reference.id}/delete`,{version:reference.version,reason:'验收：仅逻辑删除本次新建关联目标，验证转化硬限制。'},{Prefer:'return=action','Idempotency-Key':crypto.randomUUID()})
  assert(removed.value.deleted)
  await failure(dangling,'reference_current_record','关联来源')
  facts.rules={draft_invisible:true,current_required_option_dictionary_reference_unique_rejected:true,no_partial_targets:true,corrected_retry:true}
 }finally{
  if(blueChanged)await option('green','ACTIVE')
  if(statusChanged)await change('state',{config:{options:[{value:'open',label:'处理中'},{value:'closed',label:'已结束'}]}})
  if(dateChanged)await change('visit',{required:false})
  if(statusChanged||dateChanged)await publish(target.id)
  await save()
 }
 console.log('Current target publication/unique/required/dictionary/status/reference failures and corrected retry passed without partial targets.')
}else if(phase==='inspect'){console.log(JSON.stringify(facts,null,2))}
else throw Error('Unknown phase')
