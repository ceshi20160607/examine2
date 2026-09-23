"""B09.2c.1 冻结人工操作政策的定向真实验收；凭证仅从环境和本地初始化源读取。"""
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
PATH = AREA / 'flow-node-operation-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.1', 'status': 'running', 'code': 'ops_' + secrets.token_hex(5),
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


def ordinary():
    token = api('POST', '/api/auth/login', {'username': facts['registration']['username'],
        'password': os.environ['UNEXAMINE_OPS_GUI_PASSWORD']})['accessToken']
    return token, api('POST', '/api/systems/54/tenants/57/enter', {}, token)['tokens']['accessToken']


def policy(allowed, required):
    return {'allowedActions': allowed, 'opinionRequiredActions': required}


def configure(fid, nodes, edges, value, token=platform):
    nodes = copy.deepcopy(nodes)
    nodes[1]['config'][KEY] = value
    draft(fid, nodes, edges, token)
    return nodes


def state(iid):
    # 失败请求不得改变实例、任务、候选表单、历史、作业或业务意图。
    result = {}
    for table in ['flow_instance', 'flow_task', 'flow_action', 'flow_history_event', 'flow_job', 'flow_execution']:
        where = f'id={iid}' if table == 'flow_instance' else f'instance_id={iid}'
        result[table] = sql(f'SELECT * FROM {table} WHERE {where} ORDER BY id')
    return result


def act(iid, token, action='APPROVE', comment='已核对允许操作', **extra):
    task = next(t for t in read(iid, token)['tasks'] if t['currentHandler'] and t['status'] == 'PENDING')
    body = {'actionCode': action, 'comment': comment, 'idempotencyKey': code + secrets.token_hex(6), **extra}
    return task['id'], body, api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", body, token)


