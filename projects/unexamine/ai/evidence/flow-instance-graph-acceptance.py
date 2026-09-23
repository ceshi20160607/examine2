"""B09.4a 本地实例图及真实业务验收；凭证仅在内存，夹具精确登记和关闭。"""
import ast
import copy
import json
import os
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
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in names], type_ignores=[]), filename, 'exec'))
PATH = AREA / 'flow-instance-graph-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.4a', 'status': 'running', 'code': 'graph_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'actions': [], 'platform_instances': [], 'records': [], 'cases': []}
assert facts['status'] == 'running', '封存事实不重跑'
java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
platform = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']
legacy_sql = sql

def sql(statement):
    # 本包中文条件与结果使用明确UTF-8连接，避免本机mysql默认字符集导致假失败。
    return legacy_sql('SET NAMES utf8mb4; ' + statement)


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    facts['cases'].append({'case': name, **values}); save()


def grant(add=True):
    permissions = copy.deepcopy(original['permissions']) + [{'resourceType': 'MODULE', 'resourceCode': code,
        'actionCode': action, 'dataScopeType': 'ALL'} for action in ['LIST', 'DETAIL', 'CREATE', 'UPDATE']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['VIEW_RUNTIME', 'HANDLE'] + (['ADD_MANUAL_NODE'] if add else [])]
    if not add:
        permissions = [p for p in permissions if not (p['resourceType'] == 'FLOW' and p['actionCode'] in ['ADD_MANUAL_NODE', '*'])]
    policies = copy.deepcopy(original['fieldPolicies']) + [{'resourceCode': code, 'fieldCode': field,
        'channel': channel, 'readable': True, 'writable': True, 'maskStrategy': None}
        for field in facts['fields'] for channel in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def draft(fid, nodes, edges, token=admin):
    current = api('GET', f'/api/flows/{fid}', token=token)
    api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token)


def publish(fid, token=admin):
    check = api('GET', f'/api/flows/{fid}/publication-check', token=token)
    assert check['valid'], [(i['code'], i['message']) for i in check['issues']]
    return api('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': '实例动态后续节点真实验收', 'simulationVariables': {}}, token)


def make(label, realm='system', allow=None, mode='OR_SIGN', timeout=False, people=None, published=True):
    token = platform if realm == 'platform' else admin
    fid = api('POST', '/api/flows', {'code': code + '_' + secrets.token_hex(3), 'name': label}, token)['id']
    facts['flows'].append(fid); save()
    start_config = {'moduleId': facts['module_id']} if realm == 'system' else {}
    if allow is not None: start_config['runtimeRules'] = {'allowAddNodes': allow}
    people = people or ([facts['gui_account']] if realm == 'platform' else [entry['tenantMemberId']])
    nodes = [{'nodeKey': key, 'nodeType': kind, 'name': name, 'positionX': index * 220, 'positionY': 100,
        'config': start_config if kind == 'START' else {'approvalMode': mode} if kind == 'APPROVAL' else {},
        'assigneePolicy': {'type': 'PERSON', 'platformAccountIds' if realm == 'platform' else 'tenantMemberIds': people} if kind == 'APPROVAL' else {},
        'formPolicy': {'editable': list(facts['fields']), 'required': ['subject']} if kind == 'APPROVAL' and realm == 'system' else {},
        'timeoutPolicy': {'timeoutMinutes': 60, 'action': 'REMIND'} if kind == 'APPROVAL' and timeout else {}}
        for index, (key, kind, name) in enumerate([('start', 'START', '开始'), ('review', 'APPROVAL', '原始人工核对'), ('end', 'END', '结束')])]
    edges = [{'edgeKey': 'edge_' + str(index), 'sourceNodeKey': a['nodeKey'], 'targetNodeKey': b['nodeKey'],
        'priorityOrder': 0, 'config': {}} for index, (a, b) in enumerate(zip(nodes, nodes[1:]))]
    draft(fid, nodes, edges, token)
    if published: publish(fid, token)
    return fid, nodes, edges


