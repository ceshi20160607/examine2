"""B09.2a 超时真实 worker 组合验收；隔离模板/身份/业务，凭证只留进程内。"""
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

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'flow', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in names], type_ignores=[]), filename, 'exec'))
PATH = AREA / 'flow-timeout-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2a', 'status': 'running', 'code': 'timeout_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'platform_flows': [], 'actions': [], 'platform_instances': [], 'records': [], 'cases': []}
assert facts['status'] == 'running', '封存事实不能重复执行'
j = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', j).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', j).group(1)})
platform = login['accessToken']
root = api('GET', '/api/auth/me', token=platform)
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    facts['cases'].append({'case': name, **values}); save()


def grant(create=True):
    permissions = copy.deepcopy(original['permissions']) + [{'resourceType': 'MODULE', 'resourceCode': code,
        'actionCode': a, 'dataScopeType': 'ALL'} for a in ['LIST', 'DETAIL', 'UPDATE'] + (['CREATE'] if create else [])]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': a, 'dataScopeType': 'ALL'}
        for a in ['VIEW', 'DESIGN', 'PUBLISH', 'SIMULATE', 'VIEW_RUNTIME', 'START', 'HANDLE']]
    policies = copy.deepcopy(original['fieldPolicies']) + [{'resourceCode': code, 'fieldCode': f,
        'channel': ch, 'readable': True, 'writable': True, 'maskStrategy': None}
        for f in facts['fields'] for ch in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def make(label, action='AUTO_APPROVE', target=None, mode='OR_SIGN', members=None, realm='system', publish=True):
    token = platform if realm == 'platform' else admin
    v = api('POST', '/api/flows', {'name': label, 'code': code + '_' + secrets.token_hex(3)}, token)
    facts['platform_flows' if realm == 'platform' else 'flows'].append(v['id']); save()
    policy = {'type': 'PERSON', 'platformAccountIds' if realm == 'platform' else 'tenantMemberIds':
        members or ([root['accountId']] if realm == 'platform' else [entry['tenantMemberId']])}
    timeout = {'timeoutMinutes': 60, 'action': action}
    if target is not None: timeout['targetPlatformAccountId' if realm == 'platform' else 'targetTenantMemberId'] = target
    nodes = [{'nodeKey': k, 'nodeType': kind, 'name': name, 'positionX': i * 230, 'positionY': 100,
        'config': {'moduleId': facts['module_id']} if kind == 'START' and realm == 'system' else
            {'approvalMode': mode} if kind == 'APPROVAL' else {},
        'assigneePolicy': policy if kind == 'APPROVAL' else {},
        'formPolicy': {'editable': list(facts['fields']), 'required': ['subject']} if kind == 'APPROVAL' and realm == 'system' else {},
        'timeoutPolicy': timeout if kind == 'APPROVAL' else {}}
        for i, (k, kind, name) in enumerate([('start', 'START', '开始'), ('review', 'APPROVAL', label), ('end', 'END', '结束')])]
    edges = [{'edgeKey': str(i), 'sourceNodeKey': a, 'targetNodeKey': b, 'priorityOrder': 0, 'config': {}}
        for i, (a, b) in enumerate([('start', 'review'), ('review', 'end')])]
    api('PUT', f"/api/flows/{v['id']}/draft", {'expectedVersion': v['version'], 'nodes': nodes, 'edges': edges}, token)
    if publish:
        check = api('GET', f"/api/flows/{v['id']}/publication-check", token=token)
        assert check['valid'], [(i['code'], i['message']) for i in check['issues']]
        api('POST', f"/api/flows/{v['id']}/publish", {'expectedDraftRevision': check['draftRevision'], 'changeSummary': label,
            'simulationVariables': {}}, token)
    return v['id']


def bind(fid, event='CREATE', custom=None):
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': event, 'actionId': custom,
        'flowId': fid, 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0, 'condition': {'mode': 'ALL', 'conditions': []},
        'mutuallyExclusive': True, 'replaceExisting': True}, admin)


def start(label, subject='已有完整候选', token=None):
    a = api('POST', f'/api/runtime/modules/{code}/records', {'title': label, 'status': 'ACTIVE', 'participantMemberIds': [],
        'fields': {'subject': subject, 'score': 0, 'enabled': False}}, token or admin, action_key=code + secrets.token_hex(6))
    facts['actions'].append(a); save(); return a


def start_platform(fid, label):
    v = api('POST', '/api/flow-runtime/instances', {'flowId': fid, 'title': label, 'variables': {}, 'businessSnapshot': {},
        'idempotencyKey': code + secrets.token_hex(6)}, platform)['instance']
    facts['platform_instances'].append(v['id']); save(); return {'flowInstanceId': v['id'], 'realm': 'platform'}


def read(a):
    return api('GET', f"/api/flow-runtime/instances/{a['flowInstanceId']}", token=platform if a.get('realm') == 'platform' else admin)


def poll(a, predicate):
    until = time.monotonic() + 20
    while time.monotonic() < until:
        v = read(a)
        if predicate(v): return v
        time.sleep(.2)
    raise AssertionError(('真实 worker 尚未达预期', a['flowInstanceId'], v['status'],
        [(j['status'], j.get('lastErrorCode')) for j in v['jobs']]))


