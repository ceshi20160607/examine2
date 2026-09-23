"""B09.2b 真正的多人审批、冻结规则和候选变化验收；仅创建可精确清理的本地夹具。"""
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
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in names], type_ignores=[]), filename, 'exec'))
PATH = AREA / 'flow-repeat-approval-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2b', 'status': 'running', 'code': 'repeat_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'platform_flows': [], 'actions': [], 'platform_instances': [], 'records': [], 'cases': []}
assert facts['status'] == 'running', '已封存的事实不能重复运行'
java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
platform = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    facts['cases'].append({'case': name, **values}); save()


def grant(handle=True):
    permissions = copy.deepcopy(original['permissions']) + [{'resourceType': 'MODULE', 'resourceCode': code,
        'actionCode': action, 'dataScopeType': 'ALL'} for action in ['LIST', 'DETAIL', 'CREATE', 'UPDATE']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['VIEW', 'DESIGN', 'PUBLISH', 'SIMULATE', 'VIEW_RUNTIME', 'START'] + (['HANDLE'] if handle else [])]
    if not handle:
        permissions = [p for p in permissions if not (p['resourceType'] == 'FLOW' and p['actionCode'] == 'HANDLE')]
    policies = copy.deepcopy(original['fieldPolicies']) + [{'resourceCode': code, 'fieldCode': field,
        'channel': channel, 'readable': True, 'writable': True, 'maskStrategy': None}
        for field in facts['fields'] for channel in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def rule(condition=None, keys=None):
    result = {'enabled': True, 'sourceNodeKeys': keys or ['first'], 'formChangeRule': 'UNCHANGED'}
    if condition is not None: result['condition'] = condition
    return result


def draft(fid, nodes, edges, token):
    current = api('GET', f'/api/flows/{fid}', token=token)
    api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token)


def publish(fid, token, summary='后续自动审批隔离验收'):
    check = api('GET', f'/api/flows/{fid}/publication-check', token=token)
    assert check['valid'], [(i['code'], i['message']) for i in check['issues']]
    return api('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': summary, 'simulationVariables': {}}, token)


def make(label, first=None, later=None, mode='OR_SIGN', first_mode='OR_SIGN', repeat=None,
         middle=None, first_required=None, later_required=None, realm='system', published=True):
    token = platform if realm == 'platform' else admin
    fid = api('POST', '/api/flows', {'code': code + '_' + secrets.token_hex(3), 'name': label}, token)['id']
    facts['platform_flows' if realm == 'platform' else 'flows'].append(fid); save()
    default = [1] if realm == 'platform' else [entry['tenantMemberId']]
    plan = [('start', 'START', '开始', [], {}), ('first', 'APPROVAL', '前序人工确认', first or default, {'approvalMode': first_mode})]
    if middle is not None: plan.append(('middle', 'APPROVAL', '中间人工核对', middle, {'approvalMode': 'OR_SIGN'}))
    config = {'approvalMode': mode}
    if repeat is not None: config['repeatApproval'] = repeat
    plan += [('later', 'APPROVAL', '后续核对', later or default, config), ('end', 'END', '结束', [], {})]
    nodes = []
    for index, (key, kind, name, people, config) in enumerate(plan):
        required = first_required or [] if key == 'first' else later_required or []
        nodes.append({'nodeKey': key, 'nodeType': kind, 'name': name, 'positionX': index * 240, 'positionY': 100,
            'config': {'moduleId': facts['module_id']} if kind == 'START' and realm == 'system' else config,
            'assigneePolicy': {'type': 'PERSON', 'platformAccountIds' if realm == 'platform' else 'tenantMemberIds': people} if kind == 'APPROVAL' else {},
            'formPolicy': {'editable': list(facts['fields']), 'required': required} if kind == 'APPROVAL' and realm == 'system' else {},
            'timeoutPolicy': {}})
    edges = [{'edgeKey': 'edge_' + str(i), 'sourceNodeKey': a['nodeKey'], 'targetNodeKey': b['nodeKey'],
        'priorityOrder': 0, 'config': {}} for i, (a, b) in enumerate(zip(nodes, nodes[1:]))]
    draft(fid, nodes, edges, token)
    if published: publish(fid, token, label)
    return fid, nodes, edges


