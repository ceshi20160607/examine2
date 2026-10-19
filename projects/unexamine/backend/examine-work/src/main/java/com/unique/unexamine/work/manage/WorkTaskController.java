package com.unique.unexamine.work.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.work.base.entity.UnWorkProject;
import com.unique.unexamine.work.base.entity.UnWorkProjectMember;
import com.unique.unexamine.work.base.entity.UnWorkTask;
import com.unique.unexamine.work.base.entity.UnWorkTaskField;
import com.unique.unexamine.work.base.entity.UnWorkTaskGroup;
import com.unique.unexamine.work.base.entity.UnWorkTaskHistory;
import com.unique.unexamine.work.base.entity.UnWorkTaskMember;
import com.unique.unexamine.work.base.mapper.UnWorkProjectMemberMapper;
import com.unique.unexamine.work.base.mapper.UnWorkTaskFieldMapper;
import com.unique.unexamine.work.base.mapper.UnWorkTaskGroupMapper;
import com.unique.unexamine.work.base.mapper.UnWorkTaskHistoryMapper;
import com.unique.unexamine.work.base.mapper.UnWorkTaskMapper;
import com.unique.unexamine.work.base.mapper.UnWorkTaskMemberMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 项目任务、可配置字段、分页列表及状态历史。 */
@Tag(name = "工作任务", description = "任务字段、任务成员和任务处理。")
@RestController
@RequestMapping("/api/tenants/{tenantId}/work/projects/{projectId}")
public class WorkTaskController {
    private final WorkProjectController projects;
    private final SystemSessionAccess access;
    private final UnWorkTaskMapper tasks;
    private final UnWorkTaskFieldMapper fields;
    private final UnWorkTaskGroupMapper groups;
    private final UnWorkTaskHistoryMapper history;
    private final UnWorkTaskMemberMapper taskMembers;
    private final UnWorkProjectMemberMapper participants;
    private final ObjectMapper json;

    public WorkTaskController(WorkProjectController projects, SystemSessionAccess access, UnWorkTaskMapper tasks,
                              UnWorkTaskFieldMapper fields, UnWorkTaskGroupMapper groups,
                              UnWorkTaskHistoryMapper history, UnWorkTaskMemberMapper taskMembers,
                              UnWorkProjectMemberMapper participants,
                              ObjectMapper json) {
        this.projects = projects;
        this.access = access;
        this.tasks = tasks;
        this.fields = fields;
        this.groups = groups;
        this.history = history;
        this.taskMembers = taskMembers;
        this.participants = participants;
        this.json = json;
    }

