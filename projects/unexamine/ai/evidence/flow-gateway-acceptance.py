"""B10.1a 分支与汇合公共执行骨架的真实 API/DB 验收。"""
import ast
import json
import re
import secrets
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {'call', 'api', 'sql'}], type_ignores=[]),
    'shared-actor-acceptance.py', 'exec'))

PATH = AREA / 'flow-gateway-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B10.1a', 'status': 'running', 'code': 'gateway_' + secrets.token_hex(5),
    'flows': [], 'instances': [], 'cases': []}
assert facts['status'] == 'running', '验收事实已经封存'

java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
ADMIN_NAME = re.search(r'default-admin\.username:([^}]+)', java).group(1)
LOCAL_PASSWORD = re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)
platform = api('POST', '/api/auth/login', {'username': ADMIN_NAME, 'password': LOCAL_PASSWORD})['accessToken']
admin = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)['tokens']['accessToken']
member_id = api('GET', '/api/auth/me', token=admin)['tenantMemberId']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    if not any(item['case'] == name for item in facts['cases']):
        facts['cases'].append({'case': name, **values}); save()


def node(key, kind, name, x, y, config=None):
    value = {'nodeKey': key, 'nodeType': kind, 'name': name, 'positionX': x, 'positionY': y,
        'config': config or {}, 'assigneePolicy': {}, 'formPolicy': {}, 'timeoutPolicy': {}, 'exceptionPolicy': {}}
    if kind == 'START': value['config'] = {'moduleId': 51}
    if kind == 'APPROVAL':
        value['config'] = {'approvalMode': 'OR_SIGN', 'manualOperationPolicy': {
            'allowedActions': ['APPROVE', 'REJECT'], 'opinionRequiredActions': ['REJECT'], 'requireClaim': False}}
        value['assigneePolicy'] = {'type': 'PERSON', 'tenantMemberIds': [member_id]}
    return value


def edge(key, source, target, priority=0, condition=None):
    config = {} if condition is None else {'condition': condition}
    return {'edgeKey': key, 'sourceNodeKey': source, 'targetNodeKey': target,
        'priorityOrder': priority, 'config': config}


def condition(operator, value):
    return {'mode': 'ALL', 'conditions': [{'field': 'score', 'operator': operator, 'value': value}]}