def expire(a, task_ids=None):
    iid = int(a['flowInstanceId'])
    ids = [t['id'] for t in read(a)['tasks'] if t['status'] == 'PENDING'] if task_ids is None else task_ids
    assert ids
    selected = ','.join(str(int(i)) for i in ids)
    # 只提前本夹具当前任务及对应持久化job期限，不改系统时钟或其他业务的时限。
    sql(f"START TRANSACTION; UPDATE flow_task SET due_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE),version=version+1 "
        f"WHERE instance_id={iid} AND id IN({selected}) AND status='PENDING'; "
        f"UPDATE flow_job SET next_run_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE),version=version+1 WHERE instance_id={iid} "
        f"AND job_type='TASK_TIMEOUT' AND status='PENDING' AND JSON_EXTRACT(payload_json,'$.taskId') IN({selected}); COMMIT;")


def handle(task, token, values=None, action='APPROVE'):
    return call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': action,
        'comment': '隔离超时组合验收', 'fieldValues': values or {}, 'expectedFormRevision': 0,
        'idempotencyKey': code + secrets.token_hex(6)}, token)


def completed(a):
    poll(a, lambda v: v['status'] == 'COMPLETED')
    d = delivery(a); assert d['businessStatus'] == 'EXECUTED', d
    rid = d['recordId']; facts['records'].append(rid); save()
    r = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
    assert r['fields']['score'] == 0 and r['fields']['enabled'] is False
    assert int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND id={int(rid)}")[0][0]) == 1
    return r


