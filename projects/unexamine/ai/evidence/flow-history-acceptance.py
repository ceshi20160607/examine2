"""B09.2c.4 冻结人工操作政策的定向真实验收；凭证仅从环境和本地初始化源读取。"""
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
PATH = AREA / 'flow-history-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.4', 'status': 'running', 'code': 'history_' + secrets.token_hex(5),
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
tree = ast.parse((AREA / 'flow-task-claim-acceptance.py').read_text(encoding='utf-8').replace('UNEXAMINE_CLAIM_GUI_PASSWORD', 'UNEXAMINE_HISTORY_GUI_PASSWORD'))
helpers = {'ordinary', 'configure', 'state', 'act', 'local_time', 'reject_task', 'todos'}
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in helpers], type_ignores=[]), 'claim-helpers-only', 'exec'))

def policy(claim=False):
    return {'allowedActions':['APPROVE','ADD_SIGN','REMOVE_SIGN'], 'opinionRequiredActions':['ADD_SIGN','REMOVE_SIGN'], 'requireClaim':claim}

stage = sys.argv[1]
if stage=='prepare':
    assert not facts.get('prepared')
    facts['golden_baseline']=sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    facts['golden_count']=sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0');save()
    username=code+'_gui';reg=api('POST','/api/auth/register',{'username':username,'password':os.environ['UNEXAMINE_HISTORY_GUI_PASSWORD'],'displayName':'准确历史页面成员','systemName':'准确历史隔离系统'})
    aid=reg['accountId'];facts['registration']={'account_id':aid,'system_id':reg['systemId'],'username':username};facts['gui_account']=aid;save()
    pmid=int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0])
    rid=facts['platform_role_id']=int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','准确历史页面夹具','仅本地验收临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]);save()
    for action in ['VIEW','VIEW_RUNTIME','START','HANDLE','ADD_MANUAL_NODE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['platform_link_id']=int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{pmid},{rid}); SELECT LAST_INSERT_ID()')[0][0]);save()
    fid,nodes,edges=make('准确审批历史主链',realm='platform',people=[1,aid],published=False)
    configure(fid,nodes,edges,policy(True),platform);publish(fid,platform)
    iid=platform_start(fid,'领取审批状态准确历史');facts['main_instance']=iid
    af,an,ae=make('会签部分完成与终止历史',realm='platform',mode='ALL_SIGN',people=[1,aid],published=False)
    configure(af,an,ae,policy(),platform);publish(af,platform);facts['all_instance']=platform_start(af,'部分会签后终止')
    df,dn,de=make('实例加节点准确历史',realm='platform',allow=True,people=[1],published=False)
    configure(df,dn,de,policy(),platform);publish(df,platform);facts['dynamic_instance']=platform_start(df,'增加后续节点一次')
    facts['prepared']=True;save();print('Prepare PASS')