def start(fid, label):
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'CREATE', 'flowId': fid,
        'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0, 'condition': {'mode': 'ALL', 'conditions': []},
        'mutuallyExclusive': True, 'replaceExisting': True}, admin)
    action = api('POST', f'/api/runtime/modules/{code}/records', {'title': label, 'status': 'ACTIVE',
        'participantMemberIds': [], 'fields': {'subject': label, 'score': 0, 'enabled': False}}, admin,
        action_key=code + secrets.token_hex(6))
    facts['actions'].append(action); save(); return action['flowInstanceId']


def platform_start(fid, label):
    value = api('POST', '/api/flow-runtime/instances', {'flowId': fid, 'title': label, 'businessSnapshot': {},
        'variables': {'hiddenProbe': '不得从动态抄送展开的内部变量'}, 'idempotencyKey': code + secrets.token_hex(6)}, platform)
    iid = value['instance']['id']; facts['platform_instances'].append(iid); save(); return iid


def read(iid, token=admin):
    return api('GET', f'/api/flow-runtime/instances/{iid}', token=token)


def request(iid, kind='APPROVAL', mode='OR_SIGN', people=None, token=admin, name='新增业务核对'):
    value = read(iid, token)
    return {'nodeType': kind, 'name': name, 'position': 'AFTER_CURRENT', 'assigneeIds': people or [entry['tenantMemberId']],
        'approvalMode': mode, 'reason': '本次实例需要补充核对，模板保持原路径', 'expectedInstanceVersion': value['version'],
        'expectedGraphRevision': value['graphRevision'], 'idempotencyKey': code + secrets.token_hex(6)}


def add(iid, body, token=admin):
    preview = api('POST', f'/api/flow-runtime/instances/{iid}/runtime-node-preview', body, token)
    assert preview['allowed'] and preview['instanceVersion'] == body['expectedInstanceVersion']
    return api('POST', f'/api/flow-runtime/instances/{iid}/runtime-nodes', body, token)


def approve(iid, token=admin, fields=None):
    value = read(iid, token)
    task = next(t for t in value['tasks'] if t['status'] == 'PENDING' and t['currentHandler'])
    form = api('GET', f"/api/flow-runtime/tasks/{task['id']}/form", token=token)
    return api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': 'APPROVE', 'comment': '真实核对当前候选内容',
        'fieldValues': fields or {}, 'expectedFormRevision': form['formRevision'], 'idempotencyKey': code + secrets.token_hex(6)}, token)


def finish(iid):
    assert read(iid)['status'] == 'COMPLETED'
    action = next(a for a in facts['actions'] if a['flowInstanceId'] == iid)
    result = delivery(action); assert result['businessStatus'] == 'EXECUTED', result
    rid = result['recordId']; facts['records'].append(rid); save()
    record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
    assert record['fields']['score'] == 0 and record['fields']['enabled'] is False
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND id={rid}") == [['1']]
    return record


def expect_error(iid, body, code_value, token=admin, preview=False, status=409):
    before = sql(f"SELECT version,current_node_key,status FROM flow_instance WHERE id={iid}")
    count = sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={iid} AND action_code='RUNTIME_NODE_ADDED'")
    actual, response = call('POST', f"/api/flow-runtime/instances/{iid}/{'runtime-node-preview' if preview else 'runtime-nodes'}", body, token)
    assert actual == status and response['code'] == code_value, (actual, response.get('code'), response.get('message'))
    assert sql(f"SELECT version,current_node_key,status FROM flow_instance WHERE id={iid}") == before
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={iid} AND action_code='RUNTIME_NODE_ADDED'") == count


