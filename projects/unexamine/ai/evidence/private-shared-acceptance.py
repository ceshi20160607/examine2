"""真实来源私有模块共享验收，复用普通69；各阶段检查点防止重跑已通过业务。"""
import ast
import copy
import json
import re
import sys
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {'call', 'api', 'sql'}], type_ignores=[]), 'helpers', 'exec'))
path = AREA / 'private-shared-facts.json'
facts = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {
    'step': 'B07.4b.2c.2b.4', 'status': 'running', 'module_code': 'shared_private_accept_4a356e1ce2', 'fields': {}, 'cases': []}
assert facts['status'] == 'running', '已有完成事实，不重复全量验收'
code = facts['module_code']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    facts['cases'].append({'case': name, **values})
    save()


def role_set(role):
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admins[57])['roles'] if r['id'] == 114)
    draft = api('POST', '/api/admin/system/authorization/roles', {'id': 114, 'code': current['code'],
        'name': current['name'], 'description': current['description'], 'expectedVersion': current['version'],
        'permissions': role['permissions'], 'fieldPolicies': role['fieldPolicies']}, admins[57])
    api('POST', '/api/admin/system/authorization/roles/114/publish', {'expectedVersion': draft['version'],
        'reason': '本地来源私有模块公共运行入口验收'}, admins[57])


def shares():
    return next(s for s in api('GET', base + '/shares', token=admins[77]) if s['id'] == facts['share']['id'])


java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})
admins = {t: api('POST', f'/api/systems/54/tenants/{t}/enter', {}, login['accessToken'])['tokens']['accessToken'] for t in [56, 57, 77]}
stage = sys.argv[1] if len(sys.argv) > 1 else 'prepare'

if stage == 'prepare':
    if 'module_id' not in facts:
        draft = api('POST', '/api/admin/module-config/modules', {'name': '来源私有协作事项',
            'code': code, 'groupName': '来源私有协作'}, admins[77])
        facts['module_id'] = draft['module']['id']
        facts['group_id'] = draft['module']['groupId']
        save()
    mid = facts['module_id']
    specs = [{'code': 'subject', 'name': '来源事项主题', 'fieldType': 'TEXT', 'required': True, 'config': {'maxLength': 80}},
        {'code': 'choice', 'name': '来源事项类型', 'fieldType': 'SINGLE_SELECT', 'dictionaryId': 6, 'config': {}},
        {'code': 'related', 'name': '来源关联业务', 'fieldType': 'REFERENCE', 'referenceModuleId': 44, 'config': {}},
        {'code': 'hidden', 'name': '来源私有内部说明', 'fieldType': 'TEXT', 'config': {'defaultValue': '内部默认不公开'}},
        {'code': 'source_note', 'name': '来源事项说明', 'fieldType': 'TEXT', 'config': {'maxLength': 80}}]
    for index, spec in enumerate(specs):
        if spec['code'] in facts['fields']: continue
        facts['fields'][spec['code']] = api('POST', f'/api/admin/module-config/modules/{mid}/fields',
            {'required': False, 'uniqueValue': False, 'searchable': False, 'sortOrder': index, **spec}, admins[77])
        save()
    if 'action' not in facts:
        facts['action'] = api('POST', f'/api/admin/module-config/modules/{mid}/actions',
            {'name': '来源私有事项补充说明', 'location': 'ROW', 'parameterFieldCodes': ['source_note'],
             'confirmation': False}, admins[77])
        save()
    if 'publication' not in facts:
        checked = api('GET', f'/api/admin/module-config/modules/{mid}/publication-check', token=admins[77])
        assert checked['valid'], checked['issues']
        facts['publication'] = api('POST', f'/api/admin/module-config/modules/{mid}/publish',
            {'expectedDraftRevision': checked['draftRevision']}, admins[77])
        save()
    if 'record' not in facts:
        facts['record'] = api('POST', f'/api/runtime/modules/{code}/records', {'title': '来源私有共享事项验收',
            'status': 'ACTIVE', 'participantMemberIds': [], 'fields': {'subject': '来源私有共享事项验收',
                'choice': 'src_one', 'related': 149, 'hidden': '来源私有字段必须保留', 'source_note': '来源私有说明初始值'}}, admins[77])
        save()
    base = f"/api/runtime/modules/{code}/records/{facts['record']['id']}"
    if 'share' not in facts:
        facts['share'] = api('POST', base + '/shares', {'targetTenantId': 57,
            'allowedActions': ['UPDATE', facts['action']['code']]}, admins[77])
        save()
    if 'before_backend_deploy' not in facts:
        overview = api('GET', '/api/admin/system/authorization', token=admins[57])
        facts['prior_role'] = next(r for r in overview['roles'] if r['id'] == 114)
        facts['before_backend_deploy'] = {'private_resource_present': any(r['resourceCode'] == code for r in overview['resources']),
            'configuration_read': {'status': call('GET', f'/api/runtime/modules/{code}/configuration', token=admins[57])[0]},
            'record_read': {'status': call('GET', base, token=admins[57])[0]}}
        assert not facts['before_backend_deploy']['private_resource_present']
        assert facts['before_backend_deploy']['record_read']['status'] == 404
        save()
