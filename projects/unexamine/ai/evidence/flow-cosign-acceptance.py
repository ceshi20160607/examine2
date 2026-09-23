"""B09.1b.6 会签安全候选/真实并发及普通页面验收；只操作随机隔离夹具，凭证不落盘。"""
import ast
import copy
import json
import re
import secrets
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA=Path(__file__).parent
for filename,names in [('shared-actor-acceptance.py',{'call','api','sql'}),
    ('flow-node-submit-acceptance.py',{'publish_module','publish_role','record','flow','delivery','intent_action'})]:
    tree=ast.parse((AREA/filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,(ast.Import,ast.ImportFrom))
        or isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[]),filename,'exec'))
PATH=AREA/'flow-cosign-facts.json'
facts=json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step':'B09.1b.6','status':'running','code':'cosign_'+secrets.token_hex(5),'fields':{},'flows':[], 'actions':[], 'records':[], 'cases':[]}
assert facts['status']=='running'
j=(AREA.parents[1]/'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login=api('POST','/api/auth/login',{'username':re.search(r'default-admin\.username:([^}]+)',j).group(1),
    'password':re.search(r'default-admin\.initial-password:([^}]+)',j).group(1)})
entry=api('POST','/api/systems/54/tenants/57/enter',{},login['accessToken']);admin=entry['tokens']['accessToken']
original=json.loads((AREA/'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline'];code=facts['code']


def save():
    facts['checked_time']=datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def case(name,**values):facts['cases'].append({'case':name,**values});save()


def grant(update=True,score_writable=True):
    perms=copy.deepcopy(original['permissions'])+[{'resourceType':'MODULE','resourceCode':code,'actionCode':a,'dataScopeType':'ALL'}
        for a in ['LIST','DETAIL','CREATE']+(['UPDATE'] if update else [])+[facts['custom']['code']]]
    perms += [{'resourceType':'FLOW','resourceCode':'SYSTEM','actionCode':a,'dataScopeType':'ALL'}
        for a in ['VIEW_RUNTIME','HANDLE']]
    policies=copy.deepcopy(original['fieldPolicies'])+[{'resourceCode':code,'fieldCode':f,'channel':c,
        'readable':f!='hidden','writable':f!='hidden' and (f!='score' or score_writable),'maskStrategy':'FULL' if f=='credential' else None}
        for f in facts['fields'] for c in ['PAGE','FLOW']]
    publish_role(perms,policies)


def make(mode,members,label,timeout=False):
    v=api('POST','/api/flows',{'name':label,'code':code+'_'+secrets.token_hex(3)},admin)
    facts['flows'].append(v['id']);save()
    nodes=[{'nodeKey':k,'nodeType':t,'name':n,'positionX':i*230,'positionY':80,
        'config':{'moduleId':facts['module_id']} if t=='START' else {'approvalMode':mode} if t=='APPROVAL' else {},
        'assigneePolicy':{'type':'PERSON','tenantMemberIds':members} if t=='APPROVAL' else {},
        'formPolicy':{'editable':['subject','score','enabled','credential'],'required':['subject'],'hidden':['hidden']} if t=='APPROVAL' else {}}
        for i,(k,t,n) in enumerate([('start','START','开始'),('review','APPROVAL',label),('end','END','结束')])]
    edges=[{'edgeKey':str(i),'sourceNodeKey':a,'targetNodeKey':b,'priorityOrder':0,'config':{}}
        for i,(a,b) in enumerate([('start','review'),('review','end')])]
    if timeout:next(n for n in nodes if n['nodeType']=='APPROVAL')['timeoutPolicy']={'timeoutMinutes':1,'action':'AUTO_APPROVE'}
    api('PUT',f"/api/flows/{v['id']}/draft",{'expectedVersion':v['version'],'nodes':nodes,'edges':edges},admin)
    check=api('GET',f"/api/flows/{v['id']}/publication-check",token=admin);assert check['valid'],check['issues']
    api('POST',f"/api/flows/{v['id']}/publish",{'expectedDraftRevision':check['draftRevision'],'changeSummary':label,'simulationVariables':{}},admin)
    return v['id']


def bind(fid,trigger='CREATE',custom=None):
    api('POST','/api/flow-bindings/publish',{'moduleId':facts['module_id'],'triggerEvent':trigger,'actionId':custom,
        'flowId':fid,'executionMode':'AFTER_APPROVAL','priorityOrder':0,'condition':{'mode':'ALL','conditions':[]},
        'mutuallyExclusive':True,'replaceExisting':True},admin)


def start(label,values=None):
    v=api('POST',f'/api/runtime/modules/{code}/records',{'title':label,'status':'ACTIVE','participantMemberIds':[],
        'fields':{'subject':label,'score':10,'enabled':True,'credential':'synthetic-cosign-original','hidden':'隔离隐藏内容'} | (values or {})},
        admin,action_key='cosign_start_'+secrets.token_hex(6))
    facts['actions'].append(v);save();return v


def pending(action,member):return next(t for t in flow(action)['tasks'] if t['status']=='PENDING' and t['assigneeTenantMemberId']==member)


def form(task,token):return api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=token)


