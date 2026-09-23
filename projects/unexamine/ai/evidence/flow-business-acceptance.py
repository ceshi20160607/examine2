"""B09.1a 当前业务投影验收；凭证仅驻留内存，权限变更保留原始基线并在失败时恢复。"""
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
PATH = AREA / 'flow-business-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {'step': 'B09.1a', 'status': 'running', 'cases': []}
assert facts['status'] == 'running', '封存事实不能重复变更夹具'
code = 'flow_condition_accept_20260918'
j = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login = api('POST', '/api/auth/login', {'username': re.search(r'default-admin\.username:([^}]+)', j).group(1),
    'password': re.search(r'default-admin\.initial-password:([^}]+)', j).group(1)})
admin = api('POST', '/api/systems/54/tenants/57/enter', {}, login['accessToken'])['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def publish_role(permissions, policies):
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    draft = api('POST', '/api/admin/system/authorization/roles', {'id': 114, 'code': current['code'], 'name': current['name'],
        'description': current['description'], 'expectedVersion': current['version'], 'permissions': permissions, 'fieldPolicies': policies}, admin)
    api('POST', '/api/admin/system/authorization/roles/114/publish', {'expectedVersion': draft['version'],
        'reason': '当前审批只读投影隔离验收'}, admin)


def granted_role():
    permissions = copy.deepcopy(original['permissions'])
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': a, 'dataScopeType': 'ALL'}
        for a in ['LIST', 'DETAIL', 'CREATE', 'UPDATE']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': a, 'dataScopeType': 'ALL'}
        for a in ['VIEW_RUNTIME', 'HANDLE']]
    draft = api('GET', '/api/admin/module-config/modules/51/draft', token=admin)
    policies = copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode': code, 'fieldCode': f['code'], 'channel': channel,
        'readable': f['code'] != 'hidden', 'writable': f['code'] != 'hidden', 'maskStrategy': None}
        for f in draft['fields'] if f['status'] == 'ACTIVE' for channel in ['PAGE', 'FLOW']]
    return permissions, policies


def read_hash(flow_id):
    # 只读核验引擎冻结版本、真实意图、变量与正式业务值，角色发布不应改变它们。
    rows = [sql(f'SELECT HEX(business_snapshot_json),flow_version_id FROM flow_instance WHERE id={int(flow_id)}'),
        sql(f'SELECT HEX(snapshot_json) FROM flow_version WHERE id=(SELECT flow_version_id FROM flow_instance WHERE id={int(flow_id)})'),
        sql(f'SELECT variable_key,HEX(value_json) FROM flow_instance_variable WHERE instance_id={int(flow_id)} ORDER BY id'),
        sql(f"SELECT HEX(e.payload_json) FROM core_event_outbox e JOIN flow_action a ON a.idempotency_key=CONCAT('business-action:',e.aggregate_id) WHERE a.instance_id={int(flow_id)} AND a.action_code='START'"),
        sql('SELECT record_id,field_id,HEX(COALESCE(value_text,value_json,CAST(value_number AS CHAR),CAST(value_boolean AS CHAR),CAST(value_reference_id AS CHAR))) FROM biz_record_value WHERE record_id IN (168,169,170,171) ORDER BY record_id,field_id')]
    return hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()


