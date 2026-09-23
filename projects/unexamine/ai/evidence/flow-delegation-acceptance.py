"""B09.2c.7 审批任务委托、限时代办与撤销的定向真实验收。"""
import ast
import copy
import json
import re
import secrets
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from pathlib import Path

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
        or isinstance(node, ast.FunctionDef) and node.name in names], type_ignores=[]), filename, 'exec'))

PATH = AREA / 'flow-delegation-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.7', 'status': 'running', 'code': 'delegate_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'instances': [], 'records': [], 'cases': [], 'registrations': []}
assert facts['status'] == 'running', '封存事实不重跑'

java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
ADMIN_NAME = re.search(r'default-admin\.username:([^}]+)', java).group(1)
LOCAL_PASSWORD = re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)
platform = api('POST', '/api/auth/login', {'username': ADMIN_NAME, 'password': LOCAL_PASSWORD})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    if any(item['case'] == name for item in facts['cases']): return
    facts['cases'].append({'case': name, **values})
    save()


def user_tokens(index):
    registration = facts['registrations'][index]
    token = api('POST', '/api/auth/login', {
        'username': registration['username'], 'password': LOCAL_PASSWORD})['accessToken']
    system = api('POST', '/api/systems/54/tenants/57/enter', {}, token)['tokens']['accessToken']
    return token, system


def grant_system_role():
    permissions = copy.deepcopy(original['permissions'])
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['LIST', 'DETAIL', 'CREATE', 'UPDATE']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['VIEW_RUNTIME', 'HANDLE']]
    policies = copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode': code, 'fieldCode': field, 'channel': channel,
        'readable': True, 'writable': True, 'maskStrategy': None}
        for field in facts['fields'] for channel in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def draft(flow_id, nodes, edges, token):
    current = api('GET', f'/api/flows/{flow_id}', token=token)
    return api('PUT', f'/api/flows/{flow_id}/draft', {
        'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token)


def publish(flow_id, token, summary='审批任务委托定向验收'):
    checked = api('GET', f'/api/flows/{flow_id}/publication-check', token=token)
    assert checked['valid'], checked['issues']
    return api('POST', f'/api/flows/{flow_id}/publish', {
        'expectedDraftRevision': checked['draftRevision'], 'changeSummary': summary,
        'simulationVariables': {}}, token)


def make_flow(label, realm, legacy=False):
    token = platform if realm == 'platform' else admin
    flow = api('POST', '/api/flows', {'code': code + '_' + secrets.token_hex(3), 'name': label}, token)
    flow_id = flow['id']; facts['flows'].append(flow_id); save()
    principal = facts['registrations'][0]
    people_key = 'platformAccountIds' if realm == 'platform' else 'tenantMemberIds'
    person = principal['account_id'] if realm == 'platform' else principal['tenant_member_id']
    approval_config = {'approvalMode': 'OR_SIGN'}
    if not legacy:
        approval_config['manualOperationPolicy'] = {
            'allowedActions': ['APPROVE', 'REJECT', 'DELEGATE'],
            'opinionRequiredActions': ['REJECT', 'DELEGATE'], 'requireClaim': False}
    nodes = [
        {'nodeKey': 'start', 'nodeType': 'START', 'name': '开始', 'positionX': 0, 'positionY': 100,
            'config': {'moduleId': facts['module_id']} if realm == 'system' else {},
            'assigneePolicy': {}, 'formPolicy': {}, 'timeoutPolicy': {}},
        {'nodeKey': 'review', 'nodeType': 'APPROVAL', 'name': '负责人审批', 'positionX': 220, 'positionY': 100,
            'config': approval_config, 'assigneePolicy': {'type': 'PERSON', people_key: [person]},
            'formPolicy': {'editable': [], 'required': []} if realm == 'system' else {}, 'timeoutPolicy': {}},
        {'nodeKey': 'end', 'nodeType': 'END', 'name': '结束', 'positionX': 440, 'positionY': 100,
            'config': {}, 'assigneePolicy': {}, 'formPolicy': {}, 'timeoutPolicy': {}}]
    edges = [
        {'edgeKey': 'e1', 'sourceNodeKey': 'start', 'targetNodeKey': 'review', 'priorityOrder': 0, 'config': {}},
        {'edgeKey': 'e2', 'sourceNodeKey': 'review', 'targetNodeKey': 'end', 'priorityOrder': 0, 'config': {}}]
    draft(flow_id, nodes, edges, token); publish(flow_id, token)
    return flow_id


def read(instance_id, token):
    return api('GET', f'/api/flow-runtime/instances/{instance_id}', token=token)


def current_task(instance_id, token):
    return next(task for task in read(instance_id, token)['tasks']
        if task['status'] == 'PENDING' and task['currentHandler'])


def task_action(task_id, token, action, comment=None, key=None, target=None, end_time=None, platform_scope=True):
    body = {'actionCode': action, 'comment': comment,
        'idempotencyKey': key or code + secrets.token_hex(6)}
    if target is not None:
        body['targetPlatformAccountId' if platform_scope else 'targetTenantMemberId'] = target
    if end_time is not None: body['delegationEndTime'] = end_time
    return body, api('POST', f'/api/flow-runtime/tasks/{task_id}/actions', body, token)


def start_platform(flow_id, label):
    result = api('POST', '/api/flow-runtime/instances', {'flowId': flow_id, 'title': label,
        'businessSnapshot': {}, 'variables': {}, 'idempotencyKey': code + secrets.token_hex(6)},
        user_tokens(0)[0])
    instance_id = result['instance']['id']; facts['instances'].append(instance_id); save()
    return instance_id


def start_source(flow_id, label):
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'CREATE',
        'flowId': flow_id, 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True,
        'replaceExisting': True}, admin)
    action = api('POST', f'/api/runtime/modules/{code}/records', {'title': label, 'status': 'ACTIVE',
        'participantMemberIds': [], 'fields': {'subject': label}}, admin,
        action_key=code + secrets.token_hex(6))
    facts['source_action'] = {key: action[key] for key in ['flowInstanceId', 'idempotencyRecordId', 'outboxId']}
    facts['instances'].append(action['flowInstanceId']); save()
    return action['flowInstanceId']


