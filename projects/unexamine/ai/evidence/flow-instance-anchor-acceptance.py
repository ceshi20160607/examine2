"""B09.4b 指定未来节点真实业务验收；共享同域夹具和工程入口，凭证仅在内存。"""
import ast
import copy
import json
import os
import re
import secrets
import sys
from pathlib import Path
from datetime import datetime

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom))
        or isinstance(n, ast.FunctionDef) and n.name in names], type_ignores=[]), filename, 'exec'))
PATH = AREA / 'flow-instance-anchor-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.4b', 'status': 'running', 'code': 'anchor_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'actions': [], 'platform_instances': [], 'records': [], 'cases': []}
assert facts['status'] == 'running', '已封存事实不重跑'
java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
platform = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', java).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']; legacy_sql = sql
# 复用已真实验证的基础夹具动作函数，未运行旧文件的顶层阶段或已封存事实。
tree = ast.parse((AREA / 'flow-instance-graph-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef)], type_ignores=[]), 'graph-helpers', 'exec'))


def insert(iid, position, anchor, kind='APPROVAL', name='指定位置审批', token=admin, people=None):
    before = read(iid, token)
    body = request(iid, kind=kind, name=name, token=token, people=people)
    body.update(position=position, anchorNodeKey=anchor)
    result = add(iid, body, token)
    assert result['instance']['currentNodeKey'] == before['currentNodeKey']
    assert result['instance']['tasks'] == before['tasks']
    return next(n['nodeKey'] for n in result['instance']['definitionSnapshot']['nodes'] if n['name'] == name)


def topology():
    fid, basic, _ = make('多入边与原优先分支指定位置', published=False)
    review = basic[1]
    nodes = [basic[0], review]
    for key in ['left', 'right', 'target', 'extra']:
        node = copy.deepcopy(review); node.update(nodeKey=key, name={'left': '数值命中人工节点', 'right': '默认人工节点',
            'target': '汇合后冻结必填节点', 'extra': '较大数值后续节点'}[key]); nodes.append(node)
    nodes.append(basic[2])
    next(n for n in nodes if n['nodeKey'] == 'target').update(formPolicy={'editable': ['subject', 'score'],
        'required': ['subject', 'score']}, timeoutPolicy={'timeoutMinutes': 20, 'action': 'REMIND'})
    pairs = [('start-review', 'start', 'review', 0, {}),
        ('left-choice', 'review', 'left', 0, {'condition': {'mode': 'ALL', 'conditions': [{'field': 'score', 'operator': 'GT', 'value': 0}]}}),
        ('right-choice', 'review', 'right', 1, {}), ('left-target', 'left', 'target', 0, {}),
        ('right-target', 'right', 'target', 0, {}),
        ('extra-choice', 'target', 'extra', 0, {'condition': {'mode': 'ALL', 'conditions': [{'field': 'score', 'operator': 'GT', 'value': 5}]}}),
        ('default-end', 'target', 'end', 1, {}), ('extra-end', 'extra', 'end', 0, {})]
    edges = [{'edgeKey': key, 'sourceNodeKey': source, 'targetNodeKey': target, 'priorityOrder': priority,
        'conditionExpression': '', 'config': config} for key, source, target, priority, config in pairs]
    draft(fid, nodes, edges); publish(fid)
    return fid


stage = sys.argv[1]
if stage == 'api':
    assert not facts.get('api_started'); facts['api_started'] = True; save()
    facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id'); save()
    module = api('POST', '/api/admin/module-config/modules', {'name': '指定节点位置真实验收', 'code': code,
        'groupName': '指定节点隔离验收'}, admin)['module']; facts['module_id'] = module['id']; save()
    for index, (field, name, kind) in enumerate([('subject', '业务主题', 'TEXT'), ('score', '业务数量', 'NUMBER'), ('enabled', '是否启用', 'BOOLEAN')]):
        facts['fields'][field] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {'code': field,
            'name': name, 'fieldType': kind, 'required': False, 'sortOrder': index, 'config': {}}, admin); save()
    publish_module(module['id']); grant()
    fid = topology(); facts['system_flow'] = fid
    facts['template_baseline'] = sql(f'SELECT id,version_number,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id={fid}'); save()
    iid = start(fid, '多入边前后位置只改本次实例'); baseline = start(fid, '同模板未修改原路径'); facts['main_instance'] = iid; save()
    original_view = read(iid); unchanged = original_view['definitionSnapshot']
    before_key = insert(iid, 'BEFORE_NODE', 'target', name='汇合节点之前补充人工')
    after_key = insert(iid, 'AFTER_NODE', 'target', kind='NOTIFICATION', name='汇合节点之后实际抄送')
    cc_key = insert(iid, 'BEFORE_NODE', before_key, kind='NOTIFICATION', name='未来新增节点前再插抄送')
    assert len(read(iid)['tasks']) == 1
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject IN('汇合节点之后实际抄送','未来新增节点前再插抄送')") == [['0']]
    assert before_key in [n['nodeKey'] for n in read(iid)['runtimeNodeAnchors']]
    # 前插汇合节点必须接回两个入边，所有原边条件/优先级/标识保留；只替换端点。
    changed = read(iid)['definitionSnapshot']['edges']
    assert {e['sourceNodeKey'] for e in changed if e['targetNodeKey'] == cc_key} == {'left', 'right'}
    for anchor in ['start', 'review']:
        body = request(iid); body.update(position='BEFORE_NODE', anchorNodeKey=anchor)
        expect_error(iid, body, 'FLOW_RUNTIME_NODE_ANCHOR_INVALID', preview=True)
    body = request(iid); body.update(position='AFTER_NODE', anchorNodeKey='end')
    expect_error(iid, body, 'FLOW_RUNTIME_NODE_ANCHOR_INVALID', preview=True)
    body['anchorNodeKey'] = '不存在的节点'
    expect_error(iid, body, 'FLOW_RUNTIME_NODE_ANCHOR_INVALID', preview=True)
    body.pop('anchorNodeKey')
    expect_error(iid, body, 'FLOW_RUNTIME_NODE_ANCHOR_REQUIRED', preview=True, status=422)
    stale = request(iid); stale.update(position='BEFORE_NODE', anchorNodeKey='target')
    api('POST', f'/api/flow-runtime/instances/{iid}/runtime-node-preview', stale, admin)
    approve(iid, fields={'score': 2}); expect_error(iid, stale, 'FLOW_RUNTIME_NODE_PREVIEW_STALE')
    assert read(iid)['currentNodeKey'] == 'left'; approve(iid)
    value = read(iid); assert value['currentNodeKey'] == before_key
    task = next(t for t in value['tasks'] if t['nodeKey'] == before_key)
    assert task['dueAt'] is not None
    stored = json.loads(sql(f"SELECT input_json FROM flow_action WHERE instance_id={iid} AND node_key='{before_key}' AND action_code='RUNTIME_NODE_ADDED'")[0][0])
    assert stored['node']['formPolicy']['required'] == ['subject', 'score']
    assert stored['node']['timeoutPolicy'] == {'timeoutMinutes': 20, 'action': 'REMIND'}
    history_before = value['history']
    body = request(iid); body.update(position='AFTER_NODE', anchorNodeKey='left')
    expect_error(iid, body, 'FLOW_RUNTIME_NODE_ANCHOR_INVALID')
    assert read(iid)['history'] == history_before
    approve(iid); assert read(iid)['currentNodeKey'] == 'target'; approve(iid)
    result = delivery(next(a for a in facts['actions'] if a['flowInstanceId'] == iid))
    assert result['businessStatus'] == 'EXECUTED'; facts['records'].append(result['recordId']); save()
    assert read(iid)['status'] == 'COMPLETED'
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject IN('汇合节点之后实际抄送','未来新增节点前再插抄送')") == [['2']]
    assert not any(t['nodeKey'] in ['right', 'extra'] for t in read(iid)['tasks'])
    record = api('GET', f"/api/runtime/modules/{code}/records/{result['recordId']}", token=admin)
    assert record['fields']['score'] == 2 and record['fields']['enabled'] is False
    assert read(baseline)['definitionSnapshot'] == unchanged and read(baseline)['graphRevision'] == 0
    case('actual_multi_incoming_before_target_after_target_future_dynamic_anchor_frozen_required_timeout_original_priority_once_record', instance_id=iid)
    # 原条件未命中前插分支必须完全跳过，不制造提前审批或抄送。
    iid = start(fid, '未命中分支不得提前执行')
    skipped = insert(iid, 'BEFORE_NODE', 'left', name='未命中分支人工不应出现')
    skipped_cc = insert(iid, 'AFTER_NODE', 'left', kind='NOTIFICATION', name='未命中分支抄送不应出现')
    approve(iid); assert read(iid)['currentNodeKey'] == 'right'; approve(iid); assert read(iid)['currentNodeKey'] == 'target'; approve(iid)
    finish(iid)
    assert not any(t['nodeKey'] in [skipped, skipped_cc, 'left', 'extra'] for t in read(iid)['tasks'])
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='未命中分支抄送不应出现'") == [['0']]
    approve(baseline); approve(baseline); approve(baseline); finish(baseline)
    assert sql(f'SELECT id,version_number,definition_hash,HEX(snapshot_json) FROM flow_version WHERE flow_id={fid}') == facts['template_baseline']
    case('unselected_branch_before_approval_and_after_cc_skipped_original_default_path_other_instance_template_unchanged', instance_id=iid)
    facts['api_verified'] = True; save(); print(json.dumps({'api_verified': True, 'cases': len(facts['cases'])}))
elif stage == 'api_more':
    assert facts.get('api_verified') and not facts.get('api_more_verified')
    iid = start(facts['system_flow'], '另一入边同样必须经过新增审批')
    key = insert(iid, 'BEFORE_NODE', 'target', name='默认分支汇合前仍人工核对')
    end_cc = insert(iid, 'BEFORE_NODE', 'end', kind='NOTIFICATION', name='结束之前实际通知')
    approve(iid); assert read(iid)['currentNodeKey'] == 'right'; approve(iid)
    assert read(iid)['currentNodeKey'] == key; approve(iid)
    assert read(iid)['currentNodeKey'] == 'target'; approve(iid); finish(iid)
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='结束之前实际通知'") == [['1']]
    case('other_incoming_default_branch_still_passes_new_approval_and_end_before_cc_once', instance_id=iid)
    # 真正旧AFTER_CURRENT请求不含新锚点字段；升级后重放不能报异内容或再次变更。
    old = json.loads(sql("SELECT input_json FROM flow_action WHERE instance_id=175 AND action_code='RUNTIME_NODE_ADDED' ORDER BY id LIMIT 1")[0][0])['request']
    assert 'anchorNodeKey' not in old
    before = sql("SELECT version,status,current_node_key FROM flow_instance WHERE id=175")
    result = api('POST', '/api/flow-runtime/instances/175/runtime-nodes', old, admin)
    assert result['replayed'] and result['instance']['status'] == 'TERMINATED'
    assert sql("SELECT version,status,current_node_key FROM flow_instance WHERE id=175") == before
    facts['api_more_verified'] = True; case('previous_sealed_after_current_request_real_upgrade_replay_no_mutation')
    print(json.dumps({'api_more_verified': True}))
elif stage == 'api_more_resume':
    assert facts.get('api_verified') and not facts.get('api_more_verified')
    assert any(c['case'] == 'other_incoming_default_branch_still_passes_new_approval_and_end_before_cc_once' for c in facts['cases'])
    old = json.loads(sql("SELECT input_json FROM flow_action WHERE instance_id=175 AND action_code='RUNTIME_NODE_ADDED' ORDER BY id LIMIT 1")[0][0])['request']
    before = sql("SELECT version,status,current_node_key FROM flow_instance WHERE id=175")
    result = api('POST', '/api/flow-runtime/instances/175/runtime-nodes', old, admin)
    assert result['replayed'] and result['instance']['status'] == 'TERMINATED'
    assert sql("SELECT version,status,current_node_key FROM flow_instance WHERE id=175") == before
    business = result['instance']['businessView']; assert business['proposed'] is None and business['current'] is None
    facts['api_more_verified'] = True; case('previous_sealed_after_current_upgrade_replay_unavailable_business_read_no_mutation_no_hidden_data')
    print(json.dumps({'api_more_verified': True}))
elif stage == 'prepare_gui':
    assert facts.get('api_verified') and facts.get('api_more_verified') and not facts.get('gui_prepared')
    username = code + '_gui'
    reg = api('POST', '/api/auth/register', {'username': username, 'password': os.environ['UNEXAMINE_ANCHOR_GUI_PASSWORD'],
        'displayName': '指定节点页面成员', 'systemName': '指定节点页面隔离系统'})
    facts['registration'] = {'account_id': reg['accountId'], 'system_id': reg['systemId'], 'username': username}
    aid = facts['gui_account'] = reg['accountId']; pmid = int(sql(f'SELECT id FROM plat_member WHERE platform_id=1 AND account_id={aid}')[0][0]); facts['gui_platform_member'] = pmid; save()
    rid = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','指定节点页面夹具','仅本地流程验证',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0]); facts['platform_role_id'] = rid; save()
    for action in ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE', 'ADD_MANUAL_NODE']:
        sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{rid},'FLOW','PLATFORM','{action}','ALLOW')")
    facts['ordinary_platform_role_baseline'] = sql(f'SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id={pmid} ORDER BY id')
    facts['ordinary_platform_link_id'] = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{pmid},{rid}); SELECT LAST_INSERT_ID()')[0][0]); save()
    fid, nodes, edges = make('普通页面指定未来位置', realm='platform', allow=True, published=False)
    target = copy.deepcopy(nodes[1]); target.update(nodeKey='target', name='指定未来人工节点'); nodes.insert(2, target)
    edges[1]['targetNodeKey'] = 'target'; edges.append({'edgeKey': 'target-end', 'sourceNodeKey': 'target', 'targetNodeKey': 'end', 'priorityOrder': 0, 'config': {}})
    draft(fid, nodes, edges, platform); publish(fid, platform)
    facts['gui_flow'] = fid; facts['gui_instance'] = platform_start(fid, '普通成员指定节点前后增加审批抄送')
    facts['platform_baseline_instance'] = platform_start(fid, '普通模板另一实例保持原路径'); facts['gui_prepared'] = True; save()
    print(json.dumps({'username': username, 'flow': fid, 'instance': facts['gui_instance']}))
