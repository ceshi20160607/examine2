"""B09.1b.5 只读处理人实际验收；凭证仅存进程内存，隔离数据精确收尾。"""
import ast
import copy
import json
import re
import secrets
import sys
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
for name, names in [('shared-actor-acceptance.py', {'call','api','sql'}),
    ('flow-node-submit-acceptance.py', {'publish_module','publish_role','record','flow','current_task','delivery','start_update'}),
    ('flow-node-create-acceptance.py', {'terminal'})]:
    tree = ast.parse((AREA/name).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,(ast.Import,ast.ImportFrom))
        or isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[]),name,'exec'))
PATH = AREA/'flow-readonly-task-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step':'B09.1b.5','status':'running','code':'readonly_task_'+secrets.token_hex(5),'fields':{},'cases':[],'records':[]}
assert facts['status']=='running'
j=(AREA.parents[1]/'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login=api('POST','/api/auth/login',{'username':re.search(r'default-admin\.username:([^}]+)',j).group(1),
    'password':re.search(r'default-admin\.initial-password:([^}]+)',j).group(1)})
admin=api('POST','/api/systems/54/tenants/57/enter',{},login['accessToken'])['tokens']['accessToken']
original=json.loads((AREA/'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline'];code=facts['code']


def save():
    facts['checked_time']=datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def case(name,**values):
    facts['cases'].append({'case':name,**values});save()


def grant(edit=False):
    p=copy.deepcopy(original['permissions'])
    p += [{'resourceType':'MODULE','resourceCode':code,'actionCode':a,'dataScopeType':'ALL'}
        for a in ['LIST','DETAIL']+(['UPDATE'] if edit else [])]
    p += [{'resourceType':'FLOW','resourceCode':'SYSTEM','actionCode':a,'dataScopeType':'ALL'} for a in ['VIEW_RUNTIME','HANDLE']]
    policies=copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode':code,'fieldCode':f,'channel':c,'readable':f!='hidden','writable':edit and f!='hidden',
        'maskStrategy':'FULL' if f=='credential' else None} for f in facts['fields'] for c in ['PAGE','FLOW']]
    publish_role(p,policies)


def count():
    return int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0 AND deleted=0")[0][0])


def make(member,label):
    value=api('POST','/api/flows',{'code':code+'_'+secrets.token_hex(3),'name':label},admin)
    facts.setdefault('flows',[]).append(value['id']);save()
    nodes=[{'nodeKey':key,'nodeType':kind,'name':label,'positionX':i*220,'positionY':80,
        'config':{'moduleId':facts['module_id']} if kind=='START' else {'approvalMode':'OR_SIGN'} if kind=='APPROVAL' else {},
        'assigneePolicy':{'type':'PERSON','tenantMemberIds':[member]} if kind=='APPROVAL' else {},
        'formPolicy':{'editable':list(facts['fields']),'required':['subject'],'hidden':['hidden']} if kind=='APPROVAL' else {}}
        for i,(key,kind,label) in enumerate([('start','START','开始'),('review','APPROVAL','核对已有业务内容'),('end','END','结束')])]
    nodes[1]['formPolicy']['editable'].remove('hidden')
    edges=[{'edgeKey':str(i),'sourceNodeKey':a,'targetNodeKey':b,'priorityOrder':0,'config':{}}
        for i,(a,b) in enumerate([('start','review'),('review','end')])]
    value=api('PUT',f"/api/flows/{value['id']}/draft",{'expectedVersion':value['version'],'nodes':nodes,'edges':edges},admin)
    check=api('GET',f"/api/flows/{value['id']}/publication-check",token=admin);assert check['valid'],check['issues']
    api('POST',f"/api/flows/{value['id']}/publish",{'expectedDraftRevision':check['draftRevision'],'changeSummary':'合法只读处理人的候选确认验收','simulationVariables':{}},admin)
    return value['id']


def bind(fid,trigger='CREATE',custom=None):
    return api('POST','/api/flow-bindings/publish',{'moduleId':facts['module_id'],'flowId':fid,'triggerEvent':trigger,'actionId':custom,
        'executionMode':'AFTER_APPROVAL','condition':{'mode':'ALL','conditions':[]},'priorityOrder':0,'mutuallyExclusive':True,'replaceExisting':True},admin)


def start(subject):
    return api('POST',f'/api/runtime/modules/{code}/records',{'title':subject or '节点必填尚未补充','status':'ACTIVE',
        'participantMemberIds':[],'fields':{'subject':subject,'score':0,'enabled':False,'choices':['one','two'],
        'credential':'synthetic-readonly-original-secret','hidden':'只读者不能查看的内部备注'}},admin,
        action_key='readonly_origin_'+secrets.token_hex(6))


def submit(task,token,fields=None):
    return call('POST',f"/api/flow-runtime/tasks/{task['id']}/actions",{'actionCode':'APPROVE','fieldValues':fields or {},
        'idempotencyKey':'readonly_handle_'+secrets.token_hex(6)},token)