def delegate(instance_id, token, target, platform_scope, end_time=None, key=None):
    task = current_task(instance_id, token)
    end_time = end_time or (datetime.now() + timedelta(days=1)).replace(microsecond=0).isoformat()
    body, result = task_action(task['id'], token, 'DELEGATE', '出差期间由指定同事限时代办', key,
        target, end_time, platform_scope)
    return task, body, result


def compact_state(instance_id):
    return {
        'instance': sql(f'SELECT id,status,current_node_key,version FROM flow_instance WHERE id={instance_id}'),
        'tasks': sql(f"SELECT id,status,assignee_account_id,assignee_tenant_member_id FROM flow_task WHERE instance_id={instance_id} ORDER BY id"),
        'actions': sql(f"SELECT id,task_id,action_code,acted_by_account_id FROM flow_action WHERE instance_id={instance_id} ORDER BY id")}


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    if 'golden_baseline' not in facts:
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
        facts['golden_count'] = sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0'); save()
    if not facts['registrations']:
        for suffix, label in [('principal', '委托验收原审批人'), ('delegate', '委托验收代理审批人')]:
            username = code + '_' + suffix
            registered = api('POST', '/api/auth/register', {'username': username, 'password': LOCAL_PASSWORD,
                'displayName': label, 'systemName': label + '隔离系统'})
            facts['registrations'].append({'username': username, 'account_id': registered['accountId'],
                'system_id': registered['systemId'], 'display_name': label})
        role_id = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','委托验收角色','仅本地验收临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0])
        facts['platform_role_id'] = role_id
        for resource, actions in [('FLOW', ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE']), ('TODO', ['VIEW', 'HANDLE'])]:
            for action in actions:
                sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{role_id},'{resource}','PLATFORM','{action}','ALLOW')")
        facts['platform_links'] = []
        for registration in facts['registrations']:
            member_id = int(sql(f"SELECT id FROM plat_member WHERE platform_id=1 AND account_id={registration['account_id']}")[0][0])
            link_id = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{member_id},{role_id}); SELECT LAST_INSERT_ID()')[0][0])
            registration['platform_member_id'] = member_id
            facts['platform_links'].append(link_id)
            registration['tenant_member_id'] = api('POST', '/api/admin/system/authorization/members', {
                'account': registration['username'], 'roleIds': [114]}, admin)['tenantMemberId']
        save()
    if 'module_id' not in facts:
        module = api('POST', '/api/admin/module-config/modules', {'name': '委托审批来源一次验收',
            'code': code, 'groupName': '流程闭环隔离验收'}, admin)['module']
        facts['module_id'] = module['id']; save()
        facts['fields']['subject'] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {
            'code': 'subject', 'name': '申请主题', 'fieldType': 'TEXT', 'required': True,
            'sortOrder': 0, 'config': {}}, admin); save()
        publish_module(module['id']); grant_system_role()
    if 'system_flow' not in facts:
        facts['system_flow'] = facts['flows'][0] if facts['flows'] else make_flow('系统来源限时委托审批', 'system')
        save()
    if 'platform_flow' not in facts:
        facts['platform_flow'] = make_flow('平台独立限时委托审批', 'platform')
        save()
    if 'legacy_flow' not in facts:
        facts['legacy_flow'] = make_flow('旧版本不自动增加委托', 'platform', legacy=True); save()
    principal_platform, principal_system = user_tokens(0)
    delegate_platform, delegate_system = user_tokens(1)
    if 'legacy_instance' not in facts:
        facts['legacy_instance'] = start_platform(facts['legacy_flow'], '旧版本默认动作验收')
        legacy = read(facts['legacy_instance'], principal_platform)
        assert 'DELEGATE' not in legacy['allowedActions']
        task_action(current_task(facts['legacy_instance'], principal_platform)['id'], principal_platform, 'APPROVE', '旧版本正常通过')
        case('legacy-version-does-not-gain-delegation')
    if 'system_instance' not in facts:
        facts['system_instance'] = start_source(facts['system_flow'], '系统来源委托后一次生效')
        task = current_task(facts['system_instance'], principal_system)
        self_status, self_error = call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {
            'actionCode': 'DELEGATE', 'comment': '不能委托给自己', 'idempotencyKey': code + secrets.token_hex(6),
            'targetTenantMemberId': facts['registrations'][0]['tenant_member_id'],
            'delegationEndTime': (datetime.now() + timedelta(days=1)).replace(microsecond=0).isoformat()}, principal_system)
        assert self_status == 422 and self_error['code'] == 'FLOW_DELEGATION_SELF_DENIED'
        task, body, result = delegate(facts['system_instance'], principal_system,
            facts['registrations'][1]['tenant_member_id'], False, key=code + '_system_delegate')
        replay = api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", body, principal_system)
        assert replay['replayed'] and replay['actionId'] == result['actionId']
        system_delegate_view = read(facts['system_instance'], delegate_system)
        assert 'APPROVE' in system_delegate_view['allowedActions'] and 'DELEGATE' not in system_delegate_view['allowedActions']
        chain_status, chain_error = call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {
            **body, 'idempotencyKey': code + '_chain_attempt'}, delegate_system)
        assert chain_status == 409 and chain_error['code'] == 'FLOW_DELEGATION_ALREADY_ACTIVE'
        case('system-source-self-cycle-and-replay-guards', instance_id=facts['system_instance'])
    for key, label in [('concurrent_instance', '原审批人与代理人并发处理'),
            ('revoke_instance', '原审批人撤销限时代办'), ('expiry_instance', '委托到期恢复原审批人'),
            ('disabled_instance', '代理人停用后立即失权'), ('gui_instance', '普通页面委托代办验收')]:
        if key not in facts: facts[key] = start_platform(facts['platform_flow'], label); save()
    for key in ['concurrent_instance', 'revoke_instance', 'disabled_instance']:
        if not facts.get(key + '_delegated'):
            delegate(facts[key], principal_platform, facts['registrations'][1]['account_id'], True)
            facts[key + '_delegated'] = True; save()
    delegated = read(facts['revoke_instance'], delegate_platform)
    delegation = current_task(facts['revoke_instance'], delegate_platform)['assigneeSnapshot']['delegation']
    assert delegation['effective'] and delegation['principalName'] == facts['registrations'][0]['display_name']
    assert delegation['delegateName'] == facts['registrations'][1]['display_name']
    assert 'DELEGATE' not in delegated['allowedActions'] and 'TRANSFER' not in delegated['allowedActions']
    todos = api('GET', '/api/todos?status=PENDING&type=ALL', token=delegate_platform)
    assert any(item['sourceId'] == str(current_task(facts['revoke_instance'], delegate_platform)['id'])
        and 'APPROVE' in item['availableActions'] for item in todos)
    facts['restart_pending_state'] = {str(instance_id): compact_state(instance_id) for instance_id in facts['instances']}
    facts['prepared'] = True; save()
    print(json.dumps({'prepare': 'PASS', 'system_instance': facts['system_instance'],
        'gui_instance': facts['gui_instance'], 'designer_flow': facts['platform_flow']}, ensure_ascii=False))