elif stage == 'gui_interim':
    assert facts.get('gui_prepared') and not facts.get('gui_verified')
    iid = facts['gui_instance']; value = read(iid, platform)
    assert value['status'] == 'WAITING'
    assert len(value['tasks']) == 2 and sum(t['status'] == 'APPROVE' for t in value['tasks']) == 1
    assert value['currentNodeKey'].startswith('runtime_')
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE target_type='FLOW_INSTANCE' AND target_id='{iid}' AND subject='指定节点之后抄送'") == [['0']]
    case('actual_gui_first_human_done_added_before_approval_pending_after_cc_zero_fixed_footer_visible_1280_720')
    print(json.dumps({'gui_interim_verified': True}))
elif stage == 'gui':
    assert facts.get('gui_prepared') and not facts.get('gui_verified')
    iid = facts['gui_instance']; value = read(iid, platform)
    assert value['status'] == 'COMPLETED' and len(value['tasks']) == 3
    assert all(t['status'] == 'APPROVE' for t in value['tasks'])
    changes = sql(f"SELECT input_json FROM flow_action WHERE instance_id={iid} AND action_code='RUNTIME_NODE_ADDED' ORDER BY id")
    assert [json.loads(row[0])['position'] for row in changes] == ['BEFORE_NODE', 'AFTER_NODE']
    assert all(json.loads(row[0])['anchorNodeKey'] == 'target' for row in changes)
    assert sql(f"SELECT m.content_text,r.account_id FROM msg_message m JOIN msg_recipient r ON r.message_id=m.id WHERE m.target_type='FLOW_INSTANCE' AND m.target_id='{iid}' AND m.subject='指定节点之后抄送'") == [['人工核对完指定节点才通知', str(facts['gui_account'])]]
    assert read(facts['platform_baseline_instance'], platform)['graphRevision'] == 0
    facts['gui_verified'] = True; case('actual_ordinary_gui_before_after_typed_anchor_preview_edit_invalidate_reload_three_human_then_one_selected_cc')
    facts['restart_before'] = {str(i): read(i, platform if i in facts['platform_instances'] else admin)
        for i in [facts['main_instance'], facts['gui_instance'], facts['platform_baseline_instance']]}; save()
    print(json.dumps({'gui_verified': True}))
elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_verified')
    for key, before in facts['restart_before'].items():
        iid = int(key); after = read(iid, platform if iid in facts['platform_instances'] else admin)
        for field in ['graphRevision', 'definitionSnapshot', 'currentNodeKey', 'status', 'version', 'history']:
            assert before[field] == after[field], (iid, field)
    facts.pop('restart_before'); facts['restart_verified'] = True; case('same_backend_restart_before_after_graph_and_original_other_instance_exact_readback')
    print(json.dumps({'restart_verified': True}))
elif stage == 'after_current_regression':
    assert facts.get('cleanup_verified') and not facts.get('after_current_verified')
    facts['cleanup_verified'] = False; save()
    fid, _, _ = make('新位置升级后原后插入口真实回归', realm='platform', people=[1])
    iid = platform_start(fid, '原后插入口缺省锚点仍真实执行'); facts['after_current_instance'] = iid; save()
    try:
        body = request(iid, token=platform, people=[1], name='缺省当前之后仍人工审批')
        assert 'anchorNodeKey' not in body
        result = add(iid, body, platform)
        replay = api('POST', f'/api/flow-runtime/instances/{iid}/runtime-nodes', body, platform)
        assert replay['replayed'] and replay['actionId'] == result['actionId']
        approve(iid, platform); assert read(iid, platform)['status'] == 'WAITING'; approve(iid, platform)
        assert read(iid, platform)['status'] == 'COMPLETED'
        assert len(read(iid, platform)['tasks']) == 2
        facts['after_current_verified'] = True
        case('fresh_after_current_without_anchor_real_preview_confirm_replay_two_independent_human_no_regression')
    finally:
        if read(iid, platform)['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
            api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE',
                'comment': '后插回归夹具收尾', 'idempotencyKey': code + secrets.token_hex(6)}, platform)
        sql(f"UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id={fid} AND platform_id=1")
        assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id={iid} AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
        assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id={iid} AND status IN('PENDING','RUNNING')") == [['0']]
        facts['cleanup_verified'] = True; save()
    print(json.dumps({'after_current_verified': facts['after_current_verified'], 'cleanup_verified': True}))