def denied(iid, token, action, comment, error, fields=None):
    view = read(iid, token); task = next(t for t in view['tasks'] if t['currentHandler'] and t['status'] == 'PENDING')
    before = state(iid)
    body = {'actionCode': action, 'comment': comment, 'idempotencyKey': code + secrets.token_hex(6)}
    if fields is not None: body['fieldValues'] = fields
    status, result = call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", body, token)
    assert status in [403, 422] and result['code'] == error, (status, result.get('code'))
    assert state(iid) == before, '失败请求写入了实例/任务/历史/作业'


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    facts['golden_count'] = sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0'); save()
    username = code + '_gui'
    reg = api('POST', '/api/auth/register', {'username': username, 'password': os.environ['UNEXAMINE_OPS_GUI_PASSWORD'],
        'displayName': '节点操作页面成员', 'systemName': '节点操作隔离系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}
    aid = facts['gui_account'] = reg['accountId']; save()
    pmid = facts['gui_platform_member'] = int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0])
    rid = facts['platform_role_id'] = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','节点操作页面夹具','仅本地验收临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); save()
    for action in ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE', 'ADD_MANUAL_NODE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{pmid},{rid}); SELECT LAST_INSERT_ID()')[0][0]); save()
    facts['api_member'] = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)['tenantMemberId']; save()
    mid = facts['module_id'] = api('POST', '/api/admin/module-config/modules', {'name': '节点操作原动作验收', 'code': code, 'groupName': '节点操作隔离验收'}, admin)['module']['id']; save()
    for index, (field, name, kind) in enumerate([('subject', '业务主题', 'TEXT'), ('score', '业务数量', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
        facts['fields'][field] = api('POST', f'/api/admin/module-config/modules/{mid}/fields', {'code': field,
            'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
    publish_module(mid); grant()
    fid, nodes, edges = make('节点操作冻结与页面配置', realm='platform', allow=True, published=False)
    nodes = configure(fid, nodes, edges, policy(['APPROVE'], ['APPROVE'])); publish(fid, platform)
    facts['gui_flow'] = fid; facts['nodes'] = nodes; facts['edges'] = edges
    facts['frozen_instance'] = platform_start(fid, '旧版本必填意见冻结实例')
    facts['prepared'] = True; save()
    print(json.dumps({'prepared': True, 'username': username, 'account': aid, 'flow': fid}))
elif stage == 'api':
    assert facts.get('prepared') and not facts.get('api_started'); facts['api_started'] = True; save()
    other, system_other = ordinary(); fid = facts['gui_flow']; nodes = facts['nodes']; edges = facts['edges']; old = facts['frozen_instance']
    old_version = read(old, other)['flowVersionId']
    for invalid in [policy([], []), policy(['APPROVE', 'CLAIM'], []), policy(['APPROVE', 'APPROVE'], []),
            policy(['APPROVE'], ['TRANSFER']), policy(['REJECT'], []), policy(['RETURN'], []),
            {'allowedActions': 'APPROVE', 'opinionRequiredActions': []}, {'allowedActions': ['APPROVE']}]:
        configure(fid, nodes, edges, invalid)
        check = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
        assert not check['valid'] and any(i['code'] == 'FLOW_MANUAL_OPERATION_POLICY_INVALID' for i in check['issues'])
        before = sql(f'SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id={fid} ORDER BY id')
        status, result = call('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'], 'changeSummary': '非法政策不得发布', 'simulationVariables': {}}, platform)
        assert status == 422, (status, result.get('code'))
        assert before == sql(f'SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id={fid} ORDER BY id')
    nodes = configure(fid, nodes, edges, policy(['APPROVE'], [])); publish(fid, platform)
    newer = platform_start(fid, '新版本可选意见'); facts['newer_instance'] = newer; save()
    assert read(old, other)['flowVersionId'] == old_version
    assert read(old, other)['allowedActions'] == ['APPROVE', 'ADD_RUNTIME_NODE']
    assert read(old, other)['opinionRequiredActions'] == ['APPROVE']
    assert read(newer, other)['opinionRequiredActions'] == []
    for action in ['REJECT', 'RETURN', 'TRANSFER']:
        denied(old, other, action, '绕过已禁用操作', 'FLOW_TASK_OPERATION_DISABLED')
    denied(old, other, 'APPROVE', '   ', 'FLOW_TASK_REASON_REQUIRED')
    tid, body, result = act(newer, other, comment=None)
    assert result['instance']['status'] == 'COMPLETED'
    baseline = state(newer); replay = api('POST', f'/api/flow-runtime/tasks/{tid}/actions', body, other)
    assert replay['replayed'] and state(newer) == baseline
    changed = dict(body, actionCode='REJECT', comment='同键不同动作')
    status, err = call('POST', f'/api/flow-runtime/tasks/{tid}/actions', changed, other)
    assert status == 409 and err['code'] == 'FLOW_TASK_IDEMPOTENCY_CONFLICT' and state(newer) == baseline
    # 动态前插继承旧实例人工政策，不能被后来发布的可选意见版本改变。
    body = request(old, token=other, people=[facts['gui_account']], name='继承必填意见的前插核对'); body['position'] = 'BEFORE_CURRENT'
    inserted = add(old, body, other)['instance']
    assert inserted['opinionRequiredActions'] == ['APPROVE']
    denied(old, other, 'REJECT', '有原因仍不允许', 'FLOW_TASK_OPERATION_DISABLED')
    denied(old, other, 'APPROVE', '', 'FLOW_TASK_REASON_REQUIRED')
    act(old, other); assert read(old, other)['currentNodeKey'] == 'review'
    assert read(old, other)['opinionRequiredActions'] == ['APPROVE']; act(old, other)
    # 已转交的原处理人失去当前可见性后，同旧幂等键不能取回完整实例。
    transfer, tn, te = make('转交回执当前查看权限', realm='platform', published=False)
    configure(transfer, tn, te, policy(['APPROVE', 'TRANSFER'], ['TRANSFER'])); publish(transfer, platform)
    transferred = platform_start(transfer, '转交后旧回执权限'); facts['transfer_instance'] = transferred; save()
    denied(transferred, other, 'TRANSFER', '', 'FLOW_TASK_REASON_REQUIRED')
    tid, body, result = act(transferred, other, 'TRANSFER', '交由平台管理员继续', targetPlatformAccountId=1)
    before = state(transferred); status, err = call('POST', f'/api/flow-runtime/tasks/{tid}/actions', body, other)
    assert status == 403 and err['code'] == 'FLOW_INSTANCE_VIEW_DENIED' and state(transferred) == before
    act(transferred, platform)
    # 旧缺省发布仍有四项；拒绝原因不被兼容路径削弱。
    legacy, _, _ = make('旧缺省人工配置兼容', realm='platform')
    li = platform_start(legacy, '旧缺省保留原因'); facts['legacy_instance'] = li; save()
    assert read(li, other)['opinionRequiredActions'] == ['REJECT', 'RETURN']
    denied(li, other, 'REJECT', None, 'FLOW_TASK_REASON_REQUIRED'); act(li, other, 'REJECT', '真实旧版拒绝原因')
    # 原动作会签仍经统一守卫；禁用操作不能先保存候选字段。
    sf, sn, se = make('会签人工政策与原动作一次执行', mode='ALL_SIGN', people=[entry['tenantMemberId'], facts['api_member']], published=False)
    configure(sf, sn, se, policy(['APPROVE'], ['APPROVE']), admin); publish(sf)
    si = start(sf, '人工政策会签正式写入'); facts['source_instance'] = si; save()
    denied(si, admin, 'REJECT', '禁用且恶意字段', 'FLOW_TASK_OPERATION_DISABLED', {'score': 999})
    denied(si, admin, 'APPROVE', '', 'FLOW_TASK_REASON_REQUIRED', {'score': 999})
    approve(si, fields={'score': 6}); approve(si, system_other, fields={'enabled': True})
    source = next(a for a in facts['actions'] if a['flowInstanceId'] == si); completed = delivery(source)
    assert completed['businessStatus'] == 'EXECUTED'; facts['records'].append(completed['recordId']); save()
    formal = api('GET', f"/api/runtime/modules/{code}/records/{completed['recordId']}", token=admin)
    assert formal['fields']['score'] == 6 and formal['fields']['enabled'] is True
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
    facts['cases'] += ['strict-invalid-publication-no-version-writes', 'frozen-v1-v2-opinion-policy',
        'disabled-direct-operation-and-required-opinion-no-writes', 'completed-replay-no-execution-and-different-action-conflict',
        'dynamic-before-inherits-frozen-policy-and-restores-original', 'transfer-replay-current-view-denied',
        'legacy-default-reason-preserved', 'all-sign-form-failures-before-write-and-source-executed-once']
    facts['api_verified'] = True; facts['gui_version_before'] = sql(f'SELECT id,version_number FROM flow_version WHERE flow_id={fid} ORDER BY id'); save()
    print(json.dumps({'api': 'PASS', 'cases': len(facts['cases']), 'gui_flow': fid, 'username': facts['registration']['username']}))
elif stage == 'gui_prepare':
    assert facts.get('api_verified') and not facts.get('gui_instance')
    fid = facts['gui_flow']; view = api('GET', f'/api/flows/{fid}', token=platform)
    p = next(n for n in view['nodes'] if n['nodeKey'] == 'review')['config'][KEY]
    assert p == policy(['APPROVE'], ['APPROVE']), p
    assert len(sql(f'SELECT id FROM flow_version WHERE flow_id={fid}')) == len(facts['gui_version_before']) + 1
    facts['gui_instance'] = platform_start(fid, '页面必填意见与禁用按钮核验')
    facts['gui_pending_state'] = state(facts['gui_instance']); facts['frozen_versions'] = sql('SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id IN(' + ','.join(map(str, facts['flows'])) + ') ORDER BY id'); save()
    print(json.dumps({'gui_pending': facts['gui_instance'], 'username': facts['registration']['username']}))
elif stage == 'gui_blank':
    iid = facts['gui_instance']; assert state(iid) == facts['gui_pending_state']
    other, _ = ordinary(); value = read(iid, other)
    assert value['allowedActions'] == ['APPROVE', 'ADD_RUNTIME_NODE'] and value['opinionRequiredActions'] == ['APPROVE']
    facts['cases'].append('ordinary-gui-blank-opinion-rejected-without-writes'); save(); print('GUI blank PASS')
elif stage == 'gui_read':
    iid = facts['gui_instance']; other, _ = ordinary(); value = read(iid, other)
    assert value['status'] == 'COMPLETED' and value['allowedActions'] == [] and value['opinionRequiredActions'] == []
    assert sql(f"SELECT HEX(comment_text) FROM flow_action WHERE instance_id={iid} AND action_code='APPROVE'") == [['页面核对完毕，按配置填写处理意见'.encode('utf-8').hex().upper()]]
    facts['gui_completed_state'] = state(iid); facts['cases'].append('designer-checkbox-save-publish-and-ordinary-gui-real-approval'); save(); print('GUI real completion PASS')
elif stage == 'restart':
    assert facts.get('gui_completed_state')
    assert state(facts['gui_instance']) == facts['gui_completed_state']
    other, _ = ordinary(); assert read(facts['gui_instance'], other)['status'] == 'COMPLETED'
    assert facts['frozen_versions'] == sql('SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id IN(' + ','.join(map(str, facts['flows'])) + ') ORDER BY id')
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
    facts['cases'].append('same-backend-restart-frozen-versions-completion-source-once'); facts['restart_verified'] = True; save(); print('Restart PASS')
elif stage == 'cleanup':
    assert facts.get('restart_verified')
    # 只撤回随机夹具权限及停用夹具，归档唯一正式记录；不动既有用户与业务记录。
    publish_role(original['permissions'], original['fieldPolicies'])
    for rid in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': record['version'], 'reason': '节点操作验收归档'}, admin)
    mid = facts['module_id']; flows = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flows}) AND platform_id=1")
    sql(f"DELETE FROM plat_member_role WHERE id={facts['platform_link_id']} AND member_id={facts['gui_platform_member']} AND role_id={facts['platform_role_id']}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={facts['platform_role_id']} AND code='{code}'")
    aid = facts['gui_account']; sid = facts['registration']['system_id']
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON tm.system_member_id=sm.id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid}; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='node-operation-acceptance-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{facts['registration']['username']}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
    assert facts['golden_baseline'] == sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    assert facts['golden_count'] == sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0')
    ids = facts['platform_instances'] + [facts['source_instance']]
    for table, unfinished in [('flow_task', "'PENDING','QUEUED','SUSPENDED'"), ('flow_job', "'PENDING','RUNNING','SUSPENDED'"), ('flow_execution', "'ACTIVE','SUSPENDED'")]:
        assert sql(f"SELECT COUNT(*) FROM {table} WHERE instance_id IN({','.join(map(str,ids))}) AND status IN({unfinished})") == [['0']]
    facts['cleanup_verified'] = True; facts['cases'].append('exact-fixture-cleanup-and-golden-business-unchanged'); save(); print('Cleanup PASS')
else:
    raise AssertionError('unknown stage')
