"""B08.1a 真实条件链验收：按检查点继续，凭证只驻留内存，复用现有本地服务。"""
import ast
import copy
import json
import re
import sys
import time
import hashlib
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {'call', 'api', 'sql'}], type_ignores=[]), 'helpers', 'exec'))
FACTS = AREA / 'flow-condition-facts.json'
facts = json.loads(FACTS.read_text(encoding='utf-8')) if FACTS.exists() else {
    'step': 'B08.1a', 'status': 'running', 'module_code': 'flow_condition_accept_20260918', 'fields': {}, 'cases': []}
assert facts['status'] == 'running', '已封存事实不重复验收'


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    FACTS.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    facts['cases'].append({'case': name, **values})
    save()


def publish_module():
    checked = api('GET', f"/api/admin/module-config/modules/{facts['module_id']}/publication-check", token=admin)
    assert checked['valid'], checked['issues']
    return api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/publish",
        {'expectedDraftRevision': checked['draftRevision']}, admin)


def node(key, kind, config=None, assignee=None):
    return {'nodeKey': key, 'nodeType': kind, 'name': key, 'positionX': 100, 'positionY': 100,
        'config': config or {}, 'assigneePolicy': assignee or {}}


def edge(key, source, target, priority=0, condition=None, legacy=None):
    return {'edgeKey': key, 'sourceNodeKey': source, 'targetNodeKey': target, 'priorityOrder': priority,
        'config': {} if condition is None else {'condition': condition}, 'conditionExpression': legacy}


def sample(**changes):
    return {'subject': '真实业务条件验收', 'score': 10, 'enabled': True, 'occurred': '2026-09-18T10:00:00',
        'day': '2026-09-18', 'choices': ['two', 'one'], 'choice': 'one', 'date_text': '2026-09-18T02:00:00Z', **changes}


def condition():
    return {'mode': 'ALL', 'conditions': [
        {'field': 'score', 'operator': 'GTE', 'value': 10},
        {'field': 'occurred', 'operator': 'EQ', 'value': '2026-09-18T02:00:00Z'},
        {'field': 'choices', 'operator': 'EQ', 'value': ['one', 'two']},
        {'field': 'date_text', 'operator': 'NE', 'value': '2026-09-18T10:00:00'},
        {'mode': 'ANY', 'conditions': [{'field': 'enabled', 'operator': 'EQ', 'value': True},
            {'field': 'day', 'operator': 'LT', 'value': '2026-09-17'}]},
        {'field': 'choice', 'operator': 'IN', 'value': ['one']} ]}


def snapshot_hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def instance(action):
    return api('GET', f"/api/flow-runtime/instances/{action['flowInstanceId']}", token=admin)


def delivery(action):
    return api('GET', f"/api/runtime/modules/{code}/records/action-results/{action['idempotencyRecordId']}", token=admin)


def update_record(values):
    current = api('GET', f"/api/runtime/modules/{code}/records/{facts['record']['id']}", token=admin)
    return api('PUT', f"/api/runtime/modules/{code}/records/{current['id']}", {'title': current['title'], 'status': current['status'],
        'participantMemberIds': [], 'fields': values, 'version': current['version']}, admin)


def dictionary_item_status(status):
    draft = api('GET', f"/api/admin/dictionaries/{facts['dictionary_id']}", token=admin)
    item = next(item for item in draft['items'] if item['code'] == facts.get('dictionary_guard_item', {}).get('code', 'one'))
    if item['status'] != status:
        api('PUT', f"/api/admin/dictionaries/{facts['dictionary_id']}/items/{item['id']}",
            {k: item.get(k) for k in ['parentId', 'label', 'color', 'sortOrder', 'version']} | {'status': status}, admin)
    checked = api('GET', f"/api/admin/dictionaries/{facts['dictionary_id']}/publication-check", token=admin)
    return api('POST', f"/api/admin/dictionaries/{facts['dictionary_id']}/publish", {'expectedDraftRevision': checked['draftRevision']}, admin)


