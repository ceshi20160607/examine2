"""B09.1b.4 填写节点实际验收；仅使用隔离元数据、现有服务和共享接口辅助函数。"""
import ast
import copy
import json
import re
import secrets
import sys
import time
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
for name, selected in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
                       ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'record', 'flow',
                        'current_task', 'handle', 'intent_action', 'delivery', 'start_update'}),
                       ('flow-node-create-acceptance.py', {'terminal'})]:
    tree = ast.parse((AREA / name).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in selected], type_ignores=[]), name, 'exec'))
PATH = AREA / 'flow-form-task-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.1b.4', 'status': 'running', 'code': 'form_task_' + secrets.token_hex(5), 'fields': {}, 'cases': []}
assert facts['status'] == 'running', '已封存事实不得重复写入'
j = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', j).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', j).group(1)})
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, login['accessToken'])
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    facts['cases'].append({'case': name, **values}); save()


def count():
    return int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={int(facts['module_id'])} AND archived=0 AND deleted=0")[0][0])


def grant():
    permissions = copy.deepcopy(original['permissions'])
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': a, 'dataScopeType': 'ALL'}
        for a in ['LIST', 'DETAIL', 'CREATE', 'UPDATE']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': a, 'dataScopeType': 'ALL'}
        for a in ['VIEW', 'DESIGN', 'PUBLISH', 'SIMULATE', 'VIEW_RUNTIME', 'HANDLE']]
    policies = copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode': code, 'fieldCode': f, 'channel': c, 'readable': f != 'hidden',
        'writable': f != 'hidden', 'maskStrategy': None} for f in facts['fields'] for c in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def bind(fid, trigger='CREATE', custom=None):
    return api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': trigger,
        'actionId': custom, 'flowId': fid, 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True, 'replaceExisting': True}, admin)


def save_graph(fid, nodes, edges):
    current = api('GET', f'/api/flows/{fid}', token=admin)
    return api('PUT', f'/api/flows/{fid}/draft', {'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, admin)


def start(subject):
    return api('POST', f'/api/runtime/modules/{code}/records', {'title': subject, 'status': 'ACTIVE',
        'participantMemberIds': [], 'fields': {'subject': subject, 'score': 10, 'enabled': True, 'choices': ['one']}},
        admin, action_key='form_task_' + secrets.token_hex(6))


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    if 'golden_baseline' not in facts:
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN (151,154,170) ORDER BY id'); save()
    if 'module_id' not in facts:
        module = api('POST', '/api/admin/module-config/modules', {'name': '填写节点业务验收', 'code': code,
            'groupName': '填写节点隔离验收'}, admin)['module']
        facts['module_id'] = module['id']; save()
    for i, (f, label, kind) in enumerate([('subject','填写主题','TEXT'),('score','填写数量','NUMBER'),
        ('enabled','是否启用','BOOLEAN'),('choices','业务选项','MULTI_SELECT'),('person','填写人员','MEMBER'),('hidden','内部备注','TEXT')]):
        if f in facts['fields']: continue
        facts['fields'][f] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/fields", {
            'code': f, 'name': label, 'fieldType': kind, 'sortOrder': i, 'required': False,
            'dictionaryId': 11 if f == 'choices' else None, 'config': {}}, admin); save()
    publish_module(facts['module_id']); grant()
    if 'gui_flow' not in facts:
        value = api('POST', '/api/flows', {'code': code + '_gui', 'name': '普通设计者填写后再审批'}, admin)
        facts['gui_flow'] = value['id']; save()
        nodes = [{'nodeKey': key, 'nodeType': kind, 'name': label, 'positionX': i * 280, 'positionY': 80,
            'config': {'moduleId': facts['module_id']} if kind == 'START' else {'approvalMode':'OR_SIGN'} if kind == 'APPROVAL' else {},
            'assigneePolicy': {'type':'PERSON','tenantMemberIds':[108]} if kind == 'APPROVAL' else {}}
            for i,(key,kind,label) in enumerate([('start','START','开始'),('confirm','APPROVAL','确认填写结果'),('end','END','结束')])]
        save_graph(value['id'], nodes, [])
    facts['prepared'] = True; case('isolated_metadata_and_ordinary_designer_authorized', module_id=facts['module_id'], flow_id=facts['gui_flow'])
    print(json.dumps({'module_id': facts['module_id'], 'code': code, 'gui_flow': facts['gui_flow'], 'formal_count': count()}, ensure_ascii=False))
