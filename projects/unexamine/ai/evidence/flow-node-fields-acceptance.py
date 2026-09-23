"""B09.1b.1 节点字段策略实际验收，复用服务和原角色基线；凭证不落盘。"""
import ast
import copy
import hashlib
import json
import re
import secrets
import sys
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {'call', 'api', 'sql'}], type_ignores=[]), 'helpers', 'exec'))
PATH = AREA / 'flow-node-fields-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {'step': 'B09.1b.1', 'status': 'running', 'cases': []}
assert facts['status'] == 'running', '已封存事实不能重复修改'
j = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', j).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', j).group(1)})
admin = api('POST', '/api/systems/54/tenants/57/enter', {}, login['accessToken'])['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = 'flow_condition_accept_20260918'


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def publish_role(permissions, policies):
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    role = api('POST', '/api/admin/system/authorization/roles', {'id': 114, 'code': current['code'], 'name': current['name'],
        'description': current['description'], 'expectedVersion': current['version'], 'permissions': permissions, 'fieldPolicies': policies}, admin)
    api('POST', '/api/admin/system/authorization/roles/114/publish', {'expectedVersion': role['version'], 'reason': '隔离节点字段策略验收'}, admin)


def granted_role():
    permissions = copy.deepcopy(original['permissions'])
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': a, 'dataScopeType': 'ALL'}
        for a in ['LIST', 'DETAIL', 'UPDATE']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': a, 'dataScopeType': 'ALL'}
        for a in ['VIEW', 'DESIGN', 'PUBLISH', 'SIMULATE', 'VIEW_RUNTIME', 'HANDLE']]
    draft = api('GET', '/api/admin/module-config/modules/51/draft', token=admin)
    policies = copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode': code, 'fieldCode': f['code'], 'channel': channel,
        'readable': f['code'] != 'hidden', 'writable': f['code'] != 'hidden', 'maskStrategy': None}
        for f in draft['fields'] if f['status'] == 'ACTIVE' for channel in ['PAGE', 'FLOW']]
    return permissions, policies


def save_draft(nodes):
    flow_id = facts['fixture']['flow_id']
    current = api('GET', f'/api/flows/{flow_id}', token=admin)
    return api('PUT', f'/api/flows/{flow_id}/draft', {'expectedVersion': current['version'], 'nodes': nodes,
        'edges': facts['fixture']['edges']}, admin)


stage = sys.argv[1]
if stage == 'setup':
    assert not facts.get('api_verified'), '已通过 API 不重复创建模板'
    permissions, policies = granted_role()
    publish_role(permissions, policies)
    facts['temporary_role_granted'] = True
    if not facts.get('fixture'):
        flow = api('POST', '/api/flows', {'code': 'node_fields_' + secrets.token_hex(5), 'name': '节点中文字段配置验收'}, admin)
        nodes = [{'nodeKey': key, 'nodeType': kind, 'name': name, 'positionX': index * 220, 'positionY': 80,
            'config': {'moduleId': 51} if kind == 'START' else {'approvalMode': 'OR_SIGN'} if kind == 'APPROVAL' else {},
            'assigneePolicy': {'type': 'PERSON', 'tenantMemberIds': [108]} if kind == 'APPROVAL' else {},
            'formPolicy': {'hidden': [], 'customSetting': '保留已有配置'} if kind == 'APPROVAL' else {}}
            for index, (key, kind, name) in enumerate([('start','START','开始'),('review','APPROVAL','核对业务字段'),('end','END','结束')])]
        edges = [{'edgeKey': key, 'sourceNodeKey': source, 'targetNodeKey': target, 'priorityOrder': 0, 'config': {}}
            for key, source, target in [('entry','start','review'),('finish','review','end')]]
        facts['fixture'] = {'flow_id': flow['id'], 'nodes': nodes, 'edges': edges, 'record_id': 170}
        save()
    nodes = copy.deepcopy(facts['fixture']['nodes'])
    checks = []
    for policy in [{'hidden': ['missing_field']}, {'hidden': 'score'},
                   {'visible': [], 'editable': ['subject']}, {'required': ['subject']}, {'hidden': ['score', 'score']}]:
        nodes[1]['formPolicy'] = policy
        saved = save_draft(nodes)
        check = api('GET', f"/api/flows/{saved['id']}/publication-check", token=admin)
        assert not check['valid'] and any(i['code'] == 'FLOW_NODE_FIELDS_INVALID' for i in check['issues']), check
        status, result = call('POST', f"/api/flows/{saved['id']}/publish", {'expectedDraftRevision': saved['draftRevision'],
            'changeSummary': '不应发布的节点配置', 'simulationVariables': {}}, admin)
        assert status == 422, (status, result.get('code'))
        checks.append({'policy': policy, 'status': status, 'code': result.get('code')})
    save_draft(facts['fixture']['nodes'])
    fields = api('GET', '/api/flows/node-form-fields?moduleId=51', token=admin)
    assert all(set(f) <= {'id','code','name','fieldType','editable','dictionaryId','referenceModuleId'} for f in fields)
    assert next(f for f in fields if f['code'] == 'subject')['name'] == '事项主题'
    facts['cases'].append({'case': 'actual_invalid_policy_publish_rejected', 'checks': checks,
        'result': '真实保存无效字段、错误列表、不可见编辑、必填未设编辑和重复字段五种草稿；发布检查明确无效，实际发布均422，没有静默放宽或删策略。'})
    facts['api_verified'] = True
    save(); print(json.dumps({'flow_id': facts['fixture']['flow_id'], 'invalid_publish_cases': len(checks)}, ensure_ascii=False))