def field_status(status):
    draft = api('GET', f"/api/admin/module-config/modules/{facts['module_id']}/draft", token=admin)
    field = next(field for field in draft['fields'] if field['code'] == facts.get('field_guard_field', {}).get('code', 'occurred'))
    if field['status'] != status:
        api('PUT', f"/api/admin/module-config/modules/{facts['module_id']}/fields/{field['id']}",
            {k: field.get(k) for k in ['name', 'required', 'uniqueValue', 'searchable', 'dictionaryId', 'referenceModuleId', 'sortOrder', 'version']}
            | {'status': status, 'config': json.loads(field['configJson'])}, admin)
    return publish_module()


def choices_required(required):
    draft = api('GET', f"/api/admin/module-config/modules/{facts['module_id']}/draft", token=admin)
    field = next(field for field in draft['fields'] if field['code'] == 'choices')
    if field['required'] == required: return
    api('PUT', f"/api/admin/module-config/modules/{facts['module_id']}/fields/{field['id']}",
        {k: field.get(k) for k in ['name', 'uniqueValue', 'searchable', 'dictionaryId', 'referenceModuleId', 'sortOrder', 'version', 'status']}
        | {'required': required, 'config': json.loads(field['configJson'])}, admin)


java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, login['accessToken'])
admin = entry['tokens']['accessToken']
code = facts['module_code']
stage = sys.argv[1] if len(sys.argv) > 1 else 'prepare'

if stage == 'prepare':
    if 'dictionary_id' not in facts:
        dictionary = api('POST', '/api/admin/dictionaries', {'code': code + '_choices', 'name': '审批条件验收选项', 'hierarchical': False}, admin)
        facts['dictionary_id'] = dictionary['dictionary']['id']; save()
    if 'dictionary_items' not in facts:
        facts['dictionary_items'] = [api('POST', f"/api/admin/dictionaries/{facts['dictionary_id']}/items",
            {'code': value, 'label': label, 'sortOrder': index}, admin) for index, (value, label) in enumerate([('one', '普通事项'), ('two', '特殊事项')])]
        checked = api('GET', f"/api/admin/dictionaries/{facts['dictionary_id']}/publication-check", token=admin)
        facts['dictionary_publication'] = api('POST', f"/api/admin/dictionaries/{facts['dictionary_id']}/publish", {'expectedDraftRevision': checked['draftRevision']}, admin); save()
    if 'module_id' not in facts:
        draft = api('POST', '/api/admin/module-config/modules', {'name': '审批业务条件验收', 'code': code, 'groupName': '审批条件验收'}, admin)
        facts['module_id'] = draft['module']['id']; save()
    for index, (field, label, kind) in enumerate([('subject', '事项主题', 'TEXT'), ('score', '优先分值', 'NUMBER'),
            ('enabled', '是否紧急', 'BOOLEAN'), ('occurred', '发生时间', 'DATETIME'), ('day', '业务日期', 'DATE'),
            ('choices', '事项标签', 'MULTI_SELECT'), ('choice', '事项类别', 'SINGLE_SELECT'), ('date_text', '文本标识', 'TEXT'), ('hidden', '内部字段', 'TEXT')]):
        if field in facts['fields']: continue
        facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/fields", {
            'code': field, 'name': label, 'fieldType': kind, 'required': False, 'sortOrder': index,
            'dictionaryId': facts['dictionary_id'] if field in ['choices', 'choice'] else None, 'config': {}}, admin); save()
    if 'module_publication' not in facts: facts['module_publication'] = publish_module(); save()
    if 'record' not in facts:
        facts['record'] = api('POST', f'/api/runtime/modules/{code}/records', {'title': '正常审批业务样本', 'status': 'ACTIVE', 'participantMemberIds': [], 'fields': sample()}, admin); save()
    if 'role_baseline' not in facts:
        facts['role_baseline'] = next(role for role in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if role['id'] == 114); save()
    if not facts.get('role_extended'):
        role = facts['role_baseline']; permissions = copy.deepcopy(role['permissions']); policies = copy.deepcopy(role['fieldPolicies'])
        permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': action, 'dataScopeType': 'ALL'} for action in ['VIEW', 'DESIGN', 'SIMULATE', 'PUBLISH', 'BIND_VIEW', 'BIND_PUBLISH']]
        permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': action, 'dataScopeType': 'ALL'} for action in ['LIST', 'DETAIL', 'CREATE', 'UPDATE']]
        policies += [{'resourceCode': code, 'fieldCode': field, 'channel': channel, 'readable': field != 'hidden', 'writable': field != 'hidden'} for field in facts['fields'] for channel in ['PAGE', 'FLOW']]
        current = next(role for role in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if role['id'] == 114)
        draft = api('POST', '/api/admin/system/authorization/roles', {'id': 114, 'code': current['code'], 'name': current['name'],
            'description': current['description'], 'expectedVersion': current['version'], 'permissions': permissions, 'fieldPolicies': policies}, admin)
        api('POST', '/api/admin/system/authorization/roles/114/publish', {'expectedVersion': draft['version'], 'reason': '当前有权普通设计者真实条件链验收'}, admin)
        facts['role_extended'] = True; save()
    case('isolated_current_module_dictionary_record_and_ordinary_designer_role_prepared', module_id=facts['module_id'], record_id=facts['record']['id'])
