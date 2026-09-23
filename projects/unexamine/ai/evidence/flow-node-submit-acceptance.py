"""B09.1b.2 隔离记录实际验收；复用服务，凭证只在内存，不修改既有业务样本。"""
import ast
import copy
import hashlib
import json
import re
import secrets
import sys
import time
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {'call', 'api', 'sql'}], type_ignores=[]), 'helpers', 'exec'))
PATH = AREA / 'flow-node-submit-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.1b.2', 'status': 'running', 'code': 'node_submit_' + secrets.token_hex(5), 'fields': {}, 'cases': []}
assert facts['status'] == 'running', '已封存事实不能重复修改'
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


def publish_module(mid):
    check = api('GET', f'/api/admin/module-config/modules/{mid}/publication-check', token=admin)
    assert check['valid'], check['issues']
    return api('POST', f'/api/admin/module-config/modules/{mid}/publish', {'expectedDraftRevision': check['draftRevision']}, admin)


def publish_role(permissions, policies):
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    role = api('POST', '/api/admin/system/authorization/roles', {'id': 114, 'code': current['code'], 'name': current['name'],
        'description': current['description'], 'expectedVersion': current['version'], 'permissions': permissions, 'fieldPolicies': policies}, admin)
    api('POST', '/api/admin/system/authorization/roles/114/publish', {'expectedVersion': role['version'], 'reason': '隔离节点字段提交验收'}, admin)


def grant_role(update=True):
    permissions = copy.deepcopy(original['permissions'])
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['LIST', 'DETAIL', 'CREATE'] + (['UPDATE'] if update else []) + [facts['custom']['code']]]
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code + '_target', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['LIST', 'DETAIL']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['VIEW_RUNTIME', 'HANDLE']]
    policies = copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode': code, 'fieldCode': field, 'channel': channel,
        'readable': field != 'hidden' and not (field == 'flow_denied' and channel == 'FLOW'),
        'writable': field != 'hidden' and field != 'locked' and not (field == 'flow_denied' and channel == 'FLOW'),
        'maskStrategy': 'FULL' if field == 'credential' else None} for field in facts['fields'] for channel in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def make_flow(label, member):
    flow = api('POST', '/api/flows', {'code': code + '_' + secrets.token_hex(3), 'name': label}, admin)
    editable = [f for f in facts['fields'] if f not in ['hidden', 'locked']]
    nodes = [{'nodeKey': key, 'nodeType': kind, 'name': name, 'positionX': i * 220, 'positionY': 80,
        'config': {'moduleId': facts['module_id']} if kind == 'START' else {'approvalMode': 'OR_SIGN'} if kind == 'APPROVAL' else {},
        'assigneePolicy': {'type': 'PERSON', 'tenantMemberIds': [member]} if kind == 'APPROVAL' else {},
        'formPolicy': {'editable': editable, 'required': ['subject'], 'hidden': ['hidden']} if kind == 'APPROVAL' else {}}
        for i, (key, kind, name) in enumerate([('start','START','开始'),('review','APPROVAL','修改节点表单'),
            ('confirm','APPROVAL','确认待生效内容'),('end','END','结束')])]
    edges = [{'edgeKey': 'e' + str(i), 'sourceNodeKey': source, 'targetNodeKey': target, 'priorityOrder': 0, 'config': {}}
        for i, (source, target) in enumerate([('start','review'),('review','confirm'),('confirm','end')])]
    flow = api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': flow['version'], 'nodes': nodes, 'edges': edges}, admin)
    check = api('GET', f"/api/flows/{flow['id']}/publication-check", token=admin); assert check['valid'], check['issues']
    api('POST', f"/api/flows/{flow['id']}/publish", {'expectedDraftRevision': flow['draftRevision'],
        'changeSummary': '隔离原业务动作节点修改及二次确认', 'simulationVariables': {}}, admin)
    return flow['id']


def bind(flow_id, custom=False):
    return api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'],
        'actionId': facts['custom']['id'] if custom else None, 'triggerEvent': 'MANUAL' if custom else 'UPDATE',
        'flowId': flow_id, 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True, 'replaceExisting': True}, admin)


def record(record_id):
    return api('GET', f'/api/runtime/modules/{code}/records/{record_id}', token=admin)


def start_update(record_id, subject, token=None):
    current = record(record_id)
    return api('PUT', f'/api/runtime/modules/{code}/records/{record_id}', {'title': current['title'], 'recordNumber': current['recordNumber'],
        'status': current['status'], 'ownerMemberId': current['ownerMemberId'], 'departmentId': current['departmentId'],
        'participantMemberIds': current['participantMemberIds'], 'fields': {k:v for k,v in current['fields'].items()
            if k not in ['locked','credential'] and (token is None or k not in ['hidden','flow_denied'])} | {'subject': subject}, 'version': current['version']},
        token or admin, action_key='node_submit_' + secrets.token_hex(6))


