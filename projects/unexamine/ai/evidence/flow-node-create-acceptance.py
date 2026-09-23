"""B09.1b.3 新建审批实际验收：隔离元数据/记录，复用服务及既有定向辅助函数。"""
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
                       ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'make_flow', 'record', 'start_update',
                        'flow', 'current_task', 'handle', 'intent_action', 'delivery'})]:
    tree = ast.parse((AREA / name).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in selected], type_ignores=[]), name, 'exec'))
PATH = AREA / 'flow-node-create-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.1b.3', 'status': 'running', 'code': 'node_create_' + secrets.token_hex(5), 'fields': {}, 'cases': []}
assert facts['status'] == 'running'
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


def grant_role(create=True):
    permissions = copy.deepcopy(original['permissions'])
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['LIST', 'DETAIL', 'UPDATE'] + (['CREATE'] if create else [])]
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code + '_target', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['LIST', 'DETAIL']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['VIEW_RUNTIME', 'HANDLE']]
    policies = copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode': code, 'fieldCode': field, 'channel': channel,
        'readable': field != 'hidden' and not (field == 'flow_denied' and channel == 'FLOW'),
        'writable': field not in ['hidden', 'locked'] and not (field == 'flow_denied' and channel == 'FLOW'),
        'maskStrategy': 'FULL' if field == 'credential' else None} for field in facts['fields'] for channel in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def bind(fid):
    return api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'CREATE',
        'flowId': fid, 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True, 'replaceExisting': True}, admin)


def count():
    return int(sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={int(facts['module_id'])} AND deleted=0 AND archived=0")[0][0])


def start_create(subject):
    values = {'subject': subject, 'score': 10, 'enabled': True, 'occurred': '2026-09-19T10:00:00',
        'day': '2026-09-19', 'choices': ['one'], 'credential': 'synthetic-create-original-secret', 'person': 101,
        'department': 75, 'related': facts['target_record'], 'city': facts['city'], 'hidden': '隔离原隐藏内容'}
    return api('POST', f'/api/runtime/modules/{code}/records', {'title': subject, 'status': 'ACTIVE',
        'participantMemberIds': [], 'fields': values}, admin, action_key='node_create_' + secrets.token_hex(6))


def terminal(action):
    # Root不能读取别人的动作回执；只读真实持久化状态交叉核对GUI发起人的结果。
    row = sql(f"SELECT HEX(response_json) FROM core_idempotency_record WHERE id={int(action['idempotencyRecordId'])}")
    return json.loads(bytes.fromhex(row[0][0]).decode('utf-8'))


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    if 'golden_baseline' not in facts:
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN (151,154,170) ORDER BY id'); save()
    if 'target_id' not in facts:
        target = api('POST', '/api/admin/module-config/modules', {'name': '新建审批关联来源', 'code': code + '_target', 'groupName': '新建审批验收'}, admin)['module']
        facts['target_id'] = target['id']; save()
    if 'target_record' not in facts:
        draft = api('GET', f"/api/admin/module-config/modules/{facts['target_id']}/draft", token=admin)
        if not draft['fields']:
            api('POST', f"/api/admin/module-config/modules/{facts['target_id']}/fields", {'code': 'note', 'name': '关联说明',
                'fieldType': 'TEXT', 'sortOrder': 0, 'required': False, 'config': {}}, admin)
        publish_module(facts['target_id'])
        facts['target_record'] = api('POST', f'/api/runtime/modules/{code}_target/records',
            {'title': '新建节点可选关联内容', 'status': 'ACTIVE', 'fields': {'note': '仅供隔离验收'}, 'participantMemberIds': []}, admin)['id']; save()
    old = json.loads((AREA / 'flow-node-submit-facts.json').read_text(encoding='utf-8'))
    facts['city'] = [old['cascade_parent'], old['cascade_child']]
    if 'module_id' not in facts:
        module = api('POST', '/api/admin/module-config/modules', {'name': '新建审批字段验收', 'code': code, 'groupName': '新建审批验收'}, admin)['module']
        facts['module_id'] = module['id']; save()
    for index, (field, source) in enumerate(old['fields'].items()):
        if field in facts['fields']: continue
        config = json.loads(source.get('configJson') or '{}')
        facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/fields", {'code': field,
            'name': source['name'], 'fieldType': source['fieldType'], 'sortOrder': index, 'required': False, 'config': config,
            'dictionaryId': source.get('dictionaryId'), 'referenceModuleId': facts['target_id'] if field == 'related' else None}, admin); save()
    publish_module(facts['module_id']); grant_role()
    if 'gui_flow' not in facts: facts['gui_flow'] = make_flow('新建审批节点修改及确认', 108); save()
    if 'api_flow' not in facts: facts['api_flow'] = make_flow('新建审批类型补丁验收', entry['tenantMemberId']); save()
    bind(facts['gui_flow']); assert count() == 0
    facts['prepared'] = True; case('isolated_create_binding_real_options_no_formal_record', module_id=facts['module_id'], formal_count=0)
    print(json.dumps({'module_id': facts['module_id'], 'code': code, 'gui_flow': facts['gui_flow'], 'formal_count': 0}, ensure_ascii=False))
