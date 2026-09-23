"""B09.2c.6 退回、发起人修正及重新提交的定向真实验收。"""
import ast
import copy
import json
import re
import secrets
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
        or isinstance(node, ast.FunctionDef) and node.name in names], type_ignores=[]), filename, 'exec'))

PATH = AREA / 'flow-return-resubmit-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.6', 'status': 'running', 'code': 'return_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'instances': [], 'records': [], 'cases': []}
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
    facts['cases'].append({'case': name, **values})
    save()


def ordinary_tokens():
    token = api('POST', '/api/auth/login', {
        'username': facts['registration']['username'], 'password': LOCAL_PASSWORD})['accessToken']
    system = api('POST', '/api/systems/54/tenants/57/enter', {}, token)['tokens']['accessToken']
    return token, system


def grant():
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


def publish(flow_id, token):
    checked = api('GET', f'/api/flows/{flow_id}/publication-check', token=token)
    assert checked['valid'], checked['issues']
    return api('POST', f'/api/flows/{flow_id}/publish', {
        'expectedDraftRevision': checked['draftRevision'],
        'changeSummary': '退回修正重提定向验收', 'simulationVariables': {}}, token)


def human_config(return_policy=None):
    value = {'approvalMode': 'OR_SIGN', 'manualOperationPolicy': {
        'allowedActions': ['APPROVE', 'RETURN'], 'opinionRequiredActions': ['RETURN']}}
    if return_policy is not None:
        value['returnPolicy'] = return_policy
    return value


def make_flow(label, realm='platform', return_policy=None, legacy=False, invalid=False):
    token = platform if realm == 'platform' else admin
    flow = api('POST', '/api/flows', {'code': code + '_' + secrets.token_hex(3), 'name': label}, token)
    flow_id = flow['id']
    facts['flows'].append(flow_id); save()
    people_key = 'platformAccountIds' if realm == 'platform' else 'tenantMemberIds'
    person = facts['registration']['account_id'] if realm == 'platform' else facts['tenant_member_id']
    start_config = {} if realm == 'platform' else {'moduleId': facts['module_id']}
    schema = [('start', 'START', '开始'), ('review1', 'APPROVAL', '申请初审')]
    if not legacy: schema.append(('review2', 'APPROVAL', '申请复核'))
    schema.append(('end', 'END', '结束'))
    nodes = []
    for index, (node_key, node_type, name) in enumerate(schema):
        config = start_config if node_type == 'START' else {}
        if node_type == 'APPROVAL':
            policy = None
            if node_key == 'review2' and return_policy is not None: policy = return_policy
            if invalid and node_key == 'review2':
                policy = {'target': 'SPECIFIED', 'targetNodeKey': 'end', 'resubmitRoute': 'RETURN_NODE'}
            config = human_config(policy)
        nodes.append({'nodeKey': node_key, 'nodeType': node_type, 'name': name,
            'positionX': index * 220, 'positionY': 100, 'config': config,
            'assigneePolicy': {'type': 'PERSON', people_key: [person]} if node_type == 'APPROVAL' else {},
            'formPolicy': {'editable': list(facts['fields']), 'required': ['subject']}
                if node_type == 'APPROVAL' and realm == 'system' else {},
            'timeoutPolicy': {}})
    edges = [{'edgeKey': 'edge_' + str(index), 'sourceNodeKey': source['nodeKey'],
        'targetNodeKey': target['nodeKey'], 'priorityOrder': 0, 'config': {}}
        for index, (source, target) in enumerate(zip(nodes, nodes[1:]))]
    draft(flow_id, nodes, edges, token)
    if invalid:
        checked = api('GET', f'/api/flows/{flow_id}/publication-check', token=token)
        assert not checked['valid'] and any(item['code'] == 'FLOW_RETURN_TARGET_INVALID' for item in checked['issues'])
        assert sql(f'SELECT COUNT(*) FROM flow_version WHERE flow_id={flow_id}') == [['0']]
        return flow_id
    publish(flow_id, token)
    return flow_id


def read(instance_id, token=platform):
    return api('GET', f'/api/flow-runtime/instances/{instance_id}', token=token)