def flow(action):
    return api('GET', f"/api/flow-runtime/instances/{action['flowInstanceId']}", token=admin)


def current_task(action):
    return next(t for t in flow(action)['tasks'] if t['status'] == 'PENDING')


def handle(task, fields=None, action='APPROVE', key=None):
    return api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': action,
        'comment': '隔离节点提交实际验收', 'idempotencyKey': key or 'node_handle_' + secrets.token_hex(6),
        'fieldValues': fields or {}}, admin)


def intent_action(action):
    rows = sql(f"SELECT HEX(payload_json) FROM core_event_outbox WHERE id={int(action['outboxId'])}")
    return json.loads(bytes.fromhex(rows[0][0]).decode())['action']


def delivery(action, token=None):
    for _ in range(15):
        result = api('GET', f"/api/runtime/modules/{code}/records/action-results/{action['idempotencyRecordId']}", token=token or admin)
        if result['businessStatus'] != 'PENDING_APPROVAL': return result
        time.sleep(.3)
    raise AssertionError('业务审批终态尚未落定')


def at_noon(value):
    return datetime.fromisoformat(value).isoformat().startswith('2026-09-19T12:00:00')


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    if 'golden_baseline' not in facts:
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN (151,154,170) ORDER BY id')
        save()
    if 'target_id' not in facts:
        existing = next((m for m in api('GET', '/api/admin/module-config', token=admin)['modules'] if m['code'] == code + '_target'), None)
        target = existing or api('POST', '/api/admin/module-config/modules', {'name': '节点关联来源验收', 'code': code + '_target', 'groupName': '审批提交验收'}, admin)['module']
        facts['target_id'] = target['id']; save()
    if 'target_record' not in facts:
        draft = api('GET', f"/api/admin/module-config/modules/{facts['target_id']}/draft", token=admin)
        if not draft['fields']:
            api('POST', f"/api/admin/module-config/modules/{facts['target_id']}/fields", {'code':'note','name':'关联事项说明','fieldType':'TEXT','required':False,'sortOrder':0,'config':{}}, admin)
        publish_module(facts['target_id'])
        facts['target_record'] = api('POST', f'/api/runtime/modules/{code}_target/records',
            {'title': '可选的关联事项', 'status': 'ACTIVE', 'participantMemberIds': [], 'fields': {'note':'真实可选关联内容'}}, admin)['id']; save()
    if 'cascade_id' not in facts:
        dictionary = api('POST', '/api/admin/dictionaries', {'name': '节点地区来源', 'code': code + '_regions', 'hierarchical': True}, admin)
        facts['cascade_id'] = dictionary['dictionary']['id']; save()
    if 'cascade_parent' not in facts:
        parent = api('POST', f"/api/admin/dictionaries/{facts['cascade_id']}/items", {'code':'region','label':'验收省份','sortOrder':0}, admin)
        facts['cascade_parent'] = parent['id']; save()
    if 'cascade_child' not in facts:
        child = api('POST', f"/api/admin/dictionaries/{facts['cascade_id']}/items", {'code':'city','label':'验收城市',
            'parentId': facts['cascade_parent'],'sortOrder':0}, admin); facts['cascade_child'] = child['id']
        checked = api('GET', f"/api/admin/dictionaries/{facts['cascade_id']}/publication-check", token=admin)
        api('POST', f"/api/admin/dictionaries/{facts['cascade_id']}/publish", {'expectedDraftRevision':checked['draftRevision']}, admin); save()
    if 'module_id' not in facts:
        module = api('POST', '/api/admin/module-config/modules', {'name': '审批节点字段提交验收', 'code': code, 'groupName': '审批提交验收'}, admin)
        facts['module_id'] = module['module']['id']; save()
    specs = [('subject','事项主题','TEXT',{}),('score','优先分值','NUMBER',{}),('enabled','是否紧急','BOOLEAN',{}),
        ('occurred','发生时间','DATETIME',{}),('day','业务日期','DATE',{}),('choices','事项标签','MULTI_SELECT',{}),
        ('credential','业务密钥','SECRET',{}),('person','关联成员','MEMBER',{}),('department','关联部门','DEPARTMENT',{}),
        ('related','关联事项','REFERENCE',{}),('city','地区','CASCADE',{'maxDepth':2,'allowIntermediate':False,'saveMode':'PATH'}),
        ('locked','系统维护内容','TEXT',{'readOnly':True}),('hidden','内部隐藏内容','TEXT',{}),('flow_denied','流程渠道禁止内容','TEXT',{})]
    for index,(field,label,kind,config) in enumerate(specs):
        if field in facts['fields']: continue
        facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/fields", {'code':field,'name':label,
            'fieldType':kind,'sortOrder':index,'required':False,'dictionaryId':11 if field=='choices' else facts['cascade_id'] if field=='city' else None,
            'referenceModuleId':facts['target_id'] if field=='related' else None,'config':config}, admin); save()
    if 'custom' not in facts:
        facts['custom'] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/actions", {'name':'节点参数业务动作',
            'location':'ROW','parameterFieldCodes':['subject','score'],'confirmation':False}, admin)
        publish_module(facts['module_id']); save()
    if 'records' not in facts:
        values = {'subject':'原正式内容','score':10,'enabled':True,'occurred':'2026-09-19T10:00:00','day':'2026-09-19',
            'choices':['one'],'credential':'synthetic-original-node-secret','person':101,'department':75,
            'related':facts['target_record'],'city':[facts['cascade_parent'],facts['cascade_child']],'hidden':'隔离内部值','flow_denied':'隔离渠道值'}
        facts['records'] = [api('POST', f'/api/runtime/modules/{code}/records', {'title':label,'status':'ACTIVE',
            'participantMemberIds':[],'fields':values}, admin)['id'] for label in ['普通页面节点写回验收','接口节点边界验收','原身份和版本冲突验收']]; save()
    grant_role()
    if 'gui_flow' not in facts: facts['gui_flow']=make_flow('普通审批节点修改及确认',108);save()
    if 'api_flow' not in facts: facts['api_flow']=make_flow('节点字段边界及原动作续跑',entry['tenantMemberId']);save()
    bind(facts['gui_flow'])
    if 'gui_action' not in facts:
        facts['gui_action']=start_update(facts['records'][0],'原申请待批准内容');facts['gui_original_intent']=intent_action(facts['gui_action']);save()
    facts['prepared']=True;case('isolated_module_real_options_two_stage_flow_and_ordinary_permissions',module_id=facts['module_id'],records=facts['records'],gui_instance=facts['gui_action']['flowInstanceId'])
    print(json.dumps({'module_id':facts['module_id'],'gui_instance':facts['gui_action']['flowInstanceId'],'code':code},ensure_ascii=False))
