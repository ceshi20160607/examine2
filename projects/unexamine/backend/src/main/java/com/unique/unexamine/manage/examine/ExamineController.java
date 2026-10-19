package com.unique.unexamine.manage.examine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import com.unique.unexamine.manage.common.SessionAccess;
import com.unique.unexamine.manage.module.ModuleRecordAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/** 审批定义、节点人员及实例处理入口；运行实例始终使用发起时的配置快照。 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/examines")
public class ExamineController {
    private final SessionAccess access;
    private final UnExamineMapper examines;
    private final UnExamineGroupMapper groups;
    private final UnExamineConfigMapper configs;
    private final UnExamineConfigUserMapper configUsers;
    private final UnExamineNodeMapper nodes;
    private final UnExamineNodeUserMapper nodeUsers;
    private final UnExamineNodeFieldMapper nodeFields;
    private final UnExamineExampleMapper examples;
    private final UnExamineExampleNodeMapper exampleNodes;
    private final UnExamineExampleNodeUserMapper exampleUsers;
    private final UnExamineExceptionMapper exceptions;
    private final UnModuleExamineMapper moduleExamines;
    private final UnModuleRecordMapper records;
    private final UnModuleFieldMapper fields;
    private final UnSysTenantMemberMapper tenantMembers;
    private final UnSysMemberRoleMapper memberRoles;
    private final UnSysRoleMapper roles;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModuleRecordAccess recordAccess;

    public ExamineController(SessionAccess access, UnExamineMapper examines, UnExamineGroupMapper groups,
                             UnExamineConfigMapper configs, UnExamineConfigUserMapper configUsers,
                             UnExamineNodeMapper nodes, UnExamineNodeUserMapper nodeUsers,
                             UnExamineNodeFieldMapper nodeFields, UnExamineExampleMapper examples,
                             UnExamineExampleNodeMapper exampleNodes, UnExamineExampleNodeUserMapper exampleUsers,
                             UnExamineExceptionMapper exceptions,
                             UnModuleExamineMapper moduleExamines, UnModuleRecordMapper records,
                             UnModuleFieldMapper fields, UnSysTenantMemberMapper tenantMembers,
                             UnSysMemberRoleMapper memberRoles, UnSysRoleMapper roles,
                             JdbcTemplate jdbc, ObjectMapper json, ModuleRecordAccess recordAccess) {
        this.access = access;
        this.examines = examines;
        this.groups = groups;
        this.configs = configs;
        this.configUsers = configUsers;
        this.nodes = nodes;
        this.nodeUsers = nodeUsers;
        this.nodeFields = nodeFields;
        this.examples = examples;
        this.exampleNodes = exampleNodes;
        this.exampleUsers = exampleUsers;
        this.exceptions = exceptions;
        this.moduleExamines = moduleExamines;
        this.records = records;
        this.fields = fields;
        this.tenantMembers = tenantMembers;
        this.memberRoles = memberRoles;
        this.roles = roles;
        this.jdbc = jdbc;
        this.json = json;
        this.recordAccess = recordAccess;
    }

    /** 租户成员可查看审批入口，分页上限避免一次取出全部定义。 */
    @GetMapping
    public Page<UnExamine> list(@PathVariable long tenantId, @RequestParam(defaultValue = "1") long page,
                                @RequestParam(defaultValue = "20") long size,
                                @RequestParam(defaultValue = "sortNum") String sortField,
                                @RequestParam(defaultValue = "asc") String direction, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        if (page < 1 || size < 1 || size > 100) bad("分页范围无效");
        if (!Set.of("asc", "desc").contains(direction)) bad("排序方向无效");
        boolean asc = "asc".equals(direction);
        LambdaQueryWrapper<UnExamine> query = new LambdaQueryWrapper<UnExamine>()
                .eq(UnExamine::getTenantId, tenantId);
        switch (sortField) {
            case "sortNum" -> query.orderBy(true, asc, UnExamine::getSortNum);
            case "name" -> query.orderBy(true, asc, UnExamine::getName);
            case "createTime" -> query.orderBy(true, asc, UnExamine::getCreateTime);
            default -> bad("排序字段无效");
        }
        query.orderByAsc(UnExamine::getId);
        return examines.selectPage(new Page<>(page, size), query);
    }

    /** 创建审批定义；启用前需保存完整节点和人员配置。 */
    @PostMapping
    public UnExamine create(@PathVariable long tenantId, @Valid @RequestBody ExamineInput body,
                            HttpServletRequest request) {
        long memberId = access.tenantAdmin(request, tenantId).getMemberId();
        if (body.groupId() != null) requireGroup(tenantId, body.groupId());
        UnExamine examine = new UnExamine();
        examine.setTenantId(tenantId);
        examine.setGroupId(body.groupId());
        examine.setName(body.name().trim());
        examine.setDescription(body.description());
        examine.setIcon(body.icon());
        examine.setSortNum(body.sortNum() == null ? 0 : body.sortNum());
        examine.setStatus("DRAFT");
        examine.setCreateMemberId(memberId);
        examines.insert(examine);
        return examine;
    }

    /** 管理审批分组及其展示顺序。 */
    @GetMapping("/groups")
    public List<UnExamineGroup> groups(@PathVariable long tenantId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        return groups.selectList(new LambdaQueryWrapper<UnExamineGroup>()
                .eq(UnExamineGroup::getTenantId, tenantId).orderByAsc(UnExamineGroup::getSortNum));
    }

    @PostMapping("/groups")
    public UnExamineGroup createGroup(@PathVariable long tenantId, @Valid @RequestBody GroupInput body,
                                      HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnExamineGroup group = new UnExamineGroup();
        group.setTenantId(tenantId);
        group.setName(body.name().trim());
        group.setSortNum(body.sortNum() == null ? 0 : body.sortNum());
        group.setStatus("ENABLED");
        groups.insert(group);
        return group;
    }

    /** 详情同时返回节点人员和节点字段，配置界面无需猜测缺失部分。 */
    @GetMapping("/{examineId}")
    public ExamineDetail detail(@PathVariable long tenantId, @PathVariable long examineId,
                                HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        UnExamine examine = examineInTenant(tenantId, examineId);
        List<UnExamineNode> nodeList = nodesFor(examineId);
        List<UnExamineNodeUser> users = new ArrayList<>();
        List<UnExamineNodeField> fieldList = new ArrayList<>();
        for (UnExamineNode node : nodeList) {
            users.addAll(nodeUsers.selectList(new LambdaQueryWrapper<UnExamineNodeUser>()
                    .eq(UnExamineNodeUser::getNodeId, node.getId()).orderByAsc(UnExamineNodeUser::getSortNum)));
            fieldList.addAll(nodeFields.selectList(new LambdaQueryWrapper<UnExamineNodeField>()
                    .eq(UnExamineNodeField::getNodeId, node.getId()).orderByAsc(UnExamineNodeField::getSortNum)));
        }
        return new ExamineDetail(examine,
                configs.selectOne(new LambdaQueryWrapper<UnExamineConfig>().eq(UnExamineConfig::getExamineId, examineId)),
                configUsers.selectList(new LambdaQueryWrapper<UnExamineConfigUser>().eq(UnExamineConfigUser::getExamineId, examineId)),
                nodeList, users, fieldList);
    }

    /** 整体保存审批草稿；已发起实例的快照与人员不受草稿变化影响。 */
    @PutMapping("/{examineId}/design")
    @Transactional
    public ExamineDetail saveDesign(@PathVariable long tenantId, @PathVariable long examineId,
                                    @RequestBody DesignInput body, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnExamine examine = lockExamine(tenantId, examineId);
        if (body.nodes() == null || body.configUsers() == null) bad("审批节点和适用对象不能为空");
        if (body.name() != null && body.name().trim().length() > 100) bad("审批名称过长");
        if (body.name() != null && !body.name().isBlank()) examine.setName(body.name().trim());
        if (body.groupId() != null) requireGroup(tenantId, body.groupId());
        examine.setGroupId(body.groupId());
        examine.setIcon(body.icon());
        examine.setDescription(body.description());
        examine.setSortNum(body.sortNum() == null ? 0 : body.sortNum());
        // 配置修改后必须重新校验启用，已发起的实例继续使用自己的快照。
        examine.setStatus("DRAFT");
        examines.updateById(examine);
        configs.delete(new LambdaQueryWrapper<UnExamineConfig>().eq(UnExamineConfig::getExamineId, examineId));
        UnExamineConfig config = new UnExamineConfig();
        config.setExamineId(examineId);
        config.setApplyScope(body.applyScope() == null ? "ALL" : body.applyScope());
        if (!Set.of("ALL", "SPECIFIED").contains(config.getApplyScope())) bad("适用范围无效");
        config.setAllowWithdraw((byte) (Boolean.FALSE.equals(body.allowWithdraw()) ? 0 : 1));
        config.setRestartMode("FIRST");
        if ((body.timeoutMinutes() == null) != (body.timeoutAction() == null)) bad("超时时长与动作须同时配置");
        if (body.timeoutMinutes() != null && (body.timeoutMinutes() < 1 || body.timeoutMinutes() > 525600
                || !Set.of("PASS", "TRANSFER").contains(body.timeoutAction())))
            bad("当前超时仅支持 PASS 或 TRANSFER，时长须为 1 至 525600 分钟");
        config.setTimeoutMinutes(body.timeoutMinutes());
        config.setTimeoutAction(body.timeoutAction());
        configs.insert(config);
        configUsers.delete(new LambdaQueryWrapper<UnExamineConfigUser>().eq(UnExamineConfigUser::getExamineId, examineId));
        for (TargetInput target : body.configUsers()) {
            if (target.targetType() == null || !Set.of("MEMBER", "DEPARTMENT").contains(target.targetType())
                    || target.targetId() == null) bad("适用对象无效");
            UnExamineConfigUser user = new UnExamineConfigUser();
            user.setExamineId(examineId);
            user.setTargetType(target.targetType());
            user.setTargetId(target.targetId());
            configUsers.insert(user);
        }
        for (UnExamineNode old : nodesFor(examineId)) {
            nodeUsers.delete(new LambdaQueryWrapper<UnExamineNodeUser>().eq(UnExamineNodeUser::getNodeId, old.getId()));
            nodeFields.delete(new LambdaQueryWrapper<UnExamineNodeField>().eq(UnExamineNodeField::getNodeId, old.getId()));
        }
        nodes.delete(new LambdaQueryWrapper<UnExamineNode>().eq(UnExamineNode::getExamineId, examineId));
        Set<String> keys = new HashSet<>();
        for (NodeInput input : body.nodes()) {
            if (input.nodeKey() == null || input.nodeKey().isBlank() || !keys.add(input.nodeKey().trim())) bad("审批节点标识缺失或重复");
            if (input.name() == null || input.name().isBlank()) bad("审批节点名称不能为空");
            if (!"APPROVE".equals(input.nodeType()) && !"COPY".equals(input.nodeType())) bad("当前仅支持审批和抄送节点");
            if ("APPROVE".equals(input.nodeType()) && (input.passMode() == null
                    || !Set.of("ANY", "ALL", "SEQUENTIAL").contains(input.passMode()))) bad("多人审批方式无效");
            UnExamineNode node = new UnExamineNode();
            node.setExamineId(examineId);
            node.setNodeKey(input.nodeKey().trim());
            node.setName(input.name().trim());
            node.setNodeType(input.nodeType());
            node.setSortNum(input.sortNum() == null ? 0 : input.sortNum());
            node.setConditionRule(input.conditionRule());
            node.setApproverType(input.approverType());
            node.setPassMode(input.passMode());
            node.setFallbackMemberId(input.fallbackMemberId());
            nodes.insert(node);
            if (input.users() == null || input.fields() == null) bad("节点人员和字段配置不能为空");
            for (TargetInput target : input.users()) {
                if (target.targetType() == null || !Set.of("MEMBER", "DEPARTMENT", "ROLE").contains(target.targetType())
                        || target.targetId() == null) bad("审批人配置无效");
                UnExamineNodeUser user = new UnExamineNodeUser();
                user.setNodeId(node.getId());
                user.setTargetType(target.targetType());
                user.setTargetId(target.targetId());
                user.setSortNum(target.sortNum() == null ? 0 : target.sortNum());
                nodeUsers.insert(user);
            }
            for (FieldInput field : input.fields()) {
                if (field.fieldId() == null || fields.selectById(field.fieldId()) == null) bad("模块字段不存在");
                UnExamineNodeField item = new UnExamineNodeField();
                item.setNodeId(node.getId());
                item.setFieldId(field.fieldId());
                item.setIsVisible((byte) (Boolean.FALSE.equals(field.visible()) ? 0 : 1));
                item.setIsEditable((byte) (Boolean.TRUE.equals(field.editable()) ? 1 : 0));
                item.setIsRequired((byte) (Boolean.TRUE.equals(field.required()) ? 1 : 0));
                item.setSortNum(field.sortNum() == null ? 0 : field.sortNum());
                nodeFields.insert(item);
            }
        }
        return detail(tenantId, examineId, request);
    }

    /** 启用前检查运行器支持的节点类型和处理人来源。 */
    @PostMapping("/{examineId}/enable")
    @Transactional
    public UnExamine enable(@PathVariable long tenantId, @PathVariable long examineId, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnExamine examine = lockExamine(tenantId, examineId);
        List<UnExamineNode> nodeList = nodesFor(examineId);
        if (nodeList.isEmpty()) bad("审批至少需要一个节点");
        Set<Integer> positions = new HashSet<>();
        for (UnExamineNode node : nodeList) {
            if (!positions.add(node.getSortNum())) bad("审批节点顺序不能重复");
            if (node.getConditionRule() != null && !node.getConditionRule().isBlank()) bad("当前版本暂不支持条件节点启用");
            if (!Set.of("APPROVE", "COPY").contains(node.getNodeType())) bad("节点类型暂不支持启用");
            if ("APPROVE".equals(node.getNodeType()) && (node.getApproverType() == null || !Set.of("MEMBER", "DEPARTMENT", "ROLE", "SUPERIOR", "STARTER_CHOICE")
                    .contains(node.getApproverType()))) bad("审批人来源无效");
            if ("APPROVE".equals(node.getNodeType()) && !Set.of("SUPERIOR", "STARTER_CHOICE").contains(node.getApproverType())
                    && nodeUsers.selectCount(new LambdaQueryWrapper<UnExamineNodeUser>()
                    .eq(UnExamineNodeUser::getNodeId, node.getId())) == 0) bad("审批节点必须配置处理人");
        }
        examine.setStatus("ENABLED");
        examines.updateById(examine);
        return examine;
    }

    /** 停用只影响新发起，已发起实例按冻结的人员和节点继续处理。 */
    @PostMapping("/{examineId}/disable")
    @Transactional
    public UnExamine disable(@PathVariable long tenantId, @PathVariable long examineId, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnExamine examine = lockExamine(tenantId, examineId);
        examine.setStatus("DISABLED");
        examines.updateById(examine);
        return examine;
    }

    /** 发起时核对模块绑定、业务记录和申请范围，并冻结审批定义。 */
    @PostMapping("/{examineId}/examples")
    @Transactional
    public UnExamineExample start(@PathVariable long tenantId, @PathVariable long examineId,
                                  @RequestBody StartInput body, HttpServletRequest request) {
        UnSysTenantMember applicant = access.tenantMember(request, tenantId);
        UnExamine examine = lockExamine(tenantId, examineId);
        if (!"ENABLED".equals(examine.getStatus())) conflict("审批尚未启用");
        if (body.moduleId() == null || body.recordId() == null) bad("模块和记录不能为空");
        UnModuleRecord record = records.selectById(body.recordId());
        if (record == null || !eq(record.getTenantId(), tenantId) || !eq(record.getModuleId(), body.moduleId())
                || (record.getRecordStatus() != null && record.getRecordStatus() == 2)) bad("记录不属于此租户和模块，或已删除");
        recordAccess.requireWrite(recordAccess.scope(request, tenantId), record);
        if (moduleExamines.selectCount(new LambdaQueryWrapper<UnModuleExamine>()
                .in(UnModuleExamine::getTenantId, 0L, tenantId).eq(UnModuleExamine::getModuleId, body.moduleId())
                .eq(UnModuleExamine::getExamineId, examineId).eq(UnModuleExamine::getStatus, 1)) == 0) bad("模块未绑定此审批");
        ExamineDetail detail = detail(tenantId, examineId, request);
        if (detail.config() != null && "SPECIFIED".equals(detail.config().getApplyScope())) {
            boolean allowed = detail.configUsers().stream().anyMatch(u ->
                    ("MEMBER".equals(u.getTargetType()) && eq(u.getTargetId(), applicant.getMemberId()))
                            || ("DEPARTMENT".equals(u.getTargetType()) && eq(u.getTargetId(), applicant.getDepartmentId())));
            if (!allowed) forbidden("不在此审批的发起范围");
        }
        for (UnExamineNodeField field : detail.fields()) {
            UnModuleField moduleField = fields.selectById(field.getFieldId());
            if (moduleField == null || !eq(moduleField.getModuleId(), body.moduleId())) bad("审批节点字段不属于关联模块");
        }
        List<Long> selected = body.selectedMemberIds() == null ? List.of() : body.selectedMemberIds();
        for (Long id : selected) if (!validTenantMember(tenantId, id)) bad("自选处理人不属于此租户");
        String snapshot = writeJson(new ExamineSnapshot(detail.config(), detail.nodes(), detail.users(), detail.fields(), selected));
        UnExamineExample example = new UnExamineExample();
        example.setTenantId(tenantId);
        example.setExamineId(examineId);
        example.setModuleId(body.moduleId());
        example.setRecordId(body.recordId());
        example.setApplicantMemberId(applicant.getMemberId());
        example.setDefinitionSnapshot(snapshot);
        example.setStatus("PENDING");
        examples.insert(example);
        for (UnExamineNode node : detail.nodes()) {
            UnExamineExampleNode item = new UnExamineExampleNode();
            item.setExampleId(example.getId());
            item.setDefinitionNodeId(node.getId());
            item.setNodeKey(node.getNodeKey());
            item.setName(node.getName());
            item.setNodeType(node.getNodeType());
            item.setPassMode(node.getPassMode());
            item.setSortNum(node.getSortNum());
            item.setRoundNo(1);
            item.setStatus("WAITING");
            exampleNodes.insert(item);
        }
        advance(example, readJson(snapshot));
        return examples.selectById(example.getId());
    }

    /** 待办只返回本人所属且当前可处理的实例节点人员。 */
    @GetMapping("/tasks")
    public List<UnExamineExampleNodeUser> inbox(@PathVariable long tenantId, HttpServletRequest request) {
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        List<UnExamineExampleNodeUser> result = new ArrayList<>();
        for (UnExamineExampleNodeUser user : exampleUsers.selectList(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getMemberId, memberId).eq(UnExamineExampleNodeUser::getStatus, "PENDING"))) {
            UnExamineExampleNode node = exampleNodes.selectById(user.getExampleNodeId());
            UnExamineExample example = node == null ? null : examples.selectById(node.getExampleId());
            if (example != null && eq(example.getTenantId(), tenantId) && "PENDING".equals(node.getStatus())
                    && mayAct(user, node)) result.add(user);
        }
        return result;
    }

    /** 实例行锁使多人审批状态、意见和下一节点在同一事务内改变。 */
    @PostMapping("/examples/{exampleId}/actions")
    @Transactional
    public UnExamineExampleNodeUser act(@PathVariable long tenantId, @PathVariable long exampleId,
                                        @Valid @RequestBody ActionInput body, HttpServletRequest request) {
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        UnExamineExample example = lockExample(tenantId, exampleId);
        if (!Set.of("APPROVE", "REJECT").contains(body.actionType())) bad("审批操作类型无效");
        UnExamineExampleNodeUser user = exampleUsers.selectOne(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getRequestKey, body.requestKey()));
        if (user != null) {
            UnExamineExampleNode oldNode = exampleNodes.selectById(user.getExampleNodeId());
            if (oldNode != null && eq(oldNode.getExampleId(), exampleId) && eq(user.getMemberId(), memberId)
                    && (("APPROVE".equals(body.actionType()) && "APPROVED".equals(user.getStatus()))
                    || ("REJECT".equals(body.actionType()) && "REJECTED".equals(user.getStatus())))) return user;
            conflict("请求标识已被其他审批操作使用");
        }
        if (!"PENDING".equals(example.getStatus())) conflict("审批实例已结束");
        user = exampleUsers.selectById(body.nodeUserId());
        UnExamineExampleNode node = user == null ? null : exampleNodes.selectById(user.getExampleNodeId());
        if (node == null || !eq(node.getExampleId(), exampleId) || !eq(user.getMemberId(), memberId)) forbidden("不是此审批节点的处理人");
        if (!"PENDING".equals(node.getStatus()) || !"PENDING".equals(user.getStatus()) || !mayAct(user, node)) conflict("审批任务已处理或尚未轮到本人");
        LocalDateTime now = LocalDateTime.now();
        boolean approve = "APPROVE".equals(body.actionType());
        user.setStatus(approve ? "APPROVED" : "REJECTED");
        user.setOpinion(body.opinion());
        user.setRequestKey(body.requestKey());
        user.setActionTime(now);
        exampleUsers.updateById(user);
        if (!approve) {
            finishNode(node, "REJECTED", now);
            cancelOtherUsers(node.getId(), now);
            finishExample(example, "REJECTED", now);
        } else if ("ANY".equals(node.getPassMode()) || exampleUsers.selectCount(
                new LambdaQueryWrapper<UnExamineExampleNodeUser>().eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId())
                        .eq(UnExamineExampleNodeUser::getStatus, "PENDING")) == 0) {
            finishNode(node, "APPROVED", now);
            cancelOtherUsers(node.getId(), now);
            advance(example, readJson(example.getDefinitionSnapshot()));
        }
        return user;
    }

    /** 当前待办人可把自己的处理席位转给同租户有效成员，原人员记录保留在实例历史。 */
    @PostMapping("/examples/{exampleId}/transfer")
    @Transactional
    public TransferView transfer(@PathVariable long tenantId, @PathVariable long exampleId,
                                 @Valid @RequestBody TransferInput body, HttpServletRequest request) {
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        UnExamineExample example = lockExample(tenantId, exampleId);
        UnExamineExampleNodeUser previous = exampleUsers.selectOne(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getRequestKey, body.requestKey()));
        String prefix = "转办给成员" + body.targetMemberId() + "：";
        if (previous != null) {
            if (eq(previous.getId(), body.nodeUserId()) && eq(previous.getMemberId(), memberId)
                    && "CANCELLED".equals(previous.getStatus())
                    && previous.getOpinion() != null && previous.getOpinion().startsWith(prefix)) {
                UnExamineExampleNodeUser target = exampleUsers.selectOne(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                        .eq(UnExamineExampleNodeUser::getExampleNodeId, previous.getExampleNodeId())
                        .eq(UnExamineExampleNodeUser::getMemberId, body.targetMemberId()));
                if (target != null) return new TransferView(previous, target);
            }
            conflict("请求标识已被其他审批操作使用");
        }
        if (!"PENDING".equals(example.getStatus())) conflict("审批实例已结束");
        UnExamineExampleNodeUser current = exampleUsers.selectById(body.nodeUserId());
        UnExamineExampleNode node = current == null ? null : exampleNodes.selectById(current.getExampleNodeId());
        if (node == null || !eq(node.getExampleId(), exampleId) || !eq(current.getMemberId(), memberId))
            forbidden("不是此审批节点的处理人");
        if (!"PENDING".equals(node.getStatus()) || !"PENDING".equals(current.getStatus()) || !mayAct(current, node))
            conflict("审批任务已处理或尚未轮到本人");
        if (body.targetMemberId() == memberId || !validTenantMember(tenantId, body.targetMemberId()))
            bad("转办对象须为同租户其他有效成员");
        return moveAssignee(current, body.targetMemberId(), body.requestKey(), body.opinion());
    }

    /** 显式触发到期节点；管理员核对冻结配置和节点开始时间后执行自动通过或转办。 */
    @PostMapping("/examples/{exampleId}/timeout")
    @Transactional
    public UnExamineException timeout(@PathVariable long tenantId, @PathVariable long exampleId,
                                      @RequestBody TimeoutInput body, HttpServletRequest request) {
        long handler = access.tenantAdmin(request, tenantId).getMemberId();
        UnExamineExample example = lockExample(tenantId, exampleId);
        if (!"PENDING".equals(example.getStatus())) conflict("审批实例已结束");
        ExamineSnapshot snapshot = readJson(example.getDefinitionSnapshot());
        UnExamineConfig config = snapshot.config();
        if (config == null || config.getTimeoutMinutes() == null || config.getTimeoutAction() == null)
            conflict("此审批未配置超时处理");
        UnExamineExampleNode node = exampleNodes.selectOne(new LambdaQueryWrapper<UnExamineExampleNode>()
                .eq(UnExamineExampleNode::getExampleId, exampleId)
                .eq(UnExamineExampleNode::getNodeKey, example.getCurrentNodeKey())
                .eq(UnExamineExampleNode::getStatus, "PENDING"));
        if (node == null || node.getStartTime() == null) conflict("没有可处理的当前节点");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(node.getStartTime().plusMinutes(config.getTimeoutMinutes()))) conflict("审批节点尚未超时");
        if (exceptions.selectCount(new LambdaQueryWrapper<UnExamineException>()
                .eq(UnExamineException::getExampleNodeId, node.getId())
                .eq(UnExamineException::getExceptionType, "TIMEOUT")) > 0) conflict("此节点超时已处理");
        List<UnExamineExampleNodeUser> pending = exampleUsers.selectList(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId())
                .eq(UnExamineExampleNodeUser::getStatus, "PENDING"));
        if ("PASS".equals(config.getTimeoutAction())) {
            for (UnExamineExampleNodeUser user : pending) {
                user.setStatus("CANCELLED");
                user.setOpinion("超时自动通过，原待办取消");
                user.setActionTime(now);
                exampleUsers.updateById(user);
            }
            finishNode(node, "APPROVED", now);
            advance(example, snapshot);
        } else if ("TRANSFER".equals(config.getTimeoutAction())) {
            if (pending.size() != 1) conflict("超时转办仅支持当前节点一名待处理人");
            if (body.targetMemberId() == null || !validTenantMember(tenantId, body.targetMemberId())
                    || eq(pending.getFirst().getMemberId(), body.targetMemberId())) bad("须指定同租户其他有效转办成员");
            moveAssignee(pending.getFirst(), body.targetMemberId(), null, "超时转办");
        } else conflict("超时动作尚未实现");
        UnExamineException incident = new UnExamineException();
        incident.setExampleId(exampleId);
        incident.setExampleNodeId(node.getId());
        incident.setExceptionType("TIMEOUT");
        incident.setDetail("节点超过 " + config.getTimeoutMinutes() + " 分钟，执行 " + config.getTimeoutAction());
        incident.setStatus("RESOLVED");
        incident.setHandleMemberId(handler);
        incident.setHandleRemark("TRANSFER".equals(config.getTimeoutAction()) ? "转给成员 " + body.targetMemberId() : "自动通过");
        incident.setHandleTime(now);
        exceptions.insert(incident);
        return exceptions.selectById(incident.getId());
    }

    private TransferView moveAssignee(UnExamineExampleNodeUser current, long targetMemberId,
                                      String requestKey, String opinion) {
        if (exampleUsers.selectCount(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, current.getExampleNodeId())
                .eq(UnExamineExampleNodeUser::getMemberId, targetMemberId)) > 0)
            conflict("目标成员已在此节点处理人列表中");
        String description = "转办给成员" + targetMemberId + "：" + (opinion == null ? "" : opinion);
        if (description.length() > 1000) bad("转办说明过长");
        current.setStatus("CANCELLED");
        current.setOpinion(description);
        current.setRequestKey(requestKey);
        current.setActionTime(LocalDateTime.now());
        exampleUsers.updateById(current);
        UnExamineExampleNodeUser next = new UnExamineExampleNodeUser();
        next.setExampleNodeId(current.getExampleNodeId());
        next.setMemberId(targetMemberId);
        next.setSortNum(current.getSortNum());
        next.setStatus("PENDING");
        exampleUsers.insert(next);
        return new TransferView(exampleUsers.selectById(current.getId()), exampleUsers.selectById(next.getId()));
    }

    /** 仅发起人可撤回，且需符合发起时冻结的撤回规则。 */
    @PostMapping("/examples/{exampleId}/withdraw")
    @Transactional
    public UnExamineExample withdraw(@PathVariable long tenantId, @PathVariable long exampleId,
                                      HttpServletRequest request) {
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        UnExamineExample example = lockExample(tenantId, exampleId);
        if (!eq(example.getApplicantMemberId(), memberId)) forbidden("只有发起人可以撤回");
        if (!"PENDING".equals(example.getStatus())) conflict("审批实例已结束");
        ExamineSnapshot snapshot = readJson(example.getDefinitionSnapshot());
        if (snapshot.config() != null && Byte.valueOf((byte) 0).equals(snapshot.config().getAllowWithdraw())) forbidden("此审批不允许撤回");
        LocalDateTime now = LocalDateTime.now();
        for (UnExamineExampleNode node : exampleNodes.selectList(new LambdaQueryWrapper<UnExamineExampleNode>()
                .eq(UnExamineExampleNode::getExampleId, exampleId).eq(UnExamineExampleNode::getStatus, "PENDING"))) {
            finishNode(node, "CANCELLED", now);
            cancelOtherUsers(node.getId(), now);
        }
        finishExample(example, "WITHDRAWN", now);
        return example;
    }

    /** 返回实例节点及逐人意见；租户边界由实例主记录核对。 */
    @GetMapping("/examples/{exampleId}")
    public ExampleHistory history(@PathVariable long tenantId, @PathVariable long exampleId, HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        UnExamineExample example = exampleInTenant(tenantId, exampleId);
        List<UnExamineExampleNode> nodeList = exampleNodes.selectList(new LambdaQueryWrapper<UnExamineExampleNode>()
                .eq(UnExamineExampleNode::getExampleId, exampleId).orderByAsc(UnExamineExampleNode::getSortNum));
        List<UnExamineExampleNodeUser> users = new ArrayList<>();
        for (UnExamineExampleNode node : nodeList) users.addAll(exampleUsers.selectList(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId()).orderByAsc(UnExamineExampleNodeUser::getSortNum)));
        // 审批意见仅向申请人、曾经的处理人及租户管理员公开。
        if (!eq(example.getApplicantMemberId(), actorId)
                && users.stream().noneMatch(user -> eq(user.getMemberId(), actorId)))
            access.tenantAdmin(request, tenantId);
        return new ExampleHistory(example, nodeList, users,
                exceptions.selectList(new LambdaQueryWrapper<UnExamineException>()
                        .eq(UnExamineException::getExampleId, exampleId).orderByAsc(UnExamineException::getId)));
    }

    /** 沿冻结的顺序节点前进；抄送节点自动完成，审批节点必须解析到有效人员。 */
    private void advance(UnExamineExample example, ExamineSnapshot snapshot) {
        List<UnExamineExampleNode> ordered = exampleNodes.selectList(new LambdaQueryWrapper<UnExamineExampleNode>()
                .eq(UnExamineExampleNode::getExampleId, example.getId()).orderByAsc(UnExamineExampleNode::getSortNum));
        for (UnExamineExampleNode node : ordered) {
            if (!"WAITING".equals(node.getStatus())) continue;
            LocalDateTime now = LocalDateTime.now();
            node.setStartTime(now);
            if ("COPY".equals(node.getNodeType())) {
                node.setStatus("SKIPPED");
                node.setFinishTime(now);
                exampleNodes.updateById(node);
                continue;
            }
            UnExamineNode definition = snapshot.nodes().stream().filter(n -> eq(n.getId(), node.getDefinitionNodeId()))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "审批节点快照不完整"));
            List<Long> assignees = resolveAssignees(example, definition, snapshot.users(), snapshot.selectedMemberIds());
            if (assignees.isEmpty()) conflict("审批节点没有有效处理人");
            node.setStatus("PENDING");
            exampleNodes.updateById(node);
            int sort = 0;
            for (Long memberId : assignees) {
                UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
                user.setExampleNodeId(node.getId());
                user.setMemberId(memberId);
                user.setSortNum(sort++);
                user.setStatus("PENDING");
                exampleUsers.insert(user);
            }
            example.setCurrentNodeKey(node.getNodeKey());
            examples.updateById(example);
            return;
        }
        finishExample(example, "APPROVED", LocalDateTime.now());
    }

    /** 角色、部门和上级解析在发起时完成，运行中仅使用实例处理人。 */
    private List<Long> resolveAssignees(UnExamineExample example, UnExamineNode node,
                                        List<UnExamineNodeUser> snapshotUsers, List<Long> selected) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        long tenantId = example.getTenantId();
        if ("STARTER_CHOICE".equals(node.getApproverType())) {
            if (selected != null) for (Long id : selected) if (validTenantMember(tenantId, id)) result.add(id);
        } else if ("SUPERIOR".equals(node.getApproverType())) {
            UnSysTenantMember applicant = tenantMembers.selectOne(new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getMemberId, example.getApplicantMemberId()));
            UnSysTenantMember leader = applicant == null || applicant.getLeaderTenantMemberId() == null
                    ? null : tenantMembers.selectById(applicant.getLeaderTenantMemberId());
            if (leader != null && eq(leader.getTenantId(), tenantId) && validTenantMember(tenantId, leader.getMemberId())) result.add(leader.getMemberId());
            else if (validTenantMember(tenantId, node.getFallbackMemberId())) result.add(node.getFallbackMemberId());
        } else {
            for (UnExamineNodeUser configured : snapshotUsers) {
                if (!eq(configured.getNodeId(), node.getId())) continue;
                if ("MEMBER".equals(configured.getTargetType()) && validTenantMember(tenantId, configured.getTargetId())) result.add(configured.getTargetId());
                if ("DEPARTMENT".equals(configured.getTargetType())) for (UnSysTenantMember member : tenantMembers.selectList(
                        new LambdaQueryWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getTenantId, tenantId)
                                .eq(UnSysTenantMember::getDepartmentId, configured.getTargetId()).eq(UnSysTenantMember::getStatus, 1))) result.add(member.getMemberId());
                if ("ROLE".equals(configured.getTargetType())) {
                    UnSysRole role = roles.selectById(configured.getTargetId());
                    if (role == null || !eq(role.getTenantId(), tenantId) || role.getStatus() == null || role.getStatus() != 1) continue;
                    for (UnSysMemberRole relation : memberRoles.selectList(new LambdaQueryWrapper<UnSysMemberRole>()
                            .eq(UnSysMemberRole::getRoleId, configured.getTargetId()))) {
                        UnSysTenantMember member = tenantMembers.selectById(relation.getTenantMemberId());
                        if (member != null && eq(member.getTenantId(), tenantId) && validTenantMember(tenantId, member.getMemberId())) result.add(member.getMemberId());
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    private boolean mayAct(UnExamineExampleNodeUser user, UnExamineExampleNode node) {
        if (!"SEQUENTIAL".equals(node.getPassMode())) return true;
        return exampleUsers.selectCount(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId())
                .eq(UnExamineExampleNodeUser::getStatus, "PENDING")
                .lt(UnExamineExampleNodeUser::getSortNum, user.getSortNum())) == 0;
    }

    private void cancelOtherUsers(long nodeId, LocalDateTime now) {
        for (UnExamineExampleNodeUser user : exampleUsers.selectList(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId).eq(UnExamineExampleNodeUser::getStatus, "PENDING"))) {
            user.setStatus("CANCELLED");
            user.setActionTime(now);
            exampleUsers.updateById(user);
        }
    }

    private void finishNode(UnExamineExampleNode node, String status, LocalDateTime now) {
        node.setStatus(status);
        node.setFinishTime(now);
        exampleNodes.updateById(node);
    }

    private void finishExample(UnExamineExample example, String status, LocalDateTime now) {
        example.setStatus(status);
        example.setCurrentNodeKey(null);
        example.setFinishTime(now);
        examples.updateById(example);
    }

    private boolean validTenantMember(long tenantId, Long memberId) {
        if (memberId == null) return false;
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_sys_tenant_member tm
                JOIN un_sys_member sm ON sm.id = tm.member_id
                WHERE tm.tenant_id = ? AND tm.member_id = ? AND tm.status = 1 AND sm.status = 1
                """, Long.class, tenantId, memberId);
        return count != null && count > 0;
    }

    private UnExamine examineInTenant(long tenantId, long examineId) {
        UnExamine examine = examines.selectById(examineId);
        if (examine == null || !eq(examine.getTenantId(), tenantId)) notFound("审批定义不存在");
        return examine;
    }

    private UnExamineExample exampleInTenant(long tenantId, long exampleId) {
        UnExamineExample example = examples.selectById(exampleId);
        if (example == null || !eq(example.getTenantId(), tenantId)) notFound("审批实例不存在");
        return example;
    }

    /** 锁住实例主记录以序列化多人操作，并使节点及实例状态一并提交。 */
    private UnExamineExample lockExample(long tenantId, long exampleId) {
        jdbc.queryForList("SELECT id FROM un_examine_example WHERE id = ? AND tenant_id = ? FOR UPDATE", exampleId, tenantId);
        return exampleInTenant(tenantId, exampleId);
    }

    private UnExamine lockExamine(long tenantId, long examineId) {
        jdbc.queryForList("SELECT id FROM un_examine WHERE id = ? AND tenant_id = ? FOR UPDATE", examineId, tenantId);
        return examineInTenant(tenantId, examineId);
    }

    private void requireGroup(long tenantId, long groupId) {
        UnExamineGroup group = groups.selectById(groupId);
        if (group == null || !eq(group.getTenantId(), tenantId)) bad("审批分组不属于此租户");
    }

    private List<UnExamineNode> nodesFor(long examineId) {
        return nodes.selectList(new LambdaQueryWrapper<UnExamineNode>().eq(UnExamineNode::getExamineId, examineId)
                .orderByAsc(UnExamineNode::getSortNum));
    }

    private String writeJson(ExamineSnapshot snapshot) {
        try { return json.writeValueAsString(snapshot); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "审批快照生成失败", ex); }
    }

    private ExamineSnapshot readJson(String value) {
        try { return json.readValue(value, ExamineSnapshot.class); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "审批快照无法读取", ex); }
    }

    private boolean eq(Long id, Long expected) { return id != null && id.equals(expected); }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private void notFound(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    /** 接口输入只描述业务配置，身份与租户范围由会话确定。 */
    public record ExamineInput(@NotBlank @Size(max = 100) String name, Long groupId,
                               @Size(max = 100) String icon, @Size(max = 500) String description, Integer sortNum) {}
    public record GroupInput(@NotBlank @Size(max = 100) String name, Integer sortNum) {}
    public record TargetInput(String targetType, Long targetId, Integer sortNum) {}
    public record FieldInput(Long fieldId, Boolean visible, Boolean editable, Boolean required, Integer sortNum) {}
    public record NodeInput(String nodeKey, String name, String nodeType, Integer sortNum, String conditionRule,
                            String approverType, String passMode, Long fallbackMemberId,
                            List<TargetInput> users, List<FieldInput> fields) {}
    public record DesignInput(String name, Long groupId, String icon, String description, Integer sortNum,
                              String applyScope, Boolean allowWithdraw, Integer timeoutMinutes, String timeoutAction,
                              List<TargetInput> configUsers, List<NodeInput> nodes) {}
    public record StartInput(Long moduleId, Long recordId, List<Long> selectedMemberIds) {}
    public record ActionInput(Long nodeUserId, @NotBlank String actionType,
                              @NotBlank @Size(max = 64) String requestKey, @Size(max = 1000) String opinion) {}
    public record TransferInput(@NotNull Long nodeUserId, @NotNull Long targetMemberId,
                                @NotBlank @Size(max = 64) String requestKey, @Size(max = 900) String opinion) {}
    public record TimeoutInput(Long targetMemberId) {}
    public record TransferView(UnExamineExampleNodeUser previous, UnExamineExampleNodeUser target) {}
    public record ExamineSnapshot(UnExamineConfig config, List<UnExamineNode> nodes,
                                  List<UnExamineNodeUser> users, List<UnExamineNodeField> fields,
                                  List<Long> selectedMemberIds) {}
    public record ExamineDetail(UnExamine examine, UnExamineConfig config, List<UnExamineConfigUser> configUsers,
                                List<UnExamineNode> nodes, List<UnExamineNodeUser> users, List<UnExamineNodeField> fields) {}
    public record ExampleHistory(UnExamineExample example, List<UnExamineExampleNode> nodes,
                                 List<UnExamineExampleNodeUser> users, List<UnExamineException> exceptions) {}
}