stage = sys.argv[1]
if stage == 'api':
    assert not facts.get('api_started'); facts['api_started'] = True
    facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id'); save()
    module = api('POST', '/api/admin/module-config/modules', {'name': '超时审批公共闭环验收', 'code': code, 'groupName': '超时隔离验收'}, admin)['module']
    facts['module_id'] = module['id']; save()
    for i, (f, name, kind) in enumerate([('subject', '审批主题', 'TEXT'), ('score', '审批数量', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
        facts['fields'][f] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {'code': f, 'name': name,
            'fieldType': kind, 'sortOrder': i, 'required': False, 'config': {}}, admin); save()
    publish_module(module['id']); grant()
    username = code + secrets.token_hex(3)
    reg = api('POST', '/api/auth/register', {'username': username, 'password': secrets.token_urlsafe(24),
        'displayName': '超时接口处理人', 'systemName': '超时隔离注册系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}; save()
    member = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)
    other = member['tenantMemberId']; facts['api_member'] = other; save()
    token = api('POST', '/api/systems/54/tenants/57/enter', {}, reg['platformTokens']['accessToken'])['tokens']['accessToken']
    fid = make('完整候选超时自动同意'); bind(fid); a = start('超时安全同意')
    expire(a); r = completed(a)
    assert any('自动同意' in h['eventName'] and h['actorName'] == '系统自动处理' for h in read(a)['history'])
    case('trusted_complete_candidate_auto_approve_zero_false_once_real_worker', instance_id=a['flowInstanceId'], record_id=r['id'])
    missing = start('超时缺节点必填', None); expire(missing)
    v = poll(missing, lambda v: v['status'] == 'EXCEPTION')
    assert v['tasks'][0]['status'] == 'PENDING' and v['errorCode'] == 'FLOW_NODE_FIELD_REQUIRED'
    for action in ['RESUME', 'RETRY']:
        status, response = call('POST', f"/api/flow-runtime/incidents/{v['exceptions'][0]['id']}/actions",
            {'actionCode': action, 'comment': '不能以异常补偿绕过必填', 'idempotencyKey': code + secrets.token_hex(6)}, admin)
        assert status == 409 and response['code'] == 'FLOW_INCIDENT_TASK_PENDING', (status, response.get('code'))
    assert read(missing)['status'] == 'EXCEPTION'
    status, result = handle(v['tasks'][0], admin, {'subject': '经原待办合法补充'})
    assert status == 200, (status, result.get('code'))
    completed(missing); assert all(i['status'] == 'RESOLVED' for i in read(missing)['exceptions'])
    case('missing_required_preserves_task_blocks_resume_retry_and_original_task_can_recover')
    fid = make('仅提醒不自动审批', 'REMIND', mode='ALL_SIGN', members=[entry['tenantMemberId'], other]); bind(fid)
    remind = start('一次超时提醒'); tid = next(t['id'] for t in read(remind)['tasks'] if t['assigneeTenantMemberId'] == entry['tenantMemberId'])
    expire(remind, [tid]); v = poll(remind, lambda v: any(j['status'] == 'COMPLETED' for j in v['jobs']))
    assert v['status'] == 'WAITING' and all(t['status'] == 'PENDING' for t in v['tasks'])
    jid = next(j['id'] for j in v['jobs'] if j['status'] == 'COMPLETED')
    assert int(sql(f"SELECT COUNT(*) FROM msg_message WHERE source_id='timeout-job-{jid}-reminder'")[0][0]) == 1
    case('one_time_reminder_keeps_all_sign_pending_no_formal_execution', instance_id=remind['flowInstanceId'], job_id=jid)
    fid = make('超时拒绝清理会签', 'AUTO_REJECT', mode='ALL_SIGN', members=[entry['tenantMemberId'], other]); bind(fid)
    rejected = start('超时自动拒绝不生效'); expire(rejected, [next(t['id'] for t in read(rejected)['tasks'] if t['assigneeTenantMemberId'] == entry['tenantMemberId'])])
    v = poll(rejected, lambda v: v['status'] == 'REJECTED')
    assert sorted(t['status'] for t in v['tasks']) == ['CANCELLED', 'REJECT'] and delivery(rejected)['businessStatus'] == 'REJECTED'
    case('auto_reject_cancels_siblings_and_original_business_not_saved')
    fid = make('超时指定当前成员转交', 'TRANSFER', target=other); bind(fid); transferred = start('超时转交可处理')
    expire(transferred); v = poll(transferred, lambda v: v['tasks'][0]['assigneeTenantMemberId'] == other)
    task = v['tasks'][0]; assert task.get('dueAt') is None
    assert handle(task, admin)[0] == 403
    assert handle(task, token)[0] == 200
    completed(transferred)
    case('transfer_current_identity_old_handler_denied_new_handler_works_deadline_cleared')
    # 指定目标在实例锁等待期间停用；真实 worker 必须使用等待结束后的当前目录。
    waiting = start('锁等待后目标停用')
    iid = int(waiting['flowInstanceId'])
    proc = subprocess.Popen(['docker', 'exec', '-i', 'unexamine-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N --unbuffered unexamine'], stdin=subprocess.PIPE,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8')
    try:
        proc.stdin.write(f"START TRANSACTION; SELECT id FROM flow_instance WHERE id={iid} FOR UPDATE; SELECT 'LOCK_READY';\n"); proc.stdin.flush()
        assert proc.stdout.readline().strip() == str(iid) and proc.stdout.readline().strip() == 'LOCK_READY'
        expire(waiting)
        until = time.monotonic() + 10; waiters = 0
        while time.monotonic() < until:
            waiters = int(sql("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA='unexamine' AND l.OBJECT_NAME='flow_instance' AND l.LOCK_DATA='" + str(iid) + "'")[0][0])
            if waiters: break
            time.sleep(.1)
        assert waiters >= 1, '未建立实际 worker 锁等待'
        sql(f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE id={int(other)} AND system_id=54 AND tenant_id=57")
        proc.stdin.write('COMMIT;\n'); proc.stdin.flush()
    finally:
        if proc.poll() is None:
            proc.stdin.write('ROLLBACK;\n'); proc.stdin.close(); proc.wait(timeout=10)
    v = poll(waiting, lambda v: v['status'] == 'EXCEPTION')
    assert v['tasks'][0]['assigneeTenantMemberId'] == entry['tenantMemberId'] and v['tasks'][0]['status'] == 'PENDING'
    sql(f"UPDATE sys_tenant_member SET status='ACTIVE',version=version+1 WHERE id={int(other)} AND system_id=54 AND tenant_id=57")
    case('actual_worker_lock_wait_then_target_disabled_current_directory_rejects_transfer', waiters=waiters)
    fid = make('原发起人撤权与独立正常作业'); bind(fid)
    revoked = start('原发起人撤权不能自动通过', token=token); good = start('同批正常独立作业')
    grant(False); expire(revoked); expire(good)
    v = poll(revoked, lambda v: v['status'] == 'EXCEPTION')
    assert v['tasks'][0]['status'] == 'PENDING' and v['errorCode'] == 'PERMISSION_DENIED'
    completed(good); grant()
    case('original_actor_current_permission_denied_failed_job_does_not_block_other_instance')
    sequential = make('超时顺序会签激活期限', mode='SEQUENTIAL', members=[entry['tenantMemberId'], other]); bind(sequential)
    a = start('顺序超时第一人不跳后人'); before = read(a)
    assert sorted(t['status'] for t in before['tasks']) == ['PENDING', 'QUEUED']
    expire(a); v = poll(a, lambda v: any(t['assigneeTenantMemberId'] == other and t['status'] == 'PENDING' for t in v['tasks']))
    next_task = next(t for t in v['tasks'] if t['assigneeTenantMemberId'] == other)
    assert next_task['dueAt'] and next_task['status'] == 'PENDING' and v['status'] == 'WAITING'
    expire(a); completed(a)
    case('sequential_only_active_task_times_out_next_deadline_begins_at_activation')
    facts['gui_flow'] = make('普通页面超时结构化配置', 'REMIND', publish=False); save()
    facts['api_verified'] = True; save(); print(json.dumps({'api_verified': True, 'module_id': facts['module_id'], 'gui_flow': facts['gui_flow']}))
elif stage == 'platform':
    assert facts.get('api_verified') and not facts.get('platform_started')
    facts['platform_started'] = True; save()
    for action, expected in [('REMIND', 'WAITING'), ('AUTO_APPROVE', 'COMPLETED'), ('AUTO_REJECT', 'REJECTED')]:
        fid = make('平台超时' + action, action, realm='platform')
        a = start_platform(fid, '平台独立超时' + action); expire(a)
        v = poll(a, lambda v: any(j['status'] == 'COMPLETED' for j in v['jobs']))
        assert v['status'] == expected
        assert any(h['actorName'] == '系统自动处理' and '超时' in h['eventName'] for h in v['history'])
        case('platform_independent_timeout_' + action.lower(), instance_id=a['flowInstanceId'])
    facts['ordinary_platform_role_baseline'] = sql('SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id=67 ORDER BY id'); save()
    # 平台管理API尚未交付；这里显式构造隔离授权夹具，仅验证流程，结束精确撤销，不算平台组织交付。
    rid = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','超时平台流程隔离验收','仅本次流程测试授权',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0])
    facts['platform_role_id'] = rid; save()
    for action in ['VIEW', 'DESIGN', 'PUBLISH', 'SIMULATE', 'VIEW_RUNTIME', 'START', 'HANDLE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['ordinary_platform_link_id'] = int(sql(f"INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,67,{rid}); SELECT LAST_INSERT_ID()")[0][0]); save()
    fid = make('平台超时指定人员转交', 'TRANSFER', target=69, realm='platform')
    a = start_platform(fid, '平台超时转交普通页面处理'); expire(a)
    v = poll(a, lambda v: not v['tasks'][0]['currentHandler'])
    assert sql(f"SELECT assignee_account_id FROM flow_task WHERE instance_id={int(a['flowInstanceId'])}") == [['69']]
    assert v['tasks'][0].get('dueAt') is None and not v['tasks'][0]['currentHandler']
    assert handle(v['tasks'][0], platform)[0] == 403
    facts['platform_gui_instance'] = a['flowInstanceId']; save()
    case('platform_same_realm_transfer_deadline_cleared_old_handler_denied_for_actual_gui', instance_id=a['flowInstanceId'])
    # 系统成员身份不能当平台目标；发布检查和实际publish都必须拒绝，无新运行版本。
    fid = make('非法跨域超时配置不得发布', 'TRANSFER', realm='platform', publish=False)
    draft = api('GET', f'/api/flows/{fid}', token=platform)
    nodes = draft['nodes']
    for n in nodes:
        if n['nodeType'] == 'APPROVAL': n['timeoutPolicy'] = {'timeoutMinutes': 1, 'action': 'TRANSFER', 'targetTenantMemberId': entry['tenantMemberId']}
    api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': draft['version'], 'nodes': nodes, 'edges': draft['edges']}, platform)
    check = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
    assert not check['valid'] and any(i['code'] == 'FLOW_TIMEOUT_TARGET_INVALID' for i in check['issues'])
    status, response = call('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': '验证发布拒绝非法超时目标', 'simulationVariables': {}}, platform)
    assert status == 422 and response['code'] == 'FLOW_PUBLICATION_INVALID'
    case('platform_cross_system_timeout_identity_publication_and_publish_rejected')
    facts['platform_verified'] = True; save(); print(json.dumps({'platform_verified': True, 'gui_instance': facts['platform_gui_instance']}))
elif stage == 'platform_resume':
    assert facts.get('platform_started') and not facts.get('platform_verified')
    # 141–143已通过不重复；144只是Task DTO不存在accountId的验收假设错误，未改产品或重建实例。
    a = {'flowInstanceId': facts['platform_instances'][-1], 'realm': 'platform'}
    v = read(a); task = v['tasks'][0]
    assert task['status'] == 'PENDING' and not task['currentHandler'] and task.get('dueAt') is None
    assert sql(f"SELECT assignee_account_id FROM flow_task WHERE instance_id={int(a['flowInstanceId'])}") == [['69']]
    assert handle(task, platform)[0] == 403
    facts['platform_gui_instance'] = a['flowInstanceId']; save()
    case('platform_same_realm_transfer_actual_dto_and_persisted_account_old_handler_denied', instance_id=a['flowInstanceId'])
    fid = make('非法跨域超时配置不得发布', 'TRANSFER', realm='platform', publish=False)
    draft = api('GET', f'/api/flows/{fid}', token=platform); nodes = draft['nodes']
    for n in nodes:
        if n['nodeType'] == 'APPROVAL': n['timeoutPolicy'] = {'timeoutMinutes': 1, 'action': 'TRANSFER', 'targetTenantMemberId': entry['tenantMemberId']}
    api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': draft['version'], 'nodes': nodes, 'edges': draft['edges']}, platform)
    check = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
    assert not check['valid'] and any(i['code'] == 'FLOW_TIMEOUT_TARGET_INVALID' for i in check['issues'])
    status, response = call('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': '验证发布拒绝非法超时目标', 'simulationVariables': {}}, platform)
    assert status == 422 and response['code'] == 'FLOW_PUBLICATION_INVALID'
    case('platform_cross_system_timeout_identity_publication_and_publish_rejected')
    facts['platform_verified'] = True; save(); print(json.dumps({'platform_verified': True, 'gui_instance': facts['platform_gui_instance']}))
elif stage == 'extra':
    assert facts.get('platform_verified') and not facts.get('extra_started')
    facts['extra_started'] = True; save()
    # 复用未发布的非法配置夹具，逐项真实发布检查；不反复建模板、不重跑已通过worker用例。
    fid = facts['platform_flows'][-1]
    invalid = [({'timeoutMinutes': v, 'action': 'REMIND'}, 'FLOW_TIMEOUT_DURATION_INVALID') for v in [0, -1, 1.5, '1', True, 525601]]
    invalid += [({'timeoutMinutes': 1, 'minutes': 2, 'action': 'REMIND'}, 'FLOW_TIMEOUT_DURATION_CONFLICT'),
        ({'timeoutMinutes': None, 'minutes': 1, 'action': 'REMIND'}, 'FLOW_TIMEOUT_DURATION_CONFLICT'),
        ({'timeoutMinutes': 1, 'action': 'UNKNOWN'}, 'FLOW_TIMEOUT_ACTION_INVALID'),
        ({'timeoutMinutes': 1, 'action': 'ESCALATE_MANAGER'}, 'FLOW_TIMEOUT_ACTION_NOT_AVAILABLE'),
        ({'timeoutMinutes': 1, 'action': 'TRANSFER', 'targetPlatformAccountId': [69]}, 'FLOW_TIMEOUT_TARGET_INVALID'),
        ({'timeoutMinutes': 1, 'action': 'TRANSFER', 'targetPlatformAccountId': 9999999}, 'FLOW_TIMEOUT_TARGET_INVALID')]
    for policy, expected in invalid:
        draft = api('GET', f'/api/flows/{fid}', token=platform); nodes = draft['nodes']
        for n in nodes:
            if n['nodeType'] == 'APPROVAL': n['timeoutPolicy'] = policy
        api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': draft['version'], 'nodes': nodes, 'edges': draft['edges']}, platform)
        check = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
        assert not check['valid'] and expected in [i['code'] for i in check['issues']], (policy, [i['code'] for i in check['issues']])
    case('typed_publication_invalid_minutes_legacy_conflicts_actions_and_targets_blocked', checks=len(invalid))
    for policy in [{'minutes': 1, 'action': 'REMIND'}, {'timeoutMinutes': None, 'action': 'AUTO_APPROVE'}]:
        draft = api('GET', f'/api/flows/{fid}', token=platform); nodes = draft['nodes']
        for n in nodes:
            if n['nodeType'] == 'APPROVAL': n['timeoutPolicy'] = policy
        api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': draft['version'], 'nodes': nodes, 'edges': draft['edges']}, platform)
        assert api('GET', f'/api/flows/{fid}/publication-check', token=platform)['valid']
    case('legacy_minutes_supported_explicit_null_does_not_enable_old_action')
    target_account = int(facts['registration']['account_id'])
    fid = make('平台冻结目标停用不可转交', 'TRANSFER', target=target_account, realm='platform')
    a = start_platform(fid, '平台目标停用安全异常')
    sql(f"UPDATE plat_member SET status='DISABLED',version=version+1 WHERE platform_id=1 AND account_id={target_account}")
    assert not api('GET', f'/api/flows/{fid}/publication-check', token=platform)['valid']
    expire(a); v = poll(a, lambda v: v['status'] == 'EXCEPTION')
    assert v['tasks'][0]['status'] == 'PENDING' and v['tasks'][0]['currentHandler']
    sql(f"UPDATE plat_member SET status='ACTIVE',version=version+1 WHERE platform_id=1 AND account_id={target_account}")
    case('platform_target_disabled_publication_and_frozen_runtime_safe_task_preserved')
    fid = make('人工与真实超时worker竞争'); bind(fid); a = start('人工与超时只推进一次')
    iid = int(a['flowInstanceId']); task = read(a)['tasks'][0]
    proc = subprocess.Popen(['docker', 'exec', '-i', 'unexamine-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N --unbuffered unexamine'], stdin=subprocess.PIPE,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8')
    try:
        proc.stdin.write(f"START TRANSACTION; SELECT id FROM flow_instance WHERE id={iid} FOR UPDATE; SELECT 'LOCK_READY';\n"); proc.stdin.flush()
        assert proc.stdout.readline().strip() == str(iid) and proc.stdout.readline().strip() == 'LOCK_READY'
        expire(a)
        with ThreadPoolExecutor(max_workers=1) as pool:
            request = pool.submit(handle, task, admin)
            until = time.monotonic() + 10; waiters = 0
            while time.monotonic() < until:
                waiters = int(sql("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA='unexamine' AND l.OBJECT_NAME='flow_instance' AND l.LOCK_DATA='" + str(iid) + "'")[0][0])
                if waiters >= 2: break
                time.sleep(.1)
            assert waiters >= 2, '未建立人工和真实worker双请求等待'
            proc.stdin.write('COMMIT;\n'); proc.stdin.flush()
            status, response = request.result(timeout=30)
            assert status in [200, 409], (status, response.get('code'))
    finally:
        if proc.poll() is None:
            proc.stdin.write('ROLLBACK;\n'); proc.stdin.close(); proc.wait(timeout=10)
    completed(a)
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id={iid} AND node_key='end'") == [['1']]
    case('actual_manual_and_worker_wait_same_instance_only_one_task_and_end_and_formal_save', waiters=waiters, manual_status=status)
    # CUSTOM可写参数仅subject，冻结节点另要求score；不能因非参数删除节点必填。
    rid = facts['records'][0]
    r = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
    api('PUT', f'/api/runtime/modules/{code}/records/{rid}', {'title': r['title'], 'status': r['status'], 'version': r['version'],
        'ownerMemberId': r['ownerMemberId'], 'departmentId': r['departmentId'], 'participantMemberIds': r['participantMemberIds'],
        'fields': {'score': None}}, admin)
    custom = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/actions", {'name': '仅修改主题', 'location': 'ROW',
        'parameterFieldCodes': ['subject'], 'confirmation': False}, admin)
    facts['custom'] = custom; save(); publish_module(facts['module_id'])
    fid = make('CUSTOM参数外节点必填不得忽略', publish=False)
    draft = api('GET', f'/api/flows/{fid}', token=admin); nodes = draft['nodes']
    for n in nodes:
        if n['nodeType'] == 'APPROVAL': n['formPolicy']['required'] = ['score']
    api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': draft['version'], 'nodes': nodes, 'edges': draft['edges']}, admin)
    check = api('GET', f'/api/flows/{fid}/publication-check', token=admin); assert check['valid'], check['issues']
    api('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': 'CUSTOM参数外必填校验', 'simulationVariables': {}}, admin)
    bind(fid, 'MANUAL', custom['id'])
    current = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
    a = api('POST', f"/api/runtime/modules/{code}/records/{rid}/actions/{custom['code']}", {'version': current['version'],
        'values': {'subject': '不能旁路的字段动作'}}, admin, action_key=code + secrets.token_hex(6))
    facts['actions'].append(a); save(); expire(a)
    v = poll(a, lambda v: v['status'] == 'EXCEPTION')
    assert v['errorCode'] == 'FLOW_NODE_FIELD_REQUIRED' and v['tasks'][0]['status'] == 'PENDING'
    assert handle(v['tasks'][0], admin)[0] == 422
    assert api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)['version'] == current['version']
    case('custom_non_parameter_required_internal_check_automatic_and_manual_keep_formal_unchanged')
    facts['extra_verified'] = True; save(); print(json.dumps({'extra_verified': True}))
elif stage == 'extra_resume':
    assert facts.get('extra_started') and not facts.get('extra_verified')
    # 已通过12项配置校验及真实双请求竞争不重跑；仅补测试请求遗漏的changeSummary和剩余CUSTOM用例。
    fid = facts['flows'][-1]; custom = facts['custom']; rid = facts['records'][0]
    check = api('GET', f'/api/flows/{fid}/publication-check', token=admin); assert check['valid']
    api('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': 'CUSTOM参数外必填校验', 'simulationVariables': {}}, admin)
    bind(fid, 'MANUAL', custom['id'])
    current = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
    a = api('POST', f"/api/runtime/modules/{code}/records/{rid}/actions/{custom['code']}", {'version': current['version'],
        'values': {'subject': '不能旁路的字段动作'}}, admin, action_key=code + secrets.token_hex(6))
    facts['actions'].append(a); save(); expire(a)
    v = poll(a, lambda v: v['status'] == 'EXCEPTION')
    assert v['errorCode'] == 'FLOW_NODE_FIELD_REQUIRED' and v['tasks'][0]['status'] == 'PENDING'
    assert handle(v['tasks'][0], admin)[0] == 422
    assert api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)['version'] == current['version']
    case('custom_non_parameter_required_internal_check_automatic_and_manual_keep_formal_unchanged')
    # 先前publish测试漏changeSummary只得到通用VALIDATION_FAILED，不能作为真实发布拦截证据；校正并定向验证。
    pid = facts['platform_flows'][-2]
    draft = api('GET', f'/api/flows/{pid}', token=platform); nodes = draft['nodes']
    for n in nodes:
        if n['nodeType'] == 'APPROVAL': n['timeoutPolicy'] = {'timeoutMinutes': 1, 'action': 'TRANSFER', 'targetTenantMemberId': entry['tenantMemberId']}
    api('PUT', f'/api/flows/{pid}/draft', {'expectedVersion': draft['version'], 'nodes': nodes, 'edges': draft['edges']}, platform)
    check = api('GET', f'/api/flows/{pid}/publication-check', token=platform)
    status, response = call('POST', f'/api/flows/{pid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': '验证发布拒绝非法超时目标', 'simulationVariables': {}}, platform)
    assert status == 422 and response['code'] == 'FLOW_PUBLICATION_INVALID'
    case('corrected_valid_publish_request_cross_realm_timeout_target_rejected_by_business_rule', status=status, error_code=response['code'])
    facts['extra_verified'] = True; save(); print(json.dumps({'extra_verified': True}))