elif stage == 'verify':
    assert facts.get('prepared') and not facts.get('api_verified')
    principal_platform, principal_system = user_tokens(0)
    delegate_platform, delegate_system = user_tokens(1)
    # 同一后端重启后代理人仍可按冻结节点策略处理真实系统来源，来源动作只执行一次。
    if read(facts['system_instance'], admin)['status'] != 'COMPLETED':
        system_task = current_task(facts['system_instance'], delegate_system)
        task_action(system_task['id'], delegate_system, 'APPROVE', '代理人核对后同意系统来源申请')
    source = delivery(facts['source_action'], admin)
    assert source['businessStatus'] == 'EXECUTED'
    if source['recordId'] not in facts['records']: facts['records'].append(source['recordId'])
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={facts['system_instance']} AND action_code='APPROVE' AND acted_by_account_id={facts['registrations'][1]['account_id']}") == [['1']]
    case('delegate-approves-system-source-on-behalf-of-principal-once', instance_id=facts['system_instance'])

    # 原审批人与代理人共用实例锁；只允许一个处理事实成功。
    concurrent_task = current_task(facts['concurrent_instance'], principal_platform)
    def approve(token, suffix):
        return call('POST', f"/api/flow-runtime/tasks/{concurrent_task['id']}/actions", {
            'actionCode': 'APPROVE', 'comment': '并发审批 ' + suffix,
            'idempotencyKey': code + '_concurrent_' + suffix}, token)
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda args: approve(*args), [(principal_platform, 'principal'), (delegate_platform, 'delegate')]))
    assert sorted(status for status, _ in results) == [200, 409]
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE task_id={concurrent_task['id']} AND action_code='APPROVE'") == [['1']]
    case('principal-and-delegate-concurrency-serializes-one-outcome', instance_id=facts['concurrent_instance'])

    # 原审批人撤销后，历史候选仍在，但运行授权和代理待办立即失效。
    revoke_task = current_task(facts['revoke_instance'], principal_platform)
    task_action(revoke_task['id'], principal_platform, 'REVOKE_DELEGATION', '原审批人提前恢复自行处理')
    denied_status, _ = call('GET', f"/api/flow-runtime/instances/{facts['revoke_instance']}", None, delegate_platform)
    assert denied_status in [403, 404]
    revoked_todos = api('GET', '/api/todos?status=ALL&type=ALL', token=delegate_platform)
    revoked = next(item for item in revoked_todos if item['sourceId'] == str(revoke_task['id']))
    assert revoked['status'] == 'REASSIGNED'
    principal_view = read(facts['revoke_instance'], principal_platform)
    relation = next(task for task in principal_view['tasks'] if task['id'] == revoke_task['id'])['assigneeSnapshot']['delegation']
    assert relation['status'] == 'REVOKED' and not relation['effective'] and 'DELEGATE' in principal_view['allowedActions']
    task_action(revoke_task['id'], principal_platform, 'APPROVE', '撤销委托后由原审批人处理')
    case('principal-revokes-without-rewriting-history-and-delegate-todo-expires', instance_id=facts['revoke_instance'])

    # 平台请求混入系统成员标识被拒；短有效期到达后无需后台计时器即可回到原审批人。
    expiry_task = current_task(facts['expiry_instance'], principal_platform)
    cross_status, cross_error = call('POST', f"/api/flow-runtime/tasks/{expiry_task['id']}/actions", {
        'actionCode': 'DELEGATE', 'comment': '跨范围身份应拒绝', 'idempotencyKey': code + '_cross_scope',
        'targetPlatformAccountId': facts['registrations'][1]['account_id'],
        'targetTenantMemberId': facts['registrations'][1]['tenant_member_id'],
        'delegationEndTime': (datetime.now() + timedelta(minutes=5)).replace(microsecond=0).isoformat()}, principal_platform)
    assert cross_status == 422 and cross_error['code'] == 'FLOW_DELEGATION_TARGET_INVALID'
    short_end = (datetime.now() + timedelta(seconds=2)).replace(microsecond=0).isoformat()
    delegate(facts['expiry_instance'], principal_platform, facts['registrations'][1]['account_id'], True, short_end)
    time.sleep(2.5)
    expiry_status, _ = call('GET', f"/api/flow-runtime/instances/{facts['expiry_instance']}", None, delegate_platform)
    assert expiry_status in [403, 404]
    expired_view = read(facts['expiry_instance'], principal_platform)
    expired_relation = next(task for task in expired_view['tasks'] if task['id'] == expiry_task['id'])['assigneeSnapshot']['delegation']
    assert expired_relation['status'] == 'EXPIRED' and not expired_relation['effective']
    assert 'DELEGATE' in expired_view['allowedActions'] and 'REVOKE_DELEGATION' not in expired_view['allowedActions']
    task_action(expiry_task['id'], principal_platform, 'APPROVE', '委托到期后原审批人继续处理')
    case('cross-scope-denied-and-expiry-restores-principal', instance_id=facts['expiry_instance'])

    # 代理平台成员停用后不能借历史候选处理；原审批人撤销后即使重新启用也不会恢复旧关系。
    disabled_task = current_task(facts['disabled_instance'], principal_platform)
    delegate_member = facts['registrations'][1]['platform_member_id']
    sql(f"UPDATE plat_member SET status='DISABLED',version=version+1 WHERE id={delegate_member}")
    disabled_status, _ = call('GET', f"/api/flow-runtime/instances/{facts['disabled_instance']}", None, delegate_platform)
    assert disabled_status in [401, 403, 404]
    task_action(disabled_task['id'], principal_platform, 'REVOKE_DELEGATION', '代理身份停用，撤销旧委托')
    sql(f"UPDATE plat_member SET status='ACTIVE',version=version+1 WHERE id={delegate_member}")
    delegate_platform, _ = user_tokens(1)
    restored_status, _ = call('GET', f"/api/flow-runtime/instances/{facts['disabled_instance']}", None, delegate_platform)
    assert restored_status in [403, 404]
    task_action(disabled_task['id'], principal_platform, 'APPROVE', '代理失效后原审批人完成')
    case('disabled-delegate-cannot-expand-permission-or-regain-revoked-task', instance_id=facts['disabled_instance'])
    facts['api_verified'] = True; save()
    print(json.dumps({'verify': 'PASS', 'cases': len(facts['cases']),
        'gui_instance': facts['gui_instance'], 'designer_flow': facts['platform_flow']}, ensure_ascii=False))