elif stage == 'gui_capture':
    assert not facts.get('gui_action')
    rows = sql("SELECT id,aggregate_id,HEX(payload_json) FROM core_event_outbox WHERE event_type='BUSINESS_ACTION_APPROVAL_REQUESTED' AND JSON_EXTRACT(payload_json,'$.action.moduleId')=" + str(facts['module_id']) + ' ORDER BY id')
    assert len(rows) == 1 and count() == 0
    oid, aid, raw = rows[0]; payload = json.loads(bytes.fromhex(raw).decode('utf-8'))
    assert payload['action']['actor']['accountId'] == 69 and payload['action']['recordId'] is None
    facts['gui_action'] = {'outboxId': int(oid), 'idempotencyRecordId': int(aid), 'flowInstanceId': payload['flowInstanceId']}
    facts['gui_original_intent'] = payload['action']; save()
    case('ordinary_gui_create_starts_approval_without_formal_row', instance_id=payload['flowInstanceId'], formal_count=0)
    print(json.dumps({'instance_id': payload['flowInstanceId'], 'formal_count': 0}))
elif stage == 'gui_readback':
    action = facts['gui_action']; instance = flow(action)
    assert instance['currentNodeKey'] == 'confirm' and count() == 0
    assert instance['businessView']['submitted']['fields']['subject'] == '新建审批人补充后的内容'
    assert instance['businessView']['current'] is None and intent_action(action) == facts['gui_original_intent']
    case('ordinary_create_first_node_submission_no_formal_and_original_unchanged', instance_id=instance['id'])
    print(json.dumps({'instance_id': instance['id'], 'formal_count': 0}))
elif stage == 'gui_final':
    action = facts['gui_action']; result = terminal(action)
    assert flow(action)['status'] == 'COMPLETED' and result['status'] == 'EXECUTED' and count() == 1
    rid = result['value']['id']; value = record(rid)
    assert value['fields']['subject'] == '新建审批人补充后的内容' and value['fields']['score'] == 0 and value['fields']['enabled'] is False
    assert intent_action(action) == facts['gui_original_intent']
    facts['gui_record'] = rid; case('ordinary_create_final_original_identity_exactly_one_formal_row', record_id=rid, formal_count=1)
    print(json.dumps({'record_id': rid, 'formal_count': 1}))