elif stage == 'gui':
    assert facts.get('extra_verified') and not facts.get('gui_verified')
    old = read({'flowInstanceId': facts['platform_gui_instance'], 'realm': 'platform'})
    assert old['status'] == 'COMPLETED' and old['flowVersionNumber'] == 1
    assert any(h['actorName'] == '来源表单普通页面验收' and h['eventName'] == '审批通过' for h in old['history'])
    value = api('GET', '/api/flows/64', token=platform)
    policy = next(n['timeoutPolicy'] for n in value['nodes'] if n['nodeType'] == 'APPROVAL')
    assert policy['timeoutMinutes'] == 7 and policy['action'] == 'REMIND'
    current = next(v for v in value['versions'] if v['id'] == value['currentVersionId'])
    assert current['versionNumber'] == 2
    iid = int(facts['platform_gui_instance'])
    assert sql(f"SELECT JSON_UNQUOTE(JSON_EXTRACT(assignee_snapshot_json,'$.timeoutPolicy.action')),JSON_EXTRACT(assignee_snapshot_json,'$.timeoutPolicy.timeoutMinutes') FROM flow_task WHERE instance_id={iid}") == [['TRANSFER', '60']]
    facts['gui_verified'] = True; save()
    case('actual_ordinary_gui_target_approval_current_handler_and_structured_duration_person_action_save_reload_publish',
        instance_id=iid, new_version=current['versionNumber'], new_timeout_policy={'timeoutMinutes': 7, 'action': 'REMIND'},
        old_snapshot={'timeoutMinutes': 60, 'action': 'TRANSFER'})
    print(json.dumps({'gui_verified': True}))
elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_started')
    facts['restart_started'] = True; save()
    fid = make('持久化到期作业重启恢复'); bind(fid); a = start('后端停止期间到期再恢复')
    tasks = [t['id'] for t in read(a)['tasks'] if t['status'] == 'PENDING']
    result = subprocess.run(['docker', 'stop', 'unexamine-golden-backend'], capture_output=True, text=True)
    assert result.returncode == 0
    expire(a, tasks)
    assert sql(f"SELECT status FROM flow_instance WHERE id={int(a['flowInstanceId'])}") == [['WAITING']]
    assert sql(f"SELECT status FROM flow_job WHERE instance_id={int(a['flowInstanceId'])}") == [['PENDING']]
    result = subprocess.run(['docker', 'start', 'unexamine-golden-backend'], capture_output=True, text=True)
    assert result.returncode == 0
    until = time.monotonic() + 60
    while time.monotonic() < until:
        try:
            import urllib.request
            with urllib.request.urlopen('http://localhost:18080/actuator/health', timeout=2) as response:
                if json.load(response).get('status') == 'UP': break
        except Exception: pass
        time.sleep(.5)
    else: raise AssertionError('既有backend重启未就绪，保留待执行作业，不新建环境')
    completed(a)
    jid = next(c['job_id'] for c in facts['cases'] if c['case'] == 'one_time_reminder_keeps_all_sign_pending_no_formal_execution')
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE source_id='timeout-job-{jid}-reminder'") == [['1']]
    facts['restart_verified'] = True; save()
    case('same_backend_restart_persisted_due_job_recovers_once_completed_reminder_not_repeated', instance_id=a['flowInstanceId'])
    print(json.dumps({'restart_verified': True}))
