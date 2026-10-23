package com.unique.unexamine.work.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.work.base.entity.UnWorkDaily;
import com.unique.unexamine.work.base.service.IUnWorkDailyService;
import com.unique.unexamine.work.manage.service.WorkScopeService.Scope;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 工作日志只由用户显式创建，提交与撤回保留各自版本。 */
@Service
public class WorkDailyService {
    private final WorkScopeService scopes;
    private final IUnWorkDailyService dailies;
    private final WorkProjectService projects;
    private final WorkTaskService tasks;
    private final ObjectMapper json;

    /** 项目和任务由用户主动选取，写入前核对当前作用域的可见性。 */
    public record DailyRequest(@NotNull LocalDate logDate,String completedText,
                               String ongoingText,String nextText,String riskText,String helpText,
                               List<Long> projectIds,List<Long> taskIds) {}
    /** 日志只保存稳定业务主键，任务状态变化不会重写日志版本。 */
    public record Relations(List<Long> projectIds,List<Long> taskIds) {}
    /** 当前版本及历史版本使用同一种可展示结构。 */
    public record DailyView(Long id,Long rootId,int versionNo,String scopeType,
                            Long systemId,Long tenantId,Long accountId,LocalDate logDate,
                            String completedText,String ongoingText,String nextText,
                            String riskText,String helpText,Relations relations,String status,
                            LocalDateTime submitTime,LocalDateTime createTime) {}
    /** 我的日志分页结果只返回当前有效版本。 */
    public record DailyPage(long total,long current,long size,List<DailyView> records) {}

    /** 注入作用域和已生成的日志基础服务。 */
    public WorkDailyService(WorkScopeService scopes,IUnWorkDailyService dailies,
                            WorkProjectService projects,WorkTaskService tasks,ObjectMapper json) {
        this.scopes=scopes;this.dailies=dailies;this.projects=projects;
        this.tasks=tasks;this.json=json;
    }

    /** 手动创建当日或补写日期的草稿，不受任务状态事件触发。 */
    @Transactional(rollbackFor=Exception.class)
    public DailyView create(String token,Long systemId,Long tenantId,DailyRequest request) {
        Scope scope=scopes.scope(token,systemId,tenantId);
        UnWorkDaily row=new UnWorkDaily();
        row.setScopeType(scope.type());row.setSystemId(scope.systemId());row.setTenantId(scope.tenantId());
        row.setAccountId(scope.actorId());row.setVersionNo(1);row.setIsCurrent(true);
        row.setStatus("DRAFT");row.setCreateTime(LocalDateTime.now());
        content(row,request,token,systemId,tenantId);
        dailies.addOrUpdate(row);
        return view(row);
    }

    /** 当前账号按日期和状态查看自己的日志，不混入任务操作历史。 */
    public DailyPage queryPageList(String token,Long systemId,Long tenantId,
                                   LocalDate from,LocalDate to,String status,Long projectId,Long taskId,
                                   long page,long size) {
        Scope scope=scopes.scope(token,systemId,tenantId);
        if (page<1 || size<1 || size>100) throw new IllegalArgumentException("日志分页范围无效");
        if (projectId!=null) projects.queryById(token,systemId,tenantId,projectId);
        if (taskId!=null) tasks.queryById(token,systemId,tenantId,taskId);
        var where=ownerScope(scope).eq(UnWorkDaily::getIsCurrent,true);
        if (from!=null) where.ge(UnWorkDaily::getLogDate,from);
        if (to!=null) where.le(UnWorkDaily::getLogDate,to);
        if (status!=null && !status.isBlank()) where.eq(UnWorkDaily::getStatus,status);
        if (projectId!=null) where.apply(
                "JSON_CONTAINS(relation_json, JSON_OBJECT('projectIds', JSON_ARRAY({0})))",projectId);
        if (taskId!=null) where.apply(
                "JSON_CONTAINS(relation_json, JSON_OBJECT('taskIds', JSON_ARRAY({0})))",taskId);
        var result=dailies.page(Page.of(page,size),where.orderByDesc(UnWorkDaily::getLogDate)
                .orderByDesc(UnWorkDaily::getId));
        return new DailyPage(result.getTotal(),result.getCurrent(),result.getSize(),
                result.getRecords().stream().map(this::view).toList());
    }

