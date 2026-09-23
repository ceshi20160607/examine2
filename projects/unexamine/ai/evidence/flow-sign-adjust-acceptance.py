"""B09.2c.3 冻结人工操作政策的定向真实验收；凭证仅从环境和本地初始化源读取。"""
import ast
import copy
import json
import os
import re
import secrets
import sys
import time
from pathlib import Path
from datetime import datetime

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in names], type_ignores=[]), filename, 'exec'))
PATH = AREA / 'flow-sign-adjust-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.3', 'status': 'running', 'code': 'sign_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'actions': [], 'platform_instances': [], 'records': [], 'cases': []}
assert facts['status'] == 'running', '封存事实不重跑'
java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
platform = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']; legacy_sql = sql
# 带已登录 token 缺省参数的函数必须在身份初始化后定义；只加载函数，不运行旧验收阶段。
tree = ast.parse((AREA / 'flow-instance-graph-acceptance.py').read_text(encoding='utf-8'))
helpers = {'save', 'grant', 'draft', 'publish', 'make', 'start', 'platform_start', 'read', 'request', 'add', 'approve'}
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in helpers], type_ignores=[]), 'graph-helpers', 'exec'))
KEY = 'manualOperationPolicy'


# 只复用已验证的API/SQL与身份辅助函数，不执行前序步骤或共享夹具。
tree = ast.parse((AREA / 'flow-task-claim-acceptance.py').read_text(encoding='utf-8').replace('UNEXAMINE_CLAIM_GUI_PASSWORD', 'UNEXAMINE_SIGN_GUI_PASSWORD'))
helpers = {'ordinary', 'configure', 'state', 'act', 'local_time', 'reject_task', 'todos'}
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in helpers], type_ignores=[]), 'claim-helpers-only', 'exec'))

def policy(claim=False):
    return {'allowedActions':['APPROVE','ADD_SIGN','REMOVE_SIGN'], 'opinionRequiredActions':['ADD_SIGN','REMOVE_SIGN'], 'requireClaim':claim}

stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    facts['golden_count'] = sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0'); save()
    username = code + '_gui'
    reg = api('POST', '/api/auth/register', {'username': username, 'password': os.environ['UNEXAMINE_SIGN_GUI_PASSWORD'],
        'displayName': '加减签页面成员', 'systemName': '加减签隔离系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}
    aid = facts['gui_account'] = reg['accountId']; save()
    pmid = facts['gui_platform_member'] = int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0])
    rid = facts['platform_role_id'] = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','加减签页面夹具','仅本地验收临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); save()
    for action in ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE', 'ADD_MANUAL_NODE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    for action in ['VIEW', 'HANDLE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'TODO','PLATFORM','{action}','ALLOW')")
    facts['platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{pmid},{rid}); SELECT LAST_INSERT_ID()')[0][0]); save()
    facts['api_member'] = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)['tenantMemberId']; save()
    mid = facts['module_id'] = api('POST', '/api/admin/module-config/modules', {'name': '加减签原动作验收', 'code': code, 'groupName': '加减签隔离验收'}, admin)['module']['id']; save()
    for index, (field, name, kind) in enumerate([('subject', '业务主题', 'TEXT'), ('score', '业务数量', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
        facts['fields'][field] = api('POST', f'/api/admin/module-config/modules/{mid}/fields', {'code': field,
            'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
    publish_module(mid); grant()
    fid, nodes, edges = make('加减签冻结与页面配置', realm='platform', allow=True, timeout=True, people=[1, aid], published=False)
    nodes = configure(fid, nodes, edges, policy(claim=True)); publish(fid, platform)
    facts['gui_flow'] = fid; facts['nodes'] = nodes; facts['edges'] = edges
    facts['frozen_instance'] = platform_start(fid, '同节点或签参与人隔离实例')
    extra = api('POST', '/api/auth/register', {'username': code + '_extra', 'password': os.environ['UNEXAMINE_SIGN_GUI_PASSWORD'], 'displayName': '加签新处理人', 'systemName': '加签新处理人隔离系统'})
    facts['extra_registration'] = {'account_id':extra['accountId'], 'system_id':extra['systemId'], 'username':code+'_extra'}; save()
    facts['extra_member'] = api('POST', '/api/admin/system/authorization/members', {'account':code+'_extra', 'roleIds':[114]}, admin)['tenantMemberId']; save()
    emid = int(sql(f"SELECT id FROM plat_member WHERE platform_id=1 AND account_id={extra['accountId']}")[0][0])
    facts['extra_link_id'] = int(sql(f"INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{emid},{rid}); SELECT LAST_INSERT_ID()")[0][0]); save()
    facts['prepared'] = True; save(); print('Prepare PASS')
elif stage in ['api','api_resume_or']:
    assert facts.get('prepared') and not facts.get('api_verified')
    if stage == 'api':
        assert not facts.get('api_started'); facts['api_started'] = True; save()
    else:
        assert facts.get('api_started') and not facts.get('source_instance')
        facts.setdefault('harness_corrections',[]).append('87057f: expected error-code typo; actual403 FLOW_TASK_CLAIM_CANDIDATE_DENIED and no-write check, precise same-task continuation, no approval or sign-change repeated'); save()
    other, system_other = ordinary()
    extra = api('POST', '/api/auth/login', {'username': facts['extra_registration']['username'],
        'password': os.environ['UNEXAMINE_SIGN_GUI_PASSWORD']})['accessToken']
    system_extra = api('POST', '/api/systems/54/tenants/57/enter', {}, extra)['tokens']['accessToken']
    aid = facts['gui_account']; eid = facts['extra_registration']['account_id']
    # 或签同一任务：新增候选须能实际领取，移除候选不再具有处理资格。
    iid = facts['frozen_instance']; tid = next(t['id'] for t in read(iid, platform)['tasks'] if t['status']=='PENDING')
    if stage == 'api':
        reject_task(iid, tid, other, {'actionCode':'ADD_SIGN','comment':'尚未领取','targetPlatformAccountId':eid}, 'FLOW_TASK_CLAIM_REQUIRED',409)
        act(iid, other, 'CLAIM', None)
        before = read(iid, other); original_task = next(t for t in before['tasks'] if t['id']==tid)
        for body, error, status in [({'actionCode':'ADD_SIGN','comment':' ','targetPlatformAccountId':eid},'FLOW_TASK_REASON_REQUIRED',422),
            ({'actionCode':'ADD_SIGN','comment':'重复','targetPlatformAccountId':1},'FLOW_SIGN_PARTICIPANT_EXISTS',409),
            ({'actionCode':'ADD_SIGN','comment':'非法业务修改','targetPlatformAccountId':eid,'variables':{'probe':1}},'FLOW_SIGN_INPUT_INVALID',422),
            ({'actionCode':'REMOVE_SIGN','comment':'不能移除本人','targetPlatformAccountId':aid},'FLOW_SIGN_TARGET_INVALID',422),
            ({'actionCode':'ADD_SIGN','comment':'混用身份','targetPlatformAccountId':eid,'targetTenantMemberId':facts['extra_member']},'FLOW_SIGN_TARGET_INVALID',422)]:
            reject_task(iid, tid, other, body, error, status)
        _, body, added = act(iid, other, 'ADD_SIGN', '增加实际候选', targetPlatformAccountId=eid)
        task = next(t for t in added['instance']['tasks'] if t['id']==tid)
        assert {c['accountId'] for c in task['candidates']}=={1,aid,eid}
        assert task['claimedAt']==original_task['claimedAt'] and task['dueAt']==original_task['dueAt'] and added['instance']['jobs']==before['jobs']
        baseline=state(iid); replay=api('POST',f'/api/flow-runtime/tasks/{tid}/actions',body,other)
        assert replay['replayed'] and replay['actionId']==added['actionId'] and state(iid)==baseline
        reject_task(iid,tid,other,{**body,'targetPlatformAccountId':1},'FLOW_TASK_IDEMPOTENCY_CONFLICT',409)
        act(iid,other,'UNCLAIM',None); act(iid,extra,'CLAIM',None)
        act(iid,extra,'REMOVE_SIGN','移除原候选',targetPlatformAccountId=aid)
        assert sql(f"SELECT JSON_CONTAINS(JSON_EXTRACT(assignee_snapshot_json,'$.claimPool'),JSON_OBJECT('accountId',{aid})) FROM flow_task WHERE id={tid}")==[['0']]
        act(iid,extra,'UNCLAIM',None)
    else:
        task=next(t for t in read(iid,extra)['tasks'] if t['id']==tid)
        assert task['status']=='PENDING' and task['claimedAt'] is None and {c['accountId'] for c in task['candidates']}=={1,eid}
        assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={iid} AND action_code IN('CLAIM','UNCLAIM','ADD_SIGN','REMOVE_SIGN')")==[['6']]
    reject_task(iid,tid,other,{'actionCode':'CLAIM'},'FLOW_TASK_CLAIM_CANDIDATE_DENIED',403)
    act(iid,extra,'CLAIM',None); act(iid,extra)
    assert read(iid,platform)['status']=='COMPLETED'
    facts['cases'] += ['explicit-claim-before-sign-and-illegal-input-no-writes','or-sign-same-task-claim-pool-authorization-and-deadline','sign-replay-and-target-fingerprint-no-writes']; save()
    # 会签保留已批准事实；新增处理人继承原期限，减签取消专属作业，不跳过本人。
    sf,sn,se=make('系统同节点会签与原动作一次',mode='ALL_SIGN',timeout=True,
        people=[entry['tenantMemberId'],facts['api_member']],published=False)
    configure(sf,sn,se,policy(),admin); publish(sf)
    si=facts['source_instance']=start(sf,'会签加减签原动作保存'); save()
    approve(si,system_other,fields={'score':8,'enabled':True})
    initial=read(si,admin); current=next(t for t in initial['tasks'] if t['status']=='PENDING' and t['currentHandler'])
    approved=next(t for t in initial['tasks'] if t['status']=='APPROVE')
    _,_,value=act(si,admin,'ADD_SIGN','加入共同处理人',targetTenantMemberId=facts['extra_member'])
    new=next(t for t in value['instance']['tasks'] if t['id'] not in {x['id'] for x in initial['tasks']})
    assert new['status']=='PENDING' and new['dueAt']==current['dueAt']
    job=next(j for j in value['instance']['jobs'] if j['jobType']=='TASK_TIMEOUT' and j['id'] not in {x['id'] for x in initial['jobs']})
    assert job['nextRunAt']==new['dueAt']
    reject_task(si,current['id'],admin,{'actionCode':'REMOVE_SIGN','comment':'已批准不允许移除','targetTenantMemberId':facts['api_member']},'FLOW_SIGN_PARTICIPANT_NOT_PENDING',409)
    act(si,admin,'REMOVE_SIGN','取消未完成加签',targetTenantMemberId=facts['extra_member'])
    view=read(si,admin); assert next(t for t in view['tasks'] if t['id']==new['id'])['status']=='CANCELLED'
    assert next(j for j in view['jobs'] if j['id']==job['id'])['status']=='CANCELLED'
    assert next(t for t in view['tasks'] if t['id']==approved['id'])==approved and view['status']=='WAITING'
    act(si,admin,'ADD_SIGN','重新明确增加未完成人员',targetTenantMemberId=facts['extra_member'])
    approve(si,admin); assert read(si,admin)['status']=='WAITING'
    approve(si,system_extra)
    result=delivery(next(a for a in facts['actions'] if a['flowInstanceId']==si)); assert result['businessStatus']=='EXECUTED'
    facts['records'].append(result['recordId']); save()
    record=api('GET',f"/api/runtime/modules/{code}/records/{result['recordId']}",token=admin)
    assert record['fields']['score']==8 and record['fields']['enabled'] is True
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0")==[['1']]
    facts['cases'] += ['all-sign-new-task-and-matching-inherited-deadline-job','remove-only-unfinished-cancels-owned-job-preserves-approved-fact','actual-system-form-source-business-executes-once']; save()
    # 顺序会签尾追加及减去排队任务，不能重排或重用旧任务事实。
    qf,qn,qe=make('顺序会签尾追加与减签',realm='platform',mode='SEQUENTIAL',timeout=True,people=[aid,1],published=False)
    configure(qf,qn,qe,policy(),platform); publish(qf,platform)
    qi=facts['sequential_instance']=platform_start(qf,'顺序加减签真实隔离'); save()
    initial=read(qi,other); current=next(t for t in initial['tasks'] if t['status']=='PENDING')
    act(qi,other,'REMOVE_SIGN','减去排队人员',targetPlatformAccountId=1)
    _,_,value=act(qi,other,'ADD_SIGN','尾追加新处理人',targetPlatformAccountId=eid)
    added=next(t for t in value['instance']['tasks'] if t['id'] not in {t['id'] for t in initial['tasks']})
    assert added['status']=='QUEUED' and added['dueAt'] is None
    assert sql(f"SELECT JSON_EXTRACT(assignee_snapshot_json,'$.sequence') FROM flow_task WHERE id={added['id']}")==[['3']]
    assert next(t for t in value['instance']['tasks'] if t['id']==current['id'])['dueAt']==current['dueAt']
    act(qi,other); view=read(qi,extra); assert view['status']=='WAITING'
    assert next(t for t in view['tasks'] if t['id']==added['id'])['status']=='PENDING'
    act(qi,extra); assert read(qi,platform)['status']=='COMPLETED'
    # 旧无显式配置不因新能力扩权；非法直调没有写入。
    lf,_,_=make('旧缺省按钮无扩权',realm='platform'); li=platform_start(lf,'旧无加减签按钮不变')
    lt=next(t for t in read(li,other)['tasks'] if t['status']=='PENDING')
    assert 'ADD_SIGN' not in read(li,other)['allowedActions'] and 'REMOVE_SIGN' not in read(li,other)['allowedActions']
    reject_task(li,lt['id'],other,{'actionCode':'ADD_SIGN','comment':'不能绕过','targetPlatformAccountId':eid},'FLOW_TASK_OPERATION_DISABLED',403)
    act(li,other)
    facts['cases'] += ['sequential-append-after-historical-maximum-and-queued-removal','legacy-default-four-no-new-permission']; save()
    facts['gui_instance']=platform_start(facts['gui_flow'],'页面同节点加签减签与冻结核验')
    facts['gui_version_before']=sql(f"SELECT id,version_number FROM flow_version WHERE flow_id={facts['gui_flow']} ORDER BY id")
    facts['gui_original_task']=next(t for t in read(facts['gui_instance'],other)['tasks'] if t['status']=='PENDING')
    facts['api_verified']=True; save(); print(json.dumps({'API':'PASS','cases':len(facts['cases']),'gui_instance':facts['gui_instance'],'source_record':facts['records']}))
elif stage == 'gui_policy':
    assert facts.get('api_verified') and not facts.get('gui_policy_verified')
    other,_=ordinary();fid=facts['gui_flow'];iid=facts['gui_instance']
    versions=sql(f'SELECT id,version_number FROM flow_version WHERE flow_id={fid} ORDER BY id')
    assert len(versions)==len(facts['gui_version_before'])+1 and state(iid)==facts['gui_before_config']
    fresh=platform_start(fid,'新版本明确禁用加签');act(fresh,platform,'CLAIM',None)
    tid=next(t['id'] for t in read(fresh,platform)['tasks'] if t['status']=='PENDING')
    assert 'ADD_SIGN' not in read(fresh,platform)['allowedActions']
    reject_task(fresh,tid,platform,{'actionCode':'ADD_SIGN','comment':'禁用后不能直调','targetPlatformAccountId':facts['extra_registration']['account_id']},'FLOW_TASK_OPERATION_DISABLED',403)
    act(fresh,platform)
    sql(f"DELETE FROM plat_role_permission WHERE platform_id=1 AND role_id={facts['platform_role_id']} AND resource_type='FLOW' AND resource_code='PLATFORM' AND action_code IN('DESIGN','PUBLISH','SIMULATE')")
    facts['cases'].append('gui-six-options-save-reenter-publish-old-frozen-new-disabled-no-writes')
    facts['gui_policy_verified']=True;save();print('GUI policy PASS; exact temporary design permissions revoked')
elif stage == 'gui_before':
    other,_=ordinary();view=read(facts['gui_instance'],other)
    task=next(t for t in view['tasks'] if t['status']=='PENDING')
    assert task['claimedAt'] and task['version']==1 and 'ADD_SIGN' in view['allowedActions'] and 'REMOVE_SIGN' in view['allowedActions']
    assert task['dueAt']==facts['gui_original_task']['dueAt']
    facts['gui_claimed_task']=task;facts['gui_jobs']=view['jobs'];facts['gui_failure_baseline']=state(facts['gui_instance']);save();print('Claimed original V1 GUI task baseline captured')
elif stage == 'gui_blank':
    assert state(facts['gui_instance'])==facts['gui_failure_baseline'];print('Blank GUI opinion: zero checked DB writes')
elif stage == 'gui_added':
    other,_=ordinary();view=read(facts['gui_instance'],other);task=next(t for t in view['tasks'] if t['status']=='PENDING')
    original=facts['gui_claimed_task'];eid=facts['extra_registration']['account_id']
    assert task['id']==original['id'] and task['version']==original['version']+1
    assert {c['accountId'] for c in task['candidates']}=={1,facts['gui_account'],eid}
    assert task['claimedAt']==original['claimedAt'] and task['dueAt']==original['dueAt'] and view['jobs']==facts['gui_jobs']
    facts['pending_restart_state']=state(facts['gui_instance']);facts['pending_restart_view']=view
    facts['frozen_versions']=sql(f"SELECT id,version_number,HEX(snapshot_json) FROM flow_version WHERE flow_id IN({','.join(map(str,facts['flows']))}) ORDER BY id")
    facts['cases'].append('ordinary-gui-person-selector-real-same-task-add-and-opinion');save();print('GUI added PASS')
elif stage == 'pending_restart':
    other,_=ordinary()
    assert state(facts['gui_instance'])==facts['pending_restart_state'] and read(facts['gui_instance'],other)==facts['pending_restart_view']
    facts['cases'].append('same-backend-restart-retains-current-added-pool-claim-and-policy');save();print('Pending restart PASS')
elif stage == 'gui_removed':
    other,_=ordinary();view=read(facts['gui_instance'],other);task=next(t for t in view['tasks'] if t['status']=='PENDING')
    original=facts['gui_claimed_task']
    assert task['id']==original['id'] and task['version']==original['version']+2
    assert {c['accountId'] for c in task['candidates']}=={1,facts['gui_account']}
    assert task['claimedAt']==original['claimedAt'] and task['dueAt']==original['dueAt'] and view['jobs']==facts['gui_jobs']
    for action,comment in [('ADD_SIGN','增加本节点候选以协同核对'),('REMOVE_SIGN','移除尚未处理的新增候选')]:
        assert sql(f"SELECT HEX(comment_text) FROM flow_action WHERE instance_id={facts['gui_instance']} AND action_code='{action}'") == [[comment.encode().hex().upper()]]
    facts['cases'].append('ordinary-gui-remove-selector-same-task-claim-deadline-and-audited-opinions');save();print('GUI removed PASS')
elif stage == 'gui_done':
    other,_=ordinary();view=read(facts['gui_instance'],other)
    assert view['status']=='COMPLETED' and not view['allowedActions']
    assert len([a for a in view['actions'] if a['actionCode']=='APPROVE'])==1
    facts['gui_verified']=True;facts['completed_state']=state(facts['gui_instance'])
    facts['cases'].append('ordinary-gui-actual-approval-complete-own-instance-result');save();print('GUI completed PASS')
elif stage == 'restart':
    assert facts.get('gui_verified') and state(facts['gui_instance'])==facts['completed_state']
    assert sql(f"SELECT id,version_number,HEX(snapshot_json) FROM flow_version WHERE flow_id IN({','.join(map(str,facts['flows']))}) ORDER BY id")==facts['frozen_versions']
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0")==[['1']]
    facts['restart_verified']=True;facts['cases'].append('completed-restart-frozen-definitions-and-source-once');save();print('Completed restart PASS')
elif stage == 'cleanup':
    assert facts.get('restart_verified')
    other,_=ordinary()
    extra=api('POST','/api/auth/login',{'username':facts['extra_registration']['username'],'password':os.environ['UNEXAMINE_SIGN_GUI_PASSWORD']})['accessToken']
    for token in [other,extra]:api('GET','/api/todos?status=ALL&type=ALL',token=token)
    publish_role(original['permissions'],original['fieldPolicies'])
    for rid in facts['records']:
        record=api('GET',f'/api/runtime/modules/{code}/records/{rid}',token=admin)
        api('POST',f'/api/runtime/modules/{code}/records/{rid}/archive',{'version':record['version'],'reason':'加减签验收归档'},admin)
    mid=facts['module_id'];flows=','.join(map(str,facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flows}) AND platform_id=1")
    sql(f"DELETE FROM plat_member_role WHERE id IN({facts['platform_link_id']},{facts['extra_link_id']}) AND role_id={facts['platform_role_id']}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={facts['platform_role_id']} AND code='{code}'")
    for reg in [facts['registration'],facts['extra_registration']]:
        aid=reg['account_id'];sid=reg['system_id']
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON tm.system_member_id=sm.id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid}; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='sign-acceptance-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{reg['username']}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
    assert facts['golden_baseline']==sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    assert facts['golden_count']==sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0')
    ids=','.join(map(str,facts['platform_instances']+[facts['source_instance']]))
    for table,unfinished in [('flow_task',"'PENDING','QUEUED','SUSPENDED'"),('flow_job',"'PENDING','RUNNING','SUSPENDED'"),('flow_execution',"'ACTIVE','SUSPENDED'")]:
        assert sql(f'SELECT COUNT(*) FROM {table} WHERE instance_id IN({ids}) AND status IN({unfinished})')==[['0']]
    assert sql(f"SELECT COUNT(*) FROM todo_item t JOIN flow_task f ON t.source_type='FLOW_TASK' AND t.source_id=CAST(f.id AS CHAR) WHERE f.instance_id IN({ids}) AND t.assignee_account_id IN({facts['gui_account']},{facts['extra_registration']['account_id']}) AND t.status IN('PENDING','SUSPENDED')")==[['0']]
    group=sql(f'SELECT group_id FROM cfg_module WHERE id={mid}')
    if group and group[0][0]!='NULL':
        gid=int(group[0][0]);sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={gid} AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id={gid} AND status='ACTIVE')")
    facts['cleanup_verified']=True;facts['cases'].append('exact-fixture-cleanup-and-golden-business-unchanged');save();print('Cleanup PASS')
else:
    raise AssertionError('unknown stage')
    print(json.dumps({'prepared': True, 'username': username, 'account': aid, 'flow': fid}))