def start(fid, label, subject='完整候选', secret=None):
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'CREATE', 'flowId': fid,
        'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0, 'condition': {'mode': 'ALL', 'conditions': []},
        'mutuallyExclusive': True, 'replaceExisting': True}, admin)
    values = {'subject': subject, 'score': 0, 'enabled': False}
    if secret is not None: values['secret'] = secret
    action = api('POST', f'/api/runtime/modules/{code}/records', {'title': label, 'status': 'ACTIVE',
        'participantMemberIds': [], 'fields': values}, admin, action_key=code + secrets.token_hex(6))
    facts['actions'].append(action); save(); return action


def read(action):
    return api('GET', f"/api/flow-runtime/instances/{action['flowInstanceId']}", token=platform if action.get('realm') == 'platform' else admin)


def task(action, node, member=None):
    return next(t for t in read(action)['tasks'] if t['nodeKey'] == node and t['status'] == 'PENDING'
        and (member is None or t['assigneeTenantMemberId'] == member))


def handle(value, token=admin, fields=None, variables=None, action='APPROVE', target=None):
    body = {'actionCode': action, 'comment': '后续自动审批实际验收', 'fieldValues': fields or {}, 'variables': variables or {},
        'expectedFormRevision': 0, 'idempotencyKey': code + secrets.token_hex(6)}
    if target is not None: body['targetTenantMemberId'] = target
    return call('POST', f"/api/flow-runtime/tasks/{value['id']}/actions", body, token)


def approve(action, node='first', token=admin, fields=None, member=None):
    value = task(action, node, member)
    status, response = handle(value, token, fields)
    assert status == 200, (status, response.get('code'), response.get('message'))
    return response


def complete(action):
    assert read(action)['status'] == 'COMPLETED', read(action)['status']
    result = delivery(action); assert result['businessStatus'] == 'EXECUTED', result
    rid = result['recordId']; facts['records'].append(rid); save()
    record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
    assert record['fields']['score'] == 0 and record['fields']['enabled'] is False
    assert int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND id={rid}")[0][0]) == 1
    return record


def auto_events(action):
    return [h for h in read(action)['history'] if '按冻结规则自动通过' in h['eventName']]