elif stage=='api':
    assert facts.get('prepared') and not facts.get('api_verified');other=api('POST','/api/auth/login',{'username':facts['registration']['username'],'password':os.environ['UNEXAMINE_HISTORY_GUI_PASSWORD']})['accessToken']
    iid=facts['main_instance'];view=read(iid,other);task=next(t for t in view['tasks'] if t['status']=='PENDING')
    raw=lambda value:sql(f"SELECT event_type,HEX(event_name),before_status,after_status FROM flow_history_event WHERE instance_id={value} ORDER BY occurred_at,id")
    assert len([e for e in raw(iid) if e[1]=='流程已发起'.encode().hex().upper()])==1
    _,body,claim=act(iid,other,'CLAIM',None);history=claim['instance']['history']
    assert len([e for e in history if e['eventName']=='待办已领取'])==1
    event=next(e for e in history if e['eventName']=='待办已领取');assert event['beforeStatus']=='PENDING' and event['afterStatus']=='PENDING'
    before=len(raw(iid));replay=api('POST',f'/api/flow-runtime/tasks/{task["id"]}/actions',body,other)
    assert replay['replayed'] and len(raw(iid))==before
    _,_,done=act(iid,other);event=next(e for e in done['instance']['history'] if e['eventName']=='审批通过')
    assert event['beforeStatus']=='PENDING' and event['afterStatus']=='APPROVE' and done['instance']['status']=='COMPLETED'
    facts['cases'] += ['new-start-single-instance-event','claim-single-action-pending-state-and-replay-no-extra','human-approve-task-pending-to-approved']
    ai=facts['all_instance'];act(ai,other);view=read(ai,platform)
    event=next(e for e in view['history'] if e['eventName']=='审批通过');assert event['afterStatus']=='APPROVE' and view['status']=='WAITING'
    before_status=view['status'];result=api('POST',f'/api/flow-runtime/instances/{ai}/actions',{'actionCode':'TERMINATE','comment':'验收准确实例状态','idempotencyKey':code+secrets.token_hex(6)},platform)
    event=next(e for e in result['instance']['history'] if e['eventName']=='管理员终止');assert event['beforeStatus']==before_status and event['afterStatus']=='TERMINATED'
    facts['cases'] += ['partial-all-sign-approval-keeps-task-result','instance-action-captured-before-to-terminal']
    di=facts['dynamic_instance'];body=request(di,people=[1],token=platform,name='准确历史后续复核');value=add(di,body,platform)
    event=next(e for e in value['instance']['history'] if e['eventName'].startswith('已增加审批：准确历史后续复核'))
    assert event['beforeStatus']=='WAITING' and event['afterStatus']=='WAITING'
    act(di,platform);act(di,platform);assert read(di,platform)['status']=='COMPLETED'
    facts['cases'].append('runtime-graph-change-distinct-same-state-event')
    legacy=read(222,platform);raw_legacy=raw(222)
    assert len(raw_legacy)>len(legacy['history']) and len([e for e in legacy['history'] if e['eventName']=='流程已发起'])==1
    assert len([e for e in legacy['history'] if e['eventName']=='待办已领取'])==1
    assert len([e for e in legacy['history'] if e['eventName'].startswith('同节点加签')])==1
    facts['cases'].append('legacy-adjacent-duplicate-projection-without-row-delete')
    facts['api_verified']=True;facts['main_completed_state']=state(iid);save();print(json.dumps({'API':'PASS','cases':len(facts['cases']),'gui_instance':iid}))
elif stage=='gui':
    assert facts.get('api_verified')
    other=api('POST','/api/auth/login',{'username':facts['registration']['username'],'password':os.environ['UNEXAMINE_HISTORY_GUI_PASSWORD']})['accessToken']
    view=read(facts['main_instance'],other)
    assert len([e for e in view['history'] if e['eventName']=='流程已发起'])==1
    assert len([e for e in view['history'] if e['eventName']=='待办已领取'])==1
    approved=next(e for e in view['history'] if e['eventName']=='审批通过')
    assert approved['beforeStatus']=='PENDING' and approved['afterStatus']=='APPROVE'
    facts['gui_verified']=True;facts['cases'].append('ordinary-gui-single-start-claim-and-task-transition');save();print('GUI history PASS')
elif stage=='restart':
    assert facts.get('gui_verified') and state(facts['main_instance'])==facts['main_completed_state']
    other=api('POST','/api/auth/login',{'username':facts['registration']['username'],'password':os.environ['UNEXAMINE_HISTORY_GUI_PASSWORD']})['accessToken'];view=read(facts['main_instance'],other)
    assert len([e for e in view['history'] if e['eventName']=='流程已发起'])==1 and len([e for e in view['history'] if e['eventName']=='待办已领取'])==1
    facts['restart_verified']=True;facts['cases'].append('same-backend-restart-history-and-instance-state');save();print('Restart PASS')
elif stage=='cleanup':
    assert facts.get('restart_verified');rid=facts['platform_role_id'];aid=facts['gui_account'];sid=facts['registration']['system_id'];flows=','.join(map(str,facts['flows']))
    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flows}) AND platform_id=1; DELETE FROM plat_member_role WHERE id={facts['platform_link_id']} AND role_id={rid}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid} AND code='{code}'")
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON tm.system_member_id=sm.id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid}; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='history-acceptance-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{facts['registration']['username']}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
    assert facts['golden_baseline']==sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') and facts['golden_count']==sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0')
    ids=','.join(map(str,[facts['main_instance'],facts['all_instance'],facts['dynamic_instance']]))
    for table,unfinished in [('flow_task',"'PENDING','QUEUED','SUSPENDED'"),('flow_job',"'PENDING','RUNNING','SUSPENDED'"),('flow_execution',"'ACTIVE','SUSPENDED'")]:assert sql(f'SELECT COUNT(*) FROM {table} WHERE instance_id IN({ids}) AND status IN({unfinished})')==[['0']]
    facts['cleanup_verified']=True;facts['cases'].append('exact-fixture-cleanup-and-golden-business-unchanged');save();print('Cleanup PASS')
else:raise AssertionError('unknown stage')