elif stage == 'gui_observed':
    assert facts.get('api_verified') and not facts.get('gui_verified')
    principal_platform, _ = user_tokens(0); delegate_platform, _ = user_tokens(1)
    view = read(facts['gui_instance'], principal_platform)
    task = next(task for task in view['tasks'] if task['status'] == 'PENDING')
    delegation = task['assigneeSnapshot'].get('delegation')
    assert delegation and delegation['effective'] and delegation['delegateName'] == facts['registrations'][1]['display_name']
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={facts['gui_instance']} AND action_code='DELEGATE'") == [['1']]
    assert int(sql(f"SELECT MAX(version_number) FROM flow_version WHERE flow_id={facts['platform_flow']}")[0][0]) >= 2
    task_action(task['id'], delegate_platform, 'APPROVE', '页面委托关系核对后由代理人完成')
    facts['gui_verified'] = True
    case('designer-and-runtime-pages-configure-show-and-execute-delegation', instance_id=facts['gui_instance'])
    print('GUI PASS')

elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_verified')
    principal_platform, _ = user_tokens(0)
    for instance_id in facts['instances']:
        token = admin if instance_id == facts['system_instance'] else principal_platform
        assert read(instance_id, token)['status'] == 'COMPLETED'
    facts['completed_restart_state'] = {str(instance_id): compact_state(instance_id) for instance_id in facts['instances']}
    facts['restart_verified'] = True
    case('same-backend-restart-retains-delegation-actions-and-completed-results')
    print('Restart PASS')

