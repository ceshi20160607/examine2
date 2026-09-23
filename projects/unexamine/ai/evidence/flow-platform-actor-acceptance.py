"""B09.1c.1 平台真实人员/旧会话撤权及系统转交组合验收；不把SQL角色夹具算作平台管理交付。"""
import ast
import copy
import json
import re
import secrets
import sys
from datetime import datetime
from pathlib import Path

AREA=Path(__file__).parent
for filename,names in [('shared-actor-acceptance.py',{'call','api','sql'}),('flow-node-submit-acceptance.py',{'publish_role'})]:
    tree=ast.parse((AREA/filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,(ast.Import,ast.ImportFrom))
        or isinstance(n,ast.FunctionDef) and n.name in names],type_ignores=[]),filename,'exec'))
PATH=AREA/'flow-platform-actor-facts.json'
facts=json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step':'B09.1c.1','status':'running','code':'platform_actor_'+secrets.token_hex(5),'flows':[],'cases':[]}
assert facts['status']=='running'
java=(AREA.parents[1]/'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login=api('POST','/api/auth/login',{'username':re.search(r'default-admin\.username:([^}]+)',java).group(1),
    'password':re.search(r'default-admin\.initial-password:([^}]+)',java).group(1)})
platform=login['accessToken'];root=api('GET','/api/auth/me',token=platform)
admin=api('POST','/api/systems/54/tenants/57/enter',{},platform)['tokens']['accessToken']
original=json.loads((AREA/'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']


def save():
    facts['checked_time']=datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def case(name,**data):
    facts['cases'].append({'case':name,**data});save()


def make(token,policy,label,publish=True):
    value=api('POST','/api/flows',{'name':label,'code':facts['code']+'_'+secrets.token_hex(3)},token)
    facts['flows'].append(value['id']);save()
    nodes=[{'nodeKey':key,'nodeType':kind,'name':name,'positionX':i*230,'positionY':100,
        'config':{'approvalMode':'OR_SIGN'} if kind=='APPROVAL' else {},
        'assigneePolicy':policy if kind=='APPROVAL' else {},'formPolicy':{}}
        for i,(key,kind,name) in enumerate([('start','START','开始'),('review','APPROVAL','指定人员审批'),('end','END','结束')])]
    edges=[{'edgeKey':str(i),'sourceNodeKey':a,'targetNodeKey':b,'priorityOrder':0,'config':{}}
        for i,(a,b) in enumerate([('start','review'),('review','end')])]
    api('PUT',f"/api/flows/{value['id']}/draft",{'expectedVersion':value['version'],'nodes':nodes,'edges':edges},token)
    if publish:publication(value['id'],token)
    return value['id']


def publication(fid,token):
    check=api('GET',f'/api/flows/{fid}/publication-check',token=token);assert check['valid'],check['issues']
    return api('POST',f'/api/flows/{fid}/publish',{'expectedDraftRevision':check['draftRevision'],
        'changeSummary':'平台独立真实指定人员审批验收','simulationVariables':{}},token)


def start(fid,token,title):
    return api('POST','/api/flow-runtime/instances',{'flowId':fid,'title':title,'variables':{},
        'businessSnapshot':{},'idempotencyKey':'platform_start_'+secrets.token_hex(6)},token)['instance']


def read(iid,token):return api('GET',f'/api/flow-runtime/instances/{iid}',token=token)


def task(value):return next(t for t in value['tasks'] if t['status']=='PENDING')


def handle(tid,token,action='APPROVE',**params):
    return call('POST',f'/api/flow-runtime/tasks/{tid}/actions',{'actionCode':action,'fieldValues':{},
        'idempotencyKey':'platform_handle_'+secrets.token_hex(6),**params},token)


def grant_system(handle_allowed=True):
    permissions=copy.deepcopy(original['permissions'])+[{'resourceType':'FLOW','resourceCode':'SYSTEM',
        'actionCode':a,'dataScopeType':'ALL'} for a in ['VIEW_RUNTIME']+(['HANDLE'] if handle_allowed else [])]
    publish_role(permissions,original['fieldPolicies'])


stage=sys.argv[1]
if stage=='prepare_gui':
    assert not facts.get('prepared')
    facts['golden_baseline']=sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    facts['ordinary_role_baseline']=sql('SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id=67 ORDER BY id');save()
    # 独立SQL授权夹具：平台管理API仍未完成，不借此声明管理页面可用；结束移除唯一新增关联。
    role=sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{facts['code']}','平台人员流程隔离验收','仅本次本地流程权限组合夹具',0,'ACTIVE'); SELECT LAST_INSERT_ID()")
    facts['platform_role_id']=int(role[0][0]);save();rid=facts['platform_role_id']
    for action in ['VIEW','DESIGN','PUBLISH','SIMULATE','VIEW_RUNTIME','START','HANDLE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['ordinary_link_id']=int(sql(f"INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,67,{rid}); SELECT LAST_INSERT_ID()")[0][0]);save()
    facts['gui_flow']=make(platform,{'type':'PERSON','platformAccountIds':[]},'平台指定人员可视化验收',False)
    facts['prepared']=True;case('isolated_platform_role_fixture_and_real_gui_draft',flow_id=facts['gui_flow'])
    print(json.dumps({'flow_id':facts['gui_flow'],'platform_role_id':rid}))
elif stage=='gui_view_permission':
    rid=facts['platform_role_id']
    sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) SELECT 1,{rid},'FLOW','PLATFORM','VIEW','ALLOW' WHERE NOT EXISTS(SELECT 1 FROM plat_role_permission WHERE role_id={rid} AND action_code='VIEW')")
    case('gui_fixture_missing_design_catalog_VIEW_permission_restored_no_duplicate_flow')
    print('isolated design VIEW permission ready')
elif stage=='gui_configured':
    value=api('GET',f"/api/flows/{facts['gui_flow']}",token=platform)
    # 保存后从真实草稿读回平台身份，不能把系统成员ID或前端选中外观当成已配置。
    node=next(n for n in value['nodes'] if n['nodeType']=='APPROVAL')
    assert node['assigneePolicy']=={'type':'PERSON','platformAccountIds':[69]},node['assigneePolicy']
    assert value['currentVersionId'] is not None
    facts['gui_instance']=start(facts['gui_flow'],platform,'平台指定人员审批验收')
    assert facts['gui_instance']['status']=='WAITING' and facts['gui_instance']['startedByCurrentUser'] is True
    facts['gui_configured']=True;case('ordinary_platform_gui_selected_saved_published_platform_identity',instance_id=facts['gui_instance']['id'])
    print(json.dumps({'instance_id':facts['gui_instance']['id']}))
elif stage=='gui_final':
    assert facts.get('gui_configured')
    value=read(facts['gui_instance']['id'],platform);assert value['status']=='COMPLETED'
    assert len([a for a in value['actions'] if a['actionCode']=='APPROVE'])==1
    assert not any(t['currentHandler'] for t in value['tasks'])
    facts['gui_verified']=True;case('ordinary_platform_gui_real_approval_completed_once_and_my_application_not_null_equal')
    print(json.dumps({'platform_gui_completed':True}))
elif stage=='api':
    assert facts.get('gui_verified') and not facts.get('api_verified')
    username=facts['code']+'_'+secrets.token_hex(3)
    reg=api('POST','/api/auth/register',{'username':username,'password':secrets.token_urlsafe(24),
        'displayName':'平台独立节点接口处理人','systemName':'平台节点接口隔离注册系统'})
    facts['registration']={'account_id':reg['accountId'],'system_id':reg['systemId'],'username':username};save()
    actor=reg['platformTokens']['accessToken'];aid=reg['accountId'];rid=facts['platform_role_id']
    mid=int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0]);facts['api_platform_member']=mid;save()
    sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{mid},{rid})')
    directory=api('GET','/api/flow-runtime/participants',token=actor)
    assert any(p['accountId']==aid for p in directory['people'])
    assert all(not p['tenantMemberId'] for p in directory['people']) and 'username' not in json.dumps(directory)
    for policy in [{'type':'PERSON','tenantMemberIds':[108]}, {'type':'PERSON','platformAccountIds':[aid+10000]},
        {'type':'PERSON','platformAccountIds':[aid,aid]}, {'type':'INITIATOR'}]:
        bad=make(platform,policy,'平台非法人员策略隔离验收',False)
        check=api('GET',f'/api/flows/{bad}/publication-check',token=platform)
        assert not check['valid'] and any(i['code']=='APPROVER_POLICY_INVALID' for i in check['issues'])
    fid=make(platform,{'type':'PERSON','platformAccountIds':[root['accountId'],aid]},'平台或签及当前处理人组合')
    value=start(fid,platform,'平台或签转交接口验收');facts['api_instance']=value['id'];save();tid=task(value)['id']
    assert task(value)['currentHandler'] and task(read(value['id'],actor))['currentHandler'] is True
    assert read(value['id'],actor)['startedByCurrentUser'] is False
    status,error=handle(tid,platform,'TRANSFER',targetTenantMemberId=108,targetPlatformAccountId=aid)
    assert status==422 and error['code']=='FLOW_TASK_TRANSFER_TARGET_INVALID'
    status,result=handle(tid,platform,'TRANSFER',targetPlatformAccountId=aid);assert status==200,(status,result.get('code'))
    after=read(value['id'],platform);assert task(after)['currentHandler'] is False and 'APPROVE' not in after['allowedActions']
    assert any(c['accountId']==root['accountId'] for c in task(after)['candidates'])
    status,error=handle(tid,platform);assert status==403 and error['code']=='FLOW_TASK_HANDLER_DENIED'
    sql(f"UPDATE plat_role_permission SET effect='DENY',version=version+1 WHERE role_id={rid} AND action_code='HANDLE'")
    revoked=read(value['id'],actor);assert not task(revoked)['currentHandler'] and 'APPROVE' not in revoked['allowedActions']
    status,error=handle(tid,actor);assert status==403
    sql(f"UPDATE plat_role_permission SET effect='ALLOW',version=version+1 WHERE role_id={rid} AND action_code='HANDLE'")
    assert task(read(value['id'],actor))['currentHandler']
    sql(f"UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid}")
    status,error=call('GET',f"/api/flow-runtime/instances/{value['id']}",token=actor);assert status==403
    sql(f"UPDATE plat_role SET status='ACTIVE',version=version+1 WHERE id={rid}")
    sql(f"UPDATE plat_member SET status='DISABLED',version=version+1 WHERE id={mid} AND account_id={aid}")
    status,error=handle(tid,actor);assert status==403
    check=api('GET',f'/api/flows/{fid}/publication-check',token=platform);assert not check['valid']
    sql(f"UPDATE plat_member SET status='ACTIVE',version=version+1 WHERE id={mid} AND account_id={aid}")
    final_key='platform_final_'+secrets.token_hex(6)
    status,result=handle(tid,actor,idempotencyKey=final_key);assert status==200
    assert result['data']['instance']['status']=='COMPLETED'
    status,replay=handle(tid,actor,idempotencyKey=final_key);assert status==200 and replay['data']['replayed'] is True
    completed=read(value['id'],platform)
    assert len([a for a in completed['actions'] if a['actionCode']=='APPROVE'])==1
    case('platform_same_key_completed_approval_replayed_once_without_second_action')
    case('platform_or_sign_transfer_history_not_authority_and_old_token_permission_role_member_revocation')
    # 同一独立账号进入真实系统成员上下文验证共享DTO，不写黄金业务数据。
    member=api('POST','/api/admin/system/authorization/members',{'account':username,'roleIds':[114]},admin)
    facts['api_system_member']=member['tenantMemberId'];save();grant_system()
    actor_system=api('POST','/api/systems/54/tenants/57/enter',{},actor)['tokens']['accessToken']
    root_system=api('GET','/api/auth/me',token=admin)
    sysflow=make(admin,{'type':'PERSON','tenantMemberIds':[root_system['tenantMemberId']]},'系统转交处理人契约隔离验收')
    sv=start(sysflow,admin,'系统转交及撤权接口验收');facts['system_instance']=sv['id'];save();st=task(sv)['id']
    status,error=handle(st,admin,'TRANSFER',targetTenantMemberId=member['tenantMemberId'],targetPlatformAccountId=aid);assert status==422
    status,result=handle(st,admin,'TRANSFER',targetTenantMemberId=member['tenantMemberId']);assert status==200
    assert not task(read(sv['id'],admin))['currentHandler'] and task(read(sv['id'],actor_system))['currentHandler']
    grant_system(False)
    assert not task(read(sv['id'],actor_system))['currentHandler']
    status,error=handle(st,actor_system);assert status==403
    grant_system();status,result=handle(st,actor_system);assert status==200 and result['data']['instance']['status']=='COMPLETED'
    publish_role(original['permissions'],original['fieldPolicies'])
    facts['api_verified']=True;case('same_system_shared_current_handler_transfer_and_old_token_handle_revocation')
    print(json.dumps({'platform_and_system_identity_revocation_verified':True}))