elif stage == 'designer':
    value = api('GET', f"/api/flows/{facts['gui_flow']}", token=admin)
    assert value['status'] == 'PUBLISHED' and value['currentVersionId']
    form = next(n for n in value['nodes'] if n['nodeType'] == 'FORM_TASK')
    assert len(value['nodes']) == 4 and len(value['edges']) == 3
    assert form['assigneePolicy']['tenantMemberIds'] == [108]
    assert set(form['formPolicy']['editable']) == {'subject','score','enabled','choices'} and form['formPolicy']['required'] == ['subject']
    facts['form_node'] = form['nodeKey']; facts['graph'] = value; save(); bind(facts['gui_flow'])
    case('ordinary_gui_created_structured_form_node_saved_reentered_published', node_key=form['nodeKey'])
    print(json.dumps({'form_node': form['nodeKey'], 'formal_count': count()}, ensure_ascii=False))
elif stage == 'gui_capture':
    assert not facts.get('gui_action')
    rows = sql("SELECT id,aggregate_id,HEX(payload_json) FROM core_event_outbox WHERE event_type='BUSINESS_ACTION_APPROVAL_REQUESTED' AND JSON_EXTRACT(payload_json,'$.action.moduleId')=" + str(facts['module_id']) + ' ORDER BY id')
    assert len(rows) == 1 and count() == 0
    oid, aid, raw = rows[0]; payload = json.loads(bytes.fromhex(raw).decode())
    facts['gui_action'] = {'outboxId':int(oid),'idempotencyRecordId':int(aid),'flowInstanceId':payload['flowInstanceId']}
    facts['gui_original'] = payload['action']; save()
    task = current_task(facts['gui_action'])
    assert task['taskType'] == 'FORM_TASK' and task['nodeKey'] == facts['form_node'] and payload['action']['actor']['accountId'] == 69
    case('ordinary_gui_original_create_stops_at_real_form_task', instance_id=payload['flowInstanceId'], task_id=task['id'], formal_count=0)
    print(json.dumps({'instance_id':payload['flowInstanceId'],'task_id':task['id'],'formal_count':0}))
elif stage == 'gui_submitted':
    value = flow(facts['gui_action'])
    assert value['currentNodeKey'] == 'confirm' and count() == 0
    fields = value['businessView']['submitted']['fields']
    assert fields['subject'] == '普通填写节点补充的内容' and fields['score'] == 0 and fields['enabled'] is False and fields['choices'] == ['one','two']
    assert value['businessView']['current'] is None and intent_action(facts['gui_action']) == facts['gui_original']
    case('ordinary_gui_form_submission_snapshot_only_then_real_approval_pending', formal_count=0)
    print(json.dumps({'next_node':'confirm','formal_count':0}))
elif stage == 'gui_final':
    action = facts['gui_action']; value = terminal(action)
    assert value['status'] == 'EXECUTED' and count() == 1 and flow(action)['status'] == 'COMPLETED'
    rid = value['value']['id']; fields = record(rid)['fields']
    assert fields['subject'] == '普通填写节点补充的内容' and fields['score'] == 0 and fields['enabled'] is False
    facts['gui_record'] = rid; case('ordinary_original_identity_create_after_form_and_approval_once', record_id=rid, formal_count=1)
    print(json.dumps({'record_id':rid,'formal_count':1}))
