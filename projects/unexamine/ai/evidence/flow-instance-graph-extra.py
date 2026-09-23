"""复用本包活跃内存身份的追加验收：顺序审批、冻结分支及人员失效。不得单独启动或重新登录。"""
assert facts.get('api_verified') and not facts.get('extra_verified')
other = facts['api_member']
fid, nodes, edges = make('动态顺序审批与原始冻结分支', allow=True)
branch = copy.deepcopy(nodes[1]); branch['nodeKey'] = 'branch'; branch['name'] = '原始条件命中后的核对'
nodes.insert(-1, branch)
edges = [edges[0], {'edgeKey': 'conditional', 'sourceNodeKey': 'review', 'targetNodeKey': 'branch', 'priorityOrder': 0,
    'config': {'condition': {'mode': 'ALL', 'conditions': [{'field': 'score', 'operator': 'GT', 'value': 0}]}}},
    {'edgeKey': 'default', 'sourceNodeKey': 'review', 'targetNodeKey': 'end', 'priorityOrder': 1, 'config': {}},
    {'edgeKey': 'branch-end', 'sourceNodeKey': 'branch', 'targetNodeKey': 'end', 'priorityOrder': 0, 'config': {}}]
draft(fid, nodes, edges); publish(fid)
iid = start(fid, '新增顺序节点仍执行冻结条件')
body = request(iid, mode='SEQUENTIAL', people=[entry['tenantMemberId'], other], name='动态顺序补充核对')
result = add(iid, body)
dynamic = next(n['nodeKey'] for n in result['instance']['definitionSnapshot']['nodes'] if n['name'] == body['name'])
approve(iid)
value = read(iid)
tasks = [task for task in value['tasks'] if task['nodeKey'] == dynamic]
assert sorted(task['status'] for task in tasks) == ['PENDING', 'QUEUED']
assert next(task for task in tasks if task['status'] == 'QUEUED')['dueAt'] is None
approve(iid, fields={'score': 2})
assert read(iid)['status'] == 'WAITING'
approve(iid, other_token)
assert read(iid)['currentNodeKey'] == 'branch', '新增节点后的冻结条件必须按新候选值命中原路径'
approve(iid)
assert read(iid)['status'] == 'COMPLETED'
action = next(action for action in facts['actions'] if action['flowInstanceId'] == iid)
value = delivery(action); assert value['businessStatus'] == 'EXECUTED'
facts['records'].append(value['recordId']); save()
record = api('GET', f"/api/runtime/modules/{code}/records/{value['recordId']}", token=admin)
assert record['fields']['score'] == 2 and record['fields']['enabled'] is False
case('dynamic_sequential_real_candidate_patch_queued_activation_frozen_original_conditional_path_once_formal_write', instance_id=iid)
fid, _, _ = make('新增审批人员执行前失效', allow=True)
iid = start(fid, '人员失效保留异常')
add(iid, request(iid, people=[other]))
sql(f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE id={other} AND tenant_id=57 AND status='ACTIVE'")
try:
    approve(iid)
    value = read(iid)
    assert value['status'] == 'EXCEPTION' and value['errorCode'] == 'FLOW_RUNTIME_ASSIGNEE_UNRESOLVED'
    assert len(value['tasks']) == 1, '失效人员不得建立有效处理任务'
    action = next(action for action in facts['actions'] if action['flowInstanceId'] == iid)
    pending = api('GET', f"/api/runtime/modules/{code}/records/action-results/{action['idempotencyRecordId']}", token=admin)
    assert pending['businessStatus'] == 'PENDING_APPROVAL'
finally:
    sql(f"UPDATE sys_tenant_member SET status='ACTIVE',version=version+1 WHERE id={other} AND tenant_id=57 AND status='DISABLED'")
case('dynamic_assignee_currently_disabled_before_execution_exception_no_task_no_formal_write', instance_id=iid)
facts['extra_verified'] = True; save()
print(json.dumps({'extra_verified': True, 'cases': len(facts['cases'])}))
