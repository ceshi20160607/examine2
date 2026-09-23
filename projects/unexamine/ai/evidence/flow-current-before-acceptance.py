"""B09.4c 真实整组暂停恢复；只操作随机隔离夹具，凭证不写入事实或输出。"""
import ast
import copy
import json
import os
import re
import secrets
import sys
import time
from pathlib import Path
from datetime import datetime

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in names], type_ignores=[]), filename, 'exec'))
PATH = AREA / 'flow-current-before-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.4c', 'status': 'running', 'code': 'before_' + secrets.token_hex(5), 'fields': {},
    'flows': [], 'actions': [], 'platform_instances': [], 'records': [], 'cases': []}
assert facts['status'] == 'running', '封存事实不重跑'
java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
platform = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']; legacy_sql = sql
# 复用已验证的调用函数，不执行旧阶段，不读取旧完成状态作为产品证据。
tree = ast.parse((AREA / 'flow-instance-graph-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef)], type_ignores=[]), 'graph-helpers', 'exec'))


def ordinary_tokens():
    token = api('POST', '/api/auth/login', {'username': facts['registration']['username'],
        'password': os.environ['UNEXAMINE_BEFORE_GUI_PASSWORD']})['accessToken']
    system = api('POST', '/api/systems/54/tenants/57/enter', {}, token)['tokens']['accessToken']
    return token, system


def before(iid, token, people, kind='APPROVAL', name='当前之前独立人工'):
    body = request(iid, kind=kind, token=token, people=people, name=name); body['position'] = 'BEFORE_CURRENT'
    value = add(iid, body, token)
    replay = api('POST', f'/api/flow-runtime/instances/{iid}/runtime-nodes', body, token)
    assert replay['replayed'] and replay['actionId'] == value['actionId']
    assert replay['instance']['tasks'] == value['instance']['tasks']
    assert replay['instance']['version'] == value['instance']['version']
    return value['instance']


def original_tasks(iid, token):
    return [t for t in read(iid, token)['tasks'] if t['nodeKey'] == 'review']


def snapshot(iid):
    return sql(f"SELECT id,status,version,HEX(assignee_snapshot_json),assignee_account_id,assignee_tenant_member_id FROM flow_task WHERE instance_id={iid} AND node_key='review' ORDER BY id")


def terminal(iid, token, action='REJECT'):
    task = next(t for t in read(iid, token)['tasks'] if t['status'] == 'PENDING' and t['currentHandler'])
    api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': action, 'comment': '前插终态真实验收',
        'idempotencyKey': code + secrets.token_hex(6)}, token)
    assert read(iid, token)['status'] == ('REJECTED' if action == 'REJECT' else 'RETURNED')
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id={iid} AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={iid} AND status IN('PENDING','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id={iid} AND status='SUSPENDED'") == [['0']]


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id'); save()
    username = code + '_gui'
    reg = api('POST', '/api/auth/register', {'username': username, 'password': os.environ['UNEXAMINE_BEFORE_GUI_PASSWORD'],
        'displayName': '整组前插页面成员', 'systemName': '整组前插隔离系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}
    aid = facts['gui_account'] = reg['accountId']; pmid = int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0]); facts['gui_platform_member'] = pmid; save()
    rid = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','整组前插页面夹具','仅本地验证临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); facts['platform_role_id'] = rid; save()
    for action in ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE', 'ADD_MANUAL_NODE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['ordinary_platform_role_baseline'] = sql(f'SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id={pmid} ORDER BY id')
    facts['ordinary_platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{pmid},{rid}); SELECT LAST_INSERT_ID()')[0][0]); save()
    facts['api_member'] = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)['tenantMemberId']; save()
    mid = api('POST', '/api/admin/module-config/modules', {'name': '整组前插原动作验收', 'code': code,
        'groupName': '整组前插隔离验收'}, admin)['module']['id']; facts['module_id'] = mid; save()
    for index, (field, name, kind) in enumerate([('subject', '业务主题', 'TEXT'), ('score', '业务数量', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
        facts['fields'][field] = api('POST', f'/api/admin/module-config/modules/{mid}/fields', {'code': field,
            'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
    publish_module(mid); grant(); facts['prepared'] = True; save()
    print(json.dumps({'prepared': True, 'username': username, 'account': aid}))
elif stage == 'api':
    assert facts.get('prepared') and not facts.get('api_started'); facts['api_started'] = True; save()
    other, other_system = ordinary_tokens(); aid = facts['gui_account']; root = 1
    fid, _, _ = make('部分会签先暂停再恢复', realm='platform', mode='ALL_SIGN', timeout=True, people=[root, aid])
    iid = platform_start(fid, '部分已审批整组前插页面'); facts['gui_instance'] = iid; facts['gui_flow'] = fid; save()
    baseline = platform_start(fid, '同平台模板另一实例原组'); facts['baseline_instance'] = baseline; save()
    approve(iid, platform); old = original_tasks(iid, platform); frozen = snapshot(iid)
    assert [t['status'] for t in old] == ['APPROVE', 'PENDING']
    # 只提前隔离原待办的期限，使其在暂停期间真实过期；新增节点仍按原冻结60分钟策略。
    remaining = next(t for t in old if t['status'] == 'PENDING'); taskid = remaining['id']
    sql(f"UPDATE flow_task SET due_at=NOW()+INTERVAL 3 SECOND WHERE id={taskid} AND instance_id={iid}; UPDATE flow_job SET next_run_at=NOW()+INTERVAL 3 SECOND WHERE instance_id={iid} AND job_type='TASK_TIMEOUT' AND JSON_EXTRACT(payload_json,'$.taskId')={taskid} AND status='PENDING'")
    value = before(iid, platform, [root]); outer = value['currentNodeKey']
    assert [t['status'] for t in original_tasks(iid, platform)] == ['APPROVE', 'SUSPENDED']
    assert snapshot(iid)[0] == frozen[0]
    assert sql(f"SELECT status FROM flow_job WHERE instance_id={iid} AND JSON_EXTRACT(payload_json,'$.taskId')={taskid}") == [['SUSPENDED']]
    assert 'end' in [a['nodeKey'] for a in value['runtimeNodeAnchors']] and 'review' not in [a['nodeKey'] for a in value['runtimeNodeAnchors']]
    before(iid, platform, [root], 'NOTIFICATION', '当前之前即时真实抄送')
    assert read(iid, platform)['currentNodeKey'] == outer
    nested = before(iid, platform, [root], name='嵌套当前之前人工')['currentNodeKey']
    approve(iid, platform); assert read(iid, platform)['currentNodeKey'] == outer
    assert next(t for t in read(iid, platform)['tasks'] if t['nodeKey'] == nested)['status'] == 'APPROVE'
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if sql(f"SELECT COUNT(*) FROM flow_task WHERE id={taskid} AND due_at<NOW()") == [['1']]: break
        time.sleep(.2)
    else: raise AssertionError('未建立暂停期间原时限真实已到期条件')
    # 等待worker至少一轮；其只扫描PENDING，暂停job的attempt与异常应保持零。
    worker_before = sql(f"SELECT version,attempt_count FROM flow_job WHERE instance_id={iid} AND status='SUSPENDED'")
    facts['paused_worker_instance'] = iid; facts['paused_job_task'] = taskid; facts['paused_worker_before'] = worker_before; save()
    case('actual_partial_all_sign_whole_group_pause_keeps_approved_fact_nested_cc_and_approval_same_original_ids', instance_id=iid)
    facts['api_initial_verified'] = True; save(); print(json.dumps({'api_initial_verified': True, 'gui_instance': iid}))
elif stage == 'api_more':
    assert facts.get('api_initial_verified') and not facts.get('api_more_started'); facts['api_more_started'] = True; save()
    other, other_system = ordinary_tokens(); aid = facts['gui_account']; root = 1
    iid = facts['gui_instance']; taskid = facts['paused_job_task']
    control = facts['baseline_instance']; control_task = next(t for t in read(control, platform)['tasks'] if t['currentHandler'])['id']
    sql(f"UPDATE flow_task SET due_at=NOW()-INTERVAL 2 SECOND WHERE id={control_task} AND instance_id={control}; UPDATE flow_job SET next_run_at=NOW()-INTERVAL 2 SECOND WHERE instance_id={control} AND JSON_EXTRACT(payload_json,'$.taskId')={control_task} AND status='PENDING'")
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if sql(f"SELECT status FROM flow_job WHERE instance_id={control} AND JSON_EXTRACT(payload_json,'$.taskId')={control_task}") == [['COMPLETED']]: break
        time.sleep(.2)
    else: raise AssertionError('worker未完成同夹具已到期对照作业')
    assert read(control, platform)['graphRevision'] == 0
    assert sql(f"SELECT version,attempt_count FROM flow_job WHERE instance_id={iid} AND status='SUSPENDED'") == facts['paused_worker_before']
    assert sql(f"SELECT COUNT(*) FROM flow_exception WHERE instance_id={iid}") == [['0']]
    approve(iid, platform)
    value = read(iid, platform); assert value['currentNodeKey'] == 'review'
    assert [t['status'] for t in original_tasks(iid, platform)] == ['APPROVE', 'PENDING']
    assert next(t for t in original_tasks(iid, platform) if t['status'] == 'PENDING')['id'] == taskid
    assert sql(f"SELECT status FROM flow_job WHERE instance_id={iid} AND JSON_EXTRACT(payload_json,'$.taskId')={taskid}") == [['PENDING']]
    case('actual_worker_control_reminder_completed_skips_expired_suspended_job_then_same_task_and_job_resume_with_frozen_remaining_time', instance_id=iid, control_instance=control)
    fid, _, _ = make('顺序组暂停保留排队', realm='platform', mode='SEQUENTIAL', timeout=True, people=[root, aid])
    iid = platform_start(fid, '顺序会签前插'); old = original_tasks(iid, platform)
    assert [t['status'] for t in old] == ['PENDING', 'QUEUED'] and old[1]['dueAt'] is None
    before(iid, platform, [root]); assert [t['status'] for t in original_tasks(iid, platform)] == ['SUSPENDED', 'SUSPENDED']
    approve(iid, platform); restored = original_tasks(iid, platform)
    assert [(t['id'], t['status']) for t in restored] == [(t['id'], t['status']) for t in old] and restored[1]['dueAt'] is None
    approve(iid, platform); assert original_tasks(iid, platform)[1]['status'] == 'PENDING'; approve(iid, other)
    assert read(iid, platform)['status'] == 'COMPLETED'
    case('actual_sequential_group_same_ids_original_pending_and_queued_no_early_deadline_then_normal_rotation', instance_id=iid)
    for action in ['REJECT', 'RETURN']:
        iid = platform_start(fid, '新增节点' + action + '清原暂停组'); before(iid, platform, [root]); terminal(iid, platform, action)
        case('new_before_' + action.lower() + '_cancels_all_original_suspended_tasks_jobs_executions', instance_id=iid)
    iid = platform_start(fid, '新增节点终止清嵌套暂停组'); before(iid, platform, [root]); before(iid, platform, [root])
    api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE', 'comment': '嵌套整组终止验证', 'idempotencyKey': code + secrets.token_hex(6)}, platform)
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id={iid} AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={iid} AND status IN('PENDING','RUNNING','SUSPENDED')") == [['0']]
    case('nested_before_terminate_cleans_all_groups_and_paused_jobs', instance_id=iid)
    fid, _, _ = make('原业务部分会签前插候选一次执行', mode='ALL_SIGN', timeout=True, people=[entry['tenantMemberId'], facts['api_member']])
    iid = start(fid, '原业务整组前插'); approve(iid, fields={'score': 4})
    old = original_tasks(iid, admin); before(iid, admin, [entry['tenantMemberId']]); approve(iid, fields={'score': 8})
    assert [(t['id'], t['status']) for t in original_tasks(iid, admin)] == [(t['id'], t['status']) for t in old]
    task = next(t for t in original_tasks(iid, admin) if t['status'] == 'PENDING')
    form = api('GET', f"/api/flow-runtime/tasks/{task['id']}/form", token=other_system)
    assert form['form']['record']['fields']['score'] == 8
    approve(iid, other_system, {'enabled': True})
    result = delivery(next(a for a in facts['actions'] if a['flowInstanceId'] == iid)); assert result['businessStatus'] == 'EXECUTED'
    facts['records'].append(result['recordId']); save()
    record = api('GET', f"/api/runtime/modules/{code}/records/{result['recordId']}", token=admin)
    assert record['fields']['score'] == 8 and record['fields']['enabled'] is True
    assert sql(f"SELECT COUNT(*) FROM biz_record WHERE module_id={facts['module_id']} AND archived=0") == [['1']]
    case('actual_source_create_partial_group_same_tasks_candidate4_to8_current_permissions_final_once', instance_id=iid)
    facts['api_verified'] = True; save(); print(json.dumps({'api_verified': True, 'cases': len(facts['cases'])}))
elif stage == 'edge_cases':
    assert facts.get('api_verified') and not facts.get('edge_verified')
    other, _ = ordinary_tokens(); iid = facts['gui_instance']
    paused = next(t for t in read(iid, platform)['tasks'] if t['id'] == facts['paused_job_task'])
    assert paused['status'] == 'SUSPENDED'
    old = read(iid, platform)
    status, failure = call('POST', f"/api/flow-runtime/tasks/{paused['id']}/actions", {'actionCode': 'APPROVE',
        'comment': '不能越过新增审批直接处理原暂停任务', 'idempotencyKey': code + secrets.token_hex(6)}, other)
    assert status == 409, (status, failure)
    unchanged = read(iid, platform)
    assert unchanged['tasks'] == old['tasks'] and unchanged['history'] == old['history'] and unchanged['version'] == old['version']
    case('ordinary_original_suspended_task_direct_handle_denied_no_bypass_or_new_history', instance_id=iid, error=failure['code'])
    fid, nodes, edges = make('新增节点超时拒绝清原组', realm='platform', mode='ALL_SIGN',
        people=[1, facts['gui_account']], published=False)
    next(n for n in nodes if n['nodeKey'] == 'review')['timeoutPolicy'] = {'timeoutMinutes': 60, 'action': 'AUTO_REJECT'}
    draft(fid, nodes, edges, platform); publish(fid, platform)
    iid = platform_start(fid, '前插新节点真实worker拒绝'); before(iid, platform, [1])
    new = next(t for t in read(iid, platform)['tasks'] if t['status'] == 'PENDING')
    sql(f"UPDATE flow_task SET due_at=NOW()-INTERVAL 2 SECOND WHERE id={new['id']} AND instance_id={iid}; UPDATE flow_job SET next_run_at=NOW()-INTERVAL 2 SECOND WHERE instance_id={iid} AND JSON_EXTRACT(payload_json,'$.taskId')={new['id']} AND status='PENDING'")
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if read(iid, platform)['status'] == 'REJECTED': break
        time.sleep(.2)
    else: raise AssertionError('新前插节点worker没有按冻结策略实际拒绝')
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id={iid} AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={iid} AND status IN('PENDING','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id={iid} AND status='SUSPENDED'") == [['0']]
    case('actual_new_before_auto_reject_worker_cleans_original_all_sign_suspended_tasks_jobs_executions', instance_id=iid)
    facts['edge_verified'] = True; save(); print(json.dumps({'edge_verified': True}))
elif stage == 'gui_interim':
    assert facts.get('suspended_restart_verified') and not facts.get('gui_verified')
    iid = facts['gui_instance']; value = read(iid, platform)
    assert next(t for t in value['tasks'] if t['id'] == facts['paused_job_task'])['status'] == 'SUSPENDED'
    assert value['currentNodeName'] == '页面当前之前补充核对'
    assert len([t for t in value['tasks'] if t['status'] == 'PENDING']) == 1
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='前插核对完成后真实抄送'") == [['0']]
    facts['template_before_gui_completion'] = sql('SELECT id,version_number,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id IN(' + ','.join(map(str, facts['flows'])) + ') ORDER BY id')
    facts['gui_interim_verified'] = True; case('gui_current_before_after_cc_configured_original265_suspended_no_early_cc', instance_id=iid)
    print(json.dumps({'gui_interim_verified': True}))
elif stage == 'gui_resumed':
    assert facts.get('gui_interim_verified') and not facts.get('gui_verified')
    iid = facts['gui_instance']; value = read(iid, platform)
    assert value['currentNodeKey'] == 'review' and value['status'] == 'WAITING'
    original_group = original_tasks(iid, platform)
    assert [t['status'] for t in original_group] == ['APPROVE', 'PENDING']
    assert original_group[1]['id'] == facts['paused_job_task']
    assert sql(f"SELECT m.content_text,r.account_id FROM msg_message m JOIN msg_recipient r ON r.message_id=m.id WHERE m.target_type='FLOW_INSTANCE' AND m.target_id='{iid}' AND m.subject='前插核对完成后真实抄送'") == [['新增审批处理完成后通知，再恢复原会签', str(facts['gui_account'])]]
    assert sql(f"SELECT attempt_count,status FROM flow_job WHERE instance_id={iid} AND JSON_EXTRACT(payload_json,'$.taskId')={facts['paused_job_task']}") == [['1', 'COMPLETED']]
    facts['gui_resumed_verified'] = True; case('actual_gui_new_approval_then_cc_once_to_selected97_resumes_original265_completed_reminder_not_repeated', instance_id=iid)
    print(json.dumps({'gui_resumed_verified': True}))
elif stage == 'gui':
    assert facts.get('api_verified') and not facts.get('gui_verified')
    value = read(facts['gui_instance'], platform); assert value['status'] == 'COMPLETED'
    assert all(t['status'] == 'APPROVE' for t in value['tasks'])
    assert len(original_tasks(facts['gui_instance'], platform)) == 2
    assert facts.get('gui_resumed_verified')
    assert read(facts['baseline_instance'], platform)['graphRevision'] == 0
    facts['gui_verified'] = True; case('ordinary_gui_current_before_preview_confirm_reload_approval_restores_same_old_task_then_actual_completed_selection')
    facts['restart_before'] = {str(iid): read(iid, platform if iid in facts['platform_instances'] else admin)
        for iid in [facts['gui_instance'], facts['baseline_instance'], facts['actions'][0]['flowInstanceId']]}; save()
    print(json.dumps({'gui_verified': True}))
elif stage == 'suspended_restart_prepare':
    assert facts.get('api_verified') and not facts.get('suspended_restart_verified')
    value = read(facts['gui_instance'], platform)
    assert next(t for t in value['tasks'] if t['id'] == facts['paused_job_task'])['status'] == 'SUSPENDED'
    assert value['currentNodeName'] == '页面当前之前补充核对'
    facts['suspended_restart_before'] = value
    facts['template_before_gui_completion'] = sql('SELECT id,version_number,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id IN(' + ','.join(map(str, facts['flows'])) + ') ORDER BY id')
    save(); print(json.dumps({'suspended_restart_prepared': True}))
elif stage == 'suspended_restart_read':
    old = facts['suspended_restart_before']; value = read(facts['gui_instance'], platform)
    for key in ['version', 'status', 'currentNodeKey', 'graphRevision', 'definitionSnapshot', 'history', 'tasks', 'jobs']:
        assert value[key] == old[key], key
    facts['suspended_restart_verified'] = True; case('actual_pending_before_restart_original_same_id_still_suspended_new_waiting_and_frame_jobs_history_persistent')
    print(json.dumps({'suspended_restart_verified': True}))
elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_verified')
    for iid, old in facts['restart_before'].items():
        value = read(int(iid), platform if int(iid) in facts['platform_instances'] else admin)
        for key in ['version', 'status', 'currentNodeKey', 'graphRevision', 'definitionSnapshot', 'history', 'tasks']:
            assert value[key] == old[key], (iid, key)
    facts['restart_verified'] = True; case('same_backend_restart_persistent_frames_graph_restored_same_tasks_history'); print(json.dumps({'restart_verified': True}))
elif stage == 'terminal_execution_regression':
    assert facts.get('api_verified') and not facts.get('terminal_execution_verified')
    fid = facts['gui_flow']; iid = platform_start(fid, '整组终止同时关闭新旧执行')
    before(iid, platform, [1]); before(iid, platform, [1])
    api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE',
        'comment': '整组与当前新增执行的终态验证', 'idempotencyKey': code + secrets.token_hex(6)}, platform)
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id={iid} AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={iid} AND status IN('PENDING','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id={iid} AND status IN('ACTIVE','SUSPENDED')") == [['0']]
    facts['terminal_execution_verified'] = True; case('final_current_terminal_shared_cleanup_closes_current_new_active_and_all_nested_original_executions', instance_id=iid)
    print(json.dumps({'terminal_execution_verified': True}))
elif stage == 'cleanup':
    assert facts.get('api_verified') and facts.get('gui_verified') and facts.get('restart_verified') and facts.get('suspended_restart_verified') and facts.get('edge_verified') and not facts.get('cleanup_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    owned = [(a['flowInstanceId'], admin) for a in facts['actions']] + [(iid, platform) for iid in facts['platform_instances']]
    for iid, token in owned:
        if read(iid, token)['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
            api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE',
                'comment': '整组前插验收夹具关闭', 'idempotencyKey': code + secrets.token_hex(6)}, token)
    for rid in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': record['version'], 'reason': '整组前插验收归档'}, admin)
    selected = ','.join(str(iid) for iid, _ in owned)
    # 初始夹具终止沿用旧实现留下当前ACTIVE执行，新版已实测闭合；只收尾本包历史夹具，不当作新版证明。
    facts['initial_terminal_active_fixture_cleanup'] = sql(f"SELECT e.id,e.instance_id,e.node_key FROM flow_execution e JOIN flow_instance i ON i.id=e.instance_id WHERE i.id IN({selected}) AND i.status IN('TERMINATED','WITHDRAWN','REJECTED','RETURNED','COMPLETED') AND e.status='ACTIVE'")
    sql(f"UPDATE flow_execution e JOIN flow_instance i ON i.id=e.instance_id SET e.status='CANCELLED',e.result_code=i.status,e.left_at=COALESCE(i.finished_at,NOW()),e.version=e.version+1 WHERE i.id IN({selected}) AND i.status IN('TERMINATED','WITHDRAWN','REJECTED','RETURNED','COMPLETED') AND e.status='ACTIVE'"); save()
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({selected}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({selected}) AND status IN('PENDING','RUNNING','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({selected}) AND status IN('ACTIVE','SUSPENDED')") == [['0']]
    assert sql('SELECT id,version_number,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id IN(' + ','.join(map(str, facts['flows'])) + ') ORDER BY id') == facts['template_before_gui_completion']
    mid = facts['module_id']; flows = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flows}) AND platform_id=1")
    rid, link, pmid = facts['platform_role_id'], facts['ordinary_platform_link_id'], facts['gui_platform_member']
    sql(f"DELETE FROM plat_member_role WHERE id={link} AND platform_id=1 AND member_id={pmid} AND role_id={rid}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid} AND platform_id=1 AND code='{code}'")
    assert sql(f'SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id={pmid} ORDER BY id') == facts['ordinary_platform_role_baseline']
    registration = facts['registration']; aid, sid = registration['account_id'], registration['system_id']
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE auth_session SET revoked=1,revoked_reason='current-before-acceptance-closed' WHERE account_id={aid} AND revoked=0; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{registration['username']}' AND status='ACTIVE'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid} AND status='ACTIVE'")
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == [['4']]
    assert sql(f'SELECT COUNT(*) FROM biz_record WHERE module_id={mid} AND archived=0') == [['0']]
    group = sql(f'SELECT group_id FROM cfg_module WHERE id={mid}')
    if group and group[0][0] != 'NULL':
        gid = int(group[0][0]); sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={gid} AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id={gid} AND status='ACTIVE')")
    facts['cleanup_verified'] = True; case('exact_owned_identity_role_fixture_cleanup_archives_golden_unchanged_no_pending_paused_tasks_jobs_execution', records_archived=len(facts['records']))
    print(json.dumps({'cleanup_verified': True}))
else:
    raise AssertionError('未知阶段')
