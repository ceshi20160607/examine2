"""B09.2c.5 会签全部同意与比例阈值的定向真实验收。"""
import ast
import copy
import json
import os
import re
import secrets
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
        or isinstance(node, ast.FunctionDef) and node.name in names], type_ignores=[]), filename, 'exec'))

PATH = AREA / 'flow-approval-ratio-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.5', 'status': 'running', 'code': 'ratio_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'actions': [], 'platform_instances': [], 'records': [], 'cases': []}
assert facts['status'] == 'running', '封存事实不重跑'
java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
platform = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']

# 只加载已验证的本地身份、流程、模块辅助函数，不运行任何旧验收阶段。
tree = ast.parse((AREA / 'flow-instance-graph-acceptance.py').read_text(encoding='utf-8'))
helpers = {'save', 'grant', 'draft', 'publish', 'make', 'start', 'platform_start', 'read', 'approve'}
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)
    and node.name in helpers], type_ignores=[]), 'graph-helpers', 'exec'))
tree = ast.parse((AREA / 'flow-task-claim-acceptance.py').read_text(encoding='utf-8'))
helpers = {'state', 'act'}
exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)
    and node.name in helpers], type_ignores=[]), 'task-helpers', 'exec'))


def completion_nodes(nodes, mode, ratio=None, allow_sign=False):
    """把会签完成策略写入审批节点；页面和服务端使用同一结构。"""
    value = copy.deepcopy(nodes)
    config = value[1]['config']
    config['approvalMode'] = 'ALL_SIGN'
    config['allSignCompletionPolicy'] = {'mode': mode} if mode == 'ALL' else {
        'mode': 'RATIO', 'ratioPercent': ratio}
    config['manualOperationPolicy'] = {
        'allowedActions': ['APPROVE'] + (['ADD_SIGN', 'REMOVE_SIGN'] if allow_sign else []),
        'opinionRequiredActions': ['ADD_SIGN', 'REMOVE_SIGN'] if allow_sign else []}
    return value


def credentials(registration):
    token = api('POST', '/api/auth/login', {'username': registration['username'],
        'password': os.environ['UNEXAMINE_RATIO_GUI_PASSWORD']})['accessToken']
    system = api('POST', '/api/systems/54/tenants/57/enter', {}, token)['tokens']['accessToken']
    return token, system


def publication_invalid(label, approval_mode, policy):
    fid, nodes, edges = make(label, realm='platform', mode=approval_mode, people=[1, facts['account_a']], published=False)
    nodes = copy.deepcopy(nodes)
    nodes[1]['config']['approvalMode'] = approval_mode
    nodes[1]['config']['allSignCompletionPolicy'] = policy
    draft(fid, nodes, edges, platform)
    checked = api('GET', f'/api/flows/{fid}/publication-check', token=platform)
    assert not checked['valid'] and any(issue['code'] == 'FLOW_ALL_SIGN_COMPLETION_INVALID' for issue in checked['issues'])
    assert sql(f'SELECT COUNT(*) FROM flow_version WHERE flow_id={fid}') == [['0']]
    return fid