elif stage == 'api':
    assert facts.get('gui_record') and not facts.get('api_verified')
    bind(facts['api_flow']); before = count()
    action = start_create('类型接口新建申请'); original_intent = intent_action(action); task = current_task(action)
    facts['api_action'] = action; save()
    assert count() == before
    context = api('GET', f"/api/flow-runtime/tasks/{task['id']}/form", token=admin)
    assert context['recordId'] is None and context['form']['record']['id'] is None
    fields = {f['code'] for f in context['form']['configuration']['configuration']['fields']}
    assert 'hidden' not in fields and 'locked' not in fields and 'credential' in fields
    assert 'credential' not in context['form']['record']['fields']
    for field, suffix in [('choices', 'dictionary'), ('city', 'dictionary'), ('related', 'reference-options'),
                          ('person', 'organization-options'), ('department', 'organization-options')]:
        assert api('GET', f"/api/flow-runtime/tasks/{task['id']}/form/fields/{field}/{suffix}", token=admin)
    invalid = [{'hidden': '不能伪造'}, {'subject': None}]
    for values in invalid:
        status, body = call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': 'APPROVE',
            'comment': '新建非法补丁', 'fieldValues': values, 'idempotencyKey': secrets.token_hex(8)}, admin)
        assert status in [403, 422] and current_task(action)['id'] == task['id'] and count() == before, (status, body)
    patch = {'subject': '类型接口节点批准内容', 'score': 0, 'enabled': False, 'occurred': '2026-09-19T04:00:00Z',
        'day': '2026-09-20', 'choices': ['two'], 'credential': 'synthetic-create-replacement-secret', 'person': 101,
        'department': 75, 'related': facts['target_record'], 'city': facts['city']}
    key = 'node_create_typed_' + secrets.token_hex(5)
    handle(task, patch, key=key); handle(task, patch, key=key)
    assert count() == before and flow(action)['businessView']['current'] is None
    stored = sql(f"SELECT HEX(input_json) FROM flow_action WHERE task_id={int(task['id'])} AND action_code='APPROVE'")
    assert len(stored) == 1 and 'synthetic-create-replacement-secret' not in bytes.fromhex(stored[0][0]).decode('utf-8')
    following = current_task(action); candidate = api('GET', f"/api/flow-runtime/tasks/{following['id']}/form", token=admin)
    assert candidate['form']['record']['fields']['score'] == 0 and candidate['form']['record']['fields']['enabled'] is False
    handle(following); result = delivery(action); assert result['businessStatus'] == 'EXECUTED' and count() == before + 1
    rid = terminal(action)['value']['id']; value = record(rid)
    assert value['fields']['subject'] == patch['subject'] and value['fields']['score'] == 0 and value['fields']['enabled'] is False
    assert value['fields']['day'] == '2026-09-20' and datetime.fromisoformat(value['fields']['occurred']).hour == 12
    assert value['fields']['person'] == 101 and value['fields']['department'] == 75 and value['fields']['related'] == facts['target_record']
    assert intent_action(action) == original_intent
    facts['api_record'] = rid; case('create_virtual_options_typed_protected_snapshot_replay_and_final_once', record_id=rid, formal_count=count())
    rejected = start_create('新建整体拒绝申请'); handle(current_task(rejected), {'subject': '仅第一节点批准内容'})
    handle(current_task(rejected), action='REJECT'); assert delivery(rejected)['businessStatus'] == 'REJECTED' and count() == before + 1
    case('create_later_reject_produces_no_formal_row', instance_id=rejected['flowInstanceId'], formal_count=count())
    facts['api_verified'] = True; save(); print(json.dumps({'formal_count': count(), 'reject_without_record': True}))
elif stage == 'withdraw_prepare':
    assert facts.get('api_verified') and not facts.get('withdraw_action')
    bind(facts['gui_flow']); print(json.dumps({'ordinary_create_binding_ready': True, 'formal_count': count()}))
elif stage == 'withdraw_capture':
    assert not facts.get('withdraw_action')
    rows = sql("SELECT id,aggregate_id,HEX(payload_json) FROM core_event_outbox WHERE event_type='BUSINESS_ACTION_APPROVAL_REQUESTED' AND JSON_EXTRACT(payload_json,'$.action.moduleId')="
        + str(facts['module_id']) + " AND JSON_EXTRACT(payload_json,'$.action.actor.accountId')=69 ORDER BY id")
    assert len(rows) == 2 and count() == 2
    oid, aid, raw = rows[-1]; payload = json.loads(bytes.fromhex(raw).decode('utf-8'))
    facts['withdraw_action'] = {'outboxId': int(oid), 'idempotencyRecordId': int(aid), 'flowInstanceId': payload['flowInstanceId']}
    facts['withdraw_intent'] = payload['action']; save()
    print(json.dumps({'instance_id': payload['flowInstanceId'], 'formal_count': 2}))
elif stage == 'withdraw_revoke':
    assert flow(facts['withdraw_action'])['currentNodeKey'] == 'confirm' and count() == 2
    grant_role(create=False); facts['original_create_revoked'] = True; save()
    print(json.dumps({'original_create_revoked': True, 'handler_update_retained': True}))
