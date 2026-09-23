"""共用主模块的共享来源资源与逐记录投影验收；既有正式配置、无新账号。"""
import ast
import copy
import json
import re
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {'call', 'api', 'sql'}], type_ignores=[]), 'helpers', 'exec'))
tree = ast.parse((AREA / 'tenant-action-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'body'],
    type_ignores=[]), 'extension-body', 'exec'))
path = AREA / 'shared-source-projection-facts.json'
facts = json.loads(path.read_text(encoding='utf-8'))
fixture = json.loads((AREA / 'tenant-extension-facts.json').read_text(encoding='utf-8'))
code = fixture['module_code']
extension_path = '/api/admin/tenant-extensions/modules/45'
base = f'/api/runtime/modules/{code}/records/151'
assert facts['status'] == 'running', '已有完成事实，不重复全部验收'


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    facts['cases'].append({'case': name, **values})
    save()


def catalog():
    return next(r for r in api('GET', '/api/admin/system/authorization', token=admins[57])['resources']
                if r['resourceType'] == 'MODULE' and r['resourceCode'] == code)


def role_set(role):
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admins[57])['roles'] if r['id'] == 114)
    draft = api('POST', '/api/admin/system/authorization/roles', {'id': 114, 'code': current['code'],
        'name': current['name'], 'description': current['description'], 'expectedVersion': current['version'],
        'permissions': role['permissions'], 'fieldPolicies': role['fieldPolicies']}, admins[57])
    api('POST', '/api/admin/system/authorization/roles/114/publish', {'expectedVersion': draft['version'],
        'reason': '本地来源专属字段动作的公共投影验收'}, admins[57])


def shares():
    return next(s for s in api('GET', base + '/shares', token=admins[77]) if s['id'] == 15)


java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})
admins = {t: api('POST', f'/api/systems/54/tenants/{t}/enter', {}, login['accessToken'])['tokens']['accessToken'] for t in [57, 77]}

if 'saved_source_extension' not in facts:
    detail = api('GET', extension_path, token=admins[77])
    assert detail['extension'] == facts['prior_source_extension']['extension'], '失败准备后配置不应改变'
    assert sql("SELECT COUNT(*) FROM cfg_module_field WHERE module_id=45 AND code IN ('__t77_sourcenote','__t77_source_note')") == [['0']]
    assert sql("SELECT COUNT(*) FROM cfg_module_action WHERE module_id=45 AND code='__t77_SOURCE_FILL'") == [['0']]
    facts.setdefault('preparation_failures', []).append({'status': 422, 'code': 'TENANT_EXTENSION_INVALID',
        'message': '字段 sourceNote 被正规化为 sourcenote，而 FORM 引用仍为 sourceNote；失败事务未留字段、动作或扩展修改。改用小写 source_note，不修改校验。'})
    request = body(detail)
    request['fields'].append({'code': 'source_note', 'name': '来源专属说明', 'fieldType': 'TEXT', 'required': False,
        'sortOrder': 50, 'config': {'maxLength': 80}})
    for page in request['pages']:
        if page['pageType'] == 'FORM': page['fieldCodes'].append('source_note')
    request['actions'] = [{'actionId': a['id'], 'code': a['code'], 'name': a['name'], 'location': a['location'],
        'parameterFieldCodes': json.loads(a['configJson'])['parameterFieldCodes'], 'sortOrder': a['sortOrder']}
        for a in detail['draft']['actions']]
    request['actions'].append({'code': 'SOURCE_FILL', 'name': '来源专属补充说明', 'location': 'ROW',
        'parameterFieldCodes': ['source_note'], 'sortOrder': 20})
    facts['saved_source_extension'] = api('PUT', extension_path, request, admins[77])
    save()

if 'source_publication' not in facts:
    checked = api('GET', extension_path + '/publication-check', token=admins[77])
    assert checked['valid'], checked['issues']
    facts['source_publication'] = api('POST', extension_path + '/publish', {'expectedDraftRevision': checked['draftRevision']}, admins[77])
    save()

if not any(c['case'] == 'active_share_field_catalog_action_allowance_and_recipient_name_precedence' for c in facts['cases']):
    resource = catalog()
    assert 'source_note' in resource['fields'] and resource['fieldNames']['source_note'] == '来源专属说明', resource
    assert 'SOURCE_FILL' not in resource['actions'], resource
    assert resource['actionNames']['ACTION_TENANT_ACCEPT'] == '接收方扩展填写动作'
    assert not any(c.startswith('__t') for c in resource['fields'] + resource['actions'])
    case('active_share_field_catalog_action_allowance_and_recipient_name_precedence', resource=resource)