def current_task(instance_id, token):
    return next(task for task in read(instance_id, token)['tasks']
        if task['status'] == 'PENDING' and task['currentHandler'])


def act(instance_id, token, action, comment, fields=None, key=None):
    task = current_task(instance_id, token)
    body = {'actionCode': action, 'comment': comment,
        'idempotencyKey': key or code + secrets.token_hex(6)}
    if fields is not None:
        form = api('GET', f"/api/flow-runtime/tasks/{task['id']}/form", token=token)
        body['fieldValues'] = fields
        body['expectedFormRevision'] = form['formRevision']
    return task, body, api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", body, token)


def start_platform(flow_id, label):
    result = api('POST', '/api/flow-runtime/instances', {'flowId': flow_id, 'title': label,
        'businessSnapshot': {}, 'variables': {}, 'idempotencyKey': code + secrets.token_hex(6)}, platform)
    instance_id = result['instance']['id']
    facts['instances'].append(instance_id); save()
    return instance_id


def start_source(flow_id, label):
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'CREATE',
        'flowId': flow_id, 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True,
        'replaceExisting': True}, admin)
    action = api('POST', f'/api/runtime/modules/{code}/records', {'title': label, 'status': 'ACTIVE',
        'participantMemberIds': [], 'fields': {'subject': label, 'score': 1, 'enabled': False}}, admin,
        action_key=code + secrets.token_hex(6))
    facts['source_action'] = {key: action[key] for key in ['flowInstanceId', 'idempotencyRecordId', 'outboxId']}
    facts['instances'].append(action['flowInstanceId']); save()
    return action['flowInstanceId']


