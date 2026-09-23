"""普通设计者条件页面验收的只读核对和隔离夹具收尾，不代替真实页面操作。"""
import ast
import json
import re
import sys
from pathlib import Path
from datetime import datetime

AREA = Path(__file__).parent
tree = ast.parse((AREA / 'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
    or isinstance(n, ast.FunctionDef) and n.name in {'call', 'api', 'sql'}], type_ignores=[]), 'helpers', 'exec'))
facts_path = AREA / 'flow-condition-ui-facts.json'
facts = json.loads(facts_path.read_text(encoding='utf-8'))
assert facts['status'] == 'running', '已封存事实不重复变更夹具'
java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})
admin = api('POST', '/api/systems/54/tenants/57/enter', {}, login['accessToken'])['tokens']['accessToken']
code = 'flow_condition_accept_20260918'
stage = sys.argv[1]

if stage == 'verify':
    assert not facts.get('business_verified'), '只核对新页面动作，不重复已通过组合'
    flow = api('GET', '/api/flows/26', token=admin)
    version = next(v for v in flow['versions'] if v['id'] == flow['currentVersionId'])
    assert version['versionNumber'] == 5
    instance = next(i for i in api('GET', '/api/flow-runtime/instances', token=admin)
        if i['title'] == '普通页面真实条件审批' and i['startedByTenantMemberId'] == 108)
    assert instance['status'] == 'COMPLETED' and instance['flowVersionNumber'] == 5
    action_id = instance['businessSnapshot']['actionId']
    # MySQL 命令行转义会破坏 JSON 中的转义字符，以 HEX 读取完整回执。
    response = json.loads(bytes.fromhex(sql(f'SELECT HEX(response_json) FROM core_idempotency_record WHERE id={int(action_id)}')[0][0]).decode('utf-8'))
    assert response['status'] == 'EXECUTED', response.keys()
    rows = api('GET', f'/api/runtime/modules/{code}/records?page=1&pageSize=20', token=admin)['records']
    matched = [r for r in rows if r['title'] == '普通页面真实条件审批']
    assert len(matched) == 1
    record = api('GET', f"/api/runtime/modules/{code}/records/{matched[0]['id']}", token=admin)
    assert record['fields']['choices'] == ['one', 'two'] and record['fields']['score'] == 30
    assert datetime.fromisoformat(record['fields']['occurred']) == datetime(2026, 9, 18, 10)
    old = api('GET', '/api/flow-runtime/instances/80', token=admin)
    assert old['flowVersionNumber'] == 2 and old['status'] == 'WAITING'
    facts.update({'published_flow': version, 'ordinary_instance_id': instance['id'], 'ordinary_action_id': action_id,
        'ordinary_action_response': response, 'ordinary_record': record, 'old_waiting_instance': {
            'id': 80, 'flowVersionId': old['flowVersionId'], 'flowVersionNumber': old['flowVersionNumber'],
            'status': old['status'], 'definitionHash': old['definitionHash']}, 'business_verified': True})
    facts['cases'] += [
        {'case': 'ordinary_real_samples_and_publish', 'result': '普通页面选择真实记录168模拟命中普通审批；仅调整模拟分值30命中高优先审批，页面发布第5版，时间显示北京时间10点，多选显示业务名称。'},
        {'case': 'ordinary_binding_condition_publish', 'result': '普通页面配置分值GTE15、审批通过后保存、第5版流程，确认替换后原绑定保留为停用历史。'},
        {'case': 'ordinary_create_approve_once', 'instance_id': instance['id'], 'action_id': action_id, 'record_id': record['id'], 'result': '实际表单选择字典两选项、时间10点和分值30；审批前列表仍2条，第5版高优先等待；本人页面同意后EXECUTED，正式记录恰好一条且类型值正确。'},
        {'case': 'result_ownership_boundary', 'result': 'Root读取普通69动作回执返回404 ACTION_RESULT_NOT_FOUND；不放宽所有者权限，使用正式业务详情和DB核对执行结果。'},
        {'case': 'old_instance_not_rewritten', 'result': '页面发布第5版后，旧实例80仍第2版WAITING。'}]
    facts.setdefault('observed_defects', []).append({'case': 'multi_form_missing_registry', 'result': '业务表单漏注册MULTI_SELECT降为文本框；接入现有公共编辑器后，实际页面选择字典两选项并完成正式保存。'})
    facts.setdefault('remaining_gaps', []).append({'scope': 'B08运行态业务只读投影', 'result': '完整实例页直铺字段编码、重复fields对象、字典码和内部成员ID，业务快照/变量及明细未经字段裁剪；须补齐公共业务读模型，不能计整个Flow完成。'})
    print(json.dumps({'instance': instance['id'], 'action': action_id, 'record': record['id'], 'formal_count': len(rows)}, ensure_ascii=False))