elif stage == 'branch':
    if 'flow_id' not in facts:
        flow = api('POST', '/api/flows', {'name': '真实模块嵌套条件审批', 'code': code + '_flow'}, admin)
        facts['flow_id'] = flow['id']; save()
    flow = api('GET', f"/api/flows/{facts['flow_id']}", token=admin)
    if 'initial_flow_publication' not in facts:
        nodes = [node('start', 'START', {'moduleId': facts['module_id']}), node('gate', 'GATEWAY'),
            node('priority_review', 'APPROVAL', assignee={'type': 'PERSON', 'tenantMemberIds': [entry['tenantMemberId']]}),
            node('normal_review', 'APPROVAL', assignee={'type': 'PERSON', 'tenantMemberIds': [entry['tenantMemberId']]}), node('end', 'END')]
        edges = [edge('entry', 'start', 'gate'), edge('typed_priority', 'gate', 'priority_review', 0, condition()),
            edge('fallback', 'gate', 'normal_review', 10), edge('priority_end', 'priority_review', 'end'), edge('normal_end', 'normal_review', 'end')]
        flow = api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': flow['version'], 'nodes': nodes, 'edges': edges}, admin)
        checked = api('GET', f"/api/flows/{flow['id']}/publication-check", token=admin); assert checked['valid'], checked['issues']
        for name, values, expected in [('priority', sample(), 'priority_review'), ('numeric_fallback', sample(score=9), 'normal_review'),
                ('same_instant_NE', sample(occurred='2026-09-18T10:00:00+08:00'), 'priority_review'),
                ('multiselect_set', sample(choices=['one', 'two']), 'priority_review')]:
            result = api('POST', f"/api/flows/{flow['id']}/simulate", {'variables': values}, admin)
            assert result['successful'] and expected in [step['nodeKey'] for step in result['steps']], (name, result['issues'])
            case('real_typed_simulation_' + name, expected=expected)
        bad_status, bad = call('POST', f"/api/flows/{flow['id']}/simulate", {'variables': sample(occurred='bad-date')}, admin)
        assert bad_status == 422 and bad['code'] == 'CONDITION_RUNTIME_VALUE_INVALID'
        case('invalid_actual_datetime_not_false_NE_or_fallback', status=bad_status, error_code=bad['code'])
        facts['initial_flow_publication'] = api('POST', f"/api/flows/{flow['id']}/publish", {'expectedDraftRevision': flow['draftRevision'],
            'changeSummary': '真实业务对象与类型化嵌套条件', 'simulationVariables': sample()}, admin); save()
    if 'binding' not in facts:
        facts['binding'] = api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'CREATE',
            'flowId': facts['flow_id'], 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0, 'condition': {'mode': 'ALL', 'conditions': []},
            'mutuallyExclusive': True, 'replaceExisting': False}, admin); save()
    if 'priority_action' not in facts:
        facts['priority_action'] = api('POST', f'/api/runtime/modules/{code}/records', {'title': '实际高优先审批意图', 'status': 'ACTIVE',
            'participantMemberIds': [], 'fields': sample()}, admin, action_key=code + '_priority'); save()
    if 'normal_action' not in facts:
        facts['normal_action'] = api('POST', f'/api/runtime/modules/{code}/records', {'title': '实际普通审批意图', 'status': 'ACTIVE',
            'participantMemberIds': [], 'fields': sample(score=9)}, admin, action_key=code + '_normal'); save()
    print(json.dumps({'stage': stage, 'priority_action_keys': list(facts['priority_action']), 'normal_action_keys': list(facts['normal_action'])}))