elif stage == 'api':
    assert facts.get('gui_record') and not facts.get('api_verified')
    graph = facts['graph']
    value = api('GET', f"/api/flows/{facts['api_flow']}", token=admin) if facts.get('api_flow') else api('POST', '/api/flows', {'code':code+'_api','name':'填写任务真实校验与重复'}, admin)
    facts['api_flow'] = value['id']; save()
    nodes = copy.deepcopy(graph['nodes']); edges = graph['edges']
    for n in nodes:
        if n['nodeType'] in ['FORM_TASK','APPROVAL']: n['assigneePolicy'] = {'type':'PERSON','tenantMemberIds':[entry['tenantMemberId']]}
    for name in ['missing_person','missing_editable','all_sign','timeout_auto_approve']:
        invalid = copy.deepcopy(nodes); form = next(n for n in invalid if n['nodeType'] == 'FORM_TASK')
        if name == 'missing_person': form['assigneePolicy'] = {}
        elif name == 'missing_editable': form['formPolicy']['editable'] = []
        elif name == 'all_sign': form['config']['approvalMode'] = 'ALL_SIGN'
        else: form['timeoutPolicy'] = {'timeoutMinutes':1,'action':'AUTO_APPROVE'}
        save_graph(value['id'], invalid, edges)
        check = api('GET', f"/api/flows/{value['id']}/publication-check", token=admin)
        expected = {'missing_person':'APPROVER_MISSING','missing_editable':'FORM_EDITABLE_MISSING','all_sign':'FORM_TASK_MODE_NOT_AVAILABLE','timeout_auto_approve':'FORM_TASK_TIMEOUT_APPROVE_NOT_AVAILABLE'}[name]
        assert not check['valid'] and expected in {i['code'] for i in check['issues']}, (name,check)
        status, result = call('POST', f"/api/flows/{value['id']}/publish", {'expectedDraftRevision':check['draftRevision'],'simulationVariables':{},'changeSummary':'拒绝无效填写配置验收'}, admin)
        assert status == 422; case('publish_reject_'+name, status=status, issues=[i['code'] for i in check['issues']])
    save_graph(value['id'], nodes, edges)
    check = api('GET', f"/api/flows/{value['id']}/publication-check", token=admin); assert check['valid'],check['issues']
    api('POST', f"/api/flows/{value['id']}/publish", {'expectedDraftRevision':check['draftRevision'],'simulationVariables':{},'changeSummary':'填写节点真实组合验收'}, admin)
    bind(value['id']); action = start('接口填写申请'); task = current_task(action); facts['api_action'] = action; save()
    assert task['taskType'] == 'FORM_TASK' and count() == 1
    for invalid in [{'subject':None},{'hidden':'不能写入隐藏字段'}]:
        status,result = call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode':'APPROVE','idempotencyKey':'invalid_form_'+secrets.token_hex(5),'fieldValues':invalid}, admin)
        assert status in [403,422] and current_task(action)['id'] == task['id'] and count() == 1
        case('pending_form_rejects_invalid_fields', status=status, error=result.get('code'))
    key = 'repeat_form_' + secrets.token_hex(5)
    first = handle(task, {'subject':'接口真实填写','score':0,'enabled':False,'choices':['one','two']},key=key)
    repeated = handle(task, {'subject':'接口真实填写','score':0,'enabled':False,'choices':['one','two']},key=key)
    assert first['actionId'] == repeated['actionId'] and current_task(action)['nodeKey'] == 'confirm' and count() == 1
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE task_id={task['id']} AND action_code='APPROVE'") == [['1']]
    facts['api_replay_action_id'] = first['actionId']; save()
    handle(current_task(action)); result = delivery(action); assert result['businessStatus'] == 'EXECUTED' and count() == 2
    facts['api_record'] = result['recordId']; save()
    print(json.dumps({'positive_verified':True,'formal_count':2,'next_phase':'api_finish'}))
elif stage == 'api_finish':
    assert facts.get('api_action') and not facts.get('api_verified')
    result = delivery(facts['api_action']); assert result['businessStatus'] == 'EXECUTED' and count() == 2
    facts['api_record'] = result['recordId']; save()
    fields = record(result['recordId'])['fields']
    assert fields['subject'] == '接口真实填写' and fields['score'] == 0 and fields['enabled'] is False and fields['choices'] == ['one','two']
    task = next(t for t in flow(facts['api_action'])['tasks'] if t['taskType'] == 'FORM_TASK')
    rows = sql(f"SELECT id FROM flow_action WHERE task_id={task['id']} AND action_code='APPROVE'")
    assert len(rows) == 1
    facts['api_replay_action_id'] = int(rows[0][0]); save()
    rejected = start('填写后拒绝不生效'); handle(current_task(rejected),{'subject':'被拒绝的填写内容'})
    handle(current_task(rejected),action='REJECT'); assert delivery(rejected)['businessStatus'] == 'REJECTED' and count() == 2
    case('form_required_hidden_duplicate_final_once_and_later_rejection', formal_count=2, replay_action_id=facts['api_replay_action_id'])
    facts['api_verified'] = True; save(); print(json.dumps({'formal_count':2,'required_hidden_replay_reject':True}))