stage = sys.argv[1]
if stage in ['api', 'api_resume']:
    if stage == 'api':
        assert not facts.get('api_started'); facts['api_started'] = True
    else:
        assert facts.get('api_started') and not facts.get('api_verified') and not facts['cases']
    if stage == 'api':
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id'); save()
        module = api('POST', '/api/admin/module-config/modules', {'name': '条件后续自动审批真实验收', 'code': code, 'groupName': '后续审批隔离验收'}, admin)['module']
        facts['module_id'] = module['id']; save()
        for index, (field, name, kind) in enumerate([('subject', '审批主题', 'TEXT'), ('score', '审批数量', 'NUMBER'),
                ('enabled', '是否启用', 'BOOLEAN'), ('secret', '内部敏感值', 'SECRET')]):
            facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {'code': field,
                'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
        publish_module(module['id']); grant()
    else:
        old = facts['registration']; facts.setdefault('retired_registrations', []).append(old); save()
        aid, sid = old['account_id'], old['system_id']
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid}; UPDATE auth_session SET revoked=1,revoked_reason='repeat-failed-fixture-closed' WHERE account_id={aid}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid}; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid}")
        grant()
    username = code + '_' + secrets.token_hex(3)
    reg = api('POST', '/api/auth/register', {'username': username, 'password': secrets.token_urlsafe(24),
        'displayName': '后续审批接口成员', 'systemName': '后续审批隔离注册系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}; save()
    member = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)
    other = member['tenantMemberId']; facts['api_member'] = other; save()
    other_token = api('POST', '/api/systems/54/tenants/57/enter', {}, reg['platformTokens']['accessToken'])['tokens']['accessToken']
    root_member = entry['tenantMemberId']
    condition = {'mode': 'ALL', 'conditions': [{'field': 'score', 'operator': 'EQ', 'value': 0}, {'field': 'enabled', 'operator': 'EQ', 'value': False}]}
    fid, nodes, edges = make('同人范围条件与完整候选一次生效', repeat=rule(condition), later_required=['subject'])
    a = start(fid, '同人安全后续自动', secret=secrets.token_urlsafe(22)); facts['main_flow'] = fid; save()
    result = approve(a, fields={'subject': '本次前序修改必须在后续读取'})
    record = complete(a); assert record['fields']['subject'] == '本次前序修改必须在后续读取'
    assert len(auto_events(a)) == 1 and auto_events(a)[0]['actorName'] == '系统自动处理'
    assert int(sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={a['flowInstanceId']} AND action_code='APPROVE'")[0][0]) == 1
    assert all(h['input'] == {} and h['result'] == {} for h in read(a)['actions'])
    case('real_human_fact_and_current_protected_patch_before_advance_typed_zero_false_condition_auto_once', instance_id=a['flowInstanceId'])
    fid_off, _, _ = make('默认关闭不可跳过'); off = start(fid_off, '默认关闭'); approve(off)
    assert read(off)['status'] == 'WAITING' and not auto_events(off); approve(off, 'later'); complete(off)
    case('default_off_remains_manual')
    miss = copy.deepcopy(condition); miss['conditions'][0]['value'] = 1
    fid_miss, _, _ = make('附加条件不命中', repeat=rule(miss)); nohit = start(fid_miss, '附加不命中'); approve(nohit)
    assert read(nohit)['status'] == 'WAITING' and not auto_events(nohit)
    case('typed_condition_not_matched_retains_normal_pending')
    fid_change, _, _ = make('中间修改必须重审', repeat=rule(), middle=[root_member])
    changed = start(fid_change, '候选变更'); approve(changed); approve(changed, 'middle', fields={'subject': '中间已修改'})
    assert read(changed)['status'] == 'WAITING' and not auto_events(changed); approve(changed, 'later'); complete(changed)
    case('intermediate_field_revision_change_returns_manual')
    dirty = start(fid_change, '密钥变更', secret=secrets.token_urlsafe(20)); approve(dirty)
    secret_new = secrets.token_urlsafe(24); approve(dirty, 'middle', fields={'secret': secret_new})
    assert read(dirty)['status'] == 'WAITING' and not auto_events(dirty)
    assert int(sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={dirty['flowInstanceId']} AND INSTR(input_json,'{secret_new}')>0")[0][0]) == 0
    approve(dirty, 'later'); complete(dirty); case('secret_change_cannot_use_redacted_variable_equality_plaintext_not_stored_in_action')
    for mode in ['ALL_SIGN', 'SEQUENTIAL', 'OR_SIGN']:
        fgroup, _, _ = make('真实两人前序与后续' + mode, first=[root_member, other], first_mode='ALL_SIGN',
            later=[root_member, other], mode=mode, repeat=rule())
        group = start(fgroup, '两人' + mode); approve(group, member=root_member); approve(group, token=other_token, member=other)
        complete(group); assert len(auto_events(group)) == (1 if mode == 'OR_SIGN' else 2)
        assert int(sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={group['flowInstanceId']} AND action_code='APPROVE'")[0][0]) == 2
        case('real_two_human_facts_group_auto_' + mode.lower(), instance_id=group['flowInstanceId'])
    fpartial, _, _ = make('顺序首人自动后人通知一次', later=[root_member, other], mode='SEQUENTIAL', repeat=rule())
    partial = start(fpartial, '顺序混合'); approve(partial)
    next_task = task(partial, 'later', other)
    assert len(auto_events(partial)) == 1
    for _ in range(20):
        count = int(sql(f"SELECT COUNT(*) FROM msg_message WHERE source_id='instance-{partial['flowInstanceId']}-task-{next_task['id']}'")[0][0])
        if count: break
        time.sleep(.2)
    assert count == 1, count
    approve(partial, 'later', other_token, member=other); complete(partial)
    case('sequential_initial_auto_next_manual_one_actual_pending_message')
    fqueue, _, _ = make('顺序后人不可提前跳过', later=[other, root_member], mode='SEQUENTIAL', repeat=rule())
    queued = start(fqueue, '顺序队列'); approve(queued)
    assert not auto_events(queued) and any(t['status'] == 'QUEUED' for t in read(queued)['tasks'] if t['nodeKey'] == 'later')
    approve(queued, 'later', other_token, member=other); complete(queued); assert len(auto_events(queued)) == 1
    case('sequential_queued_matching_person_waits_turn_then_auto')
    cross = start(fid, '跨实例不借审批事实')
    status, response = handle(task(cross, 'first'), action='TRANSFER', target=other); assert status == 200, response.get('code')
    approve(cross, token=other_token, member=other)
    assert read(cross)['status'] == 'WAITING' and not auto_events(cross)
    case('other_instance_same_account_human_fact_not_used_after_different_actual_approver')
    freq, _, _ = make('后续新必填不跳过', repeat=rule(), later_required=['subject'])
    required = start(freq, '后续缺必填', subject=None); approve(required)
    assert read(required)['status'] == 'WAITING' and not auto_events(required)
    status, response = handle(task(required, 'later')); assert status == 422 and response['code'] == 'FLOW_NODE_FIELD_REQUIRED'
    approve(required, 'later', fields={'subject': '真实处理人补齐'}); complete(required)
    case('later_required_not_met_auto_falls_back_and_human_empty_denied_then_real_patch_recovers')
    ffrozen, frozen_nodes, frozen_edges = make('冻结规则不随后发版改变', repeat=rule())
    frozen = start(ffrozen, '旧冻结V1'); frozen_nodes[1]['name'] = '前序人工确认第二版'
    frozen_nodes[-2]['config']['repeatApproval']['enabled'] = False
    draft(ffrozen, frozen_nodes, frozen_edges, admin); publish(ffrozen, admin, '第二版关闭后续自动')
    approve(frozen); complete(frozen); assert len(auto_events(frozen)) == 1
    new = start(ffrozen, '新V2关闭'); approve(new); assert read(new)['status'] == 'WAITING' and not auto_events(new)
    case('old_instance_frozen_v1_auto_new_v2_default_manual')
    flock, _, _ = make('等待实例锁后当前HANDLE撤权', first=[other], middle=[root_member], later=[other], repeat=rule())
    locked = start(flock, '锁后撤权'); approve(locked, token=other_token, member=other)
    iid = locked['flowInstanceId']; middle_task = task(locked, 'middle')
    connection = subprocess.Popen(['docker', 'exec', '-i', 'unexamine-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N unexamine'],
        stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, text=True)
    connection.stdin.write(f'START TRANSACTION; SELECT id FROM flow_instance WHERE id={iid} FOR UPDATE;\n'); connection.stdin.flush()
    time.sleep(.4)
    with ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(handle, middle_task)
        waiters = 0
        for _ in range(30):
            waiters = int(sql(f"SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE l.OBJECT_NAME='flow_instance' AND l.LOCK_DATA='{iid}'")[0][0])
            if waiters: break
            time.sleep(.15)
        assert waiters >= 1, waiters
        grant(handle=False)
        connection.stdin.write('COMMIT;\n'); connection.stdin.flush(); connection.stdin.close(); connection.wait(timeout=10)
        status, response = future.result(timeout=20); assert status == 200, (status, response.get('code'), response.get('message'))
    assert read(locked)['status'] == 'WAITING' and not auto_events(locked)
    assert task(locked, 'later', other)['status'] == 'PENDING'; grant()
    approve(locked, 'later', other_token, member=other); complete(locked)
    case('actual_human_lock_wait_then_current_candidate_handle_revoked_no_auto', waiters=waiters)
    facts['api_verified'] = True; save(); print(json.dumps({'api_verified': True, 'module_id': facts['module_id'], 'cases': len(facts['cases'])}))
elif stage == 'prepare_gui':
    assert facts.get('api_verified') and not facts.get('gui_prepared')
    facts['ordinary_platform_role_baseline'] = sql('SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id=67 ORDER BY id'); save()
    rid = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','后续自动审批页面夹具','仅本地验证临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); facts['platform_role_id'] = rid; save()
    for action in ['VIEW', 'DESIGN', 'PUBLISH', 'SIMULATE', 'VIEW_RUNTIME', 'START', 'HANDLE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['ordinary_platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,67,{rid}); SELECT LAST_INSERT_ID()')[0][0]); save()
    fid, _, _ = make('普通页面同一审批人后续规则', first=[69], later=[69], realm='platform', published=False)
    facts['gui_flow'] = fid; save()
    value = api('GET', f'/api/flows/{fid}', token=platform); nodes, edges = value['nodes'], value['edges']
    invalid_rules = [{'enabled': 'true', 'sourceNodeKeys': ['first'], 'formChangeRule': 'UNCHANGED'},
        {'enabled': True, 'sourceNodeKeys': [], 'formChangeRule': 'UNCHANGED'}, rule(keys=['first', 'first']),
        rule(keys=['later']), rule(keys=['end']), rule(keys=['missing']),
        {'enabled': True, 'sourceNodeKeys': ['first'], 'formChangeRule': 'IGNORE_CHANGES'},
        rule(condition={'mode': 'ALL', 'conditions': []})]
    for index, invalid in enumerate(invalid_rules):
        changed = copy.deepcopy(nodes); changed[-2]['config']['repeatApproval'] = invalid
        draft(fid, changed, edges, platform); check = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
        assert not check['valid'] and any('REPEAT_APPROVAL' in i['code'] for i in check['issues']), (index, check['issues'])
    changed = copy.deepcopy(nodes); changed[1]['config']['repeatApproval'] = rule(keys=['later'])
    draft(fid, changed, edges, platform); check = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
    assert not check['valid'] and any('REPEAT_APPROVAL' in i['code'] for i in check['issues'])
    status, response = call('POST', f'/api/flows/{fid}/publish', {'expectedDraftRevision': check['draftRevision'],
        'changeSummary': '非法后续范围不能发布', 'simulationVariables': {}}, platform)
    assert status == 422 and response['code'] == 'FLOW_PUBLICATION_INVALID'
    draft(fid, nodes, edges, platform)
    case('typed_switch_scope_empty_duplicate_self_end_missing_changes_condition_and_backward_source_invalid_publish_422', checks=9)
    facts['gui_prepared'] = True; save(); print(json.dumps({'flow_id': fid}))
elif stage == 'start_gui':
    assert facts.get('gui_prepared') and not facts.get('gui_instance')
    fid = facts['gui_flow']; value = api('GET', f'/api/flows/{fid}', token=platform)
    repeat = next(n for n in value['nodes'] if n['nodeKey'] == 'later')['config']['repeatApproval']
    assert repeat == rule(), repeat
    result = api('POST', '/api/flow-runtime/instances', {'flowId': fid, 'title': '普通页面同人后续真实审批',
        'variables': {}, 'businessSnapshot': {}, 'idempotencyKey': code + secrets.token_hex(6)}, platform)
    facts['gui_instance'] = result['instance']['id']; facts['platform_instances'].append(result['instance']['id']); save()
    print(json.dumps({'instance_id': facts['gui_instance']}))
elif stage == 'gui':
    value = read({'flowInstanceId': facts['gui_instance'], 'realm': 'platform'})
    assert value['status'] == 'COMPLETED' and all(t['status'] == 'APPROVE' for t in value['tasks'])
    assert len([h for h in value['history'] if '按冻结规则自动通过' in h['eventName']]) == 1
    assert len([a for a in value['actions'] if a['actionCode'] == 'APPROVE']) == 1
    assert int(sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={facts['gui_instance']} AND action_code='APPROVE' AND acted_by_account_id=69")[0][0]) == 1
    facts['gui_verified'] = True; case('actual_ordinary_gui_scope_unchanged_configuration_save_reopen_publish_and_one_real_approval_auto_later')
    print(json.dumps({'gui_verified': True}))
elif stage == 'extra':
    assert facts.get('gui_verified') and not facts.get('extra_verified')
    fid, _, _ = make('独立流程变量变化仍需重审', first=[1], later=[1], middle=[1], repeat=rule(), realm='platform')
    for mode in ['new_key', 'same_value_new_revision']:
        value = api('POST', '/api/flow-runtime/instances', {'flowId': fid, 'title': '变量变化-' + mode,
            'variables': {}, 'businessSnapshot': {}, 'idempotencyKey': code + secrets.token_hex(6)}, platform)['instance']
        iid = value['id']; facts['platform_instances'].append(iid); save()
        action = {'flowInstanceId': iid, 'realm': 'platform'}
        status, response = handle(task(action, 'first'), platform, variables={} if mode == 'new_key' else {'gate': False})
        assert status == 200, (status, response.get('code'))
        status, response = handle(task(action, 'middle'), platform, variables={'gate': False})
        assert status == 200, (status, response.get('code'))
        assert read(action)['status'] == 'WAITING' and not auto_events(action)
        assert any('业务候选或流程变量在原审批后发生变化' in h['eventName'] for h in read(action)['history'])
        case('standalone_complete_variable_revision_set_changed_' + mode, instance_id=iid)
    facts['extra_verified'] = True; save(); print(json.dumps({'extra_verified': True}))
elif stage == 'cleanup':
    assert facts.get('api_verified') and facts.get('gui_verified') and facts.get('extra_verified') and not facts.get('cleanup_verified')
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    if current['permissions'] != original['permissions'] or current['fieldPolicies'] != original['fieldPolicies']:
        publish_role(original['permissions'], original['fieldPolicies'])
    for action in facts['actions'] + [{'flowInstanceId': iid, 'realm': 'platform'} for iid in facts['platform_instances']]:
        value = read(action)
        if value['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
            api('POST', f"/api/flow-runtime/instances/{value['id']}/actions", {'actionCode': 'TERMINATE',
                'comment': '后续自动审批隔离验收结束', 'idempotencyKey': code + secrets.token_hex(6)}, platform if action.get('realm') == 'platform' else admin)
    for rid in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': record['version'],
            'reason': '后续自动审批隔离验收结束归档'}, admin)
    instances = [a['flowInstanceId'] for a in facts['actions']] + facts['platform_instances']; selected = ','.join(map(str, instances))
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({selected}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({selected}) AND status IN('PENDING','RUNNING')") == [['0']]
    mid = facts['module_id']; flows = ','.join(map(str, facts['flows'] + facts['platform_flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flows}) AND platform_id=1")
    rid, link = facts['platform_role_id'], facts['ordinary_platform_link_id']
    sql(f"DELETE FROM plat_member_role WHERE id={link} AND platform_id=1 AND member_id=67 AND role_id={rid}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid} AND platform_id=1 AND code='{code}'")
    assert sql('SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id=67 ORDER BY id') == facts['ordinary_platform_role_baseline']
    for registration in facts.get('retired_registrations', []) + [facts['registration']]:
        aid, sid = registration['account_id'], registration['system_id']
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE auth_session SET revoked=1,revoked_reason='repeat-acceptance-closed' WHERE account_id={aid} AND revoked=0; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{registration['username']}' AND status='ACTIVE'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid} AND status='ACTIVE'")
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == [['4']]
    assert sql(f'SELECT COUNT(*) FROM biz_record WHERE module_id={mid} AND archived=0') == [['0']]
    group = sql(f'SELECT group_id FROM cfg_module WHERE id={mid}')
    if group and group[0][0] != 'NULL':
        gid = int(group[0][0]); sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={gid} AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id={gid} AND status='ACTIVE')")
    facts['cleanup_verified'] = True; case('exact_fixture_and_roles_cleanup_archives_zero_pending_tasks_jobs_golden_unchanged', archived_records=len(facts['records']))
    print(json.dumps({'cleanup_verified': True, 'records_archived': len(facts['records'])}))
else:
    raise AssertionError('未知步骤，不执行任何隐式测试')