def create_flow(label, nodes, edges, simulation=None, publish=True):
    flow = api('POST', '/api/flows', {'code': facts['code'] + '_' + secrets.token_hex(3), 'name': label}, token=admin)
    facts['flows'].append(flow['id']); save()
    current = api('GET', f"/api/flows/{flow['id']}", token=admin)
    api('PUT', f"/api/flows/{flow['id']}/draft", {
        'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token=admin)
    checked = api('GET', f"/api/flows/{flow['id']}/publication-check", token=admin)
    if not publish: return flow['id'], checked
    assert checked['valid'], checked['issues']
    api('POST', f"/api/flows/{flow['id']}/publish", {
        'expectedDraftRevision': checked['draftRevision'], 'changeSummary': '分支汇合公共执行骨架验收',
        'simulationVariables': simulation or {}}, token=admin)
    return flow['id']


def start(flow_id, label, variables=None):
    result = api('POST', '/api/flow-runtime/instances', {'flowId': flow_id, 'title': label,
        'businessSnapshot': {}, 'variables': variables or {}, 'idempotencyKey': facts['code'] + secrets.token_hex(7)}, token=admin)
    instance_id = result['instance']['id']; facts['instances'].append(instance_id); save()
    return instance_id


def read(instance_id):
    return api('GET', f'/api/flow-runtime/instances/{instance_id}', token=admin)


def approve(task, key):
    body = {'actionCode': 'APPROVE', 'comment': '分支处理完成', 'idempotencyKey': key}
    return call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", body, admin)


def parallel_graph():
    nodes = [node('start', 'START', '开始', 0, 120),
        node('split', 'GATEWAY', '并行启动', 180, 120, {'gatewayMode': 'PARALLEL', 'mergeNodeKey': 'merge'}),
        node('left', 'APPROVAL', '合同核对', 380, 40), node('right', 'APPROVAL', '预算核对', 380, 200),
        node('merge', 'GATEWAY', '核对汇合', 600, 120, {'gatewayMode': 'MERGE', 'splitNodeKey': 'split'}),
        node('end', 'END', '结束', 800, 120)]
    edges = [edge('p1', 'start', 'split'), edge('p2', 'split', 'left', 10), edge('p3', 'split', 'right', 20),
        edge('p4', 'left', 'merge'), edge('p5', 'right', 'merge'), edge('p6', 'merge', 'end')]
    return nodes, edges


def inclusive_graph():
    nodes = [node('start', 'START', '开始', 0, 160),
        node('split', 'GATEWAY', '包容选择', 180, 160, {'gatewayMode': 'INCLUSIVE', 'mergeNodeKey': 'merge'}),
        node('greater', 'APPROVAL', '高分核对', 380, 20), node('equal', 'APPROVAL', '指定分值核对', 380, 160),
        node('fallback', 'APPROVAL', '普通兜底核对', 380, 300),
        node('merge', 'GATEWAY', '选择汇合', 620, 160, {'gatewayMode': 'MERGE', 'splitNodeKey': 'split'}),
        node('end', 'END', '结束', 820, 160)]
    edges = [edge('i1', 'start', 'split'), edge('i2', 'split', 'greater', 10, condition('GT', 10)),
        edge('i3', 'split', 'equal', 20, condition('EQ', 15)), edge('i4', 'split', 'fallback', 30),
        edge('i5', 'greater', 'merge'), edge('i6', 'equal', 'merge'), edge('i7', 'fallback', 'merge'),
        edge('i8', 'merge', 'end')]
    return nodes, edges


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    invalid_nodes, invalid_edges = parallel_graph()
    invalid_nodes = [item for item in invalid_nodes if item['nodeKey'] != 'merge']
    invalid_edges = [item for item in invalid_edges if item['sourceNodeKey'] != 'merge' and item['targetNodeKey'] != 'merge']
    invalid_id, invalid_check = create_flow('错误分支配置边界', invalid_nodes, invalid_edges, publish=False)
    invalid_codes = {item['code'] for item in invalid_check['issues']}
    assert 'FLOW_GATEWAY_MERGE_MISSING' in invalid_codes and not invalid_check['valid']
    facts['invalid_flow'] = invalid_id
    case('invalid-split-merge-topology-cannot-publish', issue_codes=sorted(invalid_codes))

    p_nodes, p_edges = parallel_graph()
    facts['parallel_flow'] = create_flow('并行审批与统一汇合', p_nodes, p_edges)
    i_nodes, i_edges = inclusive_graph()
    facts['inclusive_flow'] = create_flow('包容条件与实际分支集合', i_nodes, i_edges, {'score': 5})
    old_nodes = [node('start', 'START', '开始', 0, 100), node('gate', 'GATEWAY', '旧条件节点', 200, 100, {}),
        node('end', 'END', '结束', 400, 100)]
    old_edges = [edge('o1', 'start', 'gate'), edge('o2', 'gate', 'end')]
    facts['exclusive_flow'] = create_flow('旧排他网关兼容', old_nodes, old_edges)

    facts['parallel_instance'] = start(facts['parallel_flow'], '并行分支重启并发验收')
    facts['inclusive_instance'] = start(facts['inclusive_flow'], '包容两条命中分支', {'score': 15})
    facts['fallback_instance'] = start(facts['inclusive_flow'], '包容无命中走兜底', {'score': 5})
    facts['exclusive_instance'] = start(facts['exclusive_flow'], '旧排他网关继续可用')
    assert read(facts['exclusive_instance'])['status'] == 'COMPLETED'
    for key, expected in [('parallel_instance', 2), ('inclusive_instance', 2), ('fallback_instance', 1)]:
        view = read(facts[key])
        pending = [task for task in view['tasks'] if task['status'] == 'PENDING']
        assert len(pending) == expected, (key, pending)
        split = next(event for event in view['history'] if event['eventType'] == 'GATEWAY_SPLIT')
        assert split['detail']['selectedCount'] == expected
    instance_ids = ','.join(str(value) for value in facts['instances'])
    assert int(sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({instance_ids}) AND node_key<>'start' AND parent_execution_id IS NULL")[0][0]) == 0
    facts['restart_pending'] = {str(value): {'status': read(value)['status'],
        'pending': len([task for task in read(value)['tasks'] if task['status'] == 'PENDING'])}
        for value in [facts['parallel_instance'], facts['inclusive_instance'], facts['fallback_instance']]}
    facts['prepared'] = True; save()
    print(json.dumps({'prepare': 'PASS', 'parallel_flow': facts['parallel_flow'],
        'parallel_instance': facts['parallel_instance']}, ensure_ascii=False))

elif stage == 'verify':
    assert facts.get('prepared') and not facts.get('api_verified')
    for value, expected in [(facts['parallel_instance'], 2), (facts['inclusive_instance'], 2), (facts['fallback_instance'], 1)]:
        view = read(value)
        assert view['status'] == 'WAITING'
        assert len([task for task in view['tasks'] if task['status'] == 'PENDING']) == expected
    case('restart-preserves-frozen-branch-set-and-pending-executions')

    for key in ['parallel_instance', 'inclusive_instance']:
        instance_id = facts[key]
        tasks = [task for task in read(instance_id)['tasks'] if task['status'] == 'PENDING']
        requests = [(task, facts['code'] + '_' + key + '_' + str(task['id'])) for task in tasks]
        with ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(lambda item: approve(*item), requests))
        assert all(status == 200 for status, _ in responses), responses
        completed = read(instance_id)
        assert completed['status'] == 'COMPLETED'
        assert len([action for action in completed['actions'] if action['actionCode'] == 'GATEWAY_JOIN']) == 1
        assert len([execution for execution in completed['executions'] if execution['nodeKey'] == 'end']) == 1
        replay_status, replay = approve(*requests[-1])
        assert replay_status == 200 and replay['data']['replayed']
        assert read(instance_id)['status'] == 'COMPLETED'
    case('parallel-and-inclusive-concurrent-branches-join-exactly-once-and-replay-safely')

    fallback = read(facts['fallback_instance'])
    task = next(task for task in fallback['tasks'] if task['status'] == 'PENDING')
    assert task['nodeKey'] == 'fallback'
    status, _ = approve(task, facts['code'] + '_fallback_' + str(task['id']))
    assert status == 200 and read(facts['fallback_instance'])['status'] == 'COMPLETED'
    case('inclusive-routing-freezes-all-matches-and-uses-default-only-when-none-match')

    for key, expected in [('parallel_instance', 2), ('inclusive_instance', 2), ('fallback_instance', 1)]:
        instance_id = facts[key]
        assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={instance_id} AND action_code='GATEWAY_JOIN'") == [['1']]
        assert sql(f"SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(detail_json,'$.branchExecutionId'))) FROM flow_history_event WHERE instance_id={instance_id} AND event_type='GATEWAY_JOIN_ARRIVAL'") == [[str(expected)]]
    facts['api_verified'] = True; save()
    print(json.dumps({'verify': 'PASS', 'cases': len(facts['cases']),
        'parallel_flow': facts['parallel_flow'], 'parallel_instance': facts['parallel_instance']}, ensure_ascii=False))

elif stage == 'gui_prepare':
    assert facts.get('api_verified') and not facts.get('gui_fixture_ready')
    main_entry = api('POST', '/api/systems/54/enter', {}, platform)
    main_admin = main_entry['tokens']['accessToken']
    main_member = api('GET', '/api/auth/me', token=main_admin)['tenantMemberId']
    nodes, edges = parallel_graph()
    next(item for item in nodes if item['nodeKey'] == 'start')['config'] = {}
    for item in nodes:
        if item['nodeType'] == 'APPROVAL': item['assigneePolicy'] = {'type': 'PERSON', 'tenantMemberIds': [main_member]}
    flow = api('POST', '/api/flows', {'code': facts['code'] + '_gui', 'name': '页面可见并行审批汇合'}, token=main_admin)
    facts['flows'].append(flow['id']); facts['gui_flow'] = flow['id']; save()
    current = api('GET', f"/api/flows/{flow['id']}", token=main_admin)
    api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token=main_admin)
    checked = api('GET', f"/api/flows/{flow['id']}/publication-check", token=main_admin)
    assert checked['valid'], checked['issues']
    api('POST', f"/api/flows/{flow['id']}/publish", {'expectedDraftRevision': checked['draftRevision'],
        'changeSummary': '页面结构化网关验收', 'simulationVariables': {}}, token=main_admin)
    result = api('POST', '/api/flow-runtime/instances', {'flowId': flow['id'], 'title': '页面可见分支执行进度',
        'businessSnapshot': {}, 'variables': {}, 'idempotencyKey': facts['code'] + '_gui_start'}, token=main_admin)
    instance_id = result['instance']['id']; facts['instances'].append(instance_id); facts['gui_instance'] = instance_id; save()
    tasks = [task for task in api('GET', f'/api/flow-runtime/instances/{instance_id}', token=main_admin)['tasks'] if task['status'] == 'PENDING']
    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda task: call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {
            'actionCode': 'APPROVE', 'comment': '页面分支处理完成',
            'idempotencyKey': facts['code'] + '_gui_' + str(task['id'])}, main_admin), tasks))
    assert all(status == 200 for status, _ in responses)
    assert api('GET', f'/api/flow-runtime/instances/{instance_id}', token=main_admin)['status'] == 'COMPLETED'
    facts['gui_fixture_ready'] = True; save()
    print(json.dumps({'gui_prepare': 'PASS', 'gui_flow': flow['id'], 'gui_instance': instance_id}, ensure_ascii=False))