elif stage == 'gui_readback':
    action=facts['gui_action'];current=flow(action);formal=record(facts['records'][0])
    assert current['currentNodeKey']=='confirm' and current['status']=='WAITING'
    submitted=current['businessView']['submitted'];assert submitted['fields']['subject']=='审批人修改后的业务内容'
    assert current['businessView']['proposed']['fields']['subject']=='原申请待批准内容'
    assert formal['version']==0 and formal['fields']['subject']=='原正式内容'
    assert intent_action(action)==facts['gui_original_intent']
    case('ordinary_first_node_changes_waiting_second_node_original_and_formal_unchanged',instance_id=current['id'],formal_version=formal['version'])
    print(json.dumps({'instance_id':current['id'],'formal_version':formal['version'],'current_node':current['currentNodeKey']},ensure_ascii=False))
elif stage == 'gui_final':
    action=facts['gui_action'];current=flow(action);result=delivery(action);formal=record(facts['records'][0])
    assert current['status']=='COMPLETED' and result['businessStatus']=='EXECUTED',result
    assert formal['version']==1 and formal['fields']['subject']=='审批人修改后的业务内容',formal
    assert formal['fields']['score']==30 and formal['fields']['enabled'] is False
    assert set(formal['fields']['choices'])=={'one','two'}
    assert formal['fields']['occurred'].startswith('2026-09-19T10:00:00') and formal['fields']['day']=='2026-09-19'
    assert formal['fields']['person']==101 and formal['fields']['department']==75 and formal['fields']['related']==facts['target_record']
    assert intent_action(action)==facts['gui_original_intent']
    case('ordinary_second_node_final_original_action_executes_once',instance_id=current['id'],formal_version=formal['version'],business_status=result['businessStatus'])
    print(json.dumps({'formal_version':formal['version'],'business_status':result['businessStatus']},ensure_ascii=False))