if 'source_initial_note' not in facts:
    context = api('GET', base + '/edit-context', token=admins[77])
    record = context['record']
    fields = copy.deepcopy(record['fields']); fields['source_note'] = '来源专属字段初始值'
    facts['source_initial_note'] = api('PUT', base, {'title': record['title'], 'status': record['status'],
        'ownerMemberId': record['ownerMemberId'], 'departmentId': record['departmentId'],
        'participantMemberIds': record['participantMemberIds'], 'fields': fields, 'version': record['version']}, admins[77])
    save()

if 'expanded_share' not in facts:
    prior = shares(); facts['prior_share'] = prior
    # 公共共享 DTO 已展开 allowedActions，不引用内部数据库 permissionJson。
    facts.setdefault('preparation_failures', []).append({'code': 'ACCEPTANCE_DTO_KEY_CORRECTED',
        'message': '验收脚本误用内部 permissionJson，第一次 KeyError 在共享写入前；保留已通过目录/已保存来源字段，不重跑。'})
    actions = prior['allowedActions']
    facts['expanded_share'] = api('POST', base + '/shares', {'targetTenantId': 57,
        'allowedActions': list(dict.fromkeys(actions + ['SOURCE_FILL'])), 'version': prior['version']}, admins[77])
    save()

if not any(c['case'] == 'source_only_action_and_field_grant_validates_common_role_resource' for c in facts['cases']):
    resource = catalog()
    assert resource['actionNames']['SOURCE_FILL'] == '来源专属补充说明', resource
    original = next(r for r in api('GET', '/api/admin/system/authorization', token=admins[57])['roles'] if r['id'] == 114)
    facts['prior_role'] = original
    desired = copy.deepcopy(original)
    desired['permissions'].append({'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': 'SOURCE_FILL', 'dataScopeType': 'ALL'})
    desired['fieldPolicies'].append({'resourceCode': code, 'fieldCode': 'source_note', 'channel': 'PAGE', 'readable': True, 'writable': True})
    role_set(desired)
    facts['desired_role'] = desired
    assert sql('SELECT COUNT(*) FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id WHERE m.account_id=69 AND m.system_id=54 AND tm.tenant_id=77') == [['0']]
    case('source_only_action_and_field_grant_validates_common_role_resource', resource=resource, actor_account_id=69, source_memberships=0)

if not any(c['case'] == 'trusted_source_record_action_and_readonly_field_projection' for c in facts['cases']):
    record = api('GET', base, token=admins[57])
    actions = {a['code']: a for a in record['actions']}
    assert actions['ACTION_TENANT_ACCEPT']['name'] == '来源扩展填写动作' and actions['ACTION_TENANT_ACCEPT']['id'] == 480, actions
    assert actions['SOURCE_FILL']['name'] == '来源专属补充说明' and actions['SOURCE_FILL']['actionType'] == 'CUSTOM' and actions['SOURCE_FILL']['location'] == 'ROW', actions
    assert set(json.loads(actions['SOURCE_FILL']['configJson'])) <= {'confirmation', 'confirmationText'}
    field = next(f for f in facts['saved_source_extension']['draft']['fields'] if f['code'] == 'source_note')
    descriptor = record['fieldPresentation']['source_note']
    # 普通 TEXT 不必额外生成 displayValue；原值已由字段权限保护，公共文本渲染器使用该值。
    assert descriptor == {'name': '来源专属说明', 'fieldType': 'TEXT', 'displayValue': None, 'fieldId': field['id'], 'sortOrder': 50}, descriptor
    assert record['fields']['source_note'] == '来源专属字段初始值'
    facts.setdefault('preparation_failures', []).append({'code': 'ACCEPTANCE_TEXT_PRESENTATION_CORRECTED',
        'message': '脚本误要求普通 TEXT 必有额外 displayValue；实际字段155/顺序50/授权原值已正确返回。更正断言，不改变产品脱敏或标签逻辑、不重复已通过业务分支。'})
    case('trusted_source_record_action_and_readonly_field_projection', actions=record['actions'], source_field=descriptor,
        note='本组API为既有管理员；普通69零来源身份及字段保护由实际页面另行验收，不借此组宣称普通权限。')

print(json.dumps({'status': facts['status'], 'cases': len(facts['cases']), 'source_field': 'source_note',
    'source_action': 'SOURCE_FILL', 'record_version': sql('SELECT version FROM biz_record WHERE id=151')}, ensure_ascii=False))