elif stage == 'verify':
    if not facts.get('multi_runtime_verified'):
        current = api('GET', f"/api/runtime/modules/{code}/records/{facts['record']['id']}", token=admin)
        assert current['fields']['choices'] == ['two', 'one']
        assert current['fieldPresentation']['choices']['displayValue'] == '特殊事项、普通事项'
        rows = sql(f"SELECT value_type,value_json FROM biz_record_value WHERE record_id={current['id']} AND field_code='choices'")
        assert rows == [['MULTI_SELECT', '["two", "one"]']], rows
        options = api('GET', f"/api/runtime/modules/{code}/records/{current['id']}/fields/choices/dictionary", token=admin)
        assert options['dictionaryId'] == facts['dictionary_id'] and {i['code'] for i in options['items']} == {'one', 'two'}
        for bad in ['one', [1], ['one', 'missing']]:
            status, result = call('PUT', f"/api/runtime/modules/{code}/records/{current['id']}", {'title': current['title'], 'status': current['status'],
                'participantMemberIds': [], 'version': current['version'], 'fields': sample(choices=bad)}, admin)
            assert status == 422 and result['code'] == 'FIELD_VALIDATION_FAILED'
            assert api('GET', f"/api/runtime/modules/{code}/records/{current['id']}", token=admin)['version'] == current['version']
        changed = update_record(sample(choices=['one', 'two', 'one'], occurred='2026-09-18T02:00:00.123Z'))
        assert changed['fields']['choices'] == ['one', 'two'] and changed['fields']['occurred'] == '2026-09-18T10:00:00.123'
        cleared = update_record(sample(choices=[])); assert cleared['fields']['choices'] == [] and cleared['fieldPresentation']['choices']['displayValue'] == '未填写'
        facts['record'] = update_record(sample()); facts['multi_runtime_verified'] = True
        case('actual_multi_storage_display_edit_dictionary_type_current_option_dedup_clear_and_offset_ms_save', record_id=current['id'], storage_type=rows[0][0])
    if 'old_instance_snapshot' not in facts:
        first, normal = instance(facts['priority_action']), instance(facts['normal_action'])
        assert first['currentNodeKey'] == 'priority_review' and normal['currentNodeKey'] == 'normal_review'
        assert first['flowVersionId'] == normal['flowVersionId'] == facts['initial_flow_publication']['versionId']
        assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
        facts['old_instance_snapshot'] = {'id': first['id'], 'version_id': first['flowVersionId'], 'hash': snapshot_hash(first['definitionSnapshot'])}; save()
        case('real_business_intents_use_same_typed_priority_and_fallback_as_simulation_without_formal_create', instances=[first['id'], normal['id']])
    if 'second_flow_publication' not in facts:
        flow = api('GET', f"/api/flows/{facts['flow_id']}", token=admin)
        nodes = [{k: n[k] for k in ['nodeKey', 'nodeType', 'name', 'positionX', 'positionY', 'assigneePolicy', 'formPolicy', 'timeoutPolicy', 'exceptionPolicy', 'config']} for n in flow['nodes']]
        edges = [{k: e[k] for k in ['edgeKey', 'sourceNodeKey', 'targetNodeKey', 'conditionExpression', 'priorityOrder', 'config']} for e in flow['edges']]
        next(e for e in edges if e['edgeKey'] == 'typed_priority')['config']['condition']['conditions'][0]['value'] = 20
        draft = api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': flow['version'], 'nodes': nodes, 'edges': edges}, admin)
        facts['second_flow_publication'] = api('POST', f"/api/flows/{flow['id']}/publish", {'expectedDraftRevision': draft['draftRevision'],
            'changeSummary': '分值升级至20，旧实例保持原定义', 'simulationVariables': sample()}, admin); save()
    if 'new_version_action' not in facts:
        facts['new_version_action'] = api('POST', f'/api/runtime/modules/{code}/records', {'title': '最新版本实际业务审批意图', 'status': 'ACTIVE',
            'participantMemberIds': [], 'fields': sample()}, admin, action_key=code + '_latest'); save()
    if not facts.get('version_upgrade_verified'):
        old, new = instance(facts['priority_action']), instance(facts['new_version_action'])
        assert snapshot_hash(old['definitionSnapshot']) == facts['old_instance_snapshot']['hash'] and old['currentNodeKey'] == 'priority_review'
        assert new['flowVersionId'] == facts['second_flow_publication']['versionId'] and new['currentNodeKey'] == 'normal_review'
        bindings = api('GET', f"/api/flow-bindings?moduleId={facts['module_id']}&triggerEvent=CREATE", token=admin)
        assert bindings['currentTenant'][0]['id'] == facts['binding']['id'] and bindings['currentTenant'][0]['flowVersionId'] == new['flowVersionId']
        facts['version_upgrade_verified'] = True; case('same_existing_binding_new_action_latest_version_old_instance_frozen', old_instance=old['id'], new_instance=new['id'], old_version=old['flowVersionId'], new_version=new['flowVersionId'])
    if not facts.get('mismatch_verified'):
        status, result = call('POST', '/api/flow-bindings/publish', {'moduleId': 45, 'triggerEvent': 'CREATE', 'flowId': facts['flow_id'],
            'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0, 'condition': {'mode': 'ALL', 'conditions': []}}, admin)
        assert status == 422 and result['code'] == 'FLOW_BINDING_BUSINESS_MODULE_MISMATCH'
        facts['mismatch_verified'] = True; case('wrong_flow_business_module_rejected_before_binding_writes', status=status, error_code=result['code'])
    if not facts.get('old_approval_verified'):
        current = instance(facts['priority_action'])
        if current['status'] != 'COMPLETED':
            task = next(t for t in current['tasks'] if t['status'] == 'PENDING')
            api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': 'APPROVE', 'comment': '真实旧快照审批通过',
                'variables': {}, 'idempotencyKey': code + '_approve_old'}, admin)
        for _ in range(12):
            result = delivery(facts['priority_action'])
            if result['businessStatus'] == 'EXECUTED': break
            time.sleep(1)
        assert result['businessStatus'] == 'EXECUTED', {k: result.get(k) for k in ['businessStatus', 'errorCode', 'message']}
        record = api('GET', f"/api/runtime/modules/{code}/records/{result['recordId']}", token=admin)
        assert record['fields']['score'] == 10 and set(record['fields']['choices']) == {'one', 'two'}
        facts['old_approved_record'] = record; facts['old_approval_verified'] = True
        case('old_version_real_approval_executes_original_action_once', instance=facts['priority_action']['flowInstanceId'], record_id=record['id'])
    if not facts.get('normal_rejection_verified'):
        current = instance(facts['normal_action'])
        if current['status'] != 'REJECTED':
            task = next(t for t in current['tasks'] if t['status'] == 'PENDING')
            api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': 'REJECT', 'comment': '实际普通分支拒绝不保存',
                'variables': {}, 'idempotencyKey': code + '_reject'}, admin)
        for _ in range(12):
            result = delivery(facts['normal_action'])
            if result['businessStatus'] == 'REJECTED': break
            time.sleep(1)
        assert result['businessStatus'] == 'REJECTED', result['businessStatus']
        facts['normal_rejection_verified'] = True; case('real_fallback_reject_does_not_execute_create', instance=current['id'], business_status=result['businessStatus'])
