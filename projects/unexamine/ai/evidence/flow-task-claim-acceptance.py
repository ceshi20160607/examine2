"""B09.2c.2 冻结人工操作政策的定向真实验收；凭证仅从环境和本地初始化源读取。"""
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
PATH = AREA / 'flow-task-claim-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.2', 'status': 'running', 'code': 'claim_' + secrets.token_hex(5),
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
        'password': os.environ['UNEXAMINE_CLAIM_GUI_PASSWORD']})['accessToken']
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
    for table in ['flow_instance', 'flow_instance_variable', 'flow_task', 'flow_action', 'flow_history_event', 'flow_job', 'flow_execution']:
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


# 领取只影响人工处理；明确配置的重复审批与超时自动策略仍沿用冻结语义，不伪造人工领取。
def policy(allowed, required, claim=True):
    return {'allowedActions': allowed, 'opinionRequiredActions': required, 'requireClaim': claim}


def local_time(value):
    parsed = datetime.fromisoformat(value)
    return parsed.astimezone().replace(tzinfo=None) if parsed.tzinfo else parsed


def reject_task(iid, tid, token, body, error, expected):
    before = state(iid)
    status, value = call('POST', f'/api/flow-runtime/tasks/{tid}/actions',
        {'idempotencyKey': code + secrets.token_hex(6), **body}, token)
    assert status == expected and value['code'] == error, (status, value.get('code'))
    assert state(iid) == before