elif stage == 'integrated':
    assert facts.get('api_verified') and not facts.get('integrated_verified')
    bind(facts['api_flow'],'UPDATE'); action = start_update(facts['api_record'],'编辑动作进入填写节点')
    before = record(facts['api_record']); assert current_task(action)['taskType'] == 'FORM_TASK'
    handle(current_task(action),{'subject':'填写节点修改编辑意图','score':0}); assert record(facts['api_record'])['version'] == before['version']
    handle(current_task(action)); result = delivery(action); assert result['businessStatus'] == 'EXECUTED'
    after = record(facts['api_record']); assert after['version'] == before['version']+1 and after['fields']['subject'] == '填写节点修改编辑意图'
    custom = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/actions", {'name':'填写节点字段动作','location':'ROW','parameterFieldCodes':['subject','score'],'confirmation':False}, admin)
    publish_module(facts['module_id']); bind(facts['api_flow'],'MANUAL',custom['id'])
    action = api('POST',f"/api/runtime/modules/{code}/records/{after['id']}/actions/{custom['code']}",{'version':after['version'],'values':{'subject':'原字段动作','score':10}},admin,action_key='form_custom_'+secrets.token_hex(5))
    assert current_task(action)['taskType'] == 'FORM_TASK'
    context = api('GET',f"/api/flow-runtime/tasks/{current_task(action)['id']}/form",token=admin)
    assert {f['code'] for f in context['form']['configuration']['configuration']['fields']} == {'subject','score'}
    handle(current_task(action),{'subject':'填写后的字段动作','score':0}); handle(current_task(action)); result = delivery(action)
    assert result['businessStatus'] == 'EXECUTED' and record(after['id'])['version'] == after['version']+1 and count() == 2
    facts['integrated_verified'] = True; case('same_form_engine_update_custom_snapshot_only_and_once', formal_count=2)
    print(json.dumps({'update_custom_verified':True,'formal_count':2}))
elif stage == 'unbound':
    assert facts.get('integrated_verified') and not facts.get('unbound_verified')
    result = api('POST','/api/flow-runtime/instances',{'flowId':facts['api_flow'],'title':'未关联原动作的填写节点不可空完成',
        'businessSnapshot':{},'variables':{},'idempotencyKey':'unbound_form_'+secrets.token_hex(6)},admin)
    value = result['instance']; facts['unbound_instance'] = value['id']; save()
    task = next(t for t in value['tasks'] if t['status'] == 'PENDING')
    assert task['taskType'] == 'FORM_TASK'
    context = api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=admin)
    assert context['form'] is None and '不能完成填写' in context['message']
    status,error = call('POST',f"/api/flow-runtime/tasks/{task['id']}/actions",{'actionCode':'APPROVE','idempotencyKey':'cannot_skip_'+secrets.token_hex(5),'fieldValues':{}},admin)
    assert status == 422 and error['code'] == 'FLOW_NODE_FORM_NOT_AVAILABLE'
    current = api('GET',f"/api/flow-runtime/instances/{value['id']}",token=admin)
    assert current['status'] == 'WAITING' and current['tasks'][0]['status'] == 'PENDING' and count() == 2
    api('POST',f"/api/flow-runtime/instances/{value['id']}/actions",{'actionCode':'TERMINATE','comment':'隔离负向验收完成，不执行业务动作',
        'idempotencyKey':'terminate_unbound_'+secrets.token_hex(5)},admin)
    facts['unbound_verified'] = True; case('manual_form_without_original_business_intent_blocks_empty_completion',status=status,instance_id=value['id'])
    print(json.dumps({'unbound_not_completed':True,'status':status,'formal_count':2}))