def task_for(view, account_id):
    return next(task for task in view['tasks'] if task['status'] == 'PENDING'
        and any(candidate['accountId'] == account_id for candidate in task['candidates']))


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    facts['golden_count'] = sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0'); save()
    registrations = []
    for suffix, display in [('a', '比例会签成员甲'), ('b', '比例会签成员乙')]:
        username = f'{code}_{suffix}'
        registered = api('POST', '/api/auth/register', {'username': username,
            'password': os.environ['UNEXAMINE_RATIO_GUI_PASSWORD'], 'displayName': display,
            'systemName': display + '隔离系统'})
        registration = {'username': username, 'account_id': registered['accountId'], 'system_id': registered['systemId']}
        registrations.append(registration); facts[f'registration_{suffix}'] = registration; save()
    facts['account_a'], facts['account_b'] = [item['account_id'] for item in registrations]
    role_id = facts['platform_role_id'] = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','比例会签页面夹具','仅本地验收临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); save()
    for action in ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{role_id},'FLOW','PLATFORM','{action}','ALLOW')")
    for action in ['VIEW', 'HANDLE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{role_id},'TODO','PLATFORM','{action}','ALLOW')")
    facts['platform_links'] = []
    for registration in registrations:
        member = int(sql(f"SELECT id FROM plat_member WHERE platform_id=1 AND account_id={registration['account_id']}")[0][0])
        link = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{member},{role_id}); SELECT LAST_INSERT_ID()')[0][0])
        facts['platform_links'].append(link); save()
    facts['member_a'] = api('POST', '/api/admin/system/authorization/members', {
        'account': registrations[0]['username'], 'roleIds': [114]}, admin)['tenantMemberId']; save()
    facts['member_b'] = api('POST', '/api/admin/system/authorization/members', {
        'account': registrations[1]['username'], 'roleIds': [114]}, admin)['tenantMemberId']; save()
    module = api('POST', '/api/admin/module-config/modules', {'name': '比例会签来源一次验收',
        'code': code, 'groupName': '比例会签隔离验收'}, admin)['module']
    facts['module_id'] = module['id']; save()
    for index, (field, name, kind) in enumerate([('subject', '业务主题', 'TEXT'), ('score', '业务数量', 'NUMBER'),
            ('enabled', '是否启用', 'BOOLEAN')]):
        facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {
            'code': field, 'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
    publish_module(module['id']); grant()
    # 页面先保存“全部同意”，随后用真实设计页切换为比例并发布。
    gui_flow, gui_nodes, gui_edges = make('页面比例配置与运行展示', realm='platform', mode='ALL_SIGN',
        timeout=True, people=[1, facts['account_a'], facts['account_b']], published=False)
    gui_nodes = completion_nodes(gui_nodes, 'ALL')
    draft(gui_flow, gui_nodes, gui_edges, platform); publish(gui_flow, platform)
    facts['gui_flow'] = gui_flow
    facts['prepared'] = True; save()
    print(json.dumps({'prepared': True, 'gui_flow': gui_flow, 'platform_username': re.search(r'default-admin\.username:([^}]+)', java).group(1)}))

elif stage in ['api', 'api_resume', 'api_concurrent_resume']:
    assert facts.get('prepared') and not facts.get('api_verified')
    token_a, system_a = credentials(facts['registration_a'])
    token_b, system_b = credentials(facts['registration_b'])
    if stage == 'api':
        facts['api_started'] = True; save()
        # 设计校验拒绝把比例用于或签，也拒绝越界比例，不能生成任何冻结版本。
        publication_invalid('非法或签比例不可发布', 'OR_SIGN', {'mode': 'RATIO', 'ratioPercent': 60})
        publication_invalid('非法零比例不可发布', 'ALL_SIGN', {'mode': 'RATIO', 'ratioPercent': 0})
        facts['cases'] += ['invalid-or-sign-ratio-no-version', 'invalid-out-of-range-ratio-no-version']; save()

        # 缺省旧版本继续按全部同意：第一票不得提前通过。
        legacy, _, _ = make('旧缺省会签仍需全部同意', realm='platform', mode='ALL_SIGN', people=[1, facts['account_a']])
        legacy_instance = platform_start(legacy, '旧会签默认全部同意')
        act(legacy_instance, platform)
        view = read(legacy_instance, token_a)
        assert view['status'] == 'WAITING' and len([task for task in view['tasks'] if task['status'] == 'PENDING']) == 1
        policy = next(task for task in view['tasks'] if task['status'] == 'PENDING')['assigneeSnapshot']['allSignCompletionPolicy']
        assert policy == {'mode': 'ALL'}
        act(legacy_instance, token_a)
        assert read(legacy_instance, platform)['status'] == 'COMPLETED'
        facts['cases'].append('legacy-absent-policy-freezes-all-and-requires-every-approval'); save()

        # 66% 两人会签先有一票，加签后按当前三人计算；第二票达阈值并结束新增人的任务和超时作业。
        main, nodes, edges = make('系统比例会签加签后来源一次', mode='ALL_SIGN', timeout=True,
            people=[entry['tenantMemberId'], facts['member_a']], published=False)
        nodes = completion_nodes(nodes, 'RATIO', 66, allow_sign=True)
        draft(main, nodes, edges, admin); publish(main)
        facts['main_flow'] = main; save()
    elif stage == 'api_resume':
        assert set(facts['cases']) == {'invalid-or-sign-ratio-no-version',
            'invalid-out-of-range-ratio-no-version', 'legacy-absent-policy-freezes-all-and-requires-every-approval'}
        assert not facts.get('main_instance')
        if 'enabled' not in facts['fields']:
            facts['fields']['enabled'] = api('POST', f"/api/admin/module-config/modules/{facts['module_id']}/fields", {
                'code': 'enabled', 'name': '是否启用', 'fieldType': 'BOOLEAN', 'required': False,
                'sortOrder': 2, 'config': {}}, admin)
            publish_module(facts['module_id']); grant()
        facts['api_started'] = True
        facts['main_flow'] = facts['flows'][-1]
        facts.setdefault('harness_corrections', []).append(
            'b3e6c4: source helper submits enabled; module field was absent, request stopped before action/instance; publish missing field and continue exact published flow')
        save(); main = facts['main_flow']
    else:
        assert set(facts['cases']) == {'invalid-or-sign-ratio-no-version', 'invalid-out-of-range-ratio-no-version',
            'legacy-absent-policy-freezes-all-and-requires-every-approval', 'ratio-denominator-includes-added-participant',
            'ratio-threshold-cancels-remainder-timeout-and-audits', 'approved-source-executes-once'}
        concurrent_instance = facts['platform_instances'][-1]
        facts.setdefault('harness_corrections', []).append(
            '229725: public task does not expose internal assigneeAccountId; no concurrent approval submitted, continue same tasks by public candidate accountId')
        save()
    if stage != 'api_concurrent_resume':
        main_instance = start(main, '比例会签真实来源动作')
        facts['main_instance'] = main_instance; save()
        approve(main_instance, system_a)
        waiting = read(main_instance, admin)
        assert waiting['status'] == 'WAITING'
        assert all(task['assigneeSnapshot']['allSignCompletionPolicy'] == {'mode': 'RATIO', 'ratioPercent': 66}
            for task in waiting['tasks'])
        before_ids = {task['id'] for task in waiting['tasks']}
        _, _, added = act(main_instance, admin, 'ADD_SIGN', '增加后按当前三人计算', targetTenantMemberId=facts['member_b'])
        added_task = next(task for task in added['instance']['tasks'] if task['id'] not in before_ids)
        assert added_task['status'] == 'PENDING' and added_task['assigneeSnapshot']['allSignCompletionPolicy'] == {
            'mode': 'RATIO', 'ratioPercent': 66}
        added_job_id = int(sql(f"SELECT id FROM flow_job WHERE instance_id={main_instance} AND job_type='TASK_TIMEOUT' AND JSON_EXTRACT(payload_json,'$.taskId')={added_task['id']}")[0][0])
        approve(main_instance, admin)
        completed = read(main_instance, admin)
        assert completed['status'] == 'COMPLETED'
        assert next(task for task in completed['tasks'] if task['id'] == added_task['id'])['status'] == 'CANCELLED'
        assert next(job for job in completed['jobs'] if job['id'] == added_job_id)['status'] == 'CANCELLED'
        assert len([event for event in completed['history'] if event['eventName'] == '会签比例已达到，剩余待办已结束']) == 1
        source_action = next(action for action in facts['actions'] if action['flowInstanceId'] == main_instance)
        result = delivery(source_action); assert result['businessStatus'] == 'EXECUTED'
        facts['records'].append(result['recordId']); save()
        assert delivery(source_action)['recordId'] == result['recordId']
        assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
        facts['cases'] += ['ratio-denominator-includes-added-participant',
            'ratio-threshold-cancels-remainder-timeout-and-audits', 'approved-source-executes-once']; save()

        # 67% 三人会签需要三票；最后两人真正并发时由实例锁串行，只生成一个流程终态。
        concurrent, nodes, edges = make('并发最后两票比例会签', realm='platform', mode='ALL_SIGN',
            people=[1, facts['account_a'], facts['account_b']], published=False)
        nodes = completion_nodes(nodes, 'RATIO', 67)
        draft(concurrent, nodes, edges, platform); publish(concurrent, platform)
        concurrent_instance = platform_start(concurrent, '并发最后两票只完成一次')
        act(concurrent_instance, platform)
    view = read(concurrent_instance, platform)
    task_a = task_for(view, facts['account_a']); task_b = task_for(view, facts['account_b'])
    requests = [(task_a['id'], token_a), (task_b['id'], token_b)]
    def submit(item):
        task_id, token = item
        return call('POST', f'/api/flow-runtime/tasks/{task_id}/actions', {
            'actionCode': 'APPROVE', 'comment': '并发比例会签确认',
            'idempotencyKey': code + secrets.token_hex(6)}, token)
    with ThreadPoolExecutor(max_workers=2) as executor:
        responses = list(executor.map(submit, requests))
    assert [status for status, _ in responses] == [200, 200], responses
    concurrent_done = read(concurrent_instance, platform)
    assert concurrent_done['status'] == 'COMPLETED'
    assert [task['status'] for task in concurrent_done['tasks']].count('APPROVE') == 3
    assert len([action for action in concurrent_done['actions'] if action['actionCode'] == 'AUTO_END']) == 1
    facts['cases'].append('concurrent-final-votes-serialize-and-complete-source-once'); save()
    facts['api_verified'] = True; save()
    print(json.dumps({'api': 'PASS', 'cases': len(facts['cases']), 'gui_flow': facts['gui_flow']}))

elif stage == 'gui_runtime':
    assert facts.get('api_verified') and not facts.get('gui_instance')
    current = api('GET', f"/api/flows/{facts['gui_flow']}", token=platform)
    assert current['currentVersionId']
    snapshot = json.loads(sql(f"SELECT snapshot_json FROM flow_version WHERE id={current['currentVersionId']}")[0][0])
    node = next(node for node in snapshot['nodes'] if node['nodeKey'] == 'review')
    assert node['config']['allSignCompletionPolicy'] == {'mode': 'RATIO', 'ratioPercent': 60}
    facts['gui_instance'] = platform_start(facts['gui_flow'], '页面冻结比例运行展示')
    view = read(facts['gui_instance'], platform)
    assert all(task['assigneeSnapshot']['allSignCompletionPolicy'] == {'mode': 'RATIO', 'ratioPercent': 60}
        for task in view['tasks'])
    save(); print(json.dumps({'gui_instance': facts['gui_instance']}))

elif stage == 'gui_observed':
    assert facts.get('gui_instance') and not facts.get('gui_verified')
    token_a, _ = credentials(facts['registration_a'])
    act(facts['gui_instance'], platform)
    act(facts['gui_instance'], token_a)
    done = read(facts['gui_instance'], platform)
    assert done['status'] == 'COMPLETED' and [task['status'] for task in done['tasks']].count('CANCELLED') == 1
    facts['restart_state'] = state(facts['gui_instance'])
    facts['gui_verified'] = True
    facts['cases'] += ['designer-visual-ratio-save-reenter-publish', 'runtime-page-shows-frozen-ratio-rule']; save()
    print('GUI observed and completed PASS')

elif stage == 'restart':
    assert facts.get('gui_verified') and state(facts['gui_instance']) == facts['restart_state']
    assert read(facts['gui_instance'], platform)['status'] == 'COMPLETED'
    facts['restart_verified'] = True; facts['cases'].append('same-backend-restart-retains-ratio-result'); save()
    print('Restart PASS')

elif stage == 'cleanup':
    assert facts.get('restart_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    for record_id in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{record_id}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{record_id}/archive', {
            'version': record['version'], 'reason': '比例会签验收归档'}, admin)
    flow_ids = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={facts['module_id']} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={facts['module_id']} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flow_ids}) AND platform_id=1")
    links = ','.join(map(str, facts['platform_links']))
    sql(f"DELETE FROM plat_member_role WHERE id IN({links}) AND role_id={facts['platform_role_id']}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={facts['platform_role_id']} AND code='{code}'")
    for registration in [facts['registration_a'], facts['registration_b']]:
        account_id, system_id = registration['account_id'], registration['system_id']
        sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON tm.system_member_id=sm.id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={account_id}; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE auth_session SET revoked=1,revoked_reason='ratio-acceptance-closed' WHERE account_id={account_id}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account_id} AND username='{registration['username']}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system_id} AND creator_account_id={account_id}")
    instance_ids = facts['platform_instances'] + [facts['main_instance']]
    ids = ','.join(map(str, instance_ids))
    for table, unfinished in [('flow_task', "'PENDING','QUEUED','SUSPENDED'"),
            ('flow_job', "'PENDING','RUNNING','SUSPENDED'"), ('flow_execution', "'ACTIVE','SUSPENDED'")]:
        assert sql(f'SELECT COUNT(*) FROM {table} WHERE instance_id IN({ids}) AND status IN({unfinished})') == [['0']]
    assert facts['golden_baseline'] == sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
    assert facts['golden_count'] == sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0')
    group = sql(f"SELECT group_id FROM cfg_module WHERE id={facts['module_id']}")
    if group and group[0][0] != 'NULL':
        group_id = int(group[0][0])
        sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={group_id} AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id={group_id} AND status='ACTIVE')")
    facts['cleanup_verified'] = True; facts['cases'].append('exact-isolated-cleanup-and-golden-business-unchanged'); save()
    print('Cleanup PASS')

else:
    raise AssertionError('unknown stage')