elif stage == 'lifecycle':
    assert facts.get('restart_verified') and not facts.get('lifecycle_started')
    facts['lifecycle_started'] = True; save()
    fid = make('人工完成立即取消到期检查'); bind(fid); a = start('人工完成不留无效时限')
    task = read(a)['tasks'][0]; assert read(a)['jobs'][0]['status'] == 'PENDING'
    assert handle(task, admin)[0] == 200; completed(a)
    assert all(j['status'] == 'CANCELLED' for j in read(a)['jobs'])
    case('human_task_completed_future_timeout_job_cancelled_immediately_and_business_once')
    fid = make('超时拒绝立即取消兄弟到期检查', 'AUTO_REJECT', mode='ALL_SIGN', members=[entry['tenantMemberId'], 108]); bind(fid)
    a = start('真实拒绝清理无效时限'); task = next(t for t in read(a)['tasks'] if t['assigneeTenantMemberId'] == entry['tenantMemberId'])
    expire(a, [task['id']]); v = poll(a, lambda v: v['status'] == 'REJECTED')
    assert sorted(j['status'] for j in v['jobs']) == ['CANCELLED', 'COMPLETED']
    assert sorted(t['status'] for t in v['tasks']) == ['CANCELLED', 'REJECT']
    assert delivery(a)['businessStatus'] == 'REJECTED'
    case('auto_reject_future_sibling_job_cancelled_immediately')
    fid = make('终止排队任务及持久化期限', mode='SEQUENTIAL', members=[entry['tenantMemberId'], 108]); bind(fid)
    a = start('终止取消排队与到期作业'); v = read(a)
    assert sorted(t['status'] for t in v['tasks']) == ['PENDING', 'QUEUED'] and v['jobs'][0]['status'] == 'PENDING'
    api('POST', f"/api/flow-runtime/instances/{a['flowInstanceId']}/actions", {'actionCode': 'TERMINATE',
        'comment': '隔离期限生命周期验收', 'idempotencyKey': code + secrets.token_hex(6)}, admin)
    v = read(a); assert all(t['status'] == 'CANCELLED' for t in v['tasks']) and all(j['status'] == 'CANCELLED' for j in v['jobs'])
    case('terminate_cancels_active_queued_tasks_and_all_future_jobs')
    facts['lifecycle_verified'] = True; save(); print(json.dumps({'lifecycle_verified': True}))