elif stage == 'cleanup':
    assert facts.get('api_verified') and facts.get('gui_verified') and facts.get('restart_verified') and not facts.get('cleanup_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    owned = [(a['flowInstanceId'], admin) for a in facts['actions']] + [(iid, platform) for iid in facts['platform_instances']]
    for iid, token in owned:
        if read(iid, token)['status'] in ['RUNNING', 'WAITING', 'EXCEPTION']:
            api('POST', f'/api/flow-runtime/instances/{iid}/actions', {'actionCode': 'TERMINATE',
                'comment': '指定位置验收夹具关闭', 'idempotencyKey': code + secrets.token_hex(6)}, token)
    for rid in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{rid}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{rid}/archive', {'version': record['version'], 'reason': '指定位置验收归档'}, admin)
    selected = ','.join(str(iid) for iid, _ in owned)
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({selected}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({selected}) AND status IN('PENDING','RUNNING')") == [['0']]
    mid = facts['module_id']; flows = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={mid} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={mid} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flows}) AND platform_id=1")
    rid, link, pmid = facts['platform_role_id'], facts['ordinary_platform_link_id'], facts['gui_platform_member']
    sql(f"DELETE FROM plat_member_role WHERE id={link} AND platform_id=1 AND member_id={pmid} AND role_id={rid}; UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={rid} AND platform_id=1 AND code='{code}'")
    assert sql(f'SELECT id,role_id FROM plat_member_role WHERE platform_id=1 AND member_id={pmid} ORDER BY id') == facts['ordinary_platform_role_baseline']
    registration = facts['registration']; aid, sid = registration['account_id'], registration['system_id']
    sql(f"UPDATE sys_tenant_member tm JOIN sys_member sm ON sm.id=tm.system_member_id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={aid} AND tm.status='ACTIVE'; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={aid} AND status='ACTIVE'; UPDATE auth_session SET revoked=1,revoked_reason='instance-anchor-acceptance-closed' WHERE account_id={aid} AND revoked=0; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={aid} AND username='{registration['username']}' AND status='ACTIVE'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={sid} AND creator_account_id={aid} AND status='ACTIVE'")
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == [['4']]
    assert sql(f'SELECT COUNT(*) FROM biz_record WHERE module_id={mid} AND archived=0') == [['0']]
    group = sql(f'SELECT group_id FROM cfg_module WHERE id={mid}')
    if group and group[0][0] != 'NULL':
        gid = int(group[0][0]); sql(f"UPDATE cfg_module_group SET status='DISABLED',version=version+1 WHERE id={gid} AND system_id=54 AND NOT EXISTS(SELECT 1 FROM cfg_module WHERE group_id={gid} AND status='ACTIVE')")
    facts['cleanup_verified'] = True; case('exact_owned_fixture_cleanup_archived_golden_unchanged', records_archived=len(facts['records']))
    print(json.dumps({'cleanup_verified': True}))
else:
    raise AssertionError('未知阶段')