elif stage == 'withdraw_final':
    action = facts['withdraw_action']; result = terminal(action)
    payload = json.loads(bytes.fromhex(sql(f"SELECT HEX(payload_json) FROM core_event_outbox WHERE id={action['outboxId']}")[0][0]).decode('utf-8'))
    assert flow(action)['status'] == 'COMPLETED' and result['status'] == 'CONFLICT' and payload['errorCode'] == 'PERMISSION_DENIED'
    assert count() == 2 and intent_action(action) == facts['withdraw_intent']
    facts['withdraw_verified'] = True; case('original_create_permission_revoked_final_approval_no_formal_record',
        instance_id=action['flowInstanceId'], status=result['status'], error_code=payload['errorCode'], formal_count=2)
    print(json.dumps({'business_status': result['status'], 'error_code': payload['errorCode'], 'formal_count': 2}))
elif stage == 'integrated':
    assert facts.get('withdraw_verified') and not facts.get('integrated_verified')
    value = record(facts['api_record']); fields = value['fields']
    assert fields['choices'] == ['two'] and fields['city'] == facts['city']
    cid = facts['fields']['credential']['id']
    assert sql(f"SELECT COUNT(*) FROM biz_record_value WHERE record_id={value['id']} AND field_id={cid} AND value_text NOT LIKE '%synthetic-create-replacement-secret%'") == [['1']]
    assert flow(facts['api_action'])['businessView']['submitted']['fields']['credential'] == '***'
    # 共同任务选项实现发生变更，因此只定向验证受影响的既有UPDATE和CUSTOM路径。
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'UPDATE',
        'flowId': facts['api_flow'], 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True, 'replaceExisting': True}, admin)
    action = start_update(value['id'], '共用来源目录的编辑原意图'); task = current_task(action)
    for field, suffix in [('choices', 'dictionary'), ('related', 'reference-options'), ('person', 'organization-options')]:
        assert api('GET', f"/api/flow-runtime/tasks/{task['id']}/form/fields/{field}/{suffix}", token=admin)
    handle(task, {'subject': '共用来源目录的编辑节点修改'}); handle(current_task(action))
    assert delivery(action)['businessStatus'] == 'EXECUTED'
    assert record(value['id'])['version'] == value['version'] + 1 and count() == 2
    custom = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/actions", {'name': '共用节点来源字段动作',
        'location': 'ROW', 'parameterFieldCodes': ['subject', 'score', 'choices', 'related', 'person'], 'confirmation': False}, admin)
    publish_module(facts['module_id'])
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'MANUAL', 'actionId': custom['id'],
        'flowId': facts['api_flow'], 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True, 'replaceExisting': True}, admin)
    current = record(value['id'])
    action = api('POST', f"/api/runtime/modules/{code}/records/{value['id']}/actions/{custom['code']}",
        {'version': current['version'], 'values': {'subject': '原字段动作内容', 'score': 0, 'choices': ['two'],
         'related': facts['target_record'], 'person': 101}}, admin, action_key='node_create_custom_' + secrets.token_hex(5))
    task = current_task(action)
    form = api('GET', f"/api/flow-runtime/tasks/{task['id']}/form", token=admin)
    assert {f['code'] for f in form['form']['configuration']['configuration']['fields']} == {'subject', 'score', 'choices', 'related', 'person'}
    for field, suffix in [('choices', 'dictionary'), ('related', 'reference-options'), ('person', 'organization-options')]:
        assert api('GET', f"/api/flow-runtime/tasks/{task['id']}/form/fields/{field}/{suffix}", token=admin)
    handle(task, {'subject': '共用来源目录的动作节点修改'}); handle(current_task(action))
    assert delivery(action)['businessStatus'] == 'EXECUTED' and count() == 2
    assert record(value['id'])['version'] == current['version'] + 1
    facts['integrated_verified'] = True; case('create_secret_final_storage_and_changed_common_update_custom_task_options_integrated',
        record_id=value['id'], version=record(value['id'])['version'], formal_count=2)
    print(json.dumps({'changed_shared_update_custom_verified': True, 'formal_count': 2, 'plaintext_printed': False}))