def todos(token, tid):
    return [t for t in api('GET', '/api/todos?status=ALL&type=ALL', token=token)
        if t['sourceType'] == 'FLOW_TASK' and t['sourceId'] == str(tid)]


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    facts['golden_count'] = sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0'); save()
    username = code + '_gui'
    reg = api('POST', '/api/auth/register', {'username': username, 'password': os.environ['UNEXAMINE_CLAIM_GUI_PASSWORD'],
        'displayName': '领取规则页面成员', 'systemName': '领取规则隔离系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}
    aid = facts['gui_account'] = reg['accountId']; save()
    pmid = facts['gui_platform_member'] = int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0])
    rid = facts['platform_role_id'] = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','领取规则页面夹具','仅本地验收临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); save()
    for action in ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE', 'ADD_MANUAL_NODE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    for action in ['VIEW', 'HANDLE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'TODO','PLATFORM','{action}','ALLOW')")
    facts['platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{pmid},{rid}); SELECT LAST_INSERT_ID()')[0][0]); save()
    facts['api_member'] = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)['tenantMemberId']; save()
    mid = facts['module_id'] = api('POST', '/api/admin/module-config/modules', {'name': '领取规则原动作验收', 'code': code, 'groupName': '领取规则隔离验收'}, admin)['module']['id']; save()
    for index, (field, name, kind) in enumerate([('subject', '业务主题', 'TEXT'), ('score', '业务数量', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
        facts['fields'][field] = api('POST', f'/api/admin/module-config/modules/{mid}/fields', {'code': field,
            'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
    publish_module(mid); grant()
    fid, nodes, edges = make('领取规则冻结与页面配置', realm='platform', allow=True, timeout=True, people=[1, aid], published=False)
    nodes = configure(fid, nodes, edges, policy(['APPROVE', 'TRANSFER'], ['APPROVE'])); publish(fid, platform)
    facts['gui_flow'] = fid; facts['nodes'] = nodes; facts['edges'] = edges
    facts['frozen_instance'] = platform_start(fid, '多候选实际领取隔离实例')
    facts['prepared'] = True; save()
    print(json.dumps({'prepared': True, 'username': username, 'account': aid, 'flow': fid}))
elif stage in ['api', 'api_retry_entry']:
    from concurrent.futures import ThreadPoolExecutor
    assert facts.get('prepared')
    if stage == 'api':
        assert not facts.get('api_started'); facts['api_started'] = True; save()
    else:
        assert facts.get('api_started') and not facts.get('api_main_verified')
        tid = facts['main_task']; iid = facts['frozen_instance']
        assert sql(f"SELECT status,version,assignee_account_id,claimed_at FROM flow_task WHERE id={tid} AND instance_id={iid}") == [['PENDING','0','NULL','NULL']]
        assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={iid} AND task_id={tid}") == [['0']]

    other, system_other = ordinary(); iid = facts['frozen_instance']; value = read(iid, other)
    task = next(t for t in value['tasks'] if t['status'] == 'PENDING'); tid = task['id']; facts['main_task'] = tid; save()
    assert value['allowedActions'] == ['CLAIM', 'ADD_RUNTIME_NODE'] and value['opinionRequiredActions'] == []
    original_job = value['jobs']; deadline = task['dueAt']; candidates = task['candidates']
    initial_todos = todos(other, tid); assert len(initial_todos) == 1 and initial_todos[0]['availableActions'] == ['CLAIM']
    facts['main_todo'] = initial_todos[0]['id']; save()
    reject_task(iid, tid, other, {'actionCode': 'APPROVE', 'comment': '不能跳过领取'}, 'FLOW_TASK_CLAIM_REQUIRED', 409)
    reject_task(iid, tid, other, {'actionCode': 'CLAIM', 'variables': {'malicious': 1}}, 'FLOW_TASK_CLAIM_INPUT_INVALID', 422)
    before = state(iid); status, err = call('GET', f'/api/flow-runtime/tasks/{tid}/form', token=other)
    assert status == 409 and err['code'] == 'FLOW_TASK_CLAIM_REQUIRED' and state(iid) == before
    # 真正并发两个独立 HTTP 请求争同一实例/任务锁，精确只允许一人成功。
    requests = [(platform, {'actionCode': 'CLAIM', 'idempotencyKey': code + '_race_root'}),
                (other, {'actionCode': 'CLAIM', 'idempotencyKey': code + '_race_other'})]
    with ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = list(pool.map(lambda item: call('POST', f'/api/flow-runtime/tasks/{tid}/actions', item[1], item[0]), requests))
    assert sorted(o[0] for o in outcomes) == [200, 409], [(o[0], o[1].get('code')) for o in outcomes]
    winner_index = next(i for i,o in enumerate(outcomes) if o[0] == 200); winner, body = requests[winner_index]
    view = read(iid, platform); claimed = next(t for t in view['tasks'] if t['id'] == tid)
    assert claimed['version'] == task['version'] + 1 and claimed['claimedAt'] and claimed['dueAt'] == deadline
    assert claimed['candidates'] == candidates and view['jobs'] == original_job
    before = state(iid); replay = api('POST', f'/api/flow-runtime/tasks/{tid}/actions', body, winner)
    assert replay['replayed'] and state(iid) == before
    if winner_index == 0:
        gone = todos(other, tid); assert gone[0]['status'] == 'REASSIGNED' and gone[0]['availableActions'] == []
        reject_task(iid, tid, other, {'actionCode': 'APPROVE', 'comment': '别人已领取'}, 'FLOW_TASK_HANDLER_DENIED', 403)
    else:
        assert todos(other, tid)[0]['availableActions'] == ['APPROVE', 'TRANSFER', 'UNCLAIM']
        reject_task(iid, tid, platform, {'actionCode': 'UNCLAIM'}, 'FLOW_TASK_UNCLAIM_DENIED', 403)
    act(iid, winner, 'UNCLAIM', None)
    released = next(t for t in read(iid, other)['tasks'] if t['id'] == tid)
    assert released['claimedAt'] is None and released['assigneeTenantMemberId'] is None
    assert released['version'] == task['version'] + 2 and released['dueAt'] == deadline and released['candidates'] == candidates
    assert read(iid, other)['jobs'] == original_job
    restored_todo = todos(other, tid)[0]
    assert restored_todo['id'] == facts['main_todo'] and restored_todo['status'] == 'PENDING' and restored_todo['completedAt'] is None
    assert restored_todo['availableActions'] == ['CLAIM']
    # 实际统一工作台动作也能领取，来源处理复用同一个锁内入口。
    tr = api('POST', f"/api/todos/{facts['main_todo']}/actions", {'actionCode': 'CLAIM',
        'idempotencyKey': code + '_todo_claim'}, other)
    assert next(t for t in read(iid, other)['tasks'] if t['id'] == tid)['claimedAt']
    reject_task(iid, tid, other, {'actionCode': 'APPROVE', 'comment': ''}, 'FLOW_TASK_REASON_REQUIRED', 422)
    # 转交消除释放权，但仍允许目标人直接处理，不退回原池。
    act(iid, other, 'TRANSFER', '交由平台管理员继续', targetPlatformAccountId=1)
    current = next(t for t in read(iid, platform)['tasks'] if t['id'] == tid)
    assert current['claimedAt'] is None and 'UNCLAIM' not in read(iid, platform)['allowedActions']
    reject_task(iid, tid, platform, {'actionCode': 'UNCLAIM'}, 'FLOW_TASK_UNCLAIM_DENIED', 403)
    act(iid, platform); assert read(iid, platform)['status'] == 'COMPLETED'
    facts['cases'] += ['unclaimed-approval-form-and-malicious-claim-no-writes', 'real-parallel-claim-one-winner-same-task',
        'claim-replay-no-writes', 'release-same-task-candidates-deadline-job-and-todo-reactivates',
        'todo-claim-central-entry', 'human-transfer-clears-claim-no-release-to-old-pool']
    facts['api_main_verified'] = True; facts['race_winner'] = 'root' if winner_index == 0 else 'ordinary'; save()
    print(json.dumps({'api_main':'PASS','instance':iid,'winner':facts['race_winner'],'cases':len(facts['cases'])}))
elif stage == 'api_more':
    assert facts.get('api_main_verified') and not facts.get('more_started'); facts['more_started'] = True; save()
    other, system_other = ordinary(); fid = facts['gui_flow']
    ri = platform_start(fid, '其他候选待办精确重新激活'); facts['release_instance'] = ri; save()
    tid = next(t['id'] for t in read(ri, other)['tasks'] if t['status'] == 'PENDING')
    initial = todos(other, tid)[0]; act(ri, platform, 'CLAIM', None)
    gone = todos(other, tid)[0]; assert gone['id'] == initial['id'] and gone['status'] == 'REASSIGNED' and gone['completedAt']
    act(ri, platform, 'UNCLAIM', None)
    back = todos(other, tid)[0]; assert back['id'] == initial['id'] and back['status'] == 'PENDING' and back['completedAt'] is None and back['availableActions'] == ['CLAIM']
    act(ri, other, 'CLAIM', None); act(ri, other)
    # 领取规则与多人会签不兼容，实际发布不得通过；复原原草稿，不换旧在途快照。
    bad = copy.deepcopy(facts['nodes']); bad[1]['config']['approvalMode'] = 'ALL_SIGN'
    draft(fid, bad, facts['edges'], platform)
    check = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
    assert not check['valid'] and any(i['code'] == 'FLOW_TASK_CLAIM_MODE_INVALID' for i in check['issues'])
    before = sql(f'SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id={fid}')
    status, err = call('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'], 'changeSummary': '不兼容领取不可发布', 'simulationVariables': {}}, platform)
    assert status == 422 and before == sql(f'SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id={fid}')
    draft(fid, facts['nodes'], facts['edges'], platform)
    # 动态节点也不能绕过继承的必须领取；暂停原已领取任务，恢复仍归原领取人。
    di = platform_start(fid, '动态前插继承领取并恢复原归属'); facts['dynamic_instance'] = di; save()
    act(di, platform, 'CLAIM', None); original_task = next(t for t in read(di, platform)['tasks'] if t['status'] == 'PENDING')
    body = request(di, token=platform, people=[facts['gui_account']]); body.update(position='BEFORE_CURRENT', approvalMode='ALL_SIGN')
    frozen = state(di); status, err = call('POST', f'/api/flow-runtime/instances/{di}/runtime-node-preview', body, platform)
    assert status == 422 and err['code'] == 'FLOW_TASK_CLAIM_MODE_INVALID' and state(di) == frozen
    body['approvalMode'] = 'OR_SIGN'; add(di, body, platform)
    assert 'CLAIM' in read(di, other)['allowedActions']; act(di, other, 'CLAIM', None); act(di, other)
    restored = next(t for t in read(di, platform)['tasks'] if t['id'] == original_task['id'])
    assert restored['status'] == 'PENDING' and restored['claimedAt'] == original_task['claimedAt']
    frame = json.loads(sql(f"SELECT JSON_EXTRACT(input_json,'$.suspensionFrame') FROM flow_action WHERE instance_id={di} AND action_code='RUNTIME_NODE_ADDED'")[0][0])
    resumed = sql(f"SELECT occurred_at FROM flow_history_event WHERE instance_id={di} AND event_type='NODE_RESUMED' ORDER BY id DESC LIMIT 1")[0][0]
    assert abs((local_time(restored['dueAt']) - local_time(resumed)).total_seconds()*1000 - frame['tasks'][0]['remainingMillis']) < 2000

    assert 'UNCLAIM' in read(di, platform)['allowedActions']; act(di, platform)
    # 真正超时 worker 转交已领取任务，清原领取时间/标记，目标不可释放回原池。
    tf, tn, te = make('已领取待办真实超时转交', realm='platform', people=[1, facts['gui_account']], published=False)
    tn[1]['timeoutPolicy'] = {'timeoutMinutes': 60, 'action': 'TRANSFER', 'targetPlatformAccountId': 1}
    configure(tf, tn, te, policy(['APPROVE', 'TRANSFER'], ['APPROVE'])); publish(tf, platform)
    wi = platform_start(tf, '超时转交清领取实际worker'); facts['worker_instance'] = wi; save()
    act(wi, other, 'CLAIM', None); wt = next(t for t in read(wi, other)['tasks'] if t['status'] == 'PENDING')
    sql(f"UPDATE flow_task SET due_at=NOW()-INTERVAL 2 SECOND WHERE id={wt['id']} AND instance_id={wi}; UPDATE flow_job SET next_run_at=NOW()-INTERVAL 2 SECOND WHERE instance_id={wi} AND job_type='TASK_TIMEOUT' AND JSON_EXTRACT(payload_json,'$.taskId')={wt['id']} AND status='PENDING'")
    for _ in range(20):
        view = read(wi, platform); wtask = next(t for t in view['tasks'] if t['id'] == wt['id'])
        if any(j['jobType'] == 'TASK_TIMEOUT' and j['status'] == 'COMPLETED' for j in view['jobs']):break
        time.sleep(1)
    else:raise AssertionError('实际worker未完成转交')
    assert wtask['claimedAt'] is None and wtask['dueAt'] is None and 'UNCLAIM' not in view['allowedActions']
    assert sql(f"SELECT JSON_EXTRACT(assignee_snapshot_json,'$.claimActorAccountId') FROM flow_task WHERE id={wt['id']}") == [['NULL']]
    reject_task(wi, wt['id'], platform, {'actionCode':'UNCLAIM'}, 'FLOW_TASK_UNCLAIM_DENIED', 403); act(wi, platform)
    # 系统原动作候选表单：领取前拒绝读取，领取后编辑并只执行一次正式新建。
    sf, sn, se = make('系统候选领取表单原动作一次', people=[entry['tenantMemberId'], facts['api_member']], published=False)
    configure(sf, sn, se, policy(['APPROVE'], ['APPROVE']), admin); publish(sf)
    si = start(sf, '系统领取后原动作保存'); facts['source_instance'] = si; save()
    st = next(t for t in read(si, system_other)['tasks'] if t['status'] == 'PENDING')
    baseline = state(si); status, err = call('GET', f"/api/flow-runtime/tasks/{st['id']}/form", token=system_other)
    assert status == 409 and err['code'] == 'FLOW_TASK_CLAIM_REQUIRED' and state(si) == baseline
    act(si, system_other, 'CLAIM', None); approve(si, system_other, fields={'score': 7, 'enabled': True})
    result = delivery(facts['actions'][0]); assert result['businessStatus'] == 'EXECUTED'; facts['records'].append(result['recordId']); save()
    record = api('GET', f"/api/runtime/modules/{code}/records/{result['recordId']}", token=admin)
    assert record['fields']['score'] == 7 and record['fields']['enabled'] is True
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
    # 单候选必须领取也生成队列，并保留旧缺省直接处理。
    single, _, _ = make('单候选必须领取', realm='platform', people=[facts['gui_account']], published=False)
    sv = api('GET', f'/api/flows/{single}', token=platform); configure(single, sv['nodes'], sv['edges'], policy(['APPROVE'], [])); publish(single, platform)
    ii = platform_start(single, '单候选仍必须先领'); facts['single_instance'] = ii; save()
    assert read(ii, other)['allowedActions'] == ['CLAIM', 'ADD_RUNTIME_NODE']; act(ii, other, 'CLAIM', None); act(ii, other, comment=None)
    legacy, _, _ = make('领取发布升级旧缺省兼容', realm='platform'); li = platform_start(legacy, '旧无领取开关不变'); facts['legacy_instance'] = li; save()
    assert 'CLAIM' not in read(li, other)['allowedActions']; act(li, other)
    facts['gui_instance'] = platform_start(fid, '页面候选领取取消与冻结核验'); facts['gui_version_before'] = sql(f'SELECT id,version_number FROM flow_version WHERE flow_id={fid} ORDER BY id'); save()
    facts['cases'] += ['other-candidate-original-todo-reactivates-and-clears-completed-time', 'publication-mode-reject-no-frozen-write',
        'dynamic-before-inherits-claim-and-restores-original-claimer', 'real-timeout-transfer-clears-claim',
        'system-candidate-form-claim-before-write-and-source-once', 'single-candidate-claim-and-legacy-direct-compatibility']
    facts['api_more_verified'] = True; save()
    print(json.dumps({'api_more':'PASS','gui_instance':facts['gui_instance'],'source_record':facts['records'],'cases':len(facts['cases'])}))
elif stage == 'api_more_continue':
    assert facts.get('more_started') and not facts.get('api_more_verified') and not facts.get('worker_instance')
    other, system_other = ordinary(); fid = facts['gui_flow']; di = facts['dynamic_instance']
    frame = json.loads(sql(f"SELECT JSON_EXTRACT(input_json,'$.suspensionFrame') FROM flow_action WHERE instance_id={di} AND action_code='RUNTIME_NODE_ADDED'")[0][0])
    task = next(t for t in read(di, platform)['tasks'] if t['id'] == frame['tasks'][0]['taskId'])
    assert task['status'] == 'PENDING' and task['claimedAt'] and 'UNCLAIM' in read(di, platform)['allowedActions']
    assert sql(f"SELECT assignee_account_id,JSON_EXTRACT(assignee_snapshot_json,'$.claimActorAccountId') FROM flow_task WHERE id={task['id']}") == [['1','1']]
    resumed = sql(f"SELECT occurred_at FROM flow_history_event WHERE instance_id={di} AND event_type='NODE_RESUMED' ORDER BY id DESC LIMIT 1")[0][0]
    assert abs((local_time(task['dueAt'])-local_time(resumed)).total_seconds()*1000-frame['tasks'][0]['remainingMillis']) < 2000
    act(di, platform)
    # 真正超时 worker 转交已领取任务，清原领取时间/标记，目标不可释放回原池。
    tf, tn, te = make('已领取待办真实超时转交', realm='platform', people=[1, facts['gui_account']], published=False)
    tn[1]['timeoutPolicy'] = {'timeoutMinutes': 60, 'action': 'TRANSFER', 'targetPlatformAccountId': 1}
    configure(tf, tn, te, policy(['APPROVE', 'TRANSFER'], ['APPROVE'])); publish(tf, platform)
    wi = platform_start(tf, '超时转交清领取实际worker'); facts['worker_instance'] = wi; save()
    act(wi, other, 'CLAIM', None); wt = next(t for t in read(wi, other)['tasks'] if t['status'] == 'PENDING')
    sql(f"UPDATE flow_task SET due_at=NOW()-INTERVAL 2 SECOND WHERE id={wt['id']} AND instance_id={wi}; UPDATE flow_job SET next_run_at=NOW()-INTERVAL 2 SECOND WHERE instance_id={wi} AND job_type='TASK_TIMEOUT' AND JSON_EXTRACT(payload_json,'$.taskId')={wt['id']} AND status='PENDING'")
    for _ in range(20):
        view = read(wi, platform); wtask = next(t for t in view['tasks'] if t['id'] == wt['id'])
        if any(j['jobType'] == 'TASK_TIMEOUT' and j['status'] == 'COMPLETED' for j in view['jobs']):break
        time.sleep(1)
    else:raise AssertionError('实际worker未完成转交')
    assert wtask['claimedAt'] is None and wtask['dueAt'] is None and 'UNCLAIM' not in view['allowedActions']
    assert sql(f"SELECT JSON_EXTRACT(assignee_snapshot_json,'$.claimActorAccountId') FROM flow_task WHERE id={wt['id']}") == [['NULL']]
    reject_task(wi, wt['id'], platform, {'actionCode':'UNCLAIM'}, 'FLOW_TASK_UNCLAIM_DENIED', 403); act(wi, platform)
    # 系统原动作候选表单：领取前拒绝读取，领取后编辑并只执行一次正式新建。
    sf, sn, se = make('系统候选领取表单原动作一次', people=[entry['tenantMemberId'], facts['api_member']], published=False)
    configure(sf, sn, se, policy(['APPROVE'], ['APPROVE']), admin); publish(sf)
    si = start(sf, '系统领取后原动作保存'); facts['source_instance'] = si; save()
    st = next(t for t in read(si, system_other)['tasks'] if t['status'] == 'PENDING')
    baseline = state(si); status, err = call('GET', f"/api/flow-runtime/tasks/{st['id']}/form", token=system_other)
    assert status == 409 and err['code'] == 'FLOW_TASK_CLAIM_REQUIRED' and state(si) == baseline
    act(si, system_other, 'CLAIM', None); approve(si, system_other, fields={'score': 7, 'enabled': True})
    result = delivery(facts['actions'][0]); assert result['businessStatus'] == 'EXECUTED'; facts['records'].append(result['recordId']); save()
    record = api('GET', f"/api/runtime/modules/{code}/records/{result['recordId']}", token=admin)
    assert record['fields']['score'] == 7 and record['fields']['enabled'] is True
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
    # 单候选必须领取也生成队列，并保留旧缺省直接处理。
    single, _, _ = make('单候选必须领取', realm='platform', people=[facts['gui_account']], published=False)
    sv = api('GET', f'/api/flows/{single}', token=platform); configure(single, sv['nodes'], sv['edges'], policy(['APPROVE'], [])); publish(single, platform)
    ii = platform_start(single, '单候选仍必须先领'); facts['single_instance'] = ii; save()
    assert read(ii, other)['allowedActions'] == ['CLAIM', 'ADD_RUNTIME_NODE']; act(ii, other, 'CLAIM', None); act(ii, other, comment=None)
    legacy, _, _ = make('领取发布升级旧缺省兼容', realm='platform'); li = platform_start(legacy, '旧无领取开关不变'); facts['legacy_instance'] = li; save()
    assert 'CLAIM' not in read(li, other)['allowedActions']; act(li, other)
    facts['gui_instance'] = platform_start(fid, '页面候选领取取消与冻结核验'); facts['gui_version_before'] = sql(f'SELECT id,version_number FROM flow_version WHERE flow_id={fid} ORDER BY id'); save()
    facts['cases'] += ['other-candidate-original-todo-reactivates-and-clears-completed-time', 'publication-mode-reject-no-frozen-write',
        'dynamic-before-inherits-claim-and-restores-original-claimer', 'real-timeout-transfer-clears-claim',
        'system-candidate-form-claim-before-write-and-source-once', 'single-candidate-claim-and-legacy-direct-compatibility']
    facts['api_more_verified'] = True; save()
    print(json.dumps({'api_more':'PASS','gui_instance':facts['gui_instance'],'source_record':facts['records'],'cases':len(facts['cases'])}))
elif stage == 'gui_policy':
    assert facts.get('api_more_verified') and not facts.get('gui_policy_verified')
    other, _ = ordinary(); fid = facts['gui_flow']; current = api('GET', f'/api/flows/{fid}', token=platform)
    configured = next(n for n in current['nodes'] if n['nodeKey'] == 'review')['config'][KEY]
    assert configured['requireClaim'] is False
    assert len(sql(f'SELECT id FROM flow_version WHERE flow_id={fid}')) == len(facts['gui_version_before']) + 1
    value = read(facts['gui_instance'], other)
    assert value['flowVersionNumber'] == 1 and value['allowedActions'] == ['CLAIM', 'ADD_RUNTIME_NODE']
    facts['gui_original_task'] = next(t for t in value['tasks'] if t['status'] == 'PENDING'); facts['gui_original_jobs'] = value['jobs']
    direct = platform_start(fid, '新发布关闭领取直接审批'); facts['gui_direct_instance'] = direct; save()
    assert 'CLAIM' not in read(direct, other)['allowedActions']; act(direct, other)
    facts['gui_policy_verified'] = True; facts['cases'].append('gui-claim-switch-mode-block-save-reenter-publish-and-frozen-v1-new-v2'); save(); print('GUI policy/frozen PASS')
elif stage in ['gui_claim', 'gui_release', 'gui_claim_again']:
    other, _ = ordinary(); iid = facts['gui_instance']; view = read(iid, other)
    original_task = facts['gui_original_task']; task = next(t for t in view['tasks'] if t['id'] == original_task['id'])
    assert task['dueAt'] == original_task['dueAt'] and task['candidates'] == original_task['candidates'] and view['jobs'] == facts['gui_original_jobs']
    if stage == 'gui_release':
        assert task['claimedAt'] is None and task['assigneeTenantMemberId'] is None and view['allowedActions'] == ['CLAIM', 'ADD_RUNTIME_NODE']
        assert task['version'] == original_task['version'] + 2
        facts['cases'].append('ordinary-gui-unclaim-same-task-pool-deadline')
    else:
        assert task['claimedAt'] and view['opinionRequiredActions'] == ['APPROVE']
        assert view['allowedActions'] == ['APPROVE', 'TRANSFER', 'UNCLAIM', 'ADD_RUNTIME_NODE']
        assert task['version'] == original_task['version'] + (1 if stage == 'gui_claim' else 3)
        if stage == 'gui_claim_again':
            facts['gui_claimed_state'] = state(iid); facts['gui_claimed_view'] = view
            facts['frozen_versions'] = sql('SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id IN(' + ','.join(map(str, facts['flows'])) + ') ORDER BY id')
        else:facts['cases'].append('ordinary-gui-claim-real-frozen-task-no-form-submit')
    save(); print(stage + ' PASS')
elif stage == 'claimed_restart':
    other, _ = ordinary(); iid = facts['gui_instance']; assert facts.get('gui_claimed_state')
    assert state(iid) == facts['gui_claimed_state']
    assert read(iid, other) == facts['gui_claimed_view']
    facts['claimed_restart_verified'] = True; facts['cases'].append('same-backend-restart-retains-claimer-time-task-pool-jobs-and-policy'); save(); print('Claimed restart PASS')
elif stage == 'gui_done':
    assert facts.get('claimed_restart_verified'); other, _ = ordinary(); iid = facts['gui_instance']; view = read(iid, other)
    assert view['status'] == 'COMPLETED' and view['allowedActions'] == []
    assert sql(f"SELECT HEX(comment_text) FROM flow_action WHERE instance_id={iid} AND action_code='APPROVE'") == [['已领取并核对，按冻结规则完成审批'.encode('utf-8').hex().upper()]]
    facts['gui_completed_state'] = state(iid); facts['cases'].append('ordinary-gui-claimed-approval-opinion-real-complete-own-result'); facts['gui_verified'] = True; save(); print('GUI done PASS')
elif stage == 'restart':
    assert facts.get('gui_verified'); other, _ = ordinary()
    assert state(facts['gui_instance']) == facts['gui_completed_state'] and read(facts['gui_instance'], other)['status'] == 'COMPLETED'
    assert facts['frozen_versions'] == sql('SELECT id,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id IN(' + ','.join(map(str, facts['flows'])) + ') ORDER BY id')
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
    facts['restart_verified'] = True; facts['cases'].append('completed-restart-frozen-versions-and-source-once'); save(); print('Restart PASS')
elif stage == 'cleanup':
    assert facts.get('restart_verified')
    other, _ = ordinary()
    # 通过真实工作台刷新拥有待办的源状态，避免验收结束留存已完成来源的待处理投影。
    api('GET', '/api/todos?status=ALL&type=ALL', token=other)
    # 只撤回随机夹具权限及停用夹具，归档唯一正式记录；不动既有用户与业务记录。
    publish_role(original['permissions'], original['fieldPolicies'])
    for rid in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': record['version'], 'reason': '领取验收归档'}, admin)
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
    group = sql(f'SELECT group_id FROM cfg_module WHERE id={facts["module_id"]}')
    if group and group[0][0] != 'NULL':
        gid = int(group[0][0]); sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={gid} AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id={gid} AND status='ACTIVE')")
    assert sql(f"SELECT COUNT(*) FROM todo_item t JOIN flow_task f ON t.source_type='FLOW_TASK' AND t.source_id=CAST(f.id AS CHAR) WHERE f.instance_id IN({','.join(map(str,ids))}) AND t.assignee_account_id={aid} AND t.status IN('PENDING','SUSPENDED')") == [['0']]
    facts['cleanup_verified'] = True; facts['cases'].append('exact-fixture-cleanup-and-golden-business-unchanged'); save(); print('Cleanup PASS')
else:
    raise AssertionError('unknown stage')