elif stage == 'start':
    assert facts.get('api_verified') and not facts['fixture'].get('instance_id')
    flow = api('GET', f"/api/flows/{facts['fixture']['flow_id']}", token=admin)
    assert flow['currentVersionId'], '先完成普通页面配置和发布'
    version = next(v for v in flow['versions'] if v['id'] == flow['currentVersionId'])
    node = next(n for n in version['snapshot']['nodes'] if n['nodeKey'] == 'review')
    policy = node['formPolicy']
    assert policy.get('customSetting') == '保留已有配置'
    assert 'subject' in policy.get('editable', []) and 'subject' in policy.get('required', [])
    assert 'choices' in policy.get('hidden', [])
    form = version['snapshot']['formContext']
    assert form['moduleId'] == 51 and form['moduleCode'] == code
    assert next(f for f in form['fields'] if f['code'] == 'subject')['id'] == 177
    assert 'hidden' not in [f['code'] for f in form['fields']], '普通目录不得发布不可读字段绑定'
    started = api('POST', '/api/flow-runtime/instances', {'flowId': flow['id'], 'title': '冻结节点中文配置实例',
        'businessType': 'MODULE:' + code, 'businessId': '170', 'businessSnapshot': {}, 'variables': {},
        'idempotencyKey': 'node_fields_' + secrets.token_hex(6)}, admin)['instance']
    current = started['businessView']['current']
    assert current['id'] == 170 and current['fields']['subject'] == '普通设计者完整条件链'
    assert 'choices' not in current['fields'] and 'choices' not in current['fieldPresentation']
    assert 'hidden' not in current['fields'], '冻结普通安全目录后不能用Root读新字段补回'
    facts['fixture'].update(instance_id=started['id'], version_id=started['flowVersionId'], definition_hash=started['definitionHash'],
        frozen_policy=policy, frozen_form=form)
    facts['cases'].append({'case':'ordinary_policy_published_runtime_bound','result':'普通页面发布节点策略，保留旧自定义配置，中文目录冻结真实字段177等，独立实例当前正式170按隐藏多选及冻结安全目录裁剪，Root也不能补回未冻结不可读字段。'})
    save(); print(json.dumps({'instance_id': started['id'], 'flow_id': flow['id'], 'version_id': started['flowVersionId']}, ensure_ascii=False))
elif stage == 'frozen_readback':
    fixture = facts['fixture']
    flow = api('GET', f"/api/flows/{fixture['flow_id']}", token=admin)
    draft = next(n for n in flow['nodes'] if n['nodeKey'] == 'review')['formPolicy']
    assert 'choices' not in draft.get('hidden', []), '先从普通页面修改草稿取消隐藏并保存'
    current = api('GET', f"/api/flow-runtime/instances/{fixture['instance_id']}", token=admin)
    assert current['definitionHash'] == fixture['definition_hash'] and 'choices' not in current['businessView']['current']['fields']
    version = next(v for v in flow['versions'] if v['id'] == fixture['version_id'])
    assert version['snapshot']['formContext'] == fixture['frozen_form']
    assert next(n for n in version['snapshot']['nodes'] if n['nodeKey'] == 'review')['formPolicy'] == fixture['frozen_policy']
    facts['cases'].append({'case':'ordinary_draft_change_cannot_modify_frozen_policy','result':'普通页面取消多选隐藏并保存草稿，旧实例仍隐藏该字段；字段目录身份、节点策略及旧版本定义哈希完全一致。'})
    facts['frozen_verified'] = True
    save(); print('PASS:新草稿与旧实例冻结策略独立。')