stage = sys.argv[1]
if stage in ['api', 'api_resume', 'api_finish']:
    if stage == 'api':
        assert not facts.get('api_started'); facts['api_started'] = True
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id'); save()
        module = api('POST', '/api/admin/module-config/modules', {'name': '实例动态节点真实验收', 'code': code, 'groupName': '实例动态图隔离验收'}, admin)['module']
        facts['module_id'] = module['id']; save()
        for index, (field, name, kind) in enumerate([('subject', '业务主题', 'TEXT'), ('score', '业务数量', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
            facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {'code': field,
                'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
        publish_module(module['id'])
    elif stage == 'api_resume':
        assert facts.get('api_started') and not facts.get('api_verified') and not facts['cases']
        for action in facts['actions']:
            iid = action['flowInstanceId']
            if read(iid)['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
                api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE', 'comment': '首次或签公共快照缺口失败夹具关闭',
                    'idempotencyKey': code + secrets.token_hex(6)}, admin)
        old = facts['registration']; facts.setdefault('retired_registrations', []).append(old); save()
        aid, sid = old['account_id'], old['system_id']
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='graph-failed-fixture-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid}; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
        case('initial_or_sign_snapshot_missing_account_actual_409_no_formal_write_terminated_precise_failed_fixture', instance_ids=[a['flowInstanceId'] for a in facts['actions']])
    if stage == 'api_finish':
        assert facts.get('api_started') and not facts.get('api_verified') and len(facts['records']) == 1
        facts.setdefault('retired_registrations', []).append(facts['registration']); save()
    grant()
    username = code + '_' + secrets.token_hex(3)
    reg = api('POST', '/api/auth/register', {'username': username, 'password': secrets.token_urlsafe(24),
        'displayName': '实例动态图接口成员', 'systemName': '实例动态图隔离注册系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}; save()
    other = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)['tenantMemberId']
    facts['api_member'] = other; save()
    other_token = api('POST', '/api/systems/54/tenants/57/enter', {}, reg['platformTokens']['accessToken'])['tokens']['accessToken']
    if stage != 'api_finish':
        fid, nodes, edges = make('真实新增会签与抄送', timeout=True, people=[entry['tenantMemberId'], other])
        iid = start(fid, '新增审批后才生效'); baseline = start(fid, '同模板另一实例原路径')
        facts['main_instance'] = iid; facts['baseline_instance'] = baseline
        facts['template_baseline'] = sql(f'SELECT id,version_number,definition_hash,snapshot_json FROM flow_version WHERE flow_id={fid} ORDER BY id'); save()
        original_history = copy.deepcopy(read(iid)['history'])
        cc = request(iid, 'NOTIFICATION', people=[other], name='新增实际抄送')
        cc_result = add(iid, cc, other_token)
        assert not cc_result['replayed']
        replay = api('POST', f'/api/flow-runtime/instances/{iid}/runtime-nodes', cc, other_token)
        assert replay['replayed'] and replay['actionId'] == cc_result['actionId']
        expect_error(iid, dict(cc, name='同键改内容'), 'FLOW_RUNTIME_NODE_IDEMPOTENCY_CONFLICT', other_token)
        expect_error(iid, cc, 'FLOW_RUNTIME_NODE_IDEMPOTENCY_CONFLICT', admin)
        expect_error(iid, dict(cc, idempotencyKey=code + secrets.token_hex(6)), 'FLOW_RUNTIME_NODE_PREVIEW_STALE')
        approval = request(iid, mode='ALL_SIGN', people=[entry['tenantMemberId'], other])
        result = add(iid, approval, other_token)
        dynamic = next(n['nodeKey'] for n in result['instance']['definitionSnapshot']['nodes'] if n['name'] == approval['name'])
        facts['main_dynamic_node'] = dynamic; save()
        assert read(iid)['history'][:len(original_history)] == original_history
        assert read(baseline)['graphRevision'] == 0 and len(read(baseline)['definitionSnapshot']['nodes']) == 3
        assert sql(f'SELECT id,version_number,definition_hash,snapshot_json FROM flow_version WHERE flow_id={fid} ORDER BY id') == facts['template_baseline']
        assert len(read(iid)['tasks']) == 1 and sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='新增实际抄送'") == [['0']]
        approve(iid, fields={'subject': '原始审批修改后由新增会签继续核对'})
        current = read(iid); assert current['status'] == 'WAITING' and current['currentNodeKey'] == dynamic
        active = [t for t in current['tasks'] if t['nodeKey'] == dynamic]
        assert len(active) == 2 and all(t['taskType'] == 'APPROVAL' and t['dueAt'] for t in active)
        assert len([j for j in current['jobs'] if j['nodeKey'] == dynamic and j['status'] == 'PENDING']) == 2
        form = api('GET', f"/api/flow-runtime/tasks/{active[0]['id']}/form", token=admin)
        assert form['form']['configuration']['configuration']['fields'] and form['formRevision'] > 0
        action = next(a for a in facts['actions'] if a['flowInstanceId'] == iid)
        assert api('GET', f"/api/runtime/modules/{code}/records/action-results/{action['idempotencyRecordId']}", token=admin)['businessStatus'] == 'PENDING_APPROVAL'
        approve(iid); assert read(iid)['status'] == 'WAITING'
        approve(iid, other_token)
        record = finish(iid); assert record['fields']['subject'] == '原始审批修改后由新增会签继续核对'
        assert len([t for t in read(iid)['tasks'] if t['taskType'] == 'APPROVAL']) == 3
        assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='新增实际抄送'") == [['1']]
        assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={iid} AND status IN('PENDING','RUNNING')") == [['0']]
        progress = api('GET', f"/api/flow-runtime/business-actions/{code}/{action['idempotencyRecordId']}", token=admin)
        assert any(n['name'] == approval['name'] for n in progress['nodes'])
        approve(baseline); finish(baseline)
        case('ordinary_source_add_all_sign_and_cc_real_candidate_timeout_once_formal_write_template_other_instance_history_unchanged', instance_id=iid)
        case('preview_no_mutation_same_request_replay_different_body_actor_stale_conflict_no_write', instance_id=iid)
    else:
        iid, baseline = facts['main_instance'], facts['baseline_instance']
        fid = int(sql(f'SELECT flow_id FROM flow_instance WHERE id={iid}')[0][0])
        template = api('GET', f'/api/flows/{fid}', token=admin)
        nodes, edges = template['nodes'], template['edges']
        assert read(iid)['status'] == 'COMPLETED'
        assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='新增实际抄送'") == [['1']]
        assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={iid} AND status IN('PENDING','RUNNING')") == [['0']]
        action = next(a for a in facts['actions'] if a['flowInstanceId'] == iid)
        progress = api('GET', f"/api/flow-runtime/business-actions/{code}/{action['idempotencyRecordId']}", token=admin)
        assert any(n['name'] == '新增业务核对' for n in progress['nodes'])
        # 最初基线由旧默认客户端字符集读取，比较仍使用同一读协议，不能重写旧基线冒充验证。
        assert legacy_sql(f'SELECT id,version_number,definition_hash,snapshot_json FROM flow_version WHERE flow_id={fid} ORDER BY id') == facts['template_baseline']
        approve(baseline); finish(baseline)
        case('ordinary_source_add_all_sign_and_cc_real_candidate_timeout_once_formal_write_template_other_instance_history_unchanged', instance_id=iid)
        case('preview_no_mutation_same_request_replay_different_body_actor_stale_conflict_no_write', instance_id=iid)
        case('sql_oracle_initial_charset_mismatch_actual_cc_exists_verified_using_utf8_connection')
    # 两个有效请求争用同一预览修订，只能有一个持久变更。
    race = start(fid, '并发后续节点')
    requests = [request(race, name='并发节点' + str(i)) for i in range(2)]
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda body: call('POST', f'/api/flow-runtime/instances/{race}/runtime-nodes', body, admin), requests))
    assert sorted(status for status, _ in results) == [200, 409]
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={race} AND action_code='RUNTIME_NODE_ADDED'") == [['1']]
    case('concurrent_current_instance_graph_versions_only_one_addition', instance_id=race)
    pending = start(fid, '人员权限硬限制')
    body = request(pending, people=[149])
    expect_error(pending, body, 'FLOW_RUNTIME_NODE_PEOPLE_INVALID', preview=True, status=422)
    body['assigneeIds'] = [other, other]
    expect_error(pending, body, 'FLOW_RUNTIME_NODE_PEOPLE_INVALID', preview=True, status=422)
    body['assigneeIds'] = [entry['tenantMemberId']]
    body['position'] = 'BEFORE_CURRENT'
    expect_error(pending, body, 'FLOW_RUNTIME_NODE_POSITION_INVALID', preview=True, status=422)
    body['position'] = 'AFTER_CURRENT'; body['nodeType'] = 'HTTP_REQUEST'
    expect_error(pending, body, 'FLOW_RUNTIME_NODE_TYPE_INVALID', preview=True, status=422)
    body['nodeType'] = 'APPROVAL'; grant(False)
    fresh = api('POST', '/api/systems/54/tenants/57/enter', {}, reg['platformTokens']['accessToken'])['tokens']['accessToken']
    expect_error(pending, body, 'PERMISSION_DENIED', fresh, status=403)
    grant()
    case('current_permission_disabled_member_duplicate_people_invalid_type_position_unchanged', instance_id=pending)
    # 实际持实例锁等待期间撤权，释放后服务端必须读取当前角色，不能仅检查旧token内的权限。
    lock_body = request(pending)
    lock = subprocess.Popen(['docker', 'exec', 'unexamine-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --unbuffered -N unexamine -e "$1"', 'graph-lock',
        f"START TRANSACTION; SELECT id FROM flow_instance WHERE id={pending} FOR UPDATE; DO SLEEP(8); COMMIT"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8')
    assert lock.stdout.readline().strip() == str(pending)
    with ThreadPoolExecutor(max_workers=1) as pool:
        waiting = pool.submit(call, 'POST', f'/api/flow-runtime/instances/{pending}/runtime-nodes', lock_body, other_token)
        time.sleep(.4)
        assert not waiting.done()
        grant(False)
        assert lock.wait(timeout=15) == 0
        actual, response = waiting.result(timeout=20)
        assert actual == 403 and response['code'] == 'PERMISSION_DENIED', (actual, response.get('code'))
    assert read(pending)['graphRevision'] == 0
    grant()
    case('real_instance_lock_wait_revoked_add_permission_rechecked_after_acquisition_no_mutation', instance_id=pending)
    hidden_flow, _, _ = make('加节点权限不扩大实例可见范围')
    hidden_instance = start(hidden_flow, '仅原审批人可查看的实例')
    expect_error(hidden_instance, request(hidden_instance), 'FLOW_INSTANCE_VIEW_DENIED', other_token, preview=True, status=403)
    case('add_permission_does_not_grant_guessed_same_realm_instance_visibility', instance_id=hidden_instance)
    closed_request = request(iid)
    expect_error(iid, closed_request, 'FLOW_RUNTIME_NODE_STATE_INVALID')
    off, off_nodes, off_edges = make('冻结规则禁用后续节点', allow=False)
    disabled = start(off, '不允许动态加节点')
    expect_error(disabled, request(disabled), 'FLOW_RUNTIME_NODE_DISABLED', status=403)
    actual, response = call('POST', f'/api/flow-runtime/instances/{disabled}/manual-node-preview', {'assigneeTenantMemberId': entry['tenantMemberId'],
        'position': 'BEFORE_CURRENT', 'reason': '旧入口不能绕过冻结开关', 'statusMappings': {}, 'idempotencyKey': code + secrets.token_hex(6)}, admin)
    assert actual == 403 and response['code'] == 'FLOW_RUNTIME_NODE_DISABLED'
    # 发布设置采用严格布尔；对在途实例始终采用原冻结值。
    invalid = copy.deepcopy(off_nodes); invalid[0]['config']['runtimeRules']['allowAddNodes'] = 'false'
    draft(off, invalid, off_edges)
    check = api('GET', f'/api/flows/{off}/publication-check', token=admin)
    assert not check['valid'] and any(i['code'] == 'FLOW_RUNTIME_RULES_INVALID' for i in check['issues'])
    draft(off, off_nodes, off_edges)
    changed = copy.deepcopy(nodes); changed[0]['config']['runtimeRules'] = {'allowAddNodes': False}
    draft(fid, changed, edges); publish(fid)
    assert 'ADD_RUNTIME_NODE' in read(pending)['allowedActions']
    new_off = start(fid, '新版本动态节点关闭')
    assert 'ADD_RUNTIME_NODE' not in read(new_off)['allowedActions']
    case('terminal_and_frozen_disallow_legacy_entry_strict_boolean_new_publication_not_inflight', instance_ids=[iid, disabled, pending, new_off])
    facts['restart_before'] = {str(value): read(value) for value in [iid, baseline, race]}
    facts['api_verified'] = True; save(); print(json.dumps({'api_verified': True, 'cases': len(facts['cases'])}))
elif stage == 'restart':
    assert facts.get('api_verified') and not facts.get('restart_verified')
    for key, before in facts['restart_before'].items():
        after = read(int(key))
        for field in ['graphRevision', 'definitionSnapshot', 'currentNodeKey', 'status', 'version', 'history']:
            assert before[field] == after[field], field
    facts.pop('restart_before')
    facts['restart_verified'] = True; case('same_backend_restart_persistent_graph_history_other_instance_exact_readback')
    print(json.dumps({'restart_verified': True}))
elif stage == 'final_restart':
    assert facts.get('api_verified') and facts.get('extra_verified') and not facts.get('final_restart_verified')
    for key, before in facts['final_restart_before'].items():
        iid = int(key)
        after = read(iid, platform if iid in facts['platform_instances'] else admin)
        for field in ['graphRevision', 'definitionSnapshot', 'currentNodeKey', 'status', 'version', 'history']:
            assert before[field] == after[field], (iid, field)
    facts.pop('final_restart_before')
    facts['final_restart_verified'] = True
    case('final_numeric_storage_backend_restart_eight_system_platform_instance_graphs_history_exact_readback')
    print(json.dumps({'final_restart_verified': True}))
elif stage == 'prepare_gui':
    assert facts.get('restart_verified') and not facts.get('gui_prepared')
    username = code + '_gui'
    existing = sql(f"SELECT id FROM plat_account WHERE username='{username}'")
    if existing:
        # 注册响应不确定时先核验已有本包账号与自己的空系统，不再次创建或改凭据。
        api('POST', '/api/auth/login', {'username': username, 'password': os.environ['UNEXAMINE_GRAPH_GUI_PASSWORD']})
        aid = int(existing[0][0])
        sid = sql(f"SELECT id FROM sys_system WHERE creator_account_id={aid} AND name='后续节点页面隔离系统'")
        assert len(sid) == 1
        registered = {'accountId': aid, 'systemId': int(sid[0][0])}
    else:
        registered = api('POST', '/api/auth/register', {'username': username, 'password': os.environ['UNEXAMINE_GRAPH_GUI_PASSWORD'],
            'displayName': '后续节点页面成员', 'systemName': '后续节点页面隔离系统'})
    facts['gui_registration'] = {'account_id': registered['accountId'], 'system_id': registered['systemId'], 'username': username}
    aid = registered['accountId']; pmid = int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0])
    facts['gui_account'] = aid; facts['gui_platform_member'] = pmid; save()
    facts['ordinary_platform_role_baseline'] = sql(f'SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id={pmid} ORDER BY id'); save()
    rid = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','实例后续节点页面夹具','仅本地验证临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); facts['platform_role_id'] = rid; save()
    for action in ['VIEW', 'DESIGN', 'PUBLISH', 'SIMULATE', 'VIEW_RUNTIME', 'START', 'HANDLE', 'ADD_MANUAL_NODE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['ordinary_platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{pmid},{rid}); SELECT LAST_INSERT_ID()')[0][0]); save()
    fid, _, _ = make('普通页面配置当前实例后续节点', realm='platform', allow=True)
    facts['gui_flow'] = fid; facts['gui_instance'] = platform_start(fid, '普通成员新增后续审批与实际抄送')
    facts['platform_baseline_instance'] = platform_start(fid, '同平台模板另一实例原路径')
    facts['gui_prepared'] = True; save(); print(json.dumps({'flow_id': fid, 'instance_id': facts['gui_instance']}))
elif stage == 'gui':
    assert facts.get('gui_prepared') and not facts.get('gui_verified')
    value = read(facts['gui_instance'], platform)
    assert value['status'] == 'COMPLETED' and len(value['tasks']) == 2
    assert all(t['taskType'] == 'APPROVAL' and t['status'] == 'APPROVE' for t in value['tasks'])
    assert len([a for a in value['actions'] if a['actionCode'] == 'RUNTIME_NODE_ADDED']) == 2
    assert len([a for a in value['actions'] if a['actionCode'] == 'APPROVE']) == 2
    iid = facts['gui_instance']
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='本次实例抄送说明'") == [['1']]
    assert sql(f"SELECT m.content_text,r.account_id FROM msg_message m JOIN msg_recipient r ON r.message_id=m.id WHERE m.target_type='FLOW_INSTANCE' AND m.target_id='{iid}' AND m.subject='本次实例抄送说明'") == [['增加后续抄送留痕，原文 ${hiddenProbe} 不应展开', str(facts['gui_account'])]]
    assert read(facts['platform_baseline_instance'], platform)['graphRevision'] == 0
    assert read(facts['selection_regression_instance'], platform)['status'] == 'COMPLETED'
    assert 'ADD_RUNTIME_NODE' in read(facts['platform_baseline_instance'], platform)['allowedActions']
    assert 'ADD_RUNTIME_NODE' not in read(facts['gui_disabled_new_instance'], platform)['allowedActions']
    assert sql(f"SELECT id,version_number,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id={facts['gui_flow']} AND version_number=1") == facts['gui_template_v1_baseline']
    facts['gui_verified'] = True; case('actual_ordinary_platform_gui_preview_confirm_persistent_graph_two_real_approval_and_one_cc_other_instance_original')
    print(json.dumps({'gui_verified': True}))
elif stage == 'cleanup':
    assert facts.get('api_verified') and facts.get('extra_verified') and facts.get('final_restart_verified') and facts.get('gui_verified') and not facts.get('cleanup_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    all_instances = [(a['flowInstanceId'], admin) for a in facts['actions']] + [(iid, platform) for iid in facts['platform_instances']]
    for iid, token in all_instances:
        if read(iid, token)['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
            api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE', 'comment': '实例动态图验收结束',
                'idempotencyKey': code + secrets.token_hex(6)}, token)
    for rid in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': record['version'], 'reason': '实例动态图验收夹具归档'}, admin)
    selected = ','.join(str(iid) for iid, _ in all_instances)
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({selected}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({selected}) AND status IN('PENDING','RUNNING')") == [['0']]
    mid = facts['module_id']; flows = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flows}) AND platform_id=1")
    rid, link, pmid = facts['platform_role_id'], facts['ordinary_platform_link_id'], facts['gui_platform_member']
    sql(f"DELETE FROM plat_member_role WHERE id={link} AND platform_id=1 AND member_id={pmid} AND role_id={rid}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid} AND platform_id=1 AND code='{code}'")
    assert sql(f'SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id={pmid} ORDER BY id') == facts['ordinary_platform_role_baseline']
    for registration in facts.get('retired_registrations', []) + [facts['registration'], facts['gui_registration']]:
        aid, sid = registration['account_id'], registration['system_id']
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE auth_session SET revoked=1,revoked_reason='instance-graph-acceptance-closed' WHERE account_id={aid} AND revoked=0; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{registration['username']}' AND status='ACTIVE'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid} AND status='ACTIVE'")
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == [['4']]
    assert sql(f'SELECT COUNT(*) FROM biz_record WHERE module_id={mid} AND archived=0') == [['0']]
    group = sql(f'SELECT group_id FROM cfg_module WHERE id={mid}')
    if group and group[0][0] != 'NULL':
        gid = int(group[0][0]); sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={gid} AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id={gid} AND status='ACTIVE')")
    facts['cleanup_verified'] = True; case('exact_fixture_role_identity_pending_jobs_tasks_cleanup_archives_golden_unchanged', records_archived=len(facts['records']))
    print(json.dumps({'cleanup_verified': True}))
else:
    raise AssertionError('未知阶段，不执行隐式测试')