elif stage == 'cleanup':
    assert facts.get('restart_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    for record_id in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{record_id}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{record_id}/archive', {
            'version': record['version'], 'reason': '委托审批验收归档'}, admin)
    flow_ids = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={facts['module_id']} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={facts['module_id']} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flow_ids})")
    for registration, link_id in zip(facts['registrations'], facts['platform_links']):
        account_id = registration['account_id']; system_id = registration['system_id']
        sql(f"DELETE FROM plat_member_role WHERE id={link_id} AND member_id={registration['platform_member_id']} AND role_id={facts['platform_role_id']}; UPDATE sys_tenant_member tm JOIN sys_member sm ON tm.system_member_id=sm.id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={account_id}; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE auth_session SET revoked=1,revoked_reason='flow-delegation-acceptance-closed' WHERE account_id={account_id}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account_id} AND username='{registration['username']}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system_id} AND creator_account_id={account_id}")
    sql(f"UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={facts['platform_role_id']} AND code='{code}'")
    ids = ','.join(map(str, facts['instances']))
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({ids}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({ids}) AND status IN('PENDING','RUNNING')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({ids}) AND status IN('ACTIVE','RUNNING','SUSPENDED')") == [['0']]
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == facts['golden_count']
    facts['cleanup'] = {'unfinished_tasks': 0, 'unfinished_jobs': 0, 'unfinished_executions': 0,
        'role_114_restored': True, 'golden_rows_unchanged': True}
    facts['status'] = 'verified_contribution'
    facts['remaining'] = '附件评论催办、应用开放与其余未验收需求仍保留在后续任务包，不能由本步骤代替。'
    save()
    print(json.dumps({'cleanup': 'PASS', 'status': facts['status'], 'cases': len(facts['cases'])}, ensure_ascii=False))

else:
    raise AssertionError('使用 prepare / verify / gui_observed / restart / cleanup')