elif stage=='boundary_readback':
    assert facts.get('api_verified')
    # 同一真实记录先用有权系统上下文读回。平台请求按system_id物理归属过滤为404，不能泄露记录或冒充403。
    assert api('GET','/api/runtime/modules/flow_condition_accept_20260918/records/170',token=admin)['id']==170
    status,error=call('GET','/api/runtime/modules/flow_condition_accept_20260918/records/170',token=platform)
    assert status==404 and error['code']=='RECORD_NOT_FOUND',(status,error.get('code'))
    value=read(facts['api_instance'],platform)
    assert value['systemId'] is None and value['tenantId'] is None and value['contextType']=='PLATFORM'
    assert all(c['tenantMemberId'] is None and c['accountId'] is not None for t in value['tasks'] for c in t['candidates'])
    assert not any(t['currentHandler'] for t in value['tasks'])
    facts['boundary_verified']=True;case('system_record_positive_readback_platform_physical_scope_404_and_frozen_candidates_keep_real_realm')
    print(json.dumps({'system_data_boundary_verified':True,'code':error['code']}))
elif stage=='cleanup':
    assert facts.get('api_verified')
    publish_role(original['permissions'],original['fieldPolicies']);rid=facts['platform_role_id'];link=facts['ordinary_link_id']
    # 唯一新增普通账号角色关联是隔离权限夹具，撤权后精确移除并读回原基线，所有业务审计保留。
    sql(f"UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid} AND code='{facts['code']}'; DELETE FROM plat_member_role WHERE id={link} AND member_id=67 AND role_id={rid}")
    assert sql('SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id=67 ORDER BY id')==facts['ordinary_role_baseline']
    fids=','.join(map(str,facts['flows']));sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({fids}) AND platform_id=1")
    reg=facts['registration'];aid=reg['account_id'];sid=reg['system_id'];username=reg['username']
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND sm.system_id=54 AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND system_id=54 AND status='ACTIVE'; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='platform-actor-acceptance-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{username}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
    assert sql(f"SELECT COUNT(*) FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id WHERE i.flow_id IN({fids}) AND t.status IN('PENDING','QUEUED')")==[['0']]
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')==facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0 AND deleted=0')==[['4']]
    facts['cleanup_verified']=True;case('isolated_roles_members_flows_cleanup_no_pending_and_golden_unchanged')
    print(json.dumps({'cleanup_verified':True,'golden_unchanged':True}))
else:raise AssertionError('未知验收阶段')
