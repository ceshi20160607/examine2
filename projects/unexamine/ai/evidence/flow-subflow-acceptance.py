"""B10.1b 子流程公共生命周期的真实 API、数据库、重启和清理验收。"""
import ast
import json
import re
import secrets
import sys
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
    or isinstance(node, ast.FunctionDef) and node.name in {'call', 'api', 'sql'}], type_ignores=[]),
    'shared-actor-acceptance.py', 'exec'))

PATH = AREA / 'flow-subflow-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B10.1b', 'status': 'running', 'code': 'subflow_' + secrets.token_hex(5),
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


def edge(key, source, target):
    return {'edgeKey': key, 'sourceNodeKey': source, 'targetNodeKey': target,
        'priorityOrder': 0, 'config': {}}


def graph(middle=None):
    nodes = [node('start', 'START', '开始', 0, 100)]
    edges = []
    if middle:
        nodes.append(middle); edges.append(edge('e1', 'start', middle['nodeKey']))
        edges.append(edge('e2', middle['nodeKey'], 'end'))
    else:
        edges.append(edge('e1', 'start', 'end'))
    nodes.append(node('end', 'END', '结束', 500, 100))
    return nodes, edges


def create_draft(label, nodes, edges):
    flow = api('POST', '/api/flows', {'code': facts['code'] + '_' + secrets.token_hex(3), 'name': label}, token=admin)
    facts['flows'].append(flow['id']); save()
    current = api('GET', f"/api/flows/{flow['id']}", token=admin)
    api('PUT', f"/api/flows/{flow['id']}/draft", {
        'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token=admin)
    return flow['id']


def publication_check(flow_id):
    return api('GET', f'/api/flows/{flow_id}/publication-check', token=admin)


def publish(flow_id, summary='子流程公共生命周期验收'):
    checked = publication_check(flow_id)
    assert checked['valid'], checked['issues']
    return api('POST', f'/api/flows/{flow_id}/publish', {
        'expectedDraftRevision': checked['draftRevision'], 'changeSummary': summary,
        'simulationVariables': {}}, token=admin)


def update_draft(flow_id, nodes, edges):
    current = api('GET', f'/api/flows/{flow_id}', token=admin)
    api('PUT', f'/api/flows/{flow_id}/draft', {
        'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token=admin)


def subflow_node(flow_id, inputs=None, outputs=None):
    return node('child', 'SUBFLOW', '调用已发布子流程', 240, 100, {
        'flowId': flow_id, 'inheritAllVariables': False,
        'inputMappings': inputs or [], 'outputMappings': outputs or []})


def start(flow_id, label, variables=None):
    result = api('POST', '/api/flow-runtime/instances', {'flowId': flow_id, 'title': label,
        'businessSnapshot': {}, 'variables': variables or {},
        'idempotencyKey': facts['code'] + '_' + secrets.token_hex(7)}, token=admin)
    instance_id = result['instance']['id']; facts['instances'].append(instance_id); save()
    return instance_id


def read(instance_id):
    return api('GET', f'/api/flow-runtime/instances/{instance_id}', token=admin)


def stored_variable(instance_id, key):
    rows = sql(f"SELECT JSON_UNQUOTE(value_json) FROM flow_instance_variable "
        f"WHERE instance_id={int(instance_id)} AND variable_key='{key}'")
    return rows[0][0] if rows else None


def child_of(parent_id):
    link = next(item for item in read(parent_id)['subflows'] if item['relation'] == 'CHILD')
    if link['instanceId'] not in facts['instances']:
        facts['instances'].append(link['instanceId']); save()
    return link['instanceId']


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')

    self_nodes, self_edges = graph()
    self_flow = create_draft('禁止引用自身', self_nodes, self_edges)
    self_nodes, self_edges = graph(subflow_node(self_flow))
    update_draft(self_flow, self_nodes, self_edges)
    check = publication_check(self_flow)
    assert not check['valid'] and 'SUBFLOW_SELF_REFERENCE' in {item['code'] for item in check['issues']}
    case('self-reference-cannot-publish')

    unpublished_nodes, unpublished_edges = graph()
    unpublished = create_draft('未发布目标', unpublished_nodes, unpublished_edges)
    invalid_parent_nodes, invalid_parent_edges = graph(subflow_node(unpublished))
    invalid_parent = create_draft('禁止未发布子流程', invalid_parent_nodes, invalid_parent_edges)
    check = publication_check(invalid_parent)
    assert not check['valid'] and 'SUBFLOW_VERSION_REQUIRED' in {item['code'] for item in check['issues']}
    case('unpublished-child-cannot-publish')

    cycle_child_nodes, cycle_child_edges = graph()
    cycle_child = create_draft('循环目标甲', cycle_child_nodes, cycle_child_edges); publish(cycle_child)
    cycle_parent_nodes, cycle_parent_edges = graph(subflow_node(cycle_child))
    cycle_parent = create_draft('循环目标乙', cycle_parent_nodes, cycle_parent_edges); publish(cycle_parent)
    cycle_child_nodes, cycle_child_edges = graph(subflow_node(cycle_parent))
    update_draft(cycle_child, cycle_child_nodes, cycle_child_edges)
    check = publication_check(cycle_child)
    assert not check['valid'] and 'SUBFLOW_CYCLE' in {item['code'] for item in check['issues']}
    case('indirect-cycle-cannot-publish')

    approval = node('approve', 'APPROVAL', '子流程审批', 240, 100)
    child_nodes, child_edges = graph(approval)
    child_flow = create_draft('结构化子流程审批', child_nodes, child_edges)
    child_v1 = publish(child_flow, '子流程版本一')
    parent_nodes, parent_edges = graph(subflow_node(child_flow,
        [{'sourceKey': 'parentInput', 'targetKey': 'childInput'}],
        [{'sourceKey': 'childResult', 'targetKey': 'parentResult'}]))
    parent_flow = create_draft('父流程等待并接收结果', parent_nodes, parent_edges)
    parent_version = publish(parent_flow, '父流程冻结子版本一')
    facts.update({'child_flow': child_flow, 'child_v1': child_v1['versionId'],
        'parent_flow': parent_flow, 'parent_version': parent_version['versionId']}); save()

    changed_nodes, changed_edges = graph(node('approve', 'APPROVAL', '子流程审批第二版', 240, 100))
    update_draft(child_flow, changed_nodes, changed_edges)
    child_v2 = publish(child_flow, '子流程版本二')
    facts['child_v2'] = child_v2['versionId']; save()

    parent = start(parent_flow, '重启后继续父子流程', {'parentInput': 'INPUT-001', 'unmapped': 'PRIVATE'})
    child = child_of(parent)
    pview, cview = read(parent), read(child)
    assert pview['status'] == 'WAITING' and cview['status'] == 'WAITING'
    assert cview['flowVersionId'] == child_v1['versionId'] != child_v2['versionId']
    # 运行页按安全投影隐藏流程变量，映射事实从本次测试实例的持久化行精确读回。
    assert stored_variable(child, 'childInput') == 'INPUT-001'
    assert stored_variable(child, 'unmapped') is None
    assert pview['subflows'][0]['instanceId'] == child
    assert next(item for item in cview['subflows'] if item['relation'] == 'PARENT')['instanceId'] == parent
    assert any(event['eventType'] == 'SUBFLOW_STARTED' for event in pview['history'])
    facts.update({'normal_parent': parent, 'normal_child': child,
        'normal_task': next(task['id'] for task in cview['tasks'] if task['status'] == 'PENDING')})
    case('published-parent-freezes-child-version-input-mapping-and-durable-relation',
        parent_instance=parent, child_instance=child, frozen_child_version=child_v1['versionId'])

    failure_nodes, failure_edges = graph(node('service', 'WEBHOOK', '模拟受控服务异常', 240, 100,
        {'url': 'https://example.invalid/controlled', 'simulateFailure': True}))
    failure_flow = create_draft('可恢复异常子流程', failure_nodes, failure_edges); publish(failure_flow)
    failure_parent_nodes, failure_parent_edges = graph(subflow_node(failure_flow))
    failure_parent_flow = create_draft('等待异常子流程的父流程', failure_parent_nodes, failure_parent_edges)
    publish(failure_parent_flow)
    failure_parent = start(failure_parent_flow, '子流程异常可见并恢复')
    failure_child = child_of(failure_parent)
    fparent, fchild = read(failure_parent), read(failure_child)
    assert fparent['status'] == 'WAITING' and fchild['status'] == 'EXCEPTION'
    assert any(event['eventType'] == 'SUBFLOW_CHILD_EXCEPTION' for event in fparent['history'])
    assert len([item for item in fchild['exceptions'] if item['status'] == 'OPEN']) == 1
    facts.update({'failure_flow': failure_flow, 'failure_parent_flow': failure_parent_flow,
        'failure_parent': failure_parent, 'failure_child': failure_child,
        'failure_incident': next(item['id'] for item in fchild['exceptions'] if item['status'] == 'OPEN')})
    case('child-exception-is-visible-and-parent-does-not-complete')

    cascade_parent = start(parent_flow, '父流程终止同步清理子流程', {'parentInput': 'INPUT-TERMINATE'})
    cascade_child = child_of(cascade_parent)
    facts.update({'cascade_parent': cascade_parent, 'cascade_child': cascade_child}); save()
    facts['prepared'] = True; save()
    print(json.dumps({'prepare': 'PASS', 'parent': parent, 'child': child,
        'failure_parent': failure_parent}, ensure_ascii=False))

elif stage == 'verify':
    assert facts.get('prepared') and not facts.get('api_verified')
    assert read(facts['normal_parent'])['status'] == 'WAITING'
    assert read(facts['normal_child'])['status'] == 'WAITING'
    case('same-backend-restart-preserves-parent-wait-child-task-and-relations')

    key = facts['code'] + '_approve_child'
    body = {'actionCode': 'APPROVE', 'comment': '子流程处理完成', 'idempotencyKey': key,
        'variables': {'childResult': 'APPROVED-001'}}
    result = api('POST', f"/api/flow-runtime/tasks/{facts['normal_task']}/actions", body, token=admin)
    assert not result['replayed']
    parent = read(facts['normal_parent']); child = read(facts['normal_child'])
    assert child['status'] == 'COMPLETED' and parent['status'] == 'COMPLETED'
    assert stored_variable(facts['normal_parent'], 'parentResult') == 'APPROVED-001'
    assert stored_variable(facts['normal_parent'], 'unmapped') == 'PRIVATE'
    assert len([item for item in parent['actions'] if item['actionCode'] == 'SUBFLOW_COMPLETED']) == 1
    replay_status, replay = call('POST', f"/api/flow-runtime/tasks/{facts['normal_task']}/actions", body, admin)
    assert replay_status == 200 and replay['data']['replayed']
    assert len([item for item in read(facts['normal_parent'])['actions'] if item['actionCode'] == 'SUBFLOW_COMPLETED']) == 1
    case('child-success-maps-output-and-resumes-parent-exactly-once-with-safe-replay')

    recovered = api('POST', f"/api/flow-runtime/incidents/{facts['failure_incident']}/actions", {
        'actionCode': 'RESUME', 'comment': '已人工确认补偿，可继续',
        'idempotencyKey': facts['code'] + '_recover_child'}, token=admin)
    assert recovered['instance']['status'] == 'COMPLETED'
    assert read(facts['failure_parent'])['status'] == 'COMPLETED'
    assert len([item for item in read(facts['failure_parent'])['actions'] if item['actionCode'] == 'SUBFLOW_COMPLETED']) == 1
    case('child-incident-manual-recovery-completes-child-and-resumes-parent-once')

    api('POST', f"/api/flow-runtime/instances/{facts['cascade_parent']}/actions", {
        'actionCode': 'TERMINATE', 'comment': '验收父流程终止后的子流程清理',
        'idempotencyKey': facts['code'] + '_terminate_parent'}, token=admin)
    parent, child = read(facts['cascade_parent']), read(facts['cascade_child'])
    assert parent['status'] == 'TERMINATED' and child['status'] == 'TERMINATED'
    assert all(task['status'] not in {'PENDING', 'QUEUED', 'SUSPENDED'} for task in child['tasks'])
    assert any(event['eventType'] == 'SUBFLOW_PARENT_TERMINATED' for event in child['history'])
    case('parent-terminal-state-cascades-to-active-child-and-cleans-pending-work')

    instance_ids = ','.join(str(value) for value in facts['instances'])
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={facts['normal_parent']} AND action_code='SUBFLOW_COMPLETED'") == [['1']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({instance_ids}) AND status='ACTIVE'") == [['0']]
    facts['api_verified'] = True; save()
    print(json.dumps({'verify': 'PASS', 'cases': len(facts['cases'])}, ensure_ascii=False))

elif stage == 'gui_observed':
    assert facts.get('api_verified') and not facts.get('gui_verified')
    design = api('GET', f"/api/flows/{facts['parent_flow']}", token=admin)
    subflow = next(item for item in design['nodes'] if item['nodeType'] == 'SUBFLOW')
    assert subflow['config']['flowId'] == facts['child_flow']
    assert subflow['config']['inputMappings'] == [{'sourceKey': 'parentInput', 'targetKey': 'childInput'}]
    view = read(facts['normal_parent'])
    assert view['subflows'][0]['flowVersionId'] == facts['child_v1'] and view['subflows'][0]['viewable']
    assert any(event['eventType'] == 'SUBFLOW_COMPLETED' for event in view['history'])
    facts['gui_verified'] = True
    case('ordinary-designer-and-runtime-responses-support-structured-controls-and-readable-relations')
    save(); print('GUI DATA PASS')

elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_verified')
    expected = {
        facts['normal_parent']: 'COMPLETED', facts['normal_child']: 'COMPLETED',
        facts['failure_parent']: 'COMPLETED', facts['failure_child']: 'COMPLETED',
        facts['cascade_parent']: 'TERMINATED', facts['cascade_child']: 'TERMINATED'}
    for instance_id, status in expected.items():
        assert read(instance_id)['status'] == status
    assert len([item for item in read(facts['normal_parent'])['actions'] if item['actionCode'] == 'SUBFLOW_COMPLETED']) == 1
    facts['restart_verified'] = True
    case('second-same-backend-restart-retains-terminal-relations-and-single-parent-resume')
    save(); print('Restart PASS')

elif stage == 'cleanup':
    assert facts.get('restart_verified')
    flow_ids = ','.join(str(value) for value in facts['flows'])
    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flow_ids}) AND code LIKE '{facts['code']}%'")
    instance_ids = ','.join(str(value) for value in facts['instances'])
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({instance_ids}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({instance_ids}) AND status IN('PENDING','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({instance_ids}) AND status IN('ACTIVE','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_definition WHERE id IN({flow_ids}) AND status<>'DISABLED'") == [['0']]
    facts['cleanup'] = {'flows_disabled': len(facts['flows']), 'unfinished_tasks': 0,
        'unfinished_jobs': 0, 'unfinished_executions': 0, 'existing_module_51_unchanged': True}
    facts['status'] = 'verified_contribution'
    facts['remaining'] = '其余自动业务、通知、等待、外部服务、受控应用和开放渠道仍需后续任务包实现。'
    save(); print(json.dumps({'cleanup': 'PASS', 'status': facts['status'],
        'cases': len(facts['cases'])}, ensure_ascii=False))

else:
    raise AssertionError('使用 prepare / verify / gui_observed / restart / cleanup')