elif stage == 'guards':
    if 'dictionary_guard_done' not in facts:
        # 已被业务记录引用的选项必须尊重既有停用阻断；采用未使用、但被流程条件引用的专用项。
        if 'dictionary_guard_item' not in facts:
            facts['dictionary_guard_item'] = api('POST', f"/api/admin/dictionaries/{facts['dictionary_id']}/items",
                {'code': 'unused', 'label': '流程条件专用未使用项', 'sortOrder': 2}, admin); save()
        if 'dictionary_guard_flow_publication' not in facts:
            dictionary_item_status('ACTIVE')
            flow = api('GET', f"/api/flows/{facts['flow_id']}", token=admin)
            nodes = [{k: n[k] for k in ['nodeKey', 'nodeType', 'name', 'positionX', 'positionY', 'assigneePolicy', 'formPolicy', 'timeoutPolicy', 'exceptionPolicy', 'config']} for n in flow['nodes']]
            edges = [{k: e[k] for k in ['edgeKey', 'sourceNodeKey', 'targetNodeKey', 'conditionExpression', 'priorityOrder', 'config']} for e in flow['edges']]
            condition = next(e for e in edges if e['edgeKey'] == 'typed_priority')['config']['condition']
            condition['conditions'][-1] = {'mode': 'ANY', 'conditions': [{'field': 'choice', 'operator': 'EQ', 'value': 'one'},
                {'field': 'choice', 'operator': 'EQ', 'value': 'unused'}]}
            draft = api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': flow['version'], 'nodes': nodes, 'edges': edges}, admin)
            facts['dictionary_guard_flow_publication'] = api('POST', f"/api/flows/{flow['id']}/publish", {'expectedDraftRevision': draft['draftRevision'],
                'changeSummary': '未使用字典项作为类型化流程条件', 'simulationVariables': sample()}, admin); save()
        if 'disabled_dictionary_publication' not in facts:
            facts['disabled_dictionary_publication'] = dictionary_item_status('DISABLED'); save()
        fields = sample(choices=['two'], choice='two')
        key = code + '_dictionary_guard'
        status, result = call('POST', f'/api/runtime/modules/{code}/records', {'title': '当前合法值不能绕过已失效条件', 'status': 'ACTIVE',
            'participantMemberIds': [], 'fields': fields}, admin, action_key=key)
        assert status == 422 and result['code'] == 'FLOW_CONDITION_INVALID', (status, result.get('code'))
        assert sql(f"SELECT COUNT(*) FROM core_idempotency_record WHERE idempotency_key LIKE '%{key}%'") == [['0']]
        facts['dictionary_guard_done'] = True; case('valid_actual_options_cannot_bypass_retired_flow_constant_before_new_intent', status=status, error_code=result['code'])
    if 'restored_dictionary_publication' not in facts:
        facts['restored_dictionary_publication'] = dictionary_item_status('ACTIVE'); save()
    if 'field_guard_done' not in facts:
        # 已有数据的发生时间停用被发布检查正确阻断；恢复其草稿，再验证无数据但被Flow引用的字段。
        if 'occurred_draft_restored' not in facts:
            facts['occurred_draft_restored'] = field_status('ACTIVE'); save()
        if 'field_guard_field' not in facts:
            facts['field_guard_field'] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/fields", {
                'code': 'flow_only', 'name': '流程条件未使用字段', 'fieldType': 'TEXT', 'required': False, 'sortOrder': 10, 'config': {}}, admin); save()
        if 'field_guard_flow_publication' not in facts:
            publish_module()
            flow = api('GET', f"/api/flows/{facts['flow_id']}", token=admin)
            nodes = [{k: n[k] for k in ['nodeKey', 'nodeType', 'name', 'positionX', 'positionY', 'assigneePolicy', 'formPolicy', 'timeoutPolicy', 'exceptionPolicy', 'config']} for n in flow['nodes']]
            edges = [{k: e[k] for k in ['edgeKey', 'sourceNodeKey', 'targetNodeKey', 'conditionExpression', 'priorityOrder', 'config']} for e in flow['edges']]
            next(e for e in edges if e['edgeKey'] == 'typed_priority')['config']['condition']['conditions'].append({'field': 'flow_only', 'operator': 'EMPTY'})
            draft = api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': flow['version'], 'nodes': nodes, 'edges': edges}, admin)
            facts['field_guard_flow_publication'] = api('POST', f"/api/flows/{flow['id']}/publish", {'expectedDraftRevision': draft['draftRevision'],
                'changeSummary': '无运行数据字段仍是流程条件必要配置', 'simulationVariables': sample()}, admin); save()
        if 'disabled_field_publication' not in facts:
            facts['disabled_field_publication'] = field_status('DISABLED'); save()
        fields = sample(); key = code + '_field_guard'
        status, result = call('POST', f'/api/runtime/modules/{code}/records', {'title': '已移除条件字段明确拒绝', 'status': 'ACTIVE',
            'participantMemberIds': [], 'fields': fields}, admin, action_key=key)
        assert status == 422 and result['code'] == 'FLOW_CONDITION_INVALID', (status, result.get('code'))
        assert sql(f"SELECT COUNT(*) FROM core_idempotency_record WHERE idempotency_key LIKE '%{key}%'") == [['0']]
        assert snapshot_hash(instance(facts['priority_action'])['definitionSnapshot']) == facts['old_instance_snapshot']['hash']
        facts['field_guard_done'] = True; case('current_published_condition_field_removed_rejects_new_intent_old_snapshot_unchanged', status=status, error_code=result['code'])
    if 'restored_field_publication' not in facts:
        facts['restored_field_publication'] = field_status('ACTIVE'); save()
    if 'manual_option_field' not in facts:
        facts['manual_option_field'] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/fields", {
            'code': 'manual_choice', 'name': '手动选项状态验收', 'fieldType': 'SINGLE_SELECT', 'required': False, 'sortOrder': 9,
            'config': {'options': [{'value': 'allowed', 'label': '仍可用'}, {'value': 'stopped', 'label': '已停用', 'status': 'DISABLED'}]}}, admin)
        facts['manual_option_publication'] = publish_module(); save()
    if 'manual_option_guard_done' not in facts:
        directory = api('GET', f"/api/flows/condition-fields?moduleId={facts['module_id']}", token=admin)
        field = next(field for field in directory if field['code'] == 'manual_choice')
        assert [option['value'] for option in json.loads(field['configJson'])['options']] == ['allowed']
        status, result = call('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'UPDATE',
            'executionMode': 'DIRECT', 'priorityOrder': 0, 'condition': {'field': 'manual_choice', 'operator': 'EQ', 'value': 'stopped'}}, admin)
        assert status == 422 and result['code'] == 'FLOW_BINDING_CONDITION_INVALID'
        assert not api('GET', f"/api/flow-bindings?moduleId={facts['module_id']}&triggerEvent=UPDATE", token=admin)['currentTenant']
        facts['manual_option_guard_done'] = True; case('manual_and_dictionary_options_share_current_active_catalog_and_publish_guard', status=status, error_code=result['code'])
    if 'minimal_profile_publication' not in facts:
        # 当前真实修复版本已在上面的字段条件变更时发布，不重复发布同一草稿修订。
        current = api('GET', f"/api/flows/{facts['flow_id']}", token=admin)
        published = next(v for v in current['versions'] if v['id'] == current['currentVersionId'])
        profile = published['snapshot']['conditionContext']
        assert {f['code'] for f in profile['fields']} == {'score', 'occurred', 'choices', 'date_text', 'enabled', 'day', 'choice', 'flow_only'}
        assert {f['code'] for f in profile['fieldBindings']} == {f['code'] for f in profile['fields']}
        facts['minimal_profile_publication'] = {'versionId': published['id'], 'versionNumber': published['versionNumber']}; case('new_publication_snapshot_contains_only_referenced_safe_fields', fields=len(profile['fields']))