    /** 主键使用首版日志 ID，读取当前版本。 */
    public DailyView queryById(String token,Long systemId,Long tenantId,Long rootId) {
        return view(current(scopes.scope(token,systemId,tenantId),rootId,false));
    }

    /** 草稿原位编辑；已提交版本须先显式撤回。 */
    @Transactional(rollbackFor=Exception.class)
    public DailyView edit(String token,Long systemId,Long tenantId,Long rootId,DailyRequest request) {
        UnWorkDaily row=current(scopes.scope(token,systemId,tenantId),rootId,true);
        if (!"DRAFT".equals(row.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿可以编辑");
        content(row,request,token,systemId,tenantId);
        dailies.update(new LambdaUpdateWrapper<UnWorkDaily>().eq(UnWorkDaily::getId,row.getId())
                .set(UnWorkDaily::getLogDate,row.getLogDate())
                .set(UnWorkDaily::getCompletedText,row.getCompletedText())
                .set(UnWorkDaily::getOngoingText,row.getOngoingText())
                .set(UnWorkDaily::getNextText,row.getNextText())
                .set(UnWorkDaily::getRiskText,row.getRiskText())
                .set(UnWorkDaily::getHelpText,row.getHelpText())
                .set(UnWorkDaily::getRelationJson,row.getRelationJson()));
        return view(row);
    }

    /** 用户显式提交草稿，旧草稿留作版本记录。 */
    @Transactional(rollbackFor=Exception.class)
    public DailyView submit(String token,Long systemId,Long tenantId,Long rootId) {
        UnWorkDaily current=current(scopes.scope(token,systemId,tenantId),rootId,true);
        if (!"DRAFT".equals(current.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有草稿可以提交");
        return nextVersion(current,"SUBMITTED");
    }

    /** 用户撤回已提交日志，保留提交版并生成撤回版。 */
    @Transactional(rollbackFor=Exception.class)
    public DailyView withdraw(String token,Long systemId,Long tenantId,Long rootId) {
        UnWorkDaily current=current(scopes.scope(token,systemId,tenantId),rootId,true);
        if (!"SUBMITTED".equals(current.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已提交日志可以撤回");
        return nextVersion(current,"WITHDRAWN");
    }

    /** 撤回后由用户主动恢复草稿，撤回版仍保留在版本历史。 */
    @Transactional(rollbackFor=Exception.class)
    public DailyView reopen(String token,Long systemId,Long tenantId,Long rootId) {
        UnWorkDaily current=current(scopes.scope(token,systemId,tenantId),rootId,true);
        if (!"WITHDRAWN".equals(current.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已撤回日志可以恢复草稿");
        return nextVersion(current,"DRAFT");
    }

    /** 版本历史只向日志所有人开放。 */
    public List<DailyView> versions(String token,Long systemId,Long tenantId,Long rootId) {
        Scope scope=scopes.scope(token,systemId,tenantId);
        current(scope,rootId,false);
        return dailies.list(ownerScope(scope).and(q->q.eq(UnWorkDaily::getId,rootId)
                .or().eq(UnWorkDaily::getRootId,rootId)).orderByAsc(UnWorkDaily::getVersionNo))
                .stream().map(this::view).toList();
    }

    private void content(UnWorkDaily row,DailyRequest request,
                         String token,Long systemId,Long tenantId) {
        if (request.logDate().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("工作日志日期只能是今天或过去日期");
        List<Long> projectIds=request.projectIds()==null?List.of():request.projectIds().stream().distinct().toList();
        List<Long> taskIds=request.taskIds()==null?List.of():request.taskIds().stream().distinct().toList();
        projectIds.forEach(id->projects.queryById(token,systemId,tenantId,id));
        taskIds.forEach(id->tasks.queryById(token,systemId,tenantId,id));
        row.setLogDate(request.logDate());row.setCompletedText(request.completedText());
        row.setOngoingText(request.ongoingText());row.setNextText(request.nextText());
        row.setRiskText(request.riskText());row.setHelpText(request.helpText());
        row.setRelationJson(writeRelations(new Relations(projectIds,taskIds)));
    }

    private String writeRelations(Relations relations) {
        try { return json.writeValueAsString(relations); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("工作日志关联无法保存",ex); }
    }

    private Relations readRelations(String value) {
        if (value==null) return new Relations(List.of(),List.of());
        try { return json.readValue(value,Relations.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("工作日志关联无法读取",ex); }
    }

    /** 持有当前版行锁的调用方串行推进版本号，旧版内容不被覆盖。 */
    private DailyView nextVersion(UnWorkDaily current,String status) {
        Long rootId=current.getRootId()==null?current.getId():current.getRootId();
        dailies.update(new LambdaUpdateWrapper<UnWorkDaily>().eq(UnWorkDaily::getId,current.getId())
                .set(UnWorkDaily::getIsCurrent,false));
        UnWorkDaily next=new UnWorkDaily();
        next.setRootId(rootId);next.setVersionNo(current.getVersionNo()+1);
        next.setScopeType(current.getScopeType());next.setSystemId(current.getSystemId());
        next.setTenantId(current.getTenantId());next.setAccountId(current.getAccountId());
        next.setLogDate(current.getLogDate());next.setCompletedText(current.getCompletedText());
        next.setOngoingText(current.getOngoingText());next.setNextText(current.getNextText());
        next.setRiskText(current.getRiskText());next.setHelpText(current.getHelpText());
        next.setRelationJson(current.getRelationJson());
        next.setFieldValuesJson(current.getFieldValuesJson());
        next.setVisibilityJson(current.getVisibilityJson());
        next.setStatus(status);next.setIsCurrent(true);
        next.setSubmitTime("SUBMITTED".equals(status)?LocalDateTime.now():null);
        next.setCreateTime(LocalDateTime.now());dailies.addOrUpdate(next);
        return view(next);
    }

    private LambdaQueryWrapper<UnWorkDaily> ownerScope(Scope scope) {
        var where=new LambdaQueryWrapper<UnWorkDaily>().eq(UnWorkDaily::getScopeType,scope.type())
                .eq(UnWorkDaily::getAccountId,scope.actorId());
        if (scope.systemId()==null) where.isNull(UnWorkDaily::getSystemId).isNull(UnWorkDaily::getTenantId);
        else where.eq(UnWorkDaily::getSystemId,scope.systemId())
                .eq(UnWorkDaily::getTenantId,scope.tenantId());
        return where;
    }

    private UnWorkDaily current(Scope scope,Long rootId,boolean lock) {
        var where=ownerScope(scope).eq(UnWorkDaily::getIsCurrent,true)
                .and(q->q.eq(UnWorkDaily::getId,rootId).isNull(UnWorkDaily::getRootId)
                        .or().eq(UnWorkDaily::getRootId,rootId));
        if (lock) where.last("FOR UPDATE");
        UnWorkDaily row=dailies.getOne(where,false);
        if (row==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"工作日志不存在");
        return row;
    }

    private DailyView view(UnWorkDaily row) {
        return new DailyView(row.getId(),row.getRootId()==null?row.getId():row.getRootId(),
                row.getVersionNo(),row.getScopeType(),row.getSystemId(),row.getTenantId(),
                row.getAccountId(),row.getLogDate(),row.getCompletedText(),row.getOngoingText(),
                row.getNextText(),row.getRiskText(),row.getHelpText(),
                readRelations(row.getRelationJson()),row.getStatus(),
                row.getSubmitTime(),row.getCreateTime());
    }
}
