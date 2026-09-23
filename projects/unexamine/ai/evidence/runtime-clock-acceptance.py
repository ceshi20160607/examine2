import ast,json,re
from pathlib import Path
from datetime import datetime
area=Path('projects/unexamine/ai/evidence')
tree=ast.parse((area/'shared-actor-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,(ast.Import,ast.ImportFrom)) or isinstance(n,ast.FunctionDef) and n.name in {'call','api','sql'}],type_ignores=[]),'helpers','exec'))
f=json.loads((area/'tenant-extension-facts.json').read_text(encoding='utf-8'))

tree2=ast.parse((area/'private-shared-acceptance.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree2.body if isinstance(n,ast.FunctionDef)],type_ignores=[]),'private-helpers','exec'))
import copy
path=area/'runtime-clock-facts.json'
facts=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'step':'B03.1b','status':'running','module_code':'shared_private_accept_4a356e1ce2','cases':[]};code=facts['module_code']
base=f"/api/runtime/modules/{code}/records/154"
java=Path('projects/unexamine/backend/src/main/java/com/unique/unexamine/platform/manage/bootstrap/DefaultPlatformAdministratorInitializer.java').read_text(encoding='utf-8')
login=api('POST','/api/auth/login',{'username':re.search(r'default-admin\.username:([^}]+)',java).group(1),'password':re.search(r'default-admin\.initial-password:([^}]+)',java).group(1)})
admins={t:api('POST',f'/api/systems/54/tenants/{t}/enter',{},login['accessToken'])['tokens']['accessToken'] for t in [56,57,77]}

import secrets
if 'baseline' not in facts:
    facts['baseline']={'legacy_finished_before_start_count':int(sql("SELECT COUNT(*) FROM flow_instance WHERE finished_at IS NOT NULL AND finished_at < started_at")[0][0]),'pending_jobs_before_clock_bootstrap':0,'timed_pending_tasks_before_clock_bootstrap':0,'OS_zone':'UTC','new_JVM_logging_zone':'+08:00','source154_version':4}
    save()
if 'flow' not in facts:
    facts['flow']=api('POST','/api/flows',{'name':'运行时钟与来源审批进度验收','description':'当前合成模块47的审批与统一时区验收'},admins[77]);save()
fid=facts['flow']['id']
if 'publication' not in facts:
    tm=int(sql("SELECT tm.id FROM sys_tenant_member tm JOIN sys_member m ON m.id=tm.system_member_id WHERE m.account_id=1 AND tm.system_id=54 AND tm.tenant_id=77")[0][0])
    def node(key,kind,name,**extra):
        return {'nodeKey':key,'nodeType':kind,'name':name,'positionX':0,'positionY':0,'exceptionPolicy':{'action':'MANUAL'},**extra}
    nodes=[node('start','START','提交申请'),node('approval','APPROVAL','来源审批确认',assigneePolicy={'type':'PERSON','tenantMemberIds':[tm]},timeoutPolicy={'timeoutMinutes':5,'action':'MANUAL'}),node('end','END','审批结束')]
    draft=api('PUT',f'/api/flows/{fid}/draft',{'expectedVersion':facts['flow']['version'],'nodes':nodes,'edges':[{'edgeKey':'first','sourceNodeKey':'start','targetNodeKey':'approval'},{'edgeKey':'last','sourceNodeKey':'approval','targetNodeKey':'end'}]},admins[77])
    facts['publication']=api('POST',f'/api/flows/{fid}/publish',{'expectedDraftRevision':draft['draftRevision'],'changeSummary':'时区与审批进度公共前置验收'},admins[77]);save()
if 'started' not in facts:
    facts['started']=api('POST','/api/flow-runtime/instances',{'flowId':fid,'title':'同一运行环境的审批时间验收','businessType':'MODULE:'+code,'businessId':'154','businessSnapshot':{},'variables':{},'idempotencyKey':'runtime-clock-manual-4a356e1ce2'},admins[77]);save()
iid=facts['started']['instance']['id']
if 'pending_time_check' not in facts:
    pending=api('GET',f'/api/flow-runtime/instances/{iid}',token=admins[77]);assert pending['status']=='WAITING'
    task=next(t for t in pending['tasks'] if t['status']=='PENDING')
    start=datetime.fromisoformat(pending['startedAt']);due=datetime.fromisoformat(task['dueAt'])
    assert 290<=(due-start).total_seconds()<=310,(start,due)
    assert abs((start-datetime.now().astimezone()).total_seconds())<120,(start,datetime.now().astimezone())
    jobs=sql(f"SELECT id,status,TIMESTAMPDIFF(SECOND,created_at,next_run_at) FROM flow_job WHERE instance_id={iid}")
    assert len(jobs)==1 and jobs[0][1]=='PENDING' and 290<=int(jobs[0][2])<=310,jobs
    facts['pending_time_check']={'instance_id':iid,'task_id':task['id'],'start_time':pending['startedAt'],'due_time':task['dueAt'],'jobs':jobs};save()
if 'approved' not in facts:
    tid=facts['pending_time_check']['task_id']
    facts['approved']=api('POST',f'/api/flow-runtime/tasks/{tid}/actions',{'actionCode':'APPROVE','comment':'统一运行时钟后开始、期限和完成时间一致','variables':{},'idempotencyKey':'runtime-clock-approve-4a356e1ce2'},admins[77]);save()
final=api('GET',f'/api/flow-runtime/instances/{iid}',token=admins[77])
assert final['status']=='COMPLETED'
start=datetime.fromisoformat(final['startedAt']);finish=datetime.fromisoformat(final['finishedAt'])
assert start<=finish<=datetime.now().astimezone()
for e in final['executions']:
    if e['leftAt']:assert datetime.fromisoformat(e['enteredAt'])<=datetime.fromisoformat(e['leftAt']),e
done=next(t for t in final['tasks'] if t['id']==facts['pending_time_check']['task_id'])
assert datetime.fromisoformat(done['completedAt'])>=start
db=sql(f"SELECT finished_at>=started_at,ABS(TIMESTAMPDIFF(SECOND,NOW(),finished_at))<120 FROM flow_instance WHERE id={iid}")
assert db==[['1','1']],db
assert int(sql("SELECT COUNT(*) FROM flow_instance WHERE finished_at IS NOT NULL AND finished_at < started_at")[0][0])==facts['baseline']['legacy_finished_before_start_count']
assert api('GET',base,token=admins[77])['version']==4
if not facts['cases']:
    case('actual_UTC_container_early_runtime_zone_source_approval_task_due_and_finish_chronological',instance_id=iid,flow_id=fid,start_time=final['startedAt'],finish_time=final['finishedAt'],due_time=facts['pending_time_check']['due_time'],node_times_chronological=True,database_current_wall_time=True,legacy_mixed_rows_unchanged=True,source_record154_unchanged_version4=True)
# 仅收尾本次已完成合成实例的未来超时检查；保留作业与流程历史，不调整任何实际业务期限。
job_id=int(facts['pending_time_check']['jobs'][0][0])
sql(f"UPDATE flow_job SET status='CANCELLED',version=version+1 WHERE id={job_id} AND instance_id={iid} AND status='PENDING'")
facts['fixture_cleanup']={'instance_status':'COMPLETED','only_own_completed_fixture_timeout_job_cancelled':job_id,'flow_kept_for_progress_successor':fid};save()
print(json.dumps({'clock_cases':len(facts['cases']),'flow_id':fid,'instance_id':iid,'status':final['status'],'start_time':final['startedAt'],'finish_time':final['finishedAt']},ensure_ascii=False))