def finish(action,token,fields=None):
    status,result=submit(current_task(action),token,fields);assert status==200,(status,result.get('code'),result.get('message'))
    result=delivery(action);assert result['businessStatus']=='EXECUTED';facts['records'].append(result['recordId']);save();return result


stage=sys.argv[1]
if stage=='prepare':
    assert not facts.get('prepared')
    facts['golden_baseline']=sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN (151,154,170) ORDER BY id');save()
    module=api('POST','/api/admin/module-config/modules',{'name':'只读审批权限验收','code':code,'groupName':'只读审批隔离验收'},admin)['module']
    facts['module_id']=module['id'];save()
    for i,(f,label,kind) in enumerate([('subject','业务主题','TEXT'),('score','数量','NUMBER'),('enabled','是否启用','BOOLEAN'),
        ('choices','业务选项','MULTI_SELECT'),('credential','受保护内容','SECRET'),('hidden','内部备注','TEXT')]):
        facts['fields'][f]=api('POST',f"/api/admin/module-config/modules/{module['id']}/fields",{'code':f,'name':label,'fieldType':kind,
            'sortOrder':i,'required':False,'dictionaryId':11 if f=='choices' else None,'config':{}},admin);save()
    publish_module(module['id']);grant();facts['gui_flow']=make(108,'普通只读审批人核对与确认');save();bind(facts['gui_flow'])
    facts['gui_action']=start('普通只读用户可以审批已有候选');facts['gui_original']=terminal(facts['gui_action']);save()
    assert count()==0;facts['prepared']=True;case('isolated_original_root_create_ordinary_readonly_handler_pending',instance_id=facts['gui_action']['flowInstanceId'])
    print(json.dumps({'module_id':facts['module_id'],'code':code,'instance_id':facts['gui_action']['flowInstanceId'],'formal_count':0},ensure_ascii=False))
elif stage=='gui_final':
    result=terminal(facts['gui_action']);assert result['status']=='EXECUTED' and count()==1
    rid=result['value']['id'];value=record(rid);assert value['fields']['score']==0 and value['fields']['enabled'] is False
    assert value['fields']['subject']=='普通只读用户可以审批已有候选'
    facts['records'].append(rid);facts['gui_verified']=True;case('ordinary_readonly_gui_confirm_without_update_or_dirty_patch',record_id=rid)
    print(json.dumps({'record_id':rid,'formal_count':1}))
elif stage=='api':
    assert facts.get('gui_verified') and not facts.get('api_verified')
    # 新合成身份只为HTTP负向鉴权；注册及进入回执的凭证不写入证据。
    username='readonly_actor_'+secrets.token_hex(6)
    registration=api('POST','/api/auth/register',{'username':username,'password':secrets.token_urlsafe(24),
        'displayName':'只读节点接口隔离处理人','systemName':'只读节点接口隔离注册系统'})
    facts['registration']={'account_id':registration['accountId'],'system_id':registration['systemId'],'username':username};save()
    member=api('POST','/api/admin/system/authorization/members',{'account':username,'roleIds':[114]},admin)
    facts['api_member']=member['tenantMemberId'];save()
    actor=api('POST','/api/systems/54/tenants/57/enter',{},registration['platformTokens']['accessToken'])['tokens']['accessToken']
    fid=make(member['tenantMemberId'],'只读权限与必填接口组合');facts['api_flow']=fid;save();bind(fid)
    action=start('接口只读候选');task=current_task(action)
    context=api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=actor)
    fields=context['form']['configuration']['configuration']['fields']
    assert fields and all(f['access']['readable'] and not f['access']['writable'] for f in fields)
    assert 'hidden' not in {f['code'] for f in fields} and context['form']['record']['fields']['credential']=='***'
    assert 'synthetic-readonly-original-secret' not in json.dumps(context)
    status,error=submit(task,actor,{'subject':'不能借审批越权修改'});assert status==403 and error['code']=='FLOW_NODE_FIELD_DENIED'
    assert current_task(action)['id']==task['id'] and count()==1
    status,error=call('GET',f"/api/flow-runtime/tasks/{task['id']}/form/fields/choices/dictionary",token=actor);assert status==403
    finish(action,actor);assert count()==2;case('real_readonly_actor_patch_picker_denied_but_empty_confirmation_executes_once',status=403)
    action=start(None);task=current_task(action)
    status,error=submit(task,actor);assert status==422 and error['code']=='FLOW_NODE_FIELD_REQUIRED' and '当前权限不能编辑' in error['message']
    assert current_task(action)['id']==task['id'] and count()==2
    grant(True);actor=api('POST','/api/systems/54/tenants/57/enter',{},registration['platformTokens']['accessToken'])['tokens']['accessToken']
    finish(action,actor,{'subject':'有编辑权限后补充必填'});assert count()==3
    case('readonly_required_missing_remains_pending_then_actual_permission_restored_fill',status=422)
    action=start('读取后编辑权限被撤销');task=current_task(action)
    assert any(f['access']['writable'] for f in api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=actor)['form']['configuration']['configuration']['fields'])
    grant(False)
    after=api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=actor)
    assert after['form']['configuration']['configuration']['readOnlyAtNode']
    status,error=submit(task,actor,{'score':1});assert status==403 and current_task(action)['id']==task['id']
    finish(action,actor);assert count()==4;case('old_token_update_revocation_readonly_context_fake_patch_denied_and_confirm',status=status)
    bind(fid,'UPDATE');rid=facts['records'][-1];before=record(rid)
    action=start_update(rid,'原身份提交的编辑候选');finish(action,actor)
    assert record(rid)['version']==before['version']+1 and record(rid)['fields']['subject']=='原身份提交的编辑候选'
    custom=api('POST',f"/api/admin/module-config/modules/{facts['module_id']}/actions",{'name':'只读确认字段动作','location':'ROW',
        'parameterFieldCodes':['subject','score'],'confirmation':False},admin);publish_module(facts['module_id']);bind(fid,'MANUAL',custom['id'])
    before=record(rid)
    action=api('POST',f"/api/runtime/modules/{code}/records/{rid}/actions/{custom['code']}",{'version':before['version'],
        'values':{'subject':'原字段动作只读确认','score':0}},admin,action_key='readonly_custom_'+secrets.token_hex(6))
    context=api('GET',f"/api/flow-runtime/tasks/{current_task(action)['id']}/form",token=actor)
    assert {f['code'] for f in context['form']['configuration']['configuration']['fields']}=={'subject','score'}
    finish(action,actor);assert record(rid)['version']==before['version']+1 and count()==4
    facts['api_verified']=True;case('readonly_update_custom_original_identity_final_once',formal_count=4)
    print(json.dumps({'readonly_negative_required_revocation_update_custom':True,'formal_count':4}))