elif stage == 'loss':
    permissions, policies = granted_role()
    publish_role([p for p in permissions if p['resourceCode'] != code], policies)
    facts['permission_loss_published'] = True
    save(); print('当前模块权限撤回，普通页面应清空目录且阻止发布。')
elif stage == 'restore':
    publish_role(original['permissions'], original['fieldPolicies'])
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    assert current['permissions'] == original['permissions'] and current['fieldPolicies'] == original['fieldPolicies']
    facts['original_role_restored'] = True
    save(); print('Role114准确恢复原权限与字段策略。')
elif stage == 'close_readback':
    fixture = facts['fixture']
    instance = api('GET', f"/api/flow-runtime/instances/{fixture['instance_id']}", token=admin)
    assert instance['status'] == 'REJECTED' and not any(t['status'] == 'PENDING' for t in instance['tasks'])
    assert int(sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0 AND deleted=0')[0][0]) == 4
    root_module = api('GET', f'/api/runtime/modules/{code}/records/170', token=admin)
    baseline = json.loads((AREA/'flow-condition-ui-facts.json').read_text(encoding='utf-8'))['ordinary_record']
    assert root_module['fields'] == baseline['fields'] and root_module['version'] == baseline['version']
    facts['cases'].append({'case':'actual_ordinary_reject_no_formal_write','result':'普通页面本人拒绝隔离节点实例，实际待办清零；原正式170字段和版本不变，模块51仍4条正式数据。'})
    facts['fixture_closed'] = True
    save(); print('PASS:普通节点验收拒绝结束，无正式业务写入。')
elif stage == 'integrated_readback':
    assert facts.get('fixture_closed') and facts.get('frozen_verified') and facts.get('original_role_restored')
    # 公共读策略变更后定向复验既有完成实例和来源，物理值全列读前后不能被投影修改。
    statement = 'SELECT record_id,field_id,field_code,value_type,HEX(value_text),value_number,value_date,value_datetime,value_boolean,value_reference_id,value_file_id,HEX(value_json),HEX(normalized_text),created_at,updated_at FROM biz_record_value WHERE record_id IN (151,154,168,169,170,171) ORDER BY record_id,field_id'
    before = sql(statement)
    legacy = api('GET', '/api/flow-runtime/instances/81', token=admin)
    assert legacy['status'] == 'COMPLETED' and legacy['businessView']['current']['id'] == 170
    assert legacy['businessView']['current']['fields']['score'] == 30
    assert datetime.fromisoformat(legacy['businessView']['current']['fields']['occurred']) == datetime(2026,9,18,10)
    assert not legacy['variables'] and all(not a['input'] and not a['result'] for a in legacy['actions'])
    range_facts = json.loads((AREA/'range-share-facts.json').read_text(encoding='utf-8'))
    source_admin = api('POST', '/api/systems/54/tenants/77/enter', {}, login['accessToken'])['tokens']['accessToken']
    for record_id, module, baseline in [(154,'shared_private_accept_4a356e1ce2',range_facts['baseline_source154']),
            (151,'extension_accept_4a356e1ce2',range_facts['baseline_source151'])]:
        record = api('GET', f'/api/runtime/modules/{module}/records/{record_id}', token=source_admin)
        assert record['fields'] == baseline['fields'] and record['version'] == baseline['version']
    assert sql(statement) == before
    facts['cases'].append({'case':'affected_common_projection_integrated_readback',
        'physical_values_hash':hashlib.sha256(json.dumps(before).encode()).hexdigest(),
        'result':'共享策略更新后旧完成81仍读取正式170分值30/北京时间10点并裁剪原始旁路；来源151/154完整字段和版本仍等于既有基线，全部物理值列含日期和时间读前后相等。角色114原始权限准确恢复，未重新运行旧包测试。'})
    facts['integrated_verified'] = True
    save(); print('PASS:公共策略受影响旧完成/来源读取及全列物理持久化组合。')
else:
    raise ValueError('未知阶段')