elif stage == 'api':
    assert not facts.get('api_verified')
    bind(facts['api_flow']); grant_role()
    if 'api_action' not in facts:
        facts['api_action']=start_update(facts['records'][1],'接口原待审批内容');facts['api_original_intent']=intent_action(facts['api_action']);save()
    action=facts['api_action'];task=current_task(action);rid=facts['records'][1];before=record(rid)
    context=api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=admin)
    fields=context['form']['configuration']['configuration']['fields']
    assert 'hidden' not in [f['code'] for f in fields] and 'locked' not in [f['code'] for f in fields]
    assert 'credential' not in context['form']['record']['fields']
    failures=[]
    for values,extra,expected in [({'hidden':'伪造修改'}, {},403),({'locked':'伪造只读'}, {},403),
            ({'unknown':'伪造未知'}, {},403),({'subject':None},{},422),({'occurred':'bad-date'},{},422),
            ({'choices':['not_source_option']},{},422),({}, {'variables':{'subject':'变量旁路'}},422),
            ({'subject':'未批准修改'},{'actionCode':'REJECT'},422)]:
        status,response=call('POST',f"/api/flow-runtime/tasks/{task['id']}/actions",{'actionCode':'APPROVE',
            'comment':'无效补丁实际拒绝','idempotencyKey':'node_invalid_'+secrets.token_hex(4),'fieldValues':values,**extra},admin)
        assert status==expected,(status,response.get('code'));failures.append({'status':status,'code':response.get('code')})
    assert record(rid)['version']==before['version'] and current_task(action)['id']==task['id']
    assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={int(action['flowInstanceId'])} AND action_code='APPROVE'")==[['0']]
    case('hidden_readonly_unknown_required_invalid_date_dictionary_variables_and_reject_patch_atomic',checks=failures)
    patch={'subject':'类型补丁已批准','score':0,'enabled':False,'occurred':'2026-09-19T04:00:00Z','day':'2026-09-20',
        'choices':['two'],'credential':'synthetic-approved-node-secret','person':101,'department':75,
        'related':facts['target_record'],'city':[facts['cascade_parent'],facts['cascade_child']]}
    key='node_valid_'+secrets.token_hex(5);first=handle(task,patch,key=key)
    replay=handle(task,patch,key=key);assert replay['replayed'] and replay['actionId']==first['actionId']
    status,response=call('POST',f"/api/flow-runtime/tasks/{task['id']}/actions",{'actionCode':'APPROVE','comment':'隔离节点提交实际验收',
        'idempotencyKey':key,'fieldValues':patch|{'subject':'同键不同内容'}},admin)
    assert status==409 and response['code']=='FLOW_TASK_IDEMPOTENCY_CONFLICT'
    second=current_task(action);candidate=api('GET',f"/api/flow-runtime/tasks/{second['id']}/form",token=admin)['form']['record']['fields']
    assert candidate['subject']=='类型补丁已批准' and candidate['score']==0 and candidate['enabled'] is False
    assert at_noon(candidate['occurred']) and 'credential' not in candidate
    public=flow(action);assert public['businessView']['submitted']['fields']['credential']=='***'
    assert 'synthetic-approved-node-secret' not in json.dumps(public)
    # 实际存储及变量回读只能得到保护密文或非密钥值，不把合成密钥写进证据。
    stored=sql(f"SELECT HEX(input_json) FROM flow_action WHERE id={int(first['actionId'])}")
    assert 'synthetic-approved-node-secret' not in bytes.fromhex(stored[0][0]).decode()
    variables=sql(f"SELECT HEX(value_json) FROM flow_instance_variable WHERE instance_id={int(action['flowInstanceId'])}")
    assert all('synthetic-approved-node-secret' not in bytes.fromhex(r[0]).decode() for r in variables)
    assert record(rid)['version']==before['version'] and intent_action(action)==facts['api_original_intent']
    handle(second);result=delivery(action);assert result['businessStatus']=='EXECUTED',result
    saved=record(rid);assert saved['version']==before['version']+1 and saved['fields']['score']==0 and saved['fields']['enabled'] is False
    assert saved['fields']['subject']=='类型补丁已批准' and at_noon(saved['fields']['occurred'])
    assert saved['fields']['day']=='2026-09-20' and saved['fields']['choices']==['two']
    assert saved['fields']['hidden']=='隔离内部值' and saved['fields']['person']==101 and saved['fields']['related']==facts['target_record']
    assert intent_action(action)==facts['api_original_intent']
    credential_id=facts['fields']['credential']['id']
    assert sql(f"SELECT COUNT(*) FROM biz_record_value WHERE record_id={rid} AND field_id={credential_id} AND value_text NOT LIKE '%synthetic-approved-node-secret%'")==[['1']]
    case('typed_secret_protected_independent_snapshot_candidate_final_original_identity_once_and_replay',
        instance_id=action['flowInstanceId'],formal_version=saved['version'],idempotency_diff_status=status)
    rejected=start_update(rid,'拒绝不得生效的原申请');old=record(rid)
    handle(current_task(rejected),{'subject':'第一节点已批准但整体将拒绝'})
    handle(current_task(rejected),action='REJECT');rejected_result=delivery(rejected)
    assert rejected_result['businessStatus']=='REJECTED' and record(rid)['version']==old['version'] and record(rid)['fields']==old['fields']
    case('later_reject_keeps_approved_node_snapshot_without_any_business_write',instance_id=rejected['flowInstanceId'],formal_version=old['version'])
    bind(facts['api_flow'],custom=True)
    old=record(rid)
    custom=api('POST',f"/api/runtime/modules/{code}/records/{rid}/actions/{facts['custom']['code']}",
        {'version':old['version'],'values':{'subject':'原动作参数','score':10}},admin,action_key='node_custom_'+secrets.token_hex(5))
    original_custom=intent_action(custom);task=current_task(custom)
    params=api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=admin)['form']['configuration']['configuration']['fields']
    assert {f['code'] for f in params}=={'subject','score'}
    status,response=call('POST',f"/api/flow-runtime/tasks/{task['id']}/actions",{'actionCode':'APPROVE','comment':'动作参数不能扩张',
        'idempotencyKey':'node_custom_bad_'+secrets.token_hex(4),'fieldValues':{'enabled':True}},admin);assert status==403
    handle(task,{'subject':'节点修改后的动作参数','score':45});handle(current_task(custom))
    assert delivery(custom)['businessStatus']=='EXECUTED'
    saved=record(rid);assert saved['version']==old['version']+1 and saved['fields']['subject']=='节点修改后的动作参数'
    assert saved['fields']['score']==45 and saved['fields']['enabled']==old['fields']['enabled']
    assert intent_action(custom)==original_custom
    facts['api_verified']=True;case('custom_field_action_only_original_parameters_merged_through_shared_executor',instance_id=custom['flowInstanceId'],formal_version=saved['version'])
    print(json.dumps({'api_cases':len(facts['cases']),'formal_version':saved['version'],'custom_status':'EXECUTED'},ensure_ascii=False))