def compact_state(instance_id):
    return {
        'instance': sql(f'SELECT id,status,current_node_key,version FROM flow_instance WHERE id={instance_id}'),
        'tasks': sql(f"SELECT id,node_key,task_type,status FROM flow_task WHERE instance_id={instance_id} ORDER BY id"),
        'actions': sql(f"SELECT id,task_id,node_key,action_code FROM flow_action WHERE instance_id={instance_id} ORDER BY id"),
        'executions': sql(f"SELECT id,node_key,status FROM flow_execution WHERE instance_id={instance_id} ORDER BY id")}


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    if 'golden_baseline' not in facts:
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
        facts['golden_count'] = sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0'); save()

    if 'registration' not in facts:
        username = code + '_member'
        registered = api('POST', '/api/auth/register', {'username': username, 'password': LOCAL_PASSWORD,
            'displayName': '退回重提验收成员', 'systemName': '退回重提隔离系统'})
        facts['registration'] = {'username': username, 'account_id': registered['accountId'],
            'system_id': registered['systemId']}; save()
        platform_role = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','退回重提验收角色','仅本地验收临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0])
        facts['platform_role_id'] = platform_role
        for resource, actions in [('FLOW', ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE']), ('TODO', ['VIEW', 'HANDLE'])]:
            for action in actions:
                sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{platform_role},'{resource}','PLATFORM','{action}','ALLOW')")
        platform_member = int(sql(f"SELECT id FROM plat_member WHERE platform_id=1 AND account_id={registered['accountId']}")[0][0])
        facts['platform_member_id'] = platform_member
        facts['platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{platform_member},{platform_role}); SELECT LAST_INSERT_ID()')[0][0]); save()
        facts['tenant_member_id'] = api('POST', '/api/admin/system/authorization/members', {
            'account': username, 'roleIds': [114]}, admin)['tenantMemberId']; save()

    if 'module_id' not in facts:
        module = api('POST', '/api/admin/module-config/modules', {'name': '退回修正来源一次验收',
            'code': code, 'groupName': '流程闭环隔离验收'}, admin)['module']
        facts['module_id'] = module['id']; save()
        for index, (field, name, kind) in enumerate([('subject', '申请主题', 'TEXT'),
                ('score', '申请分值', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
            facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {
                'code': field, 'name': name, 'fieldType': kind, 'required': field == 'subject',
                'sortOrder': index, 'config': {}}, admin); save()
        publish_module(module['id']); grant()
    ordinary, system_ordinary = ordinary_tokens()

    if len(facts['flows']) < 4:
        system_flow = make_flow('系统来源指定初审并从头重提', 'system', {
            'target': 'SPECIFIED', 'targetNodeKey': 'review1', 'resubmitRoute': 'FROM_START'})
        previous_flow = make_flow('平台退回上一节点并从该节点继续', 'platform', {
            'target': 'PREVIOUS', 'resubmitRoute': 'RETURN_NODE'})
        legacy_flow = make_flow('旧配置默认退回发起人并从头执行', 'platform', legacy=True)
        make_flow('非法指定结束节点不可发布', 'platform', invalid=True)
    else:
        system_flow, previous_flow, legacy_flow = facts['flows'][:3]
    facts.update({'system_flow': system_flow, 'previous_flow': previous_flow, 'legacy_flow': legacy_flow,
        'designer_version_before': int(sql(f'SELECT MAX(version_number) FROM flow_version WHERE flow_id={previous_flow}')[0][0])}); save()

    if 'system_instance' not in facts:
        facts['system_instance'] = start_source(system_flow, '原始主题待退回修正'); save()
        act(facts['system_instance'], system_ordinary, 'APPROVE', '初审通过进入复核', fields={})
        act(facts['system_instance'], system_ordinary, 'RETURN', '复核发现主题不完整，请发起人修正')
    system_instance = facts['system_instance']

    if 'previous_instance' not in facts:
        facts['previous_instance'] = start_platform(previous_flow, '上一节点继续接口验收'); save()
        act(facts['previous_instance'], ordinary, 'APPROVE', '平台初审通过')
        act(facts['previous_instance'], ordinary, 'RETURN', '平台复核退回上一人工节点')
    previous_instance = facts['previous_instance']

    if 'legacy_instance' not in facts:
        facts['legacy_instance'] = start_platform(legacy_flow, '旧配置默认策略接口验收'); save()
        act(facts['legacy_instance'], ordinary, 'RETURN', '旧配置退回发起人')
    legacy_instance = facts['legacy_instance']

    if 'gui_instance' not in facts:
        facts['gui_instance'] = start_platform(previous_flow, '页面可见退回修正实例'); save()
        act(facts['gui_instance'], ordinary, 'APPROVE', '页面实例初审通过')
        act(facts['gui_instance'], ordinary, 'RETURN', '页面实例复核退回，请修正')
    gui_instance = facts['gui_instance']

    for instance_id in [system_instance, previous_instance, legacy_instance, gui_instance]:
        view = read(instance_id, admin if instance_id == system_instance else platform)
        correction = next(task for task in view['tasks'] if task['taskType'] == 'RETURN_TASK' and task['status'] == 'PENDING')
        assert view['status'] == 'WAITING' and correction['currentHandler']
        assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={instance_id} AND status IN('PENDING','RUNNING')") == [['0']]
    source = api('GET', f"/api/runtime/modules/{code}/records/action-results/{facts['source_action']['idempotencyRecordId']}", token=admin)
    assert source['businessStatus'] == 'PENDING_APPROVAL'
    facts['restart_pending_state'] = {str(instance_id): compact_state(instance_id)
        for instance_id in [system_instance, previous_instance, legacy_instance, gui_instance]}
    facts['prepared'] = True; save()
    print(json.dumps({'prepare': 'PASS', 'system_instance': system_instance,
        'gui_instance': gui_instance, 'designer_flow': previous_flow}, ensure_ascii=False))

elif stage == 'verify':
    assert facts.get('prepared') and not facts.get('api_verified')
    ordinary, system_ordinary = ordinary_tokens()
    for instance_id, before in facts['restart_pending_state'].items():
        assert compact_state(int(instance_id)) == before, f'重启改变退回现场 {instance_id}'

    system_instance = facts['system_instance']
    correction = next(task for task in read(system_instance, admin)['tasks']
        if task['taskType'] == 'RETURN_TASK' and task['status'] == 'PENDING')
    before = compact_state(system_instance)
    denied_body = {'actionCode': 'RESUBMIT', 'comment': '非发起人不得重新提交',
        'idempotencyKey': code + secrets.token_hex(6), 'fieldValues': {},
        'expectedFormRevision': api('GET', f"/api/flow-runtime/tasks/{correction['id']}/form", token=admin)['formRevision']}
    status, response = call('POST', f"/api/flow-runtime/tasks/{correction['id']}/actions", denied_body, system_ordinary)
    assert status == 403 and response['code'] == 'FLOW_RETURN_RESUBMIT_DENIED'
    assert compact_state(system_instance) == before
    case('non-starter-resubmit-denied-without-mutation')

    form = api('GET', f"/api/flow-runtime/tasks/{correction['id']}/form", token=admin)
    bodies = [{'actionCode': 'RESUBMIT', 'comment': '修正主题后重新提交',
        'idempotencyKey': code + secrets.token_hex(6), 'fieldValues': {'subject': '修正后的完整申请主题'},
        'expectedFormRevision': form['formRevision']} for _ in range(2)]
    def submit(body):
        return call('POST', f"/api/flow-runtime/tasks/{correction['id']}/actions", body, admin)
    with ThreadPoolExecutor(max_workers=2) as executor:
        responses = list(executor.map(submit, bodies))
    assert sorted(status for status, _ in responses) == [200, 409], responses
    winner = next(body for body, result in zip(bodies, responses) if result[0] == 200)
    loser = next(result for result in responses if result[0] == 409)
    assert loser[1]['code'] == 'FLOW_TASK_ALREADY_HANDLED'
    replay_status, replay = call('POST', f"/api/flow-runtime/tasks/{correction['id']}/actions", winner, admin)
    assert replay_status == 200 and replay['data']['replayed'] is True
    view = read(system_instance, admin)
    old_review = next(task for task in view['tasks'] if task['nodeKey'] == 'review1' and task['status'] == 'APPROVE')
    new_review = next(task for task in view['tasks'] if task['nodeKey'] == 'review1' and task['status'] == 'PENDING')
    assert old_review['assigneeSnapshot']['executionId'] != new_review['assigneeSnapshot']['executionId']
    act(system_instance, system_ordinary, 'APPROVE', '重新初审已核对修正内容', fields={})
    act(system_instance, system_ordinary, 'APPROVE', '重新复核通过', fields={})
    assert read(system_instance, admin)['status'] == 'COMPLETED'
    action = facts['source_action']
    result = delivery(action)
    assert result['businessStatus'] == 'EXECUTED'
    facts['records'].append(result['recordId']); save()
    record = api('GET', f"/api/runtime/modules/{code}/records/{result['recordId']}", token=admin)
    assert record['fields']['subject'] == '修正后的完整申请主题'
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
    assert delivery(action)['recordId'] == result['recordId']
    case('concurrent-resubmit-serializes-replay-and-new-execution-group', instance_id=system_instance)
    case('corrected-system-source-executes-once', record_id=result['recordId'])

    previous = facts['previous_instance']
    previous_task = current_task(previous, platform)
    assert previous_task['taskType'] == 'RETURN_TASK'
    assert previous_task['nodeKey'] == 'review1'
    assert previous_task['assigneeSnapshot']['returnTarget'] == 'PREVIOUS'
    assert previous_task['assigneeSnapshot']['resubmitRoute'] == 'RETURN_NODE'
    act(previous, platform, 'RESUBMIT', '已修正，回到上一节点继续')
    act(previous, ordinary, 'APPROVE', '重新处理上一节点')
    act(previous, ordinary, 'APPROVE', '复核通过')
    assert read(previous, platform)['status'] == 'COMPLETED'
    case('previous-return-node-resubmit-reruns-target-and-completes', instance_id=previous)

    legacy = facts['legacy_instance']
    legacy_task = current_task(legacy, platform)
    assert legacy_task['assigneeSnapshot']['returnTarget'] == 'STARTER'
    assert legacy_task['assigneeSnapshot']['resubmitRoute'] == 'FROM_START'
    act(legacy, platform, 'RESUBMIT', '旧版本按兼容策略重新提交')
    act(legacy, ordinary, 'APPROVE', '旧版本重新审批通过')
    assert read(legacy, platform)['status'] == 'COMPLETED'
    case('legacy-absent-return-policy-defaults-starter-from-start', instance_id=legacy)

    facts['api_verified'] = True; save()
    print(json.dumps({'verify': 'PASS', 'cases': len(facts['cases']),
        'gui_instance': facts['gui_instance'], 'designer_flow': facts['previous_flow']}, ensure_ascii=False))

elif stage == 'gui_observed':
    assert facts.get('api_verified') and not facts.get('gui_verified')
    ordinary, _ = ordinary_tokens()
    flow = api('GET', f"/api/flows/{facts['previous_flow']}", token=platform)
    current_version = int(sql(f"SELECT MAX(version_number) FROM flow_version WHERE flow_id={facts['previous_flow']}")[0][0])
    assert current_version > facts['designer_version_before'], '页面应保存并发布一个新版本'
    snapshot = json.loads(sql(f"SELECT snapshot_json FROM flow_version WHERE id={flow['currentVersionId']}")[0][0])
    policy = next(node for node in snapshot['nodes'] if node['nodeKey'] == 'review2')['config']['returnPolicy']
    assert policy == {'target': 'PREVIOUS', 'resubmitRoute': 'RETURN_NODE'}
    gui = facts['gui_instance']
    correction = current_task(gui, platform)
    assert correction['taskType'] == 'RETURN_TASK' and correction['assigneeSnapshot']['returnNodeName'] == '申请初审'
    act(gui, platform, 'RESUBMIT', '页面核对后修正重提')
    act(gui, ordinary, 'APPROVE', '页面实例重新初审通过')
    act(gui, ordinary, 'APPROVE', '页面实例重新复核通过')
    assert read(gui, platform)['status'] == 'COMPLETED'
    facts['completed_restart_state'] = {str(instance_id): compact_state(instance_id) for instance_id in facts['instances']}
    facts['gui_verified'] = True
    case('designer-structured-return-policy-save-reenter-publish')
    case('runtime-page-shows-return-reason-route-and-resubmit-form', instance_id=gui)
    print('GUI observed and completed PASS')

elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_verified')
    for instance_id, before in facts['completed_restart_state'].items():
        assert compact_state(int(instance_id)) == before
        assert read(int(instance_id), admin if int(instance_id) == facts['system_instance'] else platform)['status'] == 'COMPLETED'
    facts['restart_verified'] = True
    case('same-backend-restart-retains-return-resubmit-results')
    print('Restart PASS')

elif stage == 'cleanup':
    assert facts.get('restart_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    for record_id in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{record_id}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{record_id}/archive', {
            'version': record['version'], 'reason': '退回重提验收归档'}, admin)
    flow_ids = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={facts['module_id']} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={facts['module_id']} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flow_ids})")
    sql(f"DELETE FROM plat_member_role WHERE id={facts['platform_link_id']} AND member_id={facts['platform_member_id']} AND role_id={facts['platform_role_id']}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={facts['platform_role_id']} AND code='{code}'")
    registration = facts['registration']; account_id = registration['account_id']; system_id = registration['system_id']
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON tm.system_member_id=sm.id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={account_id}; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE auth_session SET revoked=1,revoked_reason='return-resubmit-acceptance-closed' WHERE account_id={account_id}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account_id} AND username='{registration['username']}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system_id} AND creator_account_id={account_id}")
    ids = ','.join(map(str, facts['instances']))
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({ids}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({ids}) AND status IN('PENDING','RUNNING')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({ids}) AND status IN('ACTIVE','RUNNING','SUSPENDED')") == [['0']]
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == facts['golden_count']
    facts['cleanup'] = {'unfinished_tasks': 0, 'unfinished_jobs': 0, 'unfinished_executions': 0,
        'role_114_restored': True, 'golden_rows_unchanged': True}
    facts['status'] = 'verified_contribution'
    facts['remaining'] = '代理委托、附件必填、评论催办、全局高级动态节点配置及应用开放等后续包仍未完成'
    save()
    print(json.dumps({'cleanup': 'PASS', 'status': facts['status'], 'cases': len(facts['cases'])}, ensure_ascii=False))

else:
    raise AssertionError('使用 prepare / verify / gui_observed / restart / cleanup')