elif stage=='gui_missing_prepare':
    assert facts.get('api_verified') and not facts.get('gui_missing_action')
    bind(facts['gui_flow'])
    facts['gui_missing_action']=start(None);save()
    assert count()==4
    print(json.dumps({'instance_id':facts['gui_missing_action']['flowInstanceId'],'formal_count':4}))
elif stage=='gui_missing_final':
    action=facts['gui_missing_action'];task=current_task(action)
    assert task['status']=='PENDING' and count()==4
    assert terminal(action)['status']=='PENDING_APPROVAL'
    api('POST',f"/api/flow-runtime/instances/{action['flowInstanceId']}/actions",{'actionCode':'TERMINATE',
        'comment':'只读缺失必填页面负向验收结束，不执行业务动作','idempotencyKey':'readonly_missing_stop_'+secrets.token_hex(6)},admin)
    assert count()==4
    facts['gui_missing_verified']=True;case('ordinary_gui_missing_required_error_retains_modal_task_and_no_business_write')
    print(json.dumps({'missing_required_retained_task':True,'formal_count':4}))
elif stage=='cleanup':
    assert facts.get('api_verified')
    publish_role(original['permissions'],original['fieldPolicies'])
    role=next(r for r in api('GET','/api/admin/system/authorization',token=admin)['roles'] if r['id']==114)
    assert role['permissions']==original['permissions'] and role['fieldPolicies']==original['fieldPolicies']
    for rid in sorted(set(facts['records'])):
        current=record(rid)
        api('POST',f'/api/runtime/modules/{code}/records/{rid}/archive',{'version':current['version'],'reason':'隔离只读审批验收完成'},admin)
    mid=facts['module_id'];fids=','.join(map(str,facts['flows']))
    sql(f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND status='ACTIVE'; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({fids}) AND system_id=54 AND owner_tenant_id=57; UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND owner_tenant_id=57")
    sql(f"UPDATE cfg_module_group g SET status='DISABLED',version=version+1 WHERE g.id IN(SELECT group_id FROM cfg_module WHERE id={mid}) AND g.system_id=54 AND g.owner_tenant_id=57 AND NOT EXISTS(SELECT 1 FROM cfg_module m WHERE m.group_id=g.id AND m.id<>{mid})")
    reg=facts['registration'];aid=reg['account_id'];sid=reg['system_id'];username=reg['username']
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND sm.system_id=54 AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND system_id=54 AND status='ACTIVE'; UPDATE auth_session SET revoked=1,revoked_reason='readonly-task-acceptance-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{username}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
    assert count()==0 and sql(f"SELECT COUNT(*) FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id WHERE i.flow_id IN({fids}) AND t.status IN('PENDING','QUEUED')")==[['0']]
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')==facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0 AND deleted=0')==[['4']]
    facts['cleanup_verified']=True;case('readonly_isolated_cleanup_role_and_golden_restored')
    print(json.dumps({'role_restored':True,'formal_count':0,'golden_unchanged':True}))
else:raise AssertionError('未知验收阶段')
