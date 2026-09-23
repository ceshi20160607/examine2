"""B09.2c.8 审批附件必填、评论与催办的真实 API/DB 验收。"""
import ast
import copy
import json
import re
import secrets
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

AREA = Path(__file__).parent
for filename, names in [('shared-actor-acceptance.py', {'call', 'api', 'sql'}),
        ('flow-node-submit-acceptance.py', {'publish_module', 'publish_role', 'delivery'})]:
    tree = ast.parse((AREA / filename).read_text(encoding='utf-8'))
    exec(compile(ast.Module(body=[node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom))
        or isinstance(node, ast.FunctionDef) and node.name in names], type_ignores=[]), filename, 'exec'))

PATH = AREA / 'flow-collaboration-facts.json'
facts = json.loads(PATH.read_text(encoding='utf-8')) if PATH.exists() else {
    'step': 'B09.2c.8', 'status': 'running', 'code': 'collab_' + secrets.token_hex(5),
    'fields': {}, 'flows': [], 'instances': [], 'records': [], 'attachments': [], 'cases': [], 'registrations': []}
assert facts['status'] == 'running', '验收事实已经封存'

java = (AREA.parents[1] / 'backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
ADMIN_NAME = re.search(r'default-admin\.username:([^}]+)', java).group(1)
LOCAL_PASSWORD = re.search(r'default-admin\.initial-password:([^}]+)', java).group(1)
platform = api('POST', '/api/auth/login', {'username': ADMIN_NAME, 'password': LOCAL_PASSWORD})['accessToken']
entry = api('POST', '/api/systems/54/tenants/57/enter', {}, platform)
admin = entry['tokens']['accessToken']
original = json.loads((AREA / 'flow-condition-facts.json').read_text(encoding='utf-8'))['role_baseline']
code = facts['code']


def save():
    facts['checked_time'] = datetime.now().astimezone().isoformat()
    PATH.write_text(json.dumps(facts, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def case(name, **values):
    if any(item['case'] == name for item in facts['cases']): return
    facts['cases'].append({'case': name, **values}); save()


def user_tokens(index):
    item = facts['registrations'][index]
    token = api('POST', '/api/auth/login', {'username': item['username'], 'password': LOCAL_PASSWORD})['accessToken']
    system = api('POST', '/api/systems/54/tenants/57/enter', {}, token)['tokens']['accessToken']
    return token, system


def grant_system_role():
    permissions = copy.deepcopy(original['permissions'])
    permissions += [{'resourceType': 'MODULE', 'resourceCode': code, 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['LIST', 'DETAIL', 'CREATE', 'UPDATE']]
    permissions += [{'resourceType': 'FLOW', 'resourceCode': 'SYSTEM', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['VIEW_RUNTIME', 'HANDLE']]
    permissions += [{'resourceType': 'FILE', 'resourceCode': 'OBJECT', 'actionCode': action, 'dataScopeType': 'ALL'}
        for action in ['VIEW', 'UPLOAD', 'REFERENCE', 'DELETE', 'DOWNLOAD']]
    policies = copy.deepcopy(original['fieldPolicies'])
    policies += [{'resourceCode': code, 'fieldCode': field, 'channel': channel,
        'readable': True, 'writable': True, 'maskStrategy': None}
        for field in facts['fields'] for channel in ['PAGE', 'FLOW']]
    publish_role(permissions, policies)


def draft(flow_id, nodes, edges, token):
    current = api('GET', f'/api/flows/{flow_id}', token=token)
    return api('PUT', f'/api/flows/{flow_id}/draft', {
        'expectedVersion': current['version'], 'nodes': nodes, 'edges': edges}, token)


def publish(flow_id, token, summary):
    checked = api('GET', f'/api/flows/{flow_id}/publication-check', token=token)
    assert checked['valid'], checked['issues']
    return api('POST', f'/api/flows/{flow_id}/publish', {
        'expectedDraftRevision': checked['draftRevision'], 'changeSummary': summary,
        'simulationVariables': {}}, token)


def make_flow(label, realm, required):
    token = platform if realm == 'platform' else admin
    flow = api('POST', '/api/flows', {'code': code + '_' + secrets.token_hex(3), 'name': label}, token)
    facts['flows'].append(flow['id']); save()
    principal = facts['registrations'][0]
    people_key = 'platformAccountIds' if realm == 'platform' else 'tenantMemberIds'
    person = principal['account_id'] if realm == 'platform' else principal['tenant_member_id']
    config = {'approvalMode': 'OR_SIGN', 'manualOperationPolicy': {
        'allowedActions': ['APPROVE', 'REJECT'], 'opinionRequiredActions': ['REJECT'], 'requireClaim': False}}
    if required: config['attachmentPolicy'] = {'required': True, 'minimumCount': 1}
    nodes = [
        {'nodeKey': 'start', 'nodeType': 'START', 'name': '开始', 'positionX': 0, 'positionY': 100,
            'config': {'moduleId': facts['module_id']} if realm == 'system' else {},
            'assigneePolicy': {}, 'formPolicy': {}, 'timeoutPolicy': {}},
        {'nodeKey': 'review', 'nodeType': 'APPROVAL', 'name': '核对协作材料', 'positionX': 220, 'positionY': 100,
            'config': config, 'assigneePolicy': {'type': 'PERSON', people_key: [person]},
            'formPolicy': {'editable': [], 'required': []} if realm == 'system' else {}, 'timeoutPolicy': {}},
        {'nodeKey': 'end', 'nodeType': 'END', 'name': '结束', 'positionX': 440, 'positionY': 100,
            'config': {}, 'assigneePolicy': {}, 'formPolicy': {}, 'timeoutPolicy': {}}]
    edges = [
        {'edgeKey': 'e1', 'sourceNodeKey': 'start', 'targetNodeKey': 'review', 'priorityOrder': 0, 'config': {}},
        {'edgeKey': 'e2', 'sourceNodeKey': 'review', 'targetNodeKey': 'end', 'priorityOrder': 0, 'config': {}}]
    draft(flow['id'], nodes, edges, token)
    publish(flow['id'], token, '结构化审批附件、评论与催办验收')
    return flow['id']


def read(instance_id, token):
    return api('GET', f'/api/flow-runtime/instances/{instance_id}', token=token)


def current_task(instance_id, token):
    return next(task for task in read(instance_id, token)['tasks'] if task['status'] == 'PENDING' and task['currentHandler'])


def start_platform(flow_id, label, starter_index=1):
    result = api('POST', '/api/flow-runtime/instances', {'flowId': flow_id, 'title': label,
        'businessSnapshot': {}, 'variables': {}, 'idempotencyKey': code + secrets.token_hex(6)}, user_tokens(starter_index)[0])
    instance_id = result['instance']['id']; facts['instances'].append(instance_id); save(); return instance_id


def start_source(flow_id, label):
    api('POST', '/api/flow-bindings/publish', {'moduleId': facts['module_id'], 'triggerEvent': 'CREATE',
        'flowId': flow_id, 'executionMode': 'AFTER_APPROVAL', 'priorityOrder': 0,
        'condition': {'mode': 'ALL', 'conditions': []}, 'mutuallyExclusive': True,
        'replaceExisting': True}, admin)
    action = api('POST', f'/api/runtime/modules/{code}/records', {'title': label, 'status': 'ACTIVE',
        'participantMemberIds': [], 'fields': {'subject': label}}, admin, action_key=code + secrets.token_hex(6))
    facts['source_action'] = {key: action[key] for key in ['flowInstanceId', 'idempotencyRecordId', 'outboxId']}
    facts['instances'].append(action['flowInstanceId']); save(); return action['flowInstanceId']


def multipart_upload(instance_id, token, name):
    boundary = '----unexamine' + secrets.token_hex(12)
    content = ('真实审批附件：' + name).encode('utf-8')
    data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}.txt"\r\n'
        'Content-Type: text/plain\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
    request = urllib.request.Request(f'http://localhost:18080/api/business-attachments/flow-instances/{instance_id}',
        method='POST', headers={'Authorization': 'Bearer ' + token,
        'Content-Type': 'multipart/form-data; boundary=' + boundary}, data=data)
    with urllib.request.urlopen(request, timeout=25) as response:
        result = json.load(response)['data']
    facts['attachments'].append({'id': result['attachmentId'], 'instance_id': instance_id,
        'realm': 'platform' if read(instance_id, token)['contextType'] == 'PLATFORM' else 'system'})
    save(); return result


def approve(instance_id, token, attachment_ids=None, key=None, comment='材料已核对'):
    task = current_task(instance_id, token)
    body = {'actionCode': 'APPROVE', 'comment': comment,
        'idempotencyKey': key or code + secrets.token_hex(6)}
    if attachment_ids is not None: body['attachmentIds'] = attachment_ids
    return task, body, api('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", body, token)


def compact(instance_id):
    return {
        'instance': sql(f'SELECT id,status,current_node_key,version FROM flow_instance WHERE id={instance_id}'),
        'tasks': sql(f'SELECT id,status,version FROM flow_task WHERE instance_id={instance_id} ORDER BY id'),
        'actions': sql(f'SELECT id,action_code,comment_text,acted_by_account_id FROM flow_action WHERE instance_id={instance_id} ORDER BY id'),
        'history': sql(f'SELECT id,event_type,event_name,actor_account_id FROM flow_history_event WHERE instance_id={instance_id} ORDER BY id')}


stage = sys.argv[1]
if stage == 'prepare':
    assert not facts.get('prepared')
    if 'golden_baseline' not in facts:
        facts['golden_baseline'] = sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id')
        facts['golden_count'] = sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0'); save()
    if not facts['registrations']:
        for suffix, label in [('principal', '附件审批处理人'), ('starter', '流程协作发起人'), ('outsider', '无关流程成员')]:
            username = code + '_' + suffix
            registered = api('POST', '/api/auth/register', {'username': username, 'password': LOCAL_PASSWORD,
                'displayName': label, 'systemName': label + '系统'})
            facts['registrations'].append({'username': username, 'account_id': registered['accountId'],
                'system_id': registered['systemId'], 'display_name': label})
        role_id = int(sql(f"INSERT INTO plat_role(platform_id,code,name,description,built_in,status) VALUES(1,'{code}','流程协作验收角色','附件评论催办临时权限',0,'ACTIVE'); SELECT LAST_INSERT_ID()")[0][0])
        facts['platform_role_id'] = role_id; facts['platform_links'] = []
        for resource, resource_code, actions in [('FLOW', 'PLATFORM', ['VIEW', 'VIEW_RUNTIME', 'START', 'HANDLE']),
                ('FILE', 'OBJECT', ['VIEW', 'UPLOAD', 'REFERENCE', 'DELETE', 'DOWNLOAD'])]:
            for action in actions:
                sql(f"INSERT INTO plat_role_permission(platform_id,role_id,resource_type,resource_code,action_code,effect) VALUES(1,{role_id},'{resource}','{resource_code}','{action}','ALLOW')")
        for item in facts['registrations']:
            member_id = int(sql(f"SELECT id FROM plat_member WHERE platform_id=1 AND account_id={item['account_id']}")[0][0])
            link = int(sql(f'INSERT INTO plat_member_role(platform_id,member_id,role_id) VALUES(1,{member_id},{role_id}); SELECT LAST_INSERT_ID()')[0][0])
            item['platform_member_id'] = member_id; facts['platform_links'].append(link)
            item['tenant_member_id'] = api('POST', '/api/admin/system/authorization/members', {
                'account': item['username'], 'roleIds': [114]}, admin)['tenantMemberId']
        save()
    if 'module_id' not in facts:
        module = api('POST', '/api/admin/module-config/modules', {'name': '审批协作来源一次验收',
            'code': code, 'groupName': '流程协作验收'}, admin)['module']
        facts['module_id'] = module['id']; save()
        facts['fields']['subject'] = api('POST', f"/api/admin/module-config/modules/{module['id']}/fields", {
            'code': 'subject', 'name': '申请主题', 'fieldType': 'TEXT', 'required': True,
            'sortOrder': 0, 'config': {}}, admin); save()
        publish_module(module['id']); grant_system_role()
    # 失败后续跑也必须恢复本包角色；不能因模块已经创建而跳过权限发布。
    grant_system_role()
    if 'required_platform_flow' not in facts:
        facts['required_platform_flow'] = make_flow('平台审批协作材料', 'platform', True); save()
    if 'legacy_platform_flow' not in facts:
        facts['legacy_platform_flow'] = make_flow('旧版附件可选审批', 'platform', False); save()
    if 'required_system_flow' not in facts:
        facts['required_system_flow'] = make_flow('系统来源审批协作材料', 'system', True); save()

    principal_platform, principal_system = user_tokens(0)
    if 'legacy_instance' not in facts:
        facts['legacy_instance'] = start_platform(facts['legacy_platform_flow'], '旧版本不要求附件')
        task = current_task(facts['legacy_instance'], principal_platform)
        assert not task['attachmentRequired'] and task['minimumAttachmentCount'] == 0
        approve(facts['legacy_instance'], principal_platform)
        case('legacy-published-version-remains-attachment-optional', instance_id=facts['legacy_instance'])
    if not facts.get('system_instance'):
        facts['system_instance'] = facts.get('source_action', {}).get('flowInstanceId') \
            or start_source(facts['required_system_flow'], '系统来源附件必填且只执行一次')
        save()
        task = current_task(facts['system_instance'], principal_system)
        assert task['attachmentRequired'] and task['minimumAttachmentCount'] == 1
        before = compact(facts['system_instance'])
        status, error = call('POST', f"/api/flow-runtime/tasks/{task['id']}/actions", {
            'actionCode': 'APPROVE', 'comment': '缺少附件不能审批',
            'idempotencyKey': code + '_system_missing'}, principal_system)
        assert status == 422 and error['code'] == 'FLOW_APPROVAL_ATTACHMENT_REQUIRED'
        assert compact(facts['system_instance']) == before
        system_attachment = multipart_upload(facts['system_instance'], principal_system, '系统来源审批材料')
        approve(facts['system_instance'], principal_system, [system_attachment['attachmentId']])
        result = delivery(facts['source_action'], admin)
        assert result['businessStatus'] == 'EXECUTED'
        facts['records'].append(result['recordId'])
        assert sql(f"SELECT COUNT(*) FROM flow_action WHERE instance_id={facts['system_instance']} AND action_code='APPROVE'") == [['1']]
        case('required-attachment-failure-is-side-effect-free-and-system-source-executes-once', instance_id=facts['system_instance'])
    for key, label in [('cross_instance', '跨实例附件不能提交'), ('collaboration_instance', '评论催办和有效附件'),
            ('gui_instance', '页面附件评论协作')]:
        if key not in facts: facts[key] = start_platform(facts['required_platform_flow'], label); save()
    if 'cross_attachment' not in facts:
        facts['cross_attachment'] = multipart_upload(facts['cross_instance'], principal_platform, '仅属于第一个实例')['attachmentId']; save()
    if 'gui_attachment' not in facts:
        facts['gui_attachment'] = multipart_upload(facts['gui_instance'], principal_platform, '页面已上传审批材料')['attachmentId']; save()
    facts['restart_pending_state'] = {str(value): compact(value) for value in facts['instances']}
    facts['prepared'] = True; save()
    print(json.dumps({'prepare': 'PASS', 'designer_flow': facts['required_platform_flow'],
        'gui_instance': facts['gui_instance']}, ensure_ascii=False))

elif stage == 'verify':
    assert facts.get('prepared') and not facts.get('api_verified')
    principal_platform, _ = user_tokens(0); starter_platform, _ = user_tokens(1); outsider_platform, _ = user_tokens(2)
    target_task = current_task(facts['collaboration_instance'], principal_platform)
    cross_status, cross_error = call('POST', f"/api/flow-runtime/tasks/{target_task['id']}/actions", {
        'actionCode': 'APPROVE', 'comment': '伪造跨实例附件', 'idempotencyKey': code + '_cross_attachment',
        'attachmentIds': [facts['cross_attachment']]}, principal_platform)
    assert cross_status == 422 and cross_error['code'] == 'FLOW_APPROVAL_ATTACHMENT_INVALID'
    deleted = multipart_upload(facts['collaboration_instance'], principal_platform, '随后删除的附件')
    api('DELETE', f"/api/business-attachments/{deleted['attachmentId']}", token=principal_platform)
    deleted_status, deleted_error = call('POST', f"/api/flow-runtime/tasks/{target_task['id']}/actions", {
        'actionCode': 'APPROVE', 'comment': '已删除附件不能提交', 'idempotencyKey': code + '_deleted_attachment',
        'attachmentIds': [deleted['attachmentId']]}, principal_platform)
    assert deleted_status == 422 and deleted_error['code'] == 'FLOW_APPROVAL_ATTACHMENT_INVALID'
    case('cross-instance-and-deleted-attachments-never-satisfy-current-task', instance_id=facts['collaboration_instance'])

    denied_status, denied = call('POST', f"/api/flow-runtime/instances/{facts['collaboration_instance']}/actions", {
        'actionCode': 'COMMENT', 'comment': '无关成员越权评论', 'idempotencyKey': code + '_outsider'}, outsider_platform)
    assert denied_status == 403 and denied['code'] == 'FLOW_INSTANCE_VIEW_DENIED'
    comment_key = code + '_concurrent_comment'
    body = {'actionCode': 'COMMENT', 'comment': '发起人与处理人共同可见的流程评论', 'idempotencyKey': comment_key}
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: call('POST', f"/api/flow-runtime/instances/{facts['collaboration_instance']}/actions",
            body, starter_platform), range(2)))
    assert [status for status, _ in results] == [200, 200]
    payloads = [result['data'] for _, result in results]
    assert sorted(item['replayed'] for item in payloads) == [False, True]
    assert len({item['actionId'] for item in payloads}) == 1
    mismatch_status, mismatch = call('POST', f"/api/flow-runtime/instances/{facts['collaboration_instance']}/actions", {
        **body, 'comment': '同幂等键不同评论'}, starter_platform)
    assert mismatch_status == 409 and mismatch['code'] == 'FLOW_ACTION_IDEMPOTENCY_CONFLICT'
    case('authorized-comments-are-concurrent-idempotent-and-outsider-is-denied', instance_id=facts['collaboration_instance'])

    remind_body = {'actionCode': 'REMIND', 'comment': '请核对附件并尽快完成审批', 'idempotencyKey': code + '_remind'}
    first = api('POST', f"/api/flow-runtime/instances/{facts['collaboration_instance']}/actions", remind_body, starter_platform)
    replay = api('POST', f"/api/flow-runtime/instances/{facts['collaboration_instance']}/actions", remind_body, starter_platform)
    assert replay['replayed'] and replay['actionId'] == first['actionId']
    assert sql(f"SELECT COUNT(*) FROM msg_message WHERE source_type='FLOW' AND source_id='manual-reminder-action-{first['actionId']}'") == [['1']]
    assert sql(f"SELECT COUNT(*) FROM msg_recipient r JOIN msg_message m ON m.id=r.message_id WHERE m.source_id='manual-reminder-action-{first['actionId']}' AND r.account_id={facts['registrations'][0]['account_id']}") == [['1']]
    view = read(facts['collaboration_instance'], starter_platform)
    assert any(action['actionCode'] == 'COMMENT' and action['actedByName'] == facts['registrations'][1]['display_name'] for action in view['actions'])
    assert any(action['actionCode'] == 'REMIND' and action['actedByName'] == facts['registrations'][1]['display_name'] for action in view['actions'])
    assert any('1 人' in event['eventName'] for event in view['history'] if event['eventType'] == 'ACTION')
    case('reminder-deduplicates-current-recipient-and-audits-actor-time-result', instance_id=facts['collaboration_instance'])

    valid = multipart_upload(facts['collaboration_instance'], principal_platform, '当前实例有效审批附件')
    approve(facts['collaboration_instance'], principal_platform, [valid['attachmentId']])
    approve(facts['cross_instance'], principal_platform, [facts['cross_attachment']])
    case('valid-current-instance-attachment-completes-platform-approval', instance_id=facts['collaboration_instance'])
    facts['api_verified'] = True; save()
    print(json.dumps({'verify': 'PASS', 'cases': len(facts['cases']), 'gui_instance': facts['gui_instance']}, ensure_ascii=False))

elif stage == 'gui_observed':
    assert facts.get('api_verified') and not facts.get('gui_verified')
    principal_platform, _ = user_tokens(0)
    view = read(facts['gui_instance'], principal_platform)
    task = next(task for task in view['tasks'] if task['nodeKey'] == 'review')
    assert task['attachmentRequired'] and any(action['actionCode'] == 'COMMENT' for action in view['actions'])
    approval = next(action for action in view['actions'] if action['actionCode'] == 'APPROVE')
    stored = json.loads(bytes.fromhex(sql(f"SELECT HEX(input_json) FROM flow_action WHERE id={approval['id']}")[0][0]).decode())
    assert stored['attachmentIds'] == [facts['gui_attachment']]
    facts['gui_verified'] = True
    case('designer-and-runtime-gui-use-structured-attachment-comment-controls', instance_id=facts['gui_instance'])
    save(); print('GUI PASS')

elif stage == 'restart':
    assert facts.get('gui_verified') and not facts.get('restart_verified')
    principal_platform, principal_system = user_tokens(0)
    for instance_id in facts['instances']:
        token = principal_system if instance_id == facts['system_instance'] else principal_platform
        assert read(instance_id, token)['status'] == 'COMPLETED'
    facts['completed_restart_state'] = {str(value): compact(value) for value in facts['instances']}
    facts['restart_verified'] = True
    case('same-backend-restart-retains-attachments-comments-reminders-and-results')
    save(); print('Restart PASS')

elif stage == 'cleanup':
    assert facts.get('restart_verified')
    publish_role(original['permissions'], original['fieldPolicies'])
    for item in facts['attachments']:
        if sql(f"SELECT COUNT(*) FROM file_reference WHERE id={item['id']}") == [['0']]: continue
        token = admin if item['realm'] == 'system' else platform
        api('DELETE', f"/api/business-attachments/{item['id']}", token=token)
    for record_id in facts['records']:
        record = api('GET', f'/api/runtime/modules/{code}/records/{record_id}', token=admin)
        api('POST', f'/api/runtime/modules/{code}/records/{record_id}/archive', {
            'version': record['version'], 'reason': '审批协作验收归档'}, admin)
    flow_ids = ','.join(map(str, facts['flows']))
    sql(f"UPDATE cfg_module SET status='DISABLED',version=version+1 WHERE id={facts['module_id']} AND system_id=54 AND code='{code}'; UPDATE flow_trigger_binding SET status='DISABLED',version=version+1 WHERE module_id={facts['module_id']} AND system_id=54; UPDATE flow_definition SET status='DISABLED',version=version+1 WHERE id IN({flow_ids})")
    for item, link_id in zip(facts['registrations'], facts['platform_links']):
        account_id = item['account_id']; system_id = item['system_id']
        sql(f"DELETE FROM plat_member_role WHERE id={link_id} AND member_id={item['platform_member_id']} AND role_id={facts['platform_role_id']}; UPDATE sys_tenant_member tm JOIN sys_member sm ON tm.system_member_id=sm.id SET tm.status='DISABLED',tm.version=tm.version+1 WHERE sm.account_id={account_id}; UPDATE sys_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE plat_member SET status='DISABLED',version=version+1 WHERE account_id={account_id}; UPDATE auth_session SET revoked=1,revoked_reason='flow-collaboration-acceptance-closed' WHERE account_id={account_id}; UPDATE plat_account SET status='DISABLED',version=version+1 WHERE id={account_id} AND username='{item['username']}'; UPDATE sys_system SET status='DISABLED',version=version+1 WHERE id={system_id} AND creator_account_id={account_id}")
    sql(f"UPDATE plat_role SET status='DISABLED',version=version+1 WHERE id={facts['platform_role_id']} AND code='{code}'")
    ids = ','.join(map(str, facts['instances']))
    assert sql(f"SELECT COUNT(*) FROM flow_task WHERE instance_id IN({ids}) AND status IN('PENDING','QUEUED','SUSPENDED')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_job WHERE instance_id IN({ids}) AND status IN('PENDING','RUNNING')") == [['0']]
    assert sql(f"SELECT COUNT(*) FROM flow_execution WHERE instance_id IN({ids}) AND status IN('ACTIVE','RUNNING','SUSPENDED')") == [['0']]
    assert sql('SELECT id,version,HEX(title),updated_at FROM biz_record WHERE id IN(151,154,170) ORDER BY id') == facts['golden_baseline']
    assert sql('SELECT COUNT(*) FROM biz_record WHERE module_id=51 AND archived=0') == facts['golden_count']
    facts['cleanup'] = {'unfinished_tasks': 0, 'unfinished_jobs': 0, 'unfinished_executions': 0,
        'attachments_removed': True, 'role_114_restored': True, 'golden_rows_unchanged': True}
    facts['status'] = 'verified_contribution'
    facts['remaining'] = '应用开放、外部渠道和其余全产品需求仍待后续任务包验收。'
    save(); print(json.dumps({'cleanup': 'PASS', 'status': facts['status'], 'cases': len(facts['cases'])}, ensure_ascii=False))

else:
    raise AssertionError('使用 prepare / verify / gui_observed / restart / cleanup')