elif stage == 'api_resume':
    assert not facts.get('api_verified')
    action=facts['api_action'];rid=facts['records'][1];before=record(rid);public=flow(action)
    assert public['currentNodeKey']=='confirm' and before['version']==0
    first_task=next(t for t in public['tasks'] if t['nodeKey']=='review' and t['status']=='APPROVE')
    rows=sql(f"SELECT id,HEX(input_json) FROM flow_action WHERE instance_id={int(action['flowInstanceId'])} AND task_id={first_task['id']} AND action_code='APPROVE'")
    assert len(rows)==1
    stored=bytes.fromhex(rows[0][1]).decode();assert 'synthetic-approved-node-secret' not in stored
    facts.setdefault('preparation_failures',[]).append('实际前8种无效提交拒绝、原任务同键回放和不同内容409已通过；随后脚本把合法12:00误要求12:00:00。已从实际confirm任务继续，只修正语义日期断言，不重复提交已批准补丁。')
    candidate=api('GET',f"/api/flow-runtime/tasks/{current_task(action)['id']}/form",token=admin)['form']['record']['fields']
    assert candidate['subject']=='类型补丁已批准' and candidate['score']==0 and candidate['enabled'] is False
    assert at_noon(candidate['occurred']) and 'credential' not in candidate
    assert public['businessView']['submitted']['fields']['credential']=='***'
    assert 'synthetic-approved-node-secret' not in json.dumps(public)
    variables=sql(f"SELECT HEX(value_json) FROM flow_instance_variable WHERE instance_id={int(action['flowInstanceId'])}")
    assert all('synthetic-approved-node-secret' not in bytes.fromhex(r[0]).decode() for r in variables)
    assert intent_action(action)==facts['api_original_intent'];save()
    handle(current_task(action));result=delivery(action);assert result['businessStatus']=='EXECUTED',result
    saved=record(rid);assert saved['version']==1 and saved['fields']['score']==0 and saved['fields']['enabled'] is False
    assert saved['fields']['subject']=='类型补丁已批准' and at_noon(saved['fields']['occurred'])
    assert saved['fields']['day']=='2026-09-20' and saved['fields']['choices']==['two']
    assert saved['fields']['hidden']=='隔离内部值' and saved['fields']['person']==101 and saved['fields']['related']==facts['target_record']
    assert saved['fields']['city']==[facts['cascade_parent'],facts['cascade_child']]
    assert intent_action(action)==facts['api_original_intent']
    credential_id=facts['fields']['credential']['id']
    assert sql(f"SELECT COUNT(*) FROM biz_record_value WHERE record_id={rid} AND field_id={credential_id} AND value_text NOT LIKE '%synthetic-approved-node-secret%'")==[['1']]
    case('typed_secret_protected_independent_snapshot_candidate_final_original_identity_once_and_replay',instance_id=action['flowInstanceId'],formal_version=saved['version'],idempotency_diff_status=409)
    rejected=start_update(rid,'拒绝不得生效的原申请');old=record(rid)
    handle(current_task(rejected),{'subject':'第一节点已批准但整体将拒绝'})
    handle(current_task(rejected),action='REJECT');rejected_result=delivery(rejected)
    assert rejected_result['businessStatus']=='REJECTED' and record(rid)['version']==old['version'] and record(rid)['fields']==old['fields']
    case('later_reject_keeps_approved_node_snapshot_without_any_business_write',instance_id=rejected['flowInstanceId'],formal_version=old['version'])
    bind(facts['api_flow'],custom=True);old=record(rid)
    custom=api('POST',f"/api/runtime/modules/{code}/records/{rid}/actions/{facts['custom']['code']}",
        {'version':old['version'],'values':{'subject':'原动作参数','score':10}},admin,action_key='node_custom_'+secrets.token_hex(5))
    facts['custom_action']=custom;facts['custom_original_intent']=intent_action(custom);save()
    task=current_task(custom)
    params=api('GET',f"/api/flow-runtime/tasks/{task['id']}/form",token=admin)['form']['configuration']['configuration']['fields']
    assert {f['code'] for f in params}=={'subject','score'}
    status,response=call('POST',f"/api/flow-runtime/tasks/{task['id']}/actions",{'actionCode':'APPROVE','comment':'动作参数不能扩张',
        'idempotencyKey':'node_custom_bad_'+secrets.token_hex(4),'fieldValues':{'enabled':True}},admin);assert status==403
    handle(task,{'subject':'节点修改后的动作参数','score':45});handle(current_task(custom))
    assert delivery(custom)['businessStatus']=='EXECUTED'
    saved=record(rid);assert saved['version']==old['version']+1 and saved['fields']['subject']=='节点修改后的动作参数'
    assert saved['fields']['score']==45 and saved['fields']['enabled']==old['fields']['enabled']
    assert intent_action(custom)==facts['custom_original_intent']
    facts['api_verified']=True;case('custom_field_action_only_original_parameters_merged_through_shared_executor',instance_id=custom['flowInstanceId'],formal_version=saved['version'])
    print(json.dumps({'api_cases':len(facts['cases']),'formal_version':saved['version'],'custom_status':'EXECUTED'},ensure_ascii=False))