elif stage == 'cleanup':
    assert facts.get('lifecycle_verified') and not facts.get('cleanup_verified')
    # 收尾前恢复真实角色；保留所有原权限、描述和字段策略，不覆盖无关角色或用户数据。
    role = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    if role['permissions'] != original['permissions'] or role['fieldPolicies'] != original['fieldPolicies']:
        publish_role(original['permissions'], original['fieldPolicies'])
    for a in facts['actions']:
        v = read(a)
        if v['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
            api('POST', f"/api/flow-runtime/instances/{a['flowInstanceId']}/actions", {'actionCode': 'TERMINATE',
                'comment': '本次隔离超时验收收尾，不执行未批准业务', 'idempotencyKey': code + secrets.token_hex(6)}, admin)
    for iid in facts['platform_instances']:
        v = read({'flowInstanceId': iid, 'realm': 'platform'})
        if v['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
            api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE',
                'comment': '本次隔离平台超时验收收尾', 'idempotencyKey': code + secrets.token_hex(6)}, platform)
    mid = int(facts['module_id'])
    archived = {int(r[0]) for r in sql(f"SELECT id FROM biz_record WHERE module_id={mid} AND archived=1")}
    for rid in facts['records']:
        if rid in archived: continue
        r = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': r['version'], 'reason': '隔离超时组合验收完成'}, admin)
    all_instances = [int(a['flowInstanceId']) for a in facts['actions']] + [int(i) for i in facts['platform_instances']]
    selected = ','.join(str(i) for i in all_instances)
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({selected}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    # 生命周期修复之前135自动拒绝留下的兄弟未来job是旧失败夹具；精确修复并单列，非新实现证明。
    stale = sql(f"SELECT j.id,j.instance_id FROM flow_job j JOIN flow_instance i ON i.id=j.instance_id "
        f"WHERE j.instance_id IN({selected}) AND j.status='PENDING' AND i.status NOT IN('RUNNING','WAITING','EXCEPTION')")
    if stale:
        ids = ','.join(str(int(r[0])) for r in stale)
        sql(f"UPDATE flow_job SET status='CANCELLED',completed_at=NOW(),version=version+1 WHERE id IN({ids}) AND status='PENDING'")
    facts['pre_lifecycle_fixture_job_repair'] = stale
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({selected}) AND status IN('PENDING','RUNNING')") == [['0']]
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND code='{code}'; "
        f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE system_id=54 AND module_id={mid}; "
        f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE system_id=54 AND owner_tenant_id=57 AND code LIKE '{code}_%'; "
        f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE context_type='PLATFORM' AND platform_id=1 AND code LIKE '{code}_%'")
    rid = int(facts['platform_role_id']); link = int(facts['ordinary_platform_link_id'])
    sql(f"DELETE FROM plat_member_role WHERE id={link} AND platform_id=1 AND member_id=67 AND role_id={rid}; "
        f"UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid} AND platform_id=1 AND code='{code}'")
    assert sql('SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id=67 ORDER BY id') == facts['ordinary_platform_role_baseline']
    account = int(facts['registration']['account_id']); self_system = int(facts['registration']['system_id'])
    sql(f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account} AND username='{facts['registration']['username']}'; "
        f"UPDATE plat_member SET status='DISABLED',version=version+1 WHERE platform_id=1 AND account_id={account}; "
        f"UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account} AND system_id IN(54,{self_system}); "
        f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 "
        f"WHERE sm.account_id={account} AND sm.system_id IN(54,{self_system}) AND tm.system_id=sm.system_id AND tm.status='ACTIVE'; "
        f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={self_system} AND creator_account_id={account}; "
        f"UPDATE auth_session SET revoked=1,revoked_reason='timeout-acceptance-closed' WHERE account_id={account} AND revoked=0")
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == [['4']]
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={mid} AND archived=0") == [['0']]
    group = sql(f"SELECT group_id FROM cfg_module WHERE id={mid}")
    if group and group[0][0] != 'NULL':
        gid = int(group[0][0]); sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={gid} "
            "AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id=" + str(gid) + " AND status<>'DISABLED')")
    facts['cleanup_verified'] = True; save()
    case('exact_role_fixture_links_actor_sessions_modules_bindings_flows_and_formal_archive_zero_pending_jobs_golden_unchanged',
        archived_records=len(facts['records']), pending_tasks=0, pending_jobs=0, old_fixture_job_repair=stale)
    print(json.dumps({'cleanup_verified': True, 'records_archived': len(facts['records']), 'old_fixture_job_repair': stale}))
else:
    raise AssertionError('未定义验收阶段')