elif stage == 'nomatch':
    assert facts.get('business_verified') and not facts.get('nomatch_verified')
    rows = api('GET', f'/api/runtime/modules/{code}/records?page=1&pageSize=20', token=admin)['records']
    record = next(r for r in rows if r['title'] == '普通页面未命中审批直接保存')
    assert record['fields']['score'] == 5
    assert not any(i['title'] == record['title'] for i in api('GET', '/api/flow-runtime/instances', token=admin))
    facts['direct_record'] = record
    facts['nomatch_verified'] = True
    facts['cases'].append({'case': 'ordinary_no_match_direct', 'record_id': record['id'],
        'result': '普通真实表单新建分值5未命中GTE15，页面明确业务已生效，正式记录创建且无多余Flow。'})
    print('未命中审批条件时真实记录直接保存，无流程实例。')
elif stage == 'legacy':
    assert facts.get('nomatch_verified') and not facts.get('legacy_fixture')
    flow = api('POST', '/api/flows', {'code': code + '_legacy', 'name': '旧条件兼容页面验收'}, admin)
    nodes = [{'nodeKey': key, 'nodeType': kind, 'name': label, 'positionX': x, 'positionY': y,
        'config': config, 'assigneePolicy': assignee} for key, kind, label, x, y, config, assignee in [
        ('start', 'START', '开始', 40, 40, {'moduleId': 51}, {}),
        ('gate', 'GATEWAY', '旧条件分支', 260, 40, {}, {}),
        ('review', 'APPROVAL', '发起人核对', 480, 180, {'approvalMode': 'OR_SIGN'}, {'type': 'INITIATOR'}),
        ('end', 'END', '结束', 700, 40, {}, {})]]
    edges = [{'edgeKey': key, 'sourceNodeKey': source, 'targetNodeKey': target,
        'priorityOrder': priority, 'conditionExpression': expression, 'config': {}}
        for key, source, target, priority, expression in [
            ('entry', 'start', 'gate', 0, None), ('legacy', 'gate', 'review', 0, 'score >= 25'),
            ('fallback', 'gate', 'end', 10, None), ('done', 'review', 'end', 0, None)]]
    facts['legacy_fixture'] = api('PUT', f"/api/flows/{flow['id']}/draft", {
        'expectedVersion': flow['version'], 'nodes': nodes, 'edges': edges}, admin)
    print('仅准备旧存储表达式兼容夹具；普通用户仍通过可视化配置。')
elif stage == 'legacy-verify':
    assert facts.get('legacy_fixture') and not facts.get('legacy_verified')
    flow = api('GET', f"/api/flows/{facts['legacy_fixture']['id']}", token=admin)
    original = next(e for e in facts['legacy_fixture']['edges'] if e['edgeKey'] == 'legacy')
    current = next(e for e in flow['edges'] if e['edgeKey'] == 'legacy')
    assert flow['draftRevision'] > facts['legacy_fixture']['draftRevision']
    assert current['conditionExpression'] == original['conditionExpression'] and current['config'] == original['config']
    facts['legacy_verified'] = True
    facts['cases'].append({'case': 'legacy_gui_read_save', 'flow_id': flow['id'],
        'result': '普通页面显示真实数值字段及大于等于25的类型化条件，仅查看/保存草稿不将旧score >= 25改为新配置或改写原值。'})
    print('普通页面保存后旧表达式和配置完全保持。')
elif stage == 'restore':
    assert facts.get('business_verified') and not facts.get('role_restored')
    original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    api('POST', '/api/admin/system/authorization/roles', {'id': 114, 'code': current['code'], 'name': current['name'],
        'description': current['description'], 'expectedVersion': current['version'],
        'permissions': original['permissions'], 'fieldPolicies': original['fieldPolicies']}, admin)
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    facts['role_restore_publication'] = api('POST', '/api/admin/system/authorization/roles/114/publish', {
        'expectedVersion': current['version'], 'reason': '普通条件页面组合验收结束，准确恢复本轮前既有权限与字段策略'}, admin)
    restored = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    assert restored['permissions'] == original['permissions'] and restored['fieldPolicies'] == original['fieldPolicies']
    facts['role_restored'] = True
    print('角色114原有权限和字段策略已逐项一致恢复。')
else:
    raise AssertionError('未知验收检查点')
facts['checked_time'] = datetime.now().astimezone().isoformat()
facts_path.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
