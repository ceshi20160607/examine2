"""从已修复的数值分支准确断点继续，复用活跃测试身份，不重复先前会签和分支。"""
facts = json.loads(PATH.read_text(encoding='utf-8'))
assert facts.get('api_verified') and not facts.get('extra_verified')
record_id = facts['records'][-1]
record = api('GET', f'/api/runtime/modules/{code}/records/{record_id}', token=admin)
assert record['fields']['score'] == 2 and record['fields']['enabled'] is False
extra = (AREA / 'flow-instance-graph-extra.py').read_text(encoding='utf-8')
exec(extra[extra.index("fid, _, _ = make('新增审批人员执行前失效'"):])
fid, nodes, edges = make('数值协议修复后的同人自动条件回归', allow=True)
later = copy.deepcopy(nodes[1]); later['nodeKey'] = 'later'; later['name'] = '同人后续冻结数值核对'
later['config']['repeatApproval'] = {'enabled': True, 'sourceNodeKeys': ['review'], 'formChangeRule': 'UNCHANGED',
    'condition': {'mode': 'ALL', 'conditions': [{'field': 'score', 'operator': 'GT', 'value': 0}]}}
nodes.insert(-1, later)
edges = [edges[0], {'edgeKey': 'review-later', 'sourceNodeKey': 'review', 'targetNodeKey': 'later', 'priorityOrder': 0, 'config': {}},
    {'edgeKey': 'later-end', 'sourceNodeKey': 'later', 'targetNodeKey': 'end', 'priorityOrder': 0, 'config': {}}]
draft(fid, nodes, edges); publish(fid)
iid = start(fid, '内部数值改变后真实人工事实仍可自动核验')
approve(iid, fields={'score': 3})
value = read(iid)
assert value['status'] == 'COMPLETED' and len(value['tasks']) == 2
assert len([action for action in value['actions'] if action['actionCode'] == 'APPROVE']) == 1
assert any('按冻结规则自动通过' in event['eventName'] and event['actorName'] == '系统自动处理' for event in value['history'])
action = next(action for action in facts['actions'] if action['flowInstanceId'] == iid)
value = delivery(action); assert value['businessStatus'] == 'EXECUTED'
facts['records'].append(value['recordId']); save()
record = api('GET', f"/api/runtime/modules/{code}/records/{value['recordId']}", token=admin)
assert record['fields']['score'] == 3 and record['fields']['enabled'] is False
case('changed_shared_numeric_storage_repeat_approval_typed_condition_genuine_human_current_fact_regression', instance_id=iid)
print(json.dumps({'final_extra_verified': True}))