elif stage == 'project':
    base = f"/api/runtime/modules/{code}/records/{facts['record']['id']}"
    overview = api('GET', '/api/admin/system/authorization', token=admins[57])
    resource = next(r for r in overview['resources'] if r['resourceType'] == 'MODULE' and r['resourceCode'] == code)
    assert set(resource['actions']) == {'LIST', 'DETAIL', 'UPDATE', facts['action']['code']}, resource
    assert not any(a in resource['actions'] for a in ['CREATE', 'SHARE', 'RESTORE', 'DELETE', 'EXPORT'])
    assert resource['fieldNames']['source_note'] == '来源事项说明'
    if 'desired_role' not in facts:
        desired = copy.deepcopy(facts['prior_role'])
        desired['permissions'] += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': a,
            'dataScopeType': 'ALL'} for a in ['LIST', 'DETAIL', 'UPDATE', facts['action']['code']]]
        desired['fieldPolicies'] += [{'resourceCode': code, 'fieldCode': field, 'channel': 'PAGE',
            'readable': field != 'hidden', 'writable': field != 'hidden'} for field in facts['fields']]
        role_set(desired)
        facts['desired_role'] = desired
        case('legitimate_private_share_logical_resource_and_recipient_role_grant', resource=resource)
    if not any(c['case'] == 'private_runtime_mode_LIST_DETAIL_and_forbidden_source_configuration' for c in facts['cases']):
        item = next(i for i in api('GET', '/api/runtime/modules', token=admins[57]) if i['moduleCode'] == code)
        assert item['configurationMode'] == 'SHARED_RECORDS' and item['moduleId'] == facts['module_id']
        assert item['moduleName'] == '来源私有协作事项' and item['groupId'] == facts['group_id']
        assert item['menuId'] is None and item['menuParentId'] is None
        record = api('GET', base, token=admins[57])
        assert record['id'] == facts['record']['id'] and record['dataTenantId'] == 77 and record['shared']
        listing = api('GET', f'/api/runtime/modules/{code}/records', token=admins[57])
        assert [r['id'] for r in listing['records']] == [record['id']]
        config_denial = call('GET', f'/api/runtime/modules/{code}/configuration', token=admins[57])
        assert config_denial[0] == 404 and config_denial[1]['code'] == 'CONFIGURATION_NOT_FOUND'
        assert api('GET', f'/api/runtime/modules/{code}/list-views', token=admins[57]) == []
        assert call('GET', f'/api/runtime/modules/{code}/records?sortField=source_note', token=admins[57])[0] == 403
        own = api('GET', f'/api/runtime/modules/{code}/records?tenantScope=OWN', token=admins[57])
        assert own['records'] == []
        case('private_runtime_mode_LIST_DETAIL_and_forbidden_source_configuration', item=item,
            record_version=record['version'], config_status=config_denial[0], config_error=config_denial[1]['code'],
            source_field_sort_denied=403, own_records=0, note='API本组为既有管理员，只验证协议；零来源身份普通69的实际页面另验。')
else:
    raise AssertionError('未知阶段，不扩大验收范围')
print(json.dumps({'stage': stage, 'module': facts['module_id'], 'record': facts['record']['id'],
    'share': facts['share']['id'], 'cases': len(facts['cases']), 'status': facts['status']}, ensure_ascii=False))