elif stage == 'sequence_prepare':
    assert facts.get('unbound_verified') and not facts.get('sequence_action')
    graph = api('GET',f"/api/flows/{facts['api_flow']}",token=admin); nodes = graph['nodes']
    form = next(n for n in nodes if n['nodeType'] == 'FORM_TASK')
    form['config']['approvalMode'] = 'SEQUENTIAL'; form['assigneePolicy']['tenantMemberIds'] = [entry['tenantMemberId'],108]
    save_graph(graph['id'],nodes,graph['edges'])
    check = api('GET',f"/api/flows/{graph['id']}/publication-check",token=admin); assert check['valid'],check['issues']
    api('POST',f"/api/flows/{graph['id']}/publish",{'expectedDraftRevision':check['draftRevision'],'changeSummary':'两名真实人员顺序填写验收','simulationVariables':{}},admin)
    bind(graph['id']); action = start('两人顺序填写后再审批'); facts['sequence_action'] = action; save()
    first = current_task(action); assert first['taskType'] == 'FORM_TASK' and first['assigneeTenantMemberId'] == entry['tenantMemberId']
    handle(first,{'subject':'第一人填写的候选内容','score':12})
    second = current_task(action); assert second['taskType'] == 'FORM_TASK' and second['assigneeTenantMemberId'] == 108 and count() == 2
    case('sequential_form_first_person_only_activates_second_real_person',instance_id=action['flowInstanceId'],formal_count=2)
    print(json.dumps({'instance_id':action['flowInstanceId'],'second_person_pending':True,'formal_count':2}))
elif stage == 'sequence_final':
    assert facts.get('sequence_action') and not facts.get('sequence_verified')
    action = facts['sequence_action']; value = flow(action); assert value['currentNodeKey'] == 'confirm' and count() == 2
    submitted = value['businessView']['submitted']['fields']
    assert submitted['subject'] == '第二人确认后的填写内容' and submitted['score'] == 0
    tasks = [t for t in value['tasks'] if t['taskType'] == 'FORM_TASK']
    assert len(tasks) == 2 and all(t['status'] == 'APPROVE' for t in tasks)
    assert [t['assigneeTenantMemberId'] for t in tasks] == [entry['tenantMemberId'],108]
    original_actor = intent_action(action)['actor']['accountId']
    assert [r[0] for r in sql('SELECT acted_by_account_id FROM flow_action WHERE task_id IN (' + ','.join(str(t['id']) for t in tasks)
        + ") AND action_code='APPROVE' ORDER BY id")] == [str(original_actor), '69']
    handle(current_task(action)); result = delivery(action); assert result['businessStatus'] == 'EXECUTED' and count() == 3
    facts['sequence_record'] = result['recordId']; facts['sequence_verified'] = True
    case('sequential_form_second_ordinary_person_merges_then_final_original_actor_once',record_id=result['recordId'],formal_count=3)
    print(json.dumps({'sequence_verified':True,'formal_count':3}))
elif stage == 'cleanup':
    assert facts.get('integrated_verified')
    assert facts.get('unbound_verified')
    assert facts.get('sequence_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    role = next(r for r in api('GET','/api/admin/system/authorization',token=admin)['roles'] if r['id'] == 114)
    assert role['permissions'] == original['permissions'] and role['fieldPolicies'] == original['fieldPolicies']
    for rid in [facts['gui_record'],facts['api_record'],facts['sequence_record']]:
        current = record(rid)
        api('POST',f'/api/runtime/modules/{code}/records/{rid}/archive',{'version':current['version'],'reason':'隔离填写节点验收完成'},admin)
    mid = facts['module_id']; fids = ','.join(map(str,[facts['gui_flow'],facts['api_flow']]))
    sql(f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND status='ACTIVE'")
    sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN ({fids}) AND system_id=54 AND owner_tenant_id=57")
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND owner_tenant_id=57")
    sql(f"UPDATE cfg_module_group g SET status='DISABLED',version=version+1 WHERE g.id IN (SELECT group_id FROM cfg_module WHERE id={mid}) AND g.system_id=54 AND g.owner_tenant_id=57 AND NOT EXISTS(SELECT 1 FROM cfg_module m WHERE m.group_id=g.id AND m.id<>{mid})")
    assert count() == 0 and sql(f"SELECT COUNT(*) FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id WHERE i.flow_id IN ({fids}) AND t.status IN ('PENDING','QUEUED')") == [['0']]
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN (151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND deleted=0 AND archived=0') == [['4']]
    facts['cleanup_verified'] = True; case('isolated_form_tasks_cleanup_role_and_golden_restored')
    print(json.dumps({'role_restored':True,'formal_count':0,'golden_unchanged':True}))
else:
    raise AssertionError('未知验收阶段')