    /** 字段结构由项目管理员定义，字段编码创建后保持稳定。 */
    @Operation(summary = "新建组件字段（工作任务）")
    @PostMapping("/fields")
    public FieldView addField(@PathVariable long tenantId, @PathVariable long projectId,
                              @Valid @RequestBody FieldInput input, HttpServletRequest request) {
        projects.requireManagedProject(tenantId, projectId, request);
        if (!input.fieldKey().matches("[a-z][a-z0-9_]{0,63}")
                || !List.of("text", "number", "date", "select", "boolean").contains(input.fieldType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段编码或类型无效");
        }
        if ("select".equals(input.fieldType()) && (input.options() == null || input.options().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单选字段需要可选值");
        }
        UnWorkTaskField field = new UnWorkTaskField();
        field.setProjectId(projectId);
        field.setFieldKey(input.fieldKey());
        field.setFieldName(input.fieldName().trim());
        field.setFieldType(input.fieldType());
        field.setRequiredFlag(input.required() ? (byte) 1 : (byte) 0);
        field.setOptionData(input.options() == null ? null : stringify(input.options()));
        field.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        field.setStatus((byte) 1);
        fields.insert(field);
        return fieldView(field);
    }

    @Operation(summary = "查询字段（工作任务）")
    @GetMapping("/fields")
    public List<FieldView> fields(@PathVariable long tenantId, @PathVariable long projectId,
                                  HttpServletRequest request) {
        projects.requireProject(tenantId, projectId, request);
        return fields.selectList(new LambdaQueryWrapper<UnWorkTaskField>()
                        .eq(UnWorkTaskField::getProjectId, projectId)
                        .orderByAsc(UnWorkTaskField::getSortNo, UnWorkTaskField::getId))
                .stream().map(this::fieldView).toList();
    }

    @Operation(summary = "修改字段（工作任务）")
    @PutMapping("/fields/{fieldId}")
    public FieldView updateField(@PathVariable long tenantId, @PathVariable long projectId,
                                 @PathVariable long fieldId, @Valid @RequestBody FieldUpdate input,
                                 HttpServletRequest request) {
        projects.requireManagedProject(tenantId, projectId, request);
        UnWorkTaskField field = fields.selectById(fieldId);
        if (field == null || !field.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务字段不存在");
        }
        field.setFieldName(input.fieldName().trim());
        field.setRequiredFlag(input.required() ? (byte) 1 : (byte) 0);
        field.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        field.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        fields.updateById(field);
        return fieldView(field);
    }

    /** 任务列表仅接受固定字段排序，避免请求参数进入 SQL 表达式。 */
    @Operation(summary = "查询列表（工作任务）")
    @GetMapping("/tasks")
    public TaskPage list(@PathVariable long tenantId, @PathVariable long projectId,
                         @RequestParam(required = false) Long groupId,
                         @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "20") long size,
                         @RequestParam(defaultValue = "createTime") String sortField,
                         @RequestParam(defaultValue = "desc") String sortOrder,
                         HttpServletRequest request) {
        projects.requireProject(tenantId, projectId, request);
        if (page < 1 || size < 1 || size > 100
                || !List.of("createTime", "dueTime", "priority", "title").contains(sortField)
                || !List.of("asc", "desc").contains(sortOrder.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页或排序参数无效");
        }
        LambdaQueryWrapper<UnWorkTask> query = new LambdaQueryWrapper<UnWorkTask>()
                .eq(UnWorkTask::getProjectId, projectId);
        if (groupId != null) {
            query.eq(UnWorkTask::getGroupId, groupId);
        }
        if (status != null && !status.isBlank()) {
            if (!List.of("todo", "doing", "done", "cancelled").contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务状态无效");
            }
            query.eq(UnWorkTask::getStatus, status);
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "dueTime" -> query.orderBy(true, asc, UnWorkTask::getDueTime);
            case "priority" -> query.orderBy(true, asc, UnWorkTask::getPriority);
            case "title" -> query.orderBy(true, asc, UnWorkTask::getTitle);
            default -> query.orderBy(true, asc, UnWorkTask::getCreateTime);
        }
        query.orderByAsc(UnWorkTask::getId);
        Page<UnWorkTask> result = tasks.selectPage(new Page<>(page, size), query);
        return new TaskPage(result.getRecords().stream().map(this::taskView).toList(), result.getTotal(), page, size);
    }

    /** 创建任务同时验证配置字段并记录初次状态。 */
    @Operation(summary = "新建（工作任务）")
    @PostMapping("/tasks")
    @Transactional
    public TaskView create(@PathVariable long tenantId, @PathVariable long projectId,
                           @Valid @RequestBody TaskInput input, HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantMember(request, tenantId);
        projects.requireProject(tenantId, projectId, request);
        requireGroup(projectId, input.groupId());
        if (input.ownerMemberId() != null) {
            requireParticipant(projectId, input.ownerMemberId());
        }
        UnWorkTask task = new UnWorkTask();
        task.setProjectId(projectId);
        task.setGroupId(input.groupId());
        task.setTitle(input.title().trim());
        task.setDescription(input.description());
        task.setOwnerMemberId(input.ownerMemberId());
        task.setPriority(priority(input.priority()));
        task.setDueTime(input.dueTime());
        task.setFieldData(validatedData(projectId, input.fieldData()));
        task.setStatus("todo");
        task.setVersion(0);
        tasks.insert(task);
        record(task.getId(), actor.getMemberId(), "create", null, task.getFieldData());
        return taskView(task);
    }

    @Operation(summary = "查询详情（工作任务）")
    @GetMapping("/tasks/{taskId}")
    public TaskView detail(@PathVariable long tenantId, @PathVariable long projectId,
                           @PathVariable long taskId, HttpServletRequest request) {
        return taskView(requireTask(tenantId, projectId, taskId, request));
    }

    /** 编辑按版本条件写入；并发覆盖返回 409，日志与修改在同一事务提交。 */
    @Operation(summary = "修改（工作任务）")
    @PutMapping("/tasks/{taskId}")
    @Transactional
    public TaskView update(@PathVariable long tenantId, @PathVariable long projectId, @PathVariable long taskId,
                           @Valid @RequestBody TaskUpdate input, HttpServletRequest request) {
        UnWorkTask existing = requireTask(tenantId, projectId, taskId, request);
        requireEditor(tenantId, projectId, existing, request);
        requireGroup(projectId, input.groupId());
        if (input.ownerMemberId() != null) {
            requireParticipant(projectId, input.ownerMemberId());
        }
        String updatedData = validatedData(projectId, input.fieldData());
        int changed = tasks.update(null, new LambdaUpdateWrapper<UnWorkTask>()
                .eq(UnWorkTask::getId, taskId).eq(UnWorkTask::getVersion, input.version())
                .set(UnWorkTask::getTitle, input.title().trim())
                .set(UnWorkTask::getDescription, input.description())
                .set(UnWorkTask::getGroupId, input.groupId())
                .set(UnWorkTask::getOwnerMemberId, input.ownerMemberId())
                .set(UnWorkTask::getPriority, priority(input.priority()))
                .set(UnWorkTask::getDueTime, input.dueTime())
                .set(UnWorkTask::getFieldData, updatedData)
                .set(UnWorkTask::getVersion, input.version() + 1));
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务已被其他人修改，请刷新后重试");
        }
        record(taskId, access.systemMember(request).getId(), "edit", existing.getFieldData(), updatedData);
        return taskView(tasks.selectById(taskId));
    }

    /** 任务状态仅能在固定状态集合中变更，并追加可追溯的历史。 */
    @Operation(summary = "修改状态（工作任务）")
    @PutMapping("/tasks/{taskId}/status")
    @Transactional
    public TaskView status(@PathVariable long tenantId, @PathVariable long projectId, @PathVariable long taskId,
                           @Valid @RequestBody StatusInput input, HttpServletRequest request) {
        UnWorkTask existing = requireTask(tenantId, projectId, taskId, request);
        requireEditor(tenantId, projectId, existing, request);
        if (!List.of("todo", "doing", "done", "cancelled").contains(input.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务状态无效");
        }
        int changed = tasks.update(null, new LambdaUpdateWrapper<UnWorkTask>()
                .eq(UnWorkTask::getId, taskId).eq(UnWorkTask::getVersion, input.version())
                .set(UnWorkTask::getStatus, input.status())
                .set(UnWorkTask::getVersion, input.version() + 1));
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务已被其他人修改，请刷新后重试");
        }
        record(taskId, access.systemMember(request).getId(), "status", existing.getStatus(), input.status());
        return taskView(tasks.selectById(taskId));
    }

    @Operation(summary = "查询处理历史（工作任务）")
    @GetMapping("/tasks/{taskId}/history")
    public List<HistoryView> history(@PathVariable long tenantId, @PathVariable long projectId,
                                     @PathVariable long taskId, HttpServletRequest request) {
        requireTask(tenantId, projectId, taskId, request);
        return history.selectList(new LambdaQueryWrapper<UnWorkTaskHistory>()
                        .eq(UnWorkTaskHistory::getTaskId, taskId)
                        .orderByAsc(UnWorkTaskHistory::getId))
                .stream().map(item -> new HistoryView(item.getId(), item.getActionCode(),
                        item.getOperatorMemberId(), item.getBeforeData(), item.getAfterData(), item.getCreateTime()))
                .toList();
    }

    /** 协作者、关注人和验收人都必须先属于同一项目。 */
    @Operation(summary = "添加成员（工作任务）")
    @PostMapping("/tasks/{taskId}/members")
    public MemberView addMember(@PathVariable long tenantId, @PathVariable long projectId,
                                @PathVariable long taskId, @Valid @RequestBody MemberInput input,
                                HttpServletRequest request) {
        UnWorkTask task = requireTask(tenantId, projectId, taskId, request);
        requireEditor(tenantId, projectId, task, request);
        requireParticipant(projectId, input.memberId());
        if (!List.of("collaborator", "watcher", "reviewer").contains(input.relationType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务成员关系无效");
        }
        if (taskMembers.selectCount(new LambdaQueryWrapper<UnWorkTaskMember>()
                .eq(UnWorkTaskMember::getTaskId, taskId)
                .eq(UnWorkTaskMember::getMemberId, input.memberId())
                .eq(UnWorkTaskMember::getRelationType, input.relationType())) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务成员关系已存在");
        }
        UnWorkTaskMember member = new UnWorkTaskMember();
        member.setTaskId(taskId);
        member.setMemberId(input.memberId());
        member.setRelationType(input.relationType());
        taskMembers.insert(member);
        return memberView(member);
    }

    @Operation(summary = "查询成员（工作任务）")
    @GetMapping("/tasks/{taskId}/members")
    public List<MemberView> members(@PathVariable long tenantId, @PathVariable long projectId,
                                    @PathVariable long taskId, HttpServletRequest request) {
        requireTask(tenantId, projectId, taskId, request);
        return taskMembers.selectList(new LambdaQueryWrapper<UnWorkTaskMember>()
                        .eq(UnWorkTaskMember::getTaskId, taskId)
                        .orderByAsc(UnWorkTaskMember::getId))
                .stream().map(this::memberView).toList();
    }

    @Operation(summary = "移除成员（工作任务）")
    @DeleteMapping("/tasks/{taskId}/members/{relationId}")
    public void removeMember(@PathVariable long tenantId, @PathVariable long projectId,
                             @PathVariable long taskId, @PathVariable long relationId,
                             HttpServletRequest request) {
        UnWorkTask task = requireTask(tenantId, projectId, taskId, request);
        requireEditor(tenantId, projectId, task, request);
        UnWorkTaskMember member = taskMembers.selectById(relationId);
        if (member == null || !member.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务成员关系不存在");
        }
        taskMembers.deleteById(relationId);
    }

    private UnWorkTask requireTask(long tenantId, long projectId, long taskId, HttpServletRequest request) {
        projects.requireProject(tenantId, projectId, request);
        UnWorkTask task = tasks.selectById(taskId);
        if (task == null || !task.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private void requireEditor(long tenantId, long projectId, UnWorkTask task, HttpServletRequest request) {
        Long actorId = access.systemMember(request).getId();
        UnWorkProject project = projects.requireProject(tenantId, projectId, request);
        boolean collaborator = taskMembers.selectCount(new LambdaQueryWrapper<UnWorkTaskMember>()
                .eq(UnWorkTaskMember::getTaskId, task.getId())
                .eq(UnWorkTaskMember::getMemberId, actorId)
                .eq(UnWorkTaskMember::getRelationType, "collaborator")) > 0;
        if (!actorId.equals(task.getOwnerMemberId()) && !actorId.equals(project.getOwnerMemberId())
                && !collaborator) {
            access.tenantAdmin(request, tenantId);
        }
    }

    private void requireGroup(long projectId, long groupId) {
        UnWorkTaskGroup group = groups.selectById(groupId);
        if (group == null || !group.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务组不属于此项目");
        }
    }

    private void requireParticipant(long projectId, long memberId) {
        if (participants.selectCount(new LambdaQueryWrapper<UnWorkProjectMember>()
                .eq(UnWorkProjectMember::getProjectId, projectId)
                .eq(UnWorkProjectMember::getMemberId, memberId)) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务负责人不是项目参与人");
        }
    }

    /** 按启用字段验证用户提交的动态值，不允许保存未知字段。 */
    private String validatedData(long projectId, Map<String, Object> values) {
        Map<String, Object> submitted = values == null ? Map.of() : values;
        Map<String, UnWorkTaskField> definitions = new HashMap<>();
        for (UnWorkTaskField field : fields.selectList(new LambdaQueryWrapper<UnWorkTaskField>()
                .eq(UnWorkTaskField::getProjectId, projectId).eq(UnWorkTaskField::getStatus, 1))) {
            definitions.put(field.getFieldKey(), field);
            Object value = submitted.get(field.getFieldKey());
            if (field.getRequiredFlag() != null && field.getRequiredFlag() == 1
                    && (value == null || value instanceof String text && text.isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必填字段缺失：" + field.getFieldKey());
            }
        }
        for (var item : submitted.entrySet()) {
            UnWorkTaskField field = definitions.get(item.getKey());
            if (field == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知或停用的任务字段：" + item.getKey());
            }
            Object value = item.getValue();
            if (value == null) {
                continue;
            }
            boolean valid = switch (field.getFieldType()) {
                case "text" -> value instanceof String;
                case "number" -> value instanceof Number;
                case "boolean" -> value instanceof Boolean;
                case "date" -> value instanceof String text && validDate(text);
                case "select" -> value instanceof String text && allowedOption(field, text);
                default -> false;
            };
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务字段值无效：" + field.getFieldKey());
            }
        }
        return stringify(submitted);
    }

    private boolean validDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean allowedOption(UnWorkTaskField field, String value) {
        try {
            List<String> options = json.readValue(field.getOptionData(), new TypeReference<>() { });
            return options.contains(value);
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段数据无法保存");
        }
    }

    private Byte priority(Byte priority) {
        if (priority == null) {
            return 2;
        }
        if (priority < 1 || priority > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务优先级必须在 1 到 5 之间");
        }
        return priority;
    }

    private void record(long taskId, long actorId, String action, String before, String after) {
        UnWorkTaskHistory item = new UnWorkTaskHistory();
        item.setTaskId(taskId);
        item.setOperatorMemberId(actorId);
        item.setActionCode(action);
        item.setBeforeData(before == null ? null : stringify(before));
        item.setAfterData(after == null ? null : stringify(after));
        history.insert(item);
    }

    private FieldView fieldView(UnWorkTaskField field) {
        return new FieldView(field.getId(), field.getFieldKey(), field.getFieldName(), field.getFieldType(),
                field.getRequiredFlag(), field.getOptionData(), field.getSortNo(), field.getStatus());
    }

    private TaskView taskView(UnWorkTask task) {
        return new TaskView(task.getId(), task.getGroupId(), task.getTitle(), task.getDescription(),
                task.getOwnerMemberId(), task.getStatus(), task.getPriority(), task.getDueTime(),
                task.getFieldData(), task.getVersion());
    }

    private MemberView memberView(UnWorkTaskMember member) {
        return new MemberView(member.getId(), member.getMemberId(), member.getRelationType());
    }

    public record FieldInput(@NotBlank String fieldKey, @NotBlank @Size(max = 100) String fieldName,
                             @NotBlank String fieldType, boolean required, List<String> options, Integer sortNo) { }
    public record FieldUpdate(@NotBlank @Size(max = 100) String fieldName,
                              boolean required, boolean enabled, Integer sortNo) { }
    public record FieldView(Long id, String fieldKey, String fieldName, String fieldType,
                            Byte required, String optionsJson, Integer sortNo, Byte status) { }
    public record TaskInput(long groupId, @NotBlank @Size(max = 200) String title, String description,
                            Long ownerMemberId, Byte priority, LocalDateTime dueTime, Map<String, Object> fieldData) { }
    public record TaskUpdate(long groupId, @NotBlank @Size(max = 200) String title, String description,
                             Long ownerMemberId, Byte priority, LocalDateTime dueTime,
                             Map<String, Object> fieldData, int version) { }
    public record StatusInput(@NotBlank String status, int version) { }
    public record TaskView(Long id, Long groupId, String title, String description, Long ownerMemberId,
                           String status, Byte priority, LocalDateTime dueTime, String fieldDataJson, Integer version) { }
    public record TaskPage(List<TaskView> records, long total, long page, long size) { }
    public record MemberInput(long memberId, @NotBlank String relationType) { }
    public record MemberView(Long id, Long memberId, String relationType) { }
    public record HistoryView(Long id, String actionCode, Long operatorMemberId,
                              String beforeJson, String afterJson, LocalDateTime createTime) { }
}