elif stage == 'required':
    if not facts.get('required_migration_checked'):
        update_record(sample(choices=[])); choices_required(True)
        checked = api('GET', f"/api/admin/module-config/modules/{facts['module_id']}/publication-check", token=admin)
        assert not checked['valid'] and any(issue['code'] == 'REQUIRED_FIELD_DATA_MIGRATION_REQUIRED' for issue in checked['issues'])
        facts['required_migration_checked'] = True; case('existing_empty_multiselect_blocks_required_publication_until_migrated', issues=[i['code'] for i in checked['issues']])
    if 'required_publication' not in facts:
        facts['record'] = update_record(sample()); facts['required_publication'] = publish_module(); save()
    if not facts.get('required_runtime_checked'):
        key = code + '_required_guard'
        status, result = call('POST', f'/api/runtime/modules/{code}/records', {'title': '必填多选不可为空', 'status': 'ACTIVE',
            'participantMemberIds': [], 'fields': sample(choices=[])}, admin, action_key=key)
        assert status == 422 and result['code'] == 'FIELD_VALIDATION_FAILED'
        assert sql(f"SELECT COUNT(*) FROM core_idempotency_record WHERE idempotency_key LIKE '%{key}%'") == [['0']]
        facts['required_runtime_checked'] = True; case('required_multiselect_empty_rejected_before_any_business_intent', status=status, error_code=result['code'])
    if 'optional_choices_restored' not in facts:
        choices_required(False); facts['optional_choices_restored'] = publish_module(); save()
else:
    raise AssertionError('未知阶段，不扩大验收范围')
print(json.dumps({'stage': stage, 'module_id': facts.get('module_id'), 'flow_id': facts.get('flow_id'), 'cases': len(facts['cases'])}, ensure_ascii=False))