def handle(task,token,fields=None,revision=None,action='APPROVE',key=None):
    return call('POST',f"/api/flow-runtime/tasks/{task['id']}/actions",{'actionCode':action,'comment':'隔离会签验收',
        'idempotencyKey':key or 'cosign_handle_'+secrets.token_hex(6),'fieldValues':fields or {},'expectedFormRevision':revision},token)


def complete(action):
    value=delivery(action);assert value['businessStatus']=='EXECUTED',value
    facts['records'].append(value['recordId']);save();return record(value['recordId'])


def simultaneous(action,requests):
    # 独立连接仅锁隔离实例；确认证明两请求均在实例锁上等待后才释放，重现早期RR快照，无固定sleep猜时序。
    iid=int(action['flowInstanceId'])
    proc=subprocess.Popen(['docker','exec','-i','unexamine-mysql-1','sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N --unbuffered unexamine'],
        stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf-8')
    try:
        proc.stdin.write(f"START TRANSACTION; SELECT id FROM flow_instance WHERE id={iid} FOR UPDATE; SELECT 'LOCK_READY';\n");proc.stdin.flush()
        assert proc.stdout.readline().strip()==str(iid)
        assert proc.stdout.readline().strip()=='LOCK_READY'
        with ThreadPoolExecutor(max_workers=2) as pool:
            jobs=[pool.submit(handle,*args) for args in requests]
            deadline=time.monotonic()+10;waiters=0
            while time.monotonic()<deadline:
                waiters=int(sql("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA='unexamine' AND l.OBJECT_NAME='flow_instance' AND l.LOCK_DATA='"+str(iid)+"'")[0][0])
                if waiters==2:break
                if any(job.done() for job in jobs):break
                time.sleep(.05)
            proc.stdin.write('COMMIT;\n');proc.stdin.flush()
            results=[job.result(timeout=30) for job in jobs]
            assert waiters==2,('未建立真实双请求锁等待',waiters)
        case('actual_two_requests_wait_same_instance_lock_then_current_read',instance_id=iid,waiters=waiters,
            statuses=[status for status,_ in results])
        return results
    finally:
        if proc.poll() is None:
            proc.stdin.write('ROLLBACK;\n');proc.stdin.close();proc.wait(timeout=10)



def finish_api(fid,other,token,record_value):
    sequential=make('SEQUENTIAL',[entry['tenantMemberId'],other],'两人顺序会签');facts['sequential_flow']=sequential;save();bind(sequential)
    sequence=start('顺序会签原候选');a=pending(sequence,entry['tenantMemberId'])
    assert next(t for t in flow(sequence)['tasks'] if t['assigneeTenantMemberId']==other)['status']=='QUEUED'
    status,r=handle(a,admin,{'score':0,'credential':'synthetic-cosign-approved'},0);assert status==200
    b=pending(sequence,other);ctx=form(b,token)
    assert ctx['form']['record']['fields']['score']==0 and 'credential' not in ctx['form']['record']['fields']
    status,r=handle(b,token,{'enabled':False},ctx['formRevision']);assert status==200
    value=complete(sequence);assert value['fields']['score']==0 and value['fields']['enabled'] is False
    assert 'synthetic-cosign-approved' not in json.dumps(flow(sequence))
    case('sequential_configured_order_queued_latest_candidate_secret_not_prefilled_zero_false_once')
    bind(fid,'UPDATE');current=record(record_value['id'])
    update=api('PUT',f"/api/runtime/modules/{code}/records/{current['id']}",{'title':current['title'],'status':current['status'],
        'participantMemberIds':current['participantMemberIds'],'ownerMemberId':current['ownerMemberId'],'departmentId':current['departmentId'],
        'version':current['version'],'fields':{'subject':'原编辑候选','score':10,'enabled':True}},admin,action_key='cosign_update_'+secrets.token_hex(6))
    facts['actions'].append(update);save();a=pending(update,entry['tenantMemberId']);b=pending(update,other)
    status,r=handle(a,admin,{'subject':'会签批准编辑'},form(a,admin)['formRevision']);assert status==200
    ctx=form(b,token);status,r=handle(b,token,{'enabled':False},ctx['formRevision']);assert status==200
    value=delivery(update);assert value['businessStatus']=='EXECUTED'
    assert record(current['id'])['version']==current['version']+1 and record(current['id'])['fields']['enabled'] is False
    bind(fid,'MANUAL',facts['custom']['id']);current=record(current['id'])
    custom=api('POST',f"/api/runtime/modules/{code}/records/{current['id']}/actions/{facts['custom']['code']}",
        {'version':current['version'],'values':{'subject':'原字段动作','score':10}},admin,action_key='cosign_custom_'+secrets.token_hex(6))
    facts['actions'].append(custom);save();a=pending(custom,entry['tenantMemberId']);b=pending(custom,other)
    ctx=form(a,admin);assert {f['code'] for f in ctx['form']['configuration']['configuration']['fields']}=={'subject','score'}
    status,r=handle(a,admin,{'subject':'会签字段动作批准'},ctx['formRevision']);assert status==200
    ctx=form(b,token);status,r=handle(b,token,{'score':0},ctx['formRevision']);assert status==200
    value=delivery(custom);assert value['businessStatus']=='EXECUTED' and record(current['id'])['version']==current['version']+1
    case('same_all_sign_update_and_custom_original_actor_version_once')
    bind(fid);rejected=start('会签拒绝不生效');a=pending(rejected,entry['tenantMemberId']);b=pending(rejected,other)
    status,r=handle(a,admin,{'subject':None},0);assert status==422
    status,r=handle(a,admin,{'hidden':'伪造隐藏'},0);assert status==403
    status,r=handle(a,admin,{'subject':'未生效会签修改'},0);assert status==200
    status,r=handle(b,token,revision=form(b,token)['formRevision'],action='REJECT');assert status==200
    assert delivery(rejected)['businessStatus']=='REJECTED'
    assert not any(t['status'] in ['PENDING','QUEUED'] for t in flow(rejected)['tasks'])
    case('required_hidden_rejected_without_formal_save')
    facts['api_verified']=True;save();print(json.dumps({'api_verified':True,'module_id':facts['module_id'],'records':facts['records']}))

stage=sys.argv[1]
if stage=='api':
    assert not facts.get('api_started');facts['api_started']=True
    facts['golden_baseline']=sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id');save()
    module=api('POST','/api/admin/module-config/modules',{'name':'会签公共表单验收','code':code,'groupName':'会签隔离验收'},admin)['module']
    facts['module_id']=module['id'];save()
    for i,(f,n,t) in enumerate([('subject','会签主题','TEXT'),('score','会签数量','NUMBER'),('enabled','是否启用','BOOLEAN'),('credential','保护内容','SECRET'),('hidden','内部备注','TEXT')]):
        facts['fields'][f]=api('POST',f"/api/admin/module-config/modules/{module['id']}/fields",{'code':f,'name':n,'fieldType':t,'sortOrder':i,'required':False,'config':{}},admin);save()
    facts['custom']=api('POST',f"/api/admin/module-config/modules/{module['id']}/actions",{'name':'会签字段动作','location':'ROW','parameterFieldCodes':['subject','score'],'confirmation':False},admin);save();publish_module(module['id']);grant()
    username=code+'_'+secrets.token_hex(3)
    reg=api('POST','/api/auth/register',{'username':username,'password':secrets.token_urlsafe(24),'displayName':'会签接口处理人','systemName':'会签隔离注册系统'})
    facts['registration']={'account_id':reg['accountId'],'system_id':reg['systemId'],'username':username};save()
    member=api('POST','/api/admin/system/authorization/members',{'account':username,'roleIds':[114]},admin)
    other=member['tenantMemberId'];facts['api_member']=other;save()
    token=api('POST','/api/systems/54/tenants/57/enter',{},reg['platformTokens']['accessToken'])['tokens']['accessToken']
    fid=make('ALL_SIGN',[entry['tenantMemberId'],other],'两人会签安全表单');facts['all_flow']=fid;save();bind(fid)
    action=start('真实并发不同字段补丁');original_intent=intent_action(action)
    a=pending(action,entry['tenantMemberId']);b=pending(action,other)
    assert form(a,admin)['formRevision']==0 and form(b,token)['formRevision']==0
    result=simultaneous(action,[(a,admin,{'subject':'第一人候选'},0),(b,token,{'score':0},0)])
    assert sorted(status for status,_ in result)==[200,409]
    failed=next(i for i,(status,_) in enumerate(result) if status==409)
    assert result[failed][1]['code']=='FLOW_NODE_FORM_CONFLICT'
    retry_task,retry_token,retry_fields,_=[(a,admin,{'subject':'第一人候选'},0),(b,token,{'score':0},0)][failed]
    assert pending(action,retry_task['assigneeTenantMemberId'])['id']==retry_task['id']
    assert intent_action(action)==original_intent
    fresh=form(retry_task,retry_token);assert fresh['formRevision']>0
    key='cosign_retry_'+secrets.token_hex(6)
    status,r=handle(retry_task,retry_token,retry_fields,fresh['formRevision'],key=key);assert status==200
    status,replay=handle(retry_task,retry_token,retry_fields,fresh['formRevision'],key=key);assert status==200 and replay['data']['replayed']
    record_value=complete(action);assert record_value['fields']['subject']=='第一人候选' and record_value['fields']['score']==0
    case('parallel_stale_patch_409_task_preserved_refresh_disjoint_merge_once_replay',record_id=record_value['id'])
    empty=start('最后两人同时空确认');a=pending(empty,entry['tenantMemberId']);b=pending(empty,other)
    results=simultaneous(empty,[(a,admin,{},0),(b,token,{},0)]);assert all(status==200 for status,_ in results)
    assert flow(empty)['status']=='COMPLETED';complete(empty)
    case('parallel_last_two_empty_approvals_finish_instead_of_both_waiting')
    finish_api(fid,other,token,record_value)
elif stage in ['api_resume','api_recover']:
    assert facts.get('api_started') and not facts.get('api_verified')
    facts['resume_started']=True
    if not facts.get('registrations'):facts['registrations']=[facts['registration']]
    save()
    # 已通过120/121并发不重跑；仅关闭本次中断的未完成实例，保留排序失败/错误保护值测试假设历史。
    for failed in facts['actions']:
        v=flow(failed)
        if v['status'] not in ['WAITING','EXCEPTION']:continue
        api('POST',f"/api/flow-runtime/instances/{v['id']}/actions",{'actionCode':'TERMINATE','comment':'隔离验收中断实例收尾，不写正式业务',
            'idempotencyKey':'acceptance_failure_end_'+secrets.token_hex(6)},admin)
        case('interrupted_sequence_fixture_terminated_preserve_failure_history',instance_id=v['id'])
    username=code+'_'+secrets.token_hex(3)
    reg=api('POST','/api/auth/register',{'username':username,'password':secrets.token_urlsafe(24),'displayName':'会签恢复接口处理人','systemName':'会签恢复隔离注册系统'})
    facts['registration']={'account_id':reg['accountId'],'system_id':reg['systemId'],'username':username}
    facts['registrations'].append(facts['registration']);save()
    member=api('POST','/api/admin/system/authorization/members',{'account':username,'roleIds':[114]},admin)
    other=member['tenantMemberId'];facts['resume_member']=other;save()
    token=api('POST','/api/systems/54/tenants/57/enter',{},reg['platformTokens']['accessToken'])['tokens']['accessToken']
    fid=make('ALL_SIGN',[entry['tenantMemberId'],other],'当前会签编辑及字段动作组合');facts['resume_all_flow']=fid;save()
    finish_api(fid,other,token,record(facts['records'][0]))
elif stage=='diagnostic':
    for a in facts['actions']:
        v=flow(a)
        print(json.dumps({'instance_id':v['id'],'status':v['status'],'flow_id':v['flowId'],
            'tasks':[{k:t[k] for k in ['id','status','assigneeTenantMemberId','assigneeName']} for t in v['tasks']]},ensure_ascii=False))
elif stage=='timeout_guard':
    assert facts.get('api_verified') and not facts.get('timeout_verified')
    fid=make('ALL_SIGN',[entry['tenantMemberId']],'会签必填超时安全验收',True);facts['timeout_flow']=fid;save();bind(fid)
    action=start('会签缺失必填超时不可空批准',{'subject':None});facts['timeout_action']=action;save()
    # 仅把隔离实例持久化job期限提前，触发真实既有worker；不改服务时钟或其他任务期限。
    iid=int(action['flowInstanceId']);sql(f"UPDATE flow_job SET next_run_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE),version=version+1 WHERE instance_id={iid} AND job_type='TASK_TIMEOUT' AND status='PENDING'")
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        v=flow(action)
        if v['status']=='EXCEPTION':break
        time.sleep(.2)
    assert v['status']=='EXCEPTION' and pending(action,entry['tenantMemberId'])['status']=='PENDING'
    assert not any(a['actionCode']=='APPROVE' for a in v['actions'])
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND deleted=0 AND archived=0")==[[str(len(facts['records']))]]
    api('POST',f'/api/flow-runtime/instances/{iid}/actions',{'actionCode':'TERMINATE','comment':'隔离自动同意必填安全负向验收收尾','idempotencyKey':'timeout_guard_end_'+secrets.token_hex(6)},admin)
    facts['timeout_verified']=True;case('real_worker_auto_approve_with_form_not_bypass_required_incident_task_kept_no_save',instance_id=iid)
    print(json.dumps({'timeout_guard_verified':True}))
elif stage=='gui_prepare':
    assert facts.get('api_verified') and not facts.get('gui_action')
    fid=make('ALL_SIGN',[entry['tenantMemberId'],108],'普通页面会签冲突核对');facts['gui_flow']=fid;save();bind(fid)
    action=start('普通页面会签冲突验收');facts['gui_action']=action;save()
    print(json.dumps({'instance_id':action['flowInstanceId'],'ordinary_task':pending(action,108)['id']}))
elif stage=='gui_other_submit':
    assert facts.get('gui_action') and not facts.get('gui_other_submitted')
    action=facts['gui_action'];a=pending(action,entry['tenantMemberId'])
    status,r=handle(a,admin,{'subject':'另一人最新会签主题','score':8},form(a,admin)['formRevision']);assert status==200
    facts['gui_other_submitted']=True;case('gui_ordinary_opened_old_form_then_other_person_changed_candidate')
    print('other person submitted, ordinary stale form must conflict')
elif stage=='gui_final':
    assert facts.get('gui_other_submitted')
    action=facts['gui_action'];value=complete(action)
    assert value['fields']['subject']=='普通会签我保留的修改' and value['fields']['score']==0 and value['fields']['enabled'] is False
    assert flow(action)['status']=='COMPLETED'
    facts['gui_verified']=True;case('ordinary_gui_409_preserve_input_reload_safe_latest_explicit_submit_once',record_id=value['id'])
    print(json.dumps({'gui_verified':True,'record_id':value['id']}))
elif stage=='gui_revoke_prepare':
    assert facts.get('gui_verified') and not facts.get('revoke_action')
    bind(facts['gui_flow']);action=start('普通会签恢复时字段撤权');facts['revoke_action']=action;save()
    print(json.dumps({'instance_id':action['flowInstanceId'],'ordinary_task':pending(action,108)['id']}))
elif stage=='gui_revoke_other_submit':
    action=facts['revoke_action'];a=pending(action,entry['tenantMemberId'])
    status,r=handle(a,admin,{'subject':'字段撤权前另一人候选','score':8},form(a,admin)['formRevision']);assert status==200
    facts['revoke_other_submitted']=True;save();print('other actor submitted; ordinary form now stale')
elif stage=='gui_revoke_field':
    assert facts.get('revoke_other_submitted');grant(score_writable=False)
    facts['score_revoked']=True;case('ordinary_old_session_score_write_permission_revoked_before_conflict_reload')
    print('only score write permission removed; other module/flow access kept')
elif stage=='gui_revoke_final':
    assert facts.get('score_revoked');value=complete(facts['revoke_action'])
    assert value['fields']['subject']=='字段撤权后保留的主题' and value['fields']['score']==8 and value['fields']['enabled'] is False
    facts['revoke_gui_verified']=True;grant();case('ordinary_gui_reload_drops_revoked_score_patch_preserves_allowed_subject_and_false',record_id=value['id'])
    print(json.dumps({'revoked_field_patch_removed_verified':True,'record_id':value['id']}))
elif stage=='termination_guard':
    assert facts.get('revoke_gui_verified') and not facts.get('termination_verified')
    platform=login['accessToken'];v=api('POST','/api/flows',{'name':'排队会签终止验收','code':code+'_terminal'},platform)
    fid=v['id'];facts['termination_flow']=fid;facts['flows'].append(fid);save()
    account_id=api('GET','/api/auth/me',token=platform)['accountId']
    nodes=[{'nodeKey':k,'nodeType':t,'name':n,'positionX':i*230,'positionY':80,
        'config':{'approvalMode':'SEQUENTIAL'} if t=='APPROVAL' else {},
        'assigneePolicy':{'type':'PERSON','platformAccountIds':[account_id,69]} if t=='APPROVAL' else {},'formPolicy':{}}
        for i,(k,t,n) in enumerate([('start','START','开始'),('review','APPROVAL','顺序待办'),('end','END','结束')])]
    edges=[{'edgeKey':str(i),'sourceNodeKey':a,'targetNodeKey':b,'priorityOrder':0,'config':{}}
        for i,(a,b) in enumerate([('start','review'),('review','end')])]
    api('PUT',f'/api/flows/{fid}/draft',{'expectedVersion':v['version'],'nodes':nodes,'edges':edges},platform)
    check=api('GET',f'/api/flows/{fid}/publication-check',token=platform);assert check['valid'],check['issues']
    api('POST',f'/api/flows/{fid}/publish',{'expectedDraftRevision':check['draftRevision'],'changeSummary':'终止同时取消当前及排队会签','simulationVariables':{}},platform)
    started=api('POST','/api/flow-runtime/instances',{'flowId':fid,'title':'排队会签终止实测','variables':{},'businessSnapshot':{},
        'idempotencyKey':'terminal_start_'+secrets.token_hex(6)},platform)['instance']
    facts['termination_instance']=started['id'];save();assert [t['status'] for t in started['tasks']]==['PENDING','QUEUED']
    key='terminal_cancel_'+secrets.token_hex(6)
    body={'actionCode':'TERMINATE','comment':'隔离会签终止同时取消排队任务','idempotencyKey':key}
    done=api('POST',f"/api/flow-runtime/instances/{started['id']}/actions",body,platform)
    assert done['instance']['status']=='TERMINATED' and all(t['status']=='CANCELLED' and t['completedAt'] for t in done['instance']['tasks'])
    replay=api('POST',f"/api/flow-runtime/instances/{started['id']}/actions",body,platform);assert replay['replayed']
    # 126/122是本次旧排序失败夹具的残留；按已验证新业务规则精确修复，不借SQL作为终止功能证据。
    repair=sql("UPDATE flow_task t JOIN flow_instance i ON i.id=t.instance_id SET t.status='CANCELLED',t.completed_at=NOW(),t.updated_at=NOW(),t.version=t.version+1 WHERE t.id=126 AND t.instance_id=122 AND t.status='QUEUED' AND i.status='TERMINATED' AND i.flow_id=46 AND i.system_id=54 AND i.tenant_id=57; SELECT ROW_COUNT()")
    assert repair==[['1']]
    facts['termination_verified']=True;case('new_real_sequential_terminal_cancels_pending_and_queued_replay_then_exact_old_fixture_repair',instance_id=started['id'],repair_task_id=126)
    print(json.dumps({'pending_and_queued_cancelled_verified':True}))
elif stage=='cleanup':
    assert facts.get('api_verified') and facts.get('gui_verified')
    role=next(r for r in api('GET','/api/admin/system/authorization',token=admin)['roles'] if r['id']==114)
    if role['permissions']!=original['permissions'] or role['fieldPolicies']!=original['fieldPolicies']:
        publish_role(original['permissions'],original['fieldPolicies'])
    archived={int(r[0]) for r in sql(f"SELECT id FROM biz_record WHERE module_id={facts['module_id']} AND archived=1")}
    for rid in facts['records']:
        if rid in archived:continue
        v=record(rid);api('POST',f'/api/runtime/modules/{code}/records/{rid}/archive',{'version':v['version'],'reason':'隔离会签验收完成'},admin)
    mid=facts['module_id'];fids=','.join(map(str,facts['flows']))
    # 仅停用本次随机模块的绑定/流程和专属空分组；已形成动作历史和审计全部保留。
    sql(f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND status='ACTIVE'; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({fids}) AND system_id=54 AND owner_tenant_id=57; UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND owner_tenant_id=57; UPDATE cfg_module_group g SET status='DISABLED',version=version+1 WHERE g.id IN(SELECT group_id FROM cfg_module WHERE id={mid}) AND g.system_id=54 AND g.owner_tenant_id=57 AND NOT EXISTS(SELECT 1 FROM cfg_module m WHERE m.group_id=g.id AND m.id<>{mid})")
    if facts.get('termination_flow'):
        sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={facts['termination_flow']} AND context_type='PLATFORM' AND platform_id=1 AND code='{code}_terminal'")
    for reg in facts.get('registrations',[facts['registration']]):
        aid=reg['account_id'];sid=reg['system_id'];username=reg['username']
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND sm.system_id=54 AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND system_id=54; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='cosign-acceptance-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{username}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
    assert sql(f"SELECT COUNT(*) FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id WHERE i.flow_id IN({fids}) AND t.status IN('PENDING','QUEUED')")==[['0']]
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')==facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0 AND deleted=0')==[['4']]
    facts['cleanup_verified']=True;case('role_exactly_restored_isolated_archive_disable_no_pending_golden_unchanged')
    print(json.dumps({'cleanup_verified':True}))
else:raise AssertionError('未知验收阶段')