elif stage == 'conflicts':
    assert facts.get('api_verified') and not facts.get('conflicts_verified')
    bind(facts['api_flow']);rid=facts['records'][2];grant_role()
    previous=facts.get('temporary_identity')
    if previous and not facts.get('temporary_identity_closed'):
        sql(f"UPDATE auth_session SET revoked=1,revoked_reason='隔离节点原身份验收收尾' WHERE account_id={previous['account_id']}")
        sql(f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={previous['account_id']}")
        sql(f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={previous['system_id']} AND creator_account_id={previous['account_id']}")
        facts.setdefault('closed_preparation_identities',[]).append(previous)
        facts.setdefault('preparation_failures',[]).append('首次准备误把账号数字ID用于account搜索，实际404 ACCOUNT_NOT_FOUND，无原动作创建；收尾SQL误用session.status，实际表使用revoked。按真实接口/字段修正，旧账号81及空系统83准确停用。');save()
    username='node_origin_'+secrets.token_hex(5)
    registration=api('POST','/api/auth/register',{'username':username,'password':secrets.token_urlsafe(24),
        'displayName':'节点原身份临时验收者','systemName':'节点原身份临时空系统'})
    identity={'account_id':registration['accountId'],'system_id':registration['systemId'],'username':username};facts['temporary_identity']=identity;facts['temporary_identity_closed']=False;save()
    try:
        member=api('POST','/api/admin/system/authorization/members',{'account':username,'roleIds':[114]},admin)
        actor=api('POST','/api/systems/54/tenants/57/enter',{},registration['platformTokens']['accessToken'])['tokens']['accessToken']
        before=record(rid);action=start_update(rid,'原发起人撤权不能借审批人写回',actor)
        facts['actor_conflict_action']=action;save()
        handle(current_task(action),{'subject':'审批人已批准的独立修改'})
        grant_role(update=False)
        handle(current_task(action));result=delivery(action,actor)
        assert result['businessStatus']=='CONFLICT' and result['errorCode']=='PERMISSION_DENIED',result
        assert record(rid)['version']==before['version'] and record(rid)['fields']==before['fields']
        case('current_original_actor_revoked_update_approval_cannot_borrow_handler_identity',instance_id=action['flowInstanceId'],status=result['businessStatus'],error_code=result['errorCode'],formal_version=before['version'])
    finally:
        grant_role()
        # 仅结束本阶段随机创建身份及空系统，保留实际动作、审批和审计记录。
        sql(f"UPDATE auth_session SET revoked=1,revoked_reason='隔离节点原身份验收收尾' WHERE account_id={identity['account_id']}")
        sql(f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE system_id=54 AND tenant_id=57 AND system_member_id IN (SELECT id FROM sys_member WHERE account_id={identity['account_id']} AND system_id=54)")
        sql(f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={identity['account_id']}")
        sql(f"UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={identity['system_id']} AND creator_account_id={identity['account_id']}")
        facts['temporary_identity_closed']=True;save()
    before=record(rid);action=start_update(rid,'原记录版本冲突不得覆盖新正式值')
    facts['record_conflict_action']=action;save()
    handle(current_task(action),{'subject':'旧审批待生效修改'})
    api('POST','/api/flow-bindings/publish',{'moduleId':facts['module_id'],'actionId':facts['custom']['id'],
        'triggerEvent':'MANUAL','executionMode':'DIRECT','priorityOrder':0,'condition':{'mode':'ALL','conditions':[]},
        'mutuallyExclusive':True,'replaceExisting':True},admin)
    changed=api('POST',f"/api/runtime/modules/{code}/records/{rid}/actions/{facts['custom']['code']}",
        {'version':before['version'],'values':{'subject':'其他正常动作先修改的正式内容','score':20}},admin,action_key='node_conflict_direct_'+secrets.token_hex(5))
    assert changed['mode']=='DIRECT' and changed['value']['version']==before['version']+1
    latest=record(rid);handle(current_task(action));result=delivery(action)
    assert result['businessStatus']=='CONFLICT' and result['errorCode']=='RECORD_VERSION_CONFLICT',result
    assert record(rid)['version']==latest['version'] and record(rid)['fields']==latest['fields']
    case('concurrent_record_version_conflict_retains_normal_action_new_formal_values',instance_id=action['flowInstanceId'],status=result['businessStatus'],error_code=result['errorCode'],formal_version=latest['version'])
    facts['conflicts_verified']=True;save()
    print(json.dumps({'conflict_cases':2,'original_actor_closed':facts['temporary_identity_closed'],'formal_version':latest['version']},ensure_ascii=False))
elif stage == 'conflicts_resume':
    assert facts.get('api_verified') and not facts.get('conflicts_verified')
    action=facts['actor_conflict_action'];rid=facts['records'][2]
    # 该实际流程已经批准及持久化拒绝执行，账号已收尾；核对真实终态，不重建身份或重复撤权测试。
    terminal=sql(f"SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.approvalStatus')),JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.errorCode')) FROM core_event_outbox WHERE id={int(action['outboxId'])}")
    assert terminal==[['CONFLICT','PERMISSION_DENIED']] and flow(action)['status']=='COMPLETED' and record(rid)['version']==0
    identity=facts['temporary_identity'];assert facts['temporary_identity_closed']
    assert sql(f"SELECT status FROM plat_account WHERE id={identity['account_id']}")==[['DISABLED']]
    facts.setdefault('preparation_failures',[]).append('撤权流程实际批准后原动作已经CONFLICT/PERMISSION_DENIED，formal175仍v0。脚本用Root读取别人的回执实际404 ACTION_RESULT_NOT_FOUND；账号82/空系统84已准确收尾，改为持久化终态交叉验证，不重复创建或撤权。后续原账号回执函数支持token参数。')
    case('current_original_actor_revoked_update_approval_cannot_borrow_handler_identity',instance_id=action['flowInstanceId'],status='CONFLICT',error_code='PERMISSION_DENIED',formal_version=0,verification='actual persisted terminal + completed flow + formal unchanged; non-owner receipt 404')
    before=record(rid);action=start_update(rid,'原记录版本冲突不得覆盖新正式值')
    facts['record_conflict_action']=action;save()
    handle(current_task(action),{'subject':'旧审批待生效修改'})
    api('POST','/api/flow-bindings/publish',{'moduleId':facts['module_id'],'actionId':facts['custom']['id'],
        'triggerEvent':'MANUAL','executionMode':'DIRECT','priorityOrder':0,'condition':{'mode':'ALL','conditions':[]},
        'mutuallyExclusive':True,'replaceExisting':True},admin)
    changed=api('POST',f"/api/runtime/modules/{code}/records/{rid}/actions/{facts['custom']['code']}",
        {'version':before['version'],'values':{'subject':'其他正常动作先修改的正式内容','score':20}},admin,action_key='node_conflict_direct_'+secrets.token_hex(5))
    assert changed['mode']=='DIRECT' and changed['value']['version']==before['version']+1
    latest=record(rid);handle(current_task(action));result=delivery(action)
    assert result['businessStatus']=='CONFLICT' and result['errorCode']=='RECORD_VERSION_CONFLICT',result
    assert record(rid)['version']==latest['version'] and record(rid)['fields']==latest['fields']
    case('concurrent_record_version_conflict_retains_normal_action_new_formal_values',instance_id=action['flowInstanceId'],status=result['businessStatus'],error_code=result['errorCode'],formal_version=latest['version'])
    facts['conflicts_verified']=True;save()
    print(json.dumps({'conflict_cases':2,'original_actor_closed':facts['temporary_identity_closed'],'formal_version':latest['version']},ensure_ascii=False))
elif stage == 'loss_prepare':
    assert facts.get('conflicts_verified') and not facts.get('loss_action')
    grant_role();bind(facts['gui_flow'])
    facts['loss_action']=start_update(facts['records'][0],'失权后不能提交缓存节点字段');save()
    print(json.dumps({'instance_id':facts['loss_action']['flowInstanceId'],'formal_version':record(facts['records'][0])['version']},ensure_ascii=False))
elif stage == 'loss':
    assert facts.get('loss_action') and not facts.get('loss_revoked')
    grant_role(update=False);facts['loss_revoked']=True;save()
    print(json.dumps({'handler_update_revoked':True},ensure_ascii=False))
elif stage == 'cleanup':
    assert facts.get('api_verified') and facts.get('conflicts_verified')
    if facts.get('loss_action'):
        action=facts['loss_action'];instance=flow(action)
        assert instance['status']=='WAITING' and record(facts['records'][0])['version']==1
        assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={int(instance['id'])} AND action_code='APPROVE'")==[['0']]
        api('POST',f"/api/flow-runtime/instances/{instance['id']}/actions",{'actionCode':'TERMINATE',
            'comment':'缓存表单失权实际拒绝，结束隔离验收申请','idempotencyKey':'node_loss_close_'+str(instance['id'])},admin)
        assert delivery(action)['businessStatus']=='TERMINATED'
        case('ordinary_cached_form_submit_after_update_revocation_403_clears_old_context_no_write',instance_id=instance['id'],formal_version=1)
    # 恢复完整原角色，不带入验收模块或流程权限；保留不可变动作和审计。
    publish_role(original['permissions'],original['fieldPolicies'])
    current=next(r for r in api('GET','/api/admin/system/authorization',token=admin)['roles'] if r['id']==114)
    assert current['permissions']==original['permissions'] and current['fieldPolicies']==original['fieldPolicies']
    facts['original_role_restored']=True
    for rid in facts['records']:
        current=record(rid)
        if not current['archived']:api('POST',f'/api/runtime/modules/{code}/records/{rid}/archive',{'version':current['version'],'reason':'隔离节点提交验收完成'},admin)
    target=api('GET',f"/api/runtime/modules/{code}_target/records/{facts['target_record']}",token=admin)
    if not target['archived']:api('POST',f"/api/runtime/modules/{code}_target/records/{facts['target_record']}/archive",{'version':target['version'],'reason':'隔离节点关联来源验收完成'},admin)
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN (151,154,170) ORDER BY id')==facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0 AND deleted=0')==[['4']]
    # 只停用本次隔离模板、模块和活动绑定，不改既有配置或重建表。
    mids=[facts['module_id'],facts['target_id']];fids=[facts['gui_flow'],facts['api_flow']]
    sql('UPDATE flow_trigger_binding SET status=\'DISABLED\',version=version+1 WHERE module_id='+str(facts['module_id'])+" AND status='ACTIVE'")
    sql('UPDATE flow_definition SET status=\'DISABLED\',version=version+1 WHERE id IN ('+','.join(map(str,fids))+') AND owner_tenant_id=57 AND system_id=54')
    sql('UPDATE cfg_module SET status=\'DISABLED\',version=version+1 WHERE id IN ('+','.join(map(str,mids))+') AND owner_tenant_id=57 AND system_id=54')
    assert sql('SELECT COUNT(*) FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id WHERE i.flow_id IN ('+','.join(map(str,fids))+') AND t.status IN (\'PENDING\',\'QUEUED\')')==[['0']]
    facts['isolated_cleanup_verified']=True;case('isolated_records_archived_templates_bindings_disabled_role_exact_restored_existing_source_formals_unchanged')
    print(json.dumps({'role_restored':True,'isolated_archived_records':4,'active_fixture_tasks':0,'module51_formal_count':4},ensure_ascii=False))
else:
    raise AssertionError('未知验收阶段')