elif stage == 'gui_observed':
    assert facts.get('api_verified') and not facts.get('gui_verified')
    assert facts.get('gui_fixture_ready')
    main_admin = api('POST', '/api/systems/54/enter', {}, platform)['tokens']['accessToken']
    design = api('GET', f"/api/flows/{facts['gui_flow']}", token=main_admin)
    modes = {item['config'].get('gatewayMode') for item in design['nodes'] if item['nodeType'] == 'GATEWAY'}
    assert modes == {'PARALLEL', 'MERGE'}
    view = api('GET', f"/api/flow-runtime/instances/{facts['gui_instance']}", token=main_admin)
    assert any(event['eventType'] == 'GATEWAY_SPLIT' and event['detail']['selectedCount'] == 2 for event in view['history'])
    assert any(event['eventType'] == 'GATEWAY_JOIN' and event['detail']['expectedCount'] == 2 for event in view['history'])
    facts['gui_verified'] = True
    case('ordinary-designer-and-runtime-pages-show-structured-gateway-pair-and-branch-progress')
    save(); print('GUI PASS')

elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_verified')
    for instance_id in facts['instances']:
        if instance_id == facts.get('gui_instance'):
            main_admin = api('POST', '/api/systems/54/enter', {}, platform)['tokens']['accessToken']
            assert api('GET', f'/api/flow-runtime/instances/{instance_id}', token=main_admin)['status'] == 'COMPLETED'
        else:
            assert read(instance_id)['status'] == 'COMPLETED'
    ids = ','.join(str(value) for value in facts['instances'])
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({ids}) AND status IN('ACTIVE','RUNNING','SUSPENDED')") == [['0']]
    facts['restart_verified'] = True
    case('same-backend-restart-retains-completed-branch-and-single-join-facts')
    save(); print('Restart PASS')

elif stage == 'cleanup':
    assert facts.get('restart_verified')
    flow_ids = ','.join(str(value) for value in facts['flows'])
    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flow_ids}) AND code LIKE '{facts['code']}%'")
    ids = ','.join(str(value) for value in facts['instances'])
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({ids}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({ids}) AND status IN('PENDING','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({ids}) AND status IN('ACTIVE','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_definition WHERE id IN({flow_ids}) AND status<>'DISABLED'") == [['0']]
    facts['cleanup'] = {'flows_disabled': len(facts['flows']), 'unfinished_tasks': 0,
        'unfinished_jobs': 0, 'unfinished_executions': 0, 'existing_module_51_unchanged': True}
    facts['status'] = 'verified_contribution'
    facts['remaining'] = '子流程、其余自动节点、受控应用和外部渠道继续按后续任务包实现。'
    save(); print(json.dumps({'cleanup': 'PASS', 'status': facts['status'], 'cases': len(facts['cases'])}, ensure_ascii=False))

else:
    raise AssertionError('使用 prepare / verify / gui_observed / restart / cleanup')