elif stage == 'defaults':
    assert facts.get('integrated_verified') and not facts.get('defaults_verified')
    draft = api('GET', f"/api/admin/module-config/modules/{facts['module_id']}/draft", token=admin)
    field = next(f for f in draft['fields'] if f['code'] == 'score')
    config = json.loads(field.get('configJson') or '{}'); config['defaultValue'] = 17
    api('PUT', f"/api/admin/module-config/modules/{facts['module_id']}/fields/{field['id']}",
        {'name': field['name'], 'required': False, 'sortOrder': field['sortOrder'], 'status': 'ACTIVE',
         'config': config, 'version': field['version']}, admin)
    publish_module(facts['module_id']); bind(facts['api_flow'])
    before = count()
    action = api('POST', f'/api/runtime/modules/{code}/records', {'title': '审批明确清空原默认值验收',
        'status': 'ACTIVE', 'participantMemberIds': [], 'fields': {'subject': '遗漏数字先使用原默认值'}}, admin,
        action_key='node_create_default_' + secrets.token_hex(5))
    task = current_task(action); form = api('GET', f"/api/flow-runtime/tasks/{task['id']}/form", token=admin)
    assert count() == before and form['form']['record']['fields']['score'] == 17
    assert not json.loads(next(f for f in form['form']['configuration']['configuration']['fields'] if f['code'] == 'score')['configJson']).get('defaultValue')
    handle(task, {'score': None}); following = current_task(action)
    form = api('GET', f"/api/flow-runtime/tasks/{following['id']}/form", token=admin)
    assert form['form']['record']['fields']['score'] is None and count() == before
    handle(following); assert delivery(action)['businessStatus'] == 'EXECUTED' and count() == before + 1
    rid = terminal(action)['value']['id']; value = record(rid)
    assert 'score' not in value['fields'] or value['fields']['score'] is None
    facts['default_record'] = rid; facts['defaults_verified'] = True
    case('create_omitted_uses_frozen_original_default_node_explicit_null_remains_cleared_on_final_save', record_id=rid,
        original_default=17, final_field_empty=True, formal_count=count())
    print(json.dumps({'default_candidate': 17, 'explicit_null_not_replaced': True, 'formal_count': count()}))
elif stage == 'cleanup':
    assert facts.get('api_verified')
    assert facts.get('withdraw_verified')
    assert facts.get('integrated_verified')
    assert facts.get('defaults_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    assert current['permissions'] == original['permissions'] and current['fieldPolicies'] == original['fieldPolicies']
    for rid in [facts['gui_record'], facts['api_record'], facts['default_record']]:
        value = record(rid)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': value['version'], 'reason': '隔离新建审批验收完成'}, admin)
    target = api('GET', f"/api/runtime/modules/{code}_target/records/{facts['target_record']}", token=admin)
    api('POST', f"/api/runtime/modules/{code}_target/records/{facts['target_record']}/archive", {'version': target['version'], 'reason': '隔离关联来源验收完成'}, admin)
    sql(f"UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={facts['module_id']} AND status='ACTIVE'")
    sql("UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN (" + ','.join(map(str, [facts['gui_flow'], facts['api_flow']])) + ') AND system_id=54 AND owner_tenant_id=57')
    sql("UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id IN (" + ','.join(map(str, [facts['module_id'], facts['target_id']])) + ') AND system_id=54 AND owner_tenant_id=57')
    # 本次通过新建分组模式创建了两个隔离组；仅停用没有其他模块使用的空验收组。
    sql("UPDATE cfg_module_group g SET status='DISABLED',version=version+1 WHERE g.system_id=54 AND g.owner_tenant_id=57"
        + " AND g.id IN (SELECT group_id FROM cfg_module WHERE id IN (" + ','.join(map(str, [facts['module_id'], facts['target_id']]))
        + ")) AND NOT EXISTS (SELECT 1 FROM cfg_module m WHERE m.group_id=g.id AND m.id NOT IN ("
        + ','.join(map(str, [facts['module_id'], facts['target_id']])) + '))')
    assert sql('SELECT COUNT(*) FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id WHERE i.flow_id IN ('
        + ','.join(map(str, [facts['gui_flow'], facts['api_flow']])) + ") AND t.status IN ('PENDING','QUEUED')") == [['0']]
    assert count() == 0 and sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN (151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND deleted=0 AND archived=0') == [['4']]
    old = api('GET', '/api/flow-runtime/instances/81', token=admin)
    assert old['businessView']['current']['id'] == 170 and old['businessView']['current']['fields']['score'] == 30
    facts['cleanup_verified'] = True; case('create_isolated_cleanup_original_role_and_golden_baselines_restored')
    print(json.dumps({'role_restored': True, 'isolated_formal_count': 0, 'golden_source_unchanged': True}))
else:
    raise AssertionError('未知验收阶段')