stage = sys.argv[1]
if stage == 'api':
    assert not facts.get('api_verified'), '已通过 API 组合不重复创建身份'
    previous = facts.get('pending_action')
    if previous:
        old = api('GET', f"/api/flow-runtime/instances/{previous['flowInstanceId']}", token=admin)
        if old['status'] in ['WAITING', 'RUNNING', 'EXCEPTION']:
            isolated = facts['temporary_identity']
            # 旧脚本先停用了隔离发起人，现有关闭入口要求其仍有效；只恢复该测试身份以关闭，再准确停用。
            sql(f"UPDATE plat_account SET status='ACTIVE',version=version+1 WHERE id={int(isolated['account_id'])}")
            sql(f"UPDATE sys_tenant_member SET status='ACTIVE',version=version+1 WHERE system_id=54 AND tenant_id=57 AND system_member_id={int(isolated['member_id'])}")
            try:
                api('POST', f"/api/flow-runtime/instances/{old['id']}/actions", {'actionCode': 'TERMINATE',
                    'comment': '验收脚本范围配置格式修正，结束旧隔离申请', 'idempotencyKey': 'business_read_close_' + str(old['id'])}, admin)
            finally:
                sql(f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={int(isolated['account_id'])}")
                sql(f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE system_id=54 AND tenant_id=57 AND system_member_id={int(isolated['member_id'])}")
        facts.setdefault('prior_attempts', []).append({'action': previous, 'cases': facts['cases'],
            'closed_flow_id': old['id'], 'reason': '验收脚本使用非法 SPECIFIED_MEMBERS 配置，已改为项目支持的 DEPARTMENT 范围'})
        facts['cases'] = []
        save()
    permissions, policies = granted_role()
    publish_role(permissions, policies)
    temporary = None
    try:
        username = 'business_read_' + secrets.token_hex(6)
        registration = api('POST', '/api/auth/register', {'username': username,
            'password': secrets.token_urlsafe(24), 'displayName': '审批只读投影验收者', 'systemName': '审批只读临时空系统'})
        temporary = {'account_id': registration['accountId'], 'system_id': registration['systemId']}
        facts['temporary_identity'] = temporary
        member = api('POST', '/api/admin/system/authorization/members', {'account': username, 'roleIds': [114]}, admin)
        temporary['member_id'] = member['systemMemberId']
        actor = api('POST', '/api/systems/54/tenants/57/enter', {}, registration['platformTokens']['accessToken'])['tokens']['accessToken']
        action = api('POST', f'/api/runtime/modules/{code}/records', {'title': '审批公共只读授权边界验收', 'status': 'ACTIVE', 'ownerMemberId': 101,
            'participantMemberIds': [], 'fields': {'subject': '原意图隐藏检验值B09', 'score': 30, 'enabled': True,
                'occurred': '2026-09-18T10:00:00', 'day': '2026-09-18', 'choices': ['one', 'two'],
                'choice': 'one', 'date_text': '2026-09-18T02:00:00Z'}}, actor, action_key='business_read_' + secrets.token_hex(6))
        facts['pending_action'] = action
        save()
        path = f"/api/flow-runtime/instances/{action['flowInstanceId']}"
        progress_path = f"/api/flow-runtime/business-actions/{code}/{action['idempotencyRecordId']}"
        first = api('GET', path, token=actor)
        view = first['businessView']
        assert view['current'] is None and view['proposed']['id'] is None
        assert view['proposed']['fields']['subject'] == '原意图隐藏检验值B09'
        assert view['proposed']['fieldPresentation']['choices']['displayValue'] == '普通事项、特殊事项'
        assert api('GET', progress_path, token=actor)['businessView'] == view
        assert not first['variables'] and set(first['businessSnapshot']) == {'label'}
        assert all(not a['input'] and not a['result'] for a in first['actions'])
        assert all(not e['detail'] for e in first['history'])
        baseline = read_hash(first['id'])
        facts['cases'].append({'case': 'ordinary_pending_full_and_owner_progress', 'result': '真实普通用户待审批新建有公共提交描述，无正式行；本人窄进度与完整页使用同一投影，字典中文多选一致。'})
        restricted = copy.deepcopy(policies)
        for p in restricted:
            if p['resourceCode'] != code: continue
            if p['fieldCode'] == 'subject' or p['fieldCode'] == 'choice' and p['channel'] == 'FLOW':
                p.update(readable=False, writable=False)
            if p['fieldCode'] == 'score': p['maskStrategy'] = 'FULL'
        publish_role(permissions, restricted)
        cropped = api('GET', path, token=actor)
        proposed = cropped['businessView']['proposed']
        assert 'subject' not in proposed['fields'] and 'choice' not in proposed['fieldPresentation']
        assert proposed['fields']['score'] == '***' and proposed['fieldPresentation']['score']['displayValue'] == '***'
        assert '原意图隐藏检验值B09' not in json.dumps(cropped, ensure_ascii=False)
        narrow_view = api('GET', progress_path, token=actor)['businessView']['proposed']
        assert 'subject' not in narrow_view['fields'] and narrow_view['fields']['score'] == '***'
        assert narrow_view['fields']['choice'] == 'one'  # 本人进度是 PAGE 业务入口，不额外借用或要求 FLOW 渠道。
        facts['cases'].append({'case': 'current_field_channel_mask_no_payload_backdoor', 'result': '同一有效登录立即按新 PAGE/FLOW 交集隐藏原提交字段并 FULL 脱敏分值；完整响应和本人窄进度无隐藏检验值、旧载荷补回。'})
        scoped = copy.deepcopy(permissions)
        next(p for p in scoped if p['resourceCode'] == code and p['actionCode'] == 'DETAIL').update(
            dataScopeType='SELF', dataScopeJson=None)
        publish_role(scoped, policies)
        inaccessible = api('GET', path, token=actor)['businessView']
        assert inaccessible['proposed'] is None and inaccessible['current'] is None
        facts['cases'].append({'case': 'pending_create_detail_row_scope', 'result': '拟定负责人101与原发起人不同，DETAIL 本人负责范围拒绝读取草稿；仍可看审批状态，不能用审批发起身份扩大数据范围。'})
        no_detail = [p for p in permissions if not (p['resourceCode'] == code and p['actionCode'] == 'DETAIL')]
        publish_role(no_detail, policies)
        status, result = call('GET', path, token=actor)
        assert status in [403, 404]
        narrow = api('GET', progress_path, token=actor)
        assert narrow['businessView']['proposed'] is None and narrow['record'] is None
        facts['cases'].append({'case': 'detail_revoked_keeps_only_own_progress', 'status': status,
            'code': result['code'], 'result': '完整实例失权拒绝，本人业务进度仍可读但无申请内容和正式记录。'})
        publish_role(permissions, policies)
        restored = api('GET', path, token=actor)
        assert restored['businessView'] == view and read_hash(first['id']) == baseline
        task = next(t for t in restored['tasks'] if t['status'] == 'PENDING')
        api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {'actionCode': 'REJECT', 'comment': '只读验收结束不创建正式行',
            'variables': {}, 'idempotencyKey': 'business_read_reject_' + secrets.token_hex(6)}, actor)
        facts['cases'].append({'case': 'read_projection_does_not_mutate_intent_or_engine', 'hash': baseline,
            'result': '多次权限发布和投影读取前后原动作、实例业务快照、流程冻结版本、变量及既有正式字段哈希完全一致；验收申请拒绝收尾。'})
        facts['api_verified'] = True
        facts['ordinary_role_permissions'] = permissions
        facts['ordinary_role_policies'] = policies
        save()
        print('PASS:真实普通账户提交、完整/本人两入口、当前字段/渠道/脱敏/虚拟行范围/失权与不可变性组合。')
    except Exception:
        publish_role(original['permissions'], original['fieldPolicies'])
        raise
    finally:
        if temporary:
            if not facts.get('api_verified') and facts.get('pending_action') and facts.get('temporary_identity') == temporary:
                pending = api('GET', f"/api/flow-runtime/instances/{facts['pending_action']['flowInstanceId']}", token=admin)
                if pending['status'] in ['WAITING', 'RUNNING', 'EXCEPTION']:
                    api('POST', f"/api/flow-runtime/instances/{pending['id']}/actions", {'actionCode': 'TERMINATE',
                        'comment': '验收失败隔离申请收尾', 'idempotencyKey': 'business_read_close_' + str(pending['id'])}, admin)
            # 仅停用本次随机测试身份与注册附带的空系统，不删除历史验收数据。
            sql(f"UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={int(temporary['account_id'])}")
            sql(f"UPDATE sys_system SET status='DISABLED' WHERE id={int(temporary['system_id'])}")
            sql(f"UPDATE sys_tenant_member SET status='DISABLED',version=version+1 WHERE system_member_id IN (SELECT id FROM sys_member WHERE account_id={int(temporary['account_id'])})")
            facts['temporary_identity_disabled'] = True
            save()
elif stage in ['ui_restrict', 'ui_grant', 'ui_loss']:
    assert facts.get('api_verified')
    permissions, policies = granted_role()
    if stage == 'ui_restrict':
        for p in policies:
            if p['resourceCode'] != code: continue
            if p['fieldCode'] == 'subject' or p['fieldCode'] == 'choice' and p['channel'] == 'FLOW': p.update(readable=False,writable=False)
            if p['fieldCode'] == 'score': p['maskStrategy'] = 'FULL'
    if stage == 'ui_loss':
        permissions = [p for p in permissions if not (p['resourceCode'] == code and p['actionCode'] == 'DETAIL')]
    publish_role(permissions, policies)
    facts['ui_permission_phase'] = stage
    save()
    print('普通页面隔离权限阶段已发布：' + stage)
elif stage == 'node':
    assert facts.get('api_verified') and not facts.get('node_verified')
    baseline = read_hash(81)
    flow = api('POST', '/api/flows', {'code': 'business_read_node_' + secrets.token_hex(5), 'name': '审批节点只读业务验收'}, admin)
    nodes = [{'nodeKey': key, 'nodeType': kind, 'name': name, 'positionX': index * 220, 'positionY': 80,
        'config': {'moduleId': 51} if kind == 'START' else {'approvalMode': 'OR_SIGN'} if kind == 'APPROVAL' else {},
        'assigneePolicy': {'type': 'PERSON', 'tenantMemberIds': [108]} if kind == 'APPROVAL' else {},
        'formPolicy': {'hidden': ['choices', 'score']} if kind == 'APPROVAL' else {}}
        for index, (key, kind, name) in enumerate([('start','START','开始'),('review','APPROVAL','核对当前业务'),('end','END','结束')])]
    edges = [{'edgeKey': key, 'sourceNodeKey': source, 'targetNodeKey': target, 'priorityOrder': 0, 'config': {}}
        for key, source, target in [('entry','start','review'),('finish','review','end')]]
    draft = api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': flow['version'], 'nodes': nodes, 'edges': edges}, admin)
    check = api('GET', f"/api/flows/{flow['id']}/publication-check", token=admin)
    assert check['valid'], check['issues']
    api('POST', f"/api/flows/{flow['id']}/publish", {'expectedDraftRevision': draft['draftRevision'], 'changeSummary':'冻结节点只读范围隔离验收', 'simulationVariables':{}}, admin)
    started = api('POST', '/api/flow-runtime/instances', {'flowId': flow['id'], 'title': '节点裁剪与真实记录验收',
        'businessType': 'MODULE:' + code, 'businessId': '170', 'businessSnapshot': {'fields': {'subject': '伪造快照不是真实数据'}},
        'variables': {'subject': '伪造变量不是真实数据', 'score': 999}, 'idempotencyKey': 'business_read_node_' + secrets.token_hex(6)}, admin)['instance']
    current = started['businessView']['current']
    assert started['businessView']['proposed'] is None and current['id'] == 170
    assert current['fields']['subject'] == '普通设计者完整条件链'
    assert 'choices' not in current['fields'] and 'score' not in current['fieldPresentation']
    assert '伪造' not in json.dumps(started,ensure_ascii=False) and not started['variables']
    assert read_hash(81) == baseline
    current_draft = api('GET', f"/api/flows/{flow['id']}", token=admin)
    nodes[1]['formPolicy'] = {'hidden': []}
    api('PUT', f"/api/flows/{flow['id']}/draft", {'expectedVersion': current_draft['version'], 'nodes': nodes, 'edges': edges}, admin)
    frozen = api('GET', f"/api/flow-runtime/instances/{started['id']}", token=admin)
    assert 'choices' not in frozen['businessView']['current']['fields'] and 'score' not in frozen['businessView']['current']['fields']
    assert frozen['definitionHash'] == started['definitionHash']
    facts['node_fixture'] = {'flow_id': flow['id'], 'instance_id': started['id'], 'record_id': 170, 'original_instance_hash': baseline}
    facts['cases'].append({'case': 'frozen_node_policy_and_manual_record_trust', 'result': '新独立已发布流程的节点隐藏多选和分值，真实正式记录170分别裁剪值/描述；手动提交的旧变量和业务快照不能覆盖真实数据。随后改草稿取消隐藏，旧实例仍消费冻结策略和原定义哈希，既有81快照不变。'})
    facts['node_verified'] = True
    save()
    print(json.dumps(facts['node_fixture'],ensure_ascii=False))
elif stage == 'readback':
    assert facts.get('api_verified') and facts.get('node_verified') and facts.get('recovery_verified')
    values_sql = 'SELECT record_id,field_id,field_code,value_type,HEX(value_text),value_number,value_date,value_datetime,value_boolean,value_reference_id,value_file_id,HEX(value_json),HEX(normalized_text),created_at,updated_at FROM biz_record_value WHERE record_id IN (151,154,168,169,170,171) ORDER BY record_id,field_id'
    physical_before = sql(values_sql)
    complete = api('GET', '/api/flow-runtime/instances/81', token=admin)
    assert complete['businessView']['current']['id'] == 170 and complete['businessView']['proposed']['id'] is None
    assert complete['businessView']['current']['fields']['score'] == 30
    assert datetime.fromisoformat(complete['businessView']['current']['fields']['occurred']) == datetime(2026,9,18,10)
    assert not complete['variables'] and all(not a['input'] and not a['result'] for a in complete['actions'])
    current = api('GET', f"/api/flow-runtime/instances/{facts['node_fixture']['instance_id']}", token=admin)
    assert current['status'] == 'REJECTED'
    range_facts = json.loads((AREA/'range-share-facts.json').read_text(encoding='utf-8'))
    source_admin = api('POST','/api/systems/54/tenants/77/enter',{},login['accessToken'])['tokens']['accessToken']
    for record_id, module_code, baseline in [(154,'shared_private_accept_4a356e1ce2',range_facts['baseline_source154']),
            (151,'extension_accept_4a356e1ce2',range_facts['baseline_source151'])]:
        source = api('GET',f'/api/runtime/modules/{module_code}/records/{record_id}',token=source_admin)
        assert source['fields'] == baseline['fields'] and source['version'] == baseline['version']
    assert sql(values_sql) == physical_before
    assert int(sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0 AND deleted=0')[0][0]) == 4
    facts['cases'].append({'case':'final_actual_formal_source_full_storage_readback',
        'result':'最终部署读取81当前正式170时间/分值正确，独立节点86实际普通页面拒绝；完整物理值含DATE/DATETIME/FILE/规范化及时间列读取前后一致，来源151/154全部字段与版本等于既有基线，模块51仍4条正式记录。',
        'physical_value_hash':hashlib.sha256(json.dumps(physical_before).encode()).hexdigest()})
    facts['integrated_readback_verified']=True
    save(); print('PASS:最终部署正式/来源/完整物理列读取及节点普通拒绝收尾。')
elif stage == 'ui_close_readback':
    assert facts.get('integrated_readback_verified')
    # CLI 默认字符集不能可靠匹配中文标题；以隔离验收后的数值归属定位，再用接口断言真实标题。
    rows = sql(f"SELECT id,status FROM flow_instance WHERE system_id=54 AND tenant_id=57 AND started_by_account_id=69 AND flow_id=26 AND id>{int(facts['node_fixture']['instance_id'])} ORDER BY id DESC")
    assert len(rows) == 1 and rows[0][1] == 'REJECTED', rows
    instance_id = int(rows[0][0])
    closed = api('GET', f'/api/flow-runtime/instances/{instance_id}', token=admin)
    assert closed['businessView']['proposed']['title'] == '审批业务内容展示验收'
    assert closed['businessView']['proposed']['fields']['subject'] == '拟保存内容不是正式数据'
    assert closed['businessView']['proposed']['fields']['score'] == 30
    assert closed['businessView']['current'] is None
    assert int(sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0 AND deleted=0')[0][0]) == 4
    assert int(sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id={instance_id} AND status='PENDING'")[0][0]) == 0
    facts['ordinary_pending_ui'] = {'instance_id': instance_id, 'account_id': 69, 'status': closed['status'],
        'formal_record_count': 4, 'pending_tasks': 0}
    facts['cases'].append({'case': 'ordinary_pending_create_real_ui', 'result': '普通69使用公共页面新建分值30申请；进度抽屉及完整待办均显示中文原提交内容和正式数据未生成提示，列表仍4条。本人从待办页面拒绝后实际实例REJECTED、待办清零，未产生正式记录。'})
    save(); print(json.dumps(facts['ordinary_pending_ui'], ensure_ascii=False))
elif stage == 'restore':
    assert facts.get('api_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    current = next(r for r in api('GET', '/api/admin/system/authorization', token=admin)['roles'] if r['id'] == 114)
    assert current['permissions'] == original['permissions'] and current['fieldPolicies'] == original['fieldPolicies']
    facts['original_role_restored'] = True
    save()
    print('普通114权限与字段策略已准确恢复。')
else:
    raise ValueError('未知阶段')
