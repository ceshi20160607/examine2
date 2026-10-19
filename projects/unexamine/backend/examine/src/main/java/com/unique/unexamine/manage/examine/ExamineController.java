package com.unique.unexamine.manage.examine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** 审批定义、分组、节点和适用范围的配置入口。 */
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
    private final UnModuleFieldMapper fields;
    private final JdbcTemplate jdbc;

    public ExamineController(SessionAccess access, UnExamineMapper examines, UnExamineGroupMapper groups,
                             UnExamineConfigMapper configs, UnExamineConfigUserMapper configUsers,
                             UnExamineNodeMapper nodes, UnExamineNodeUserMapper nodeUsers,
                             UnExamineNodeFieldMapper nodeFields, UnModuleFieldMapper fields, JdbcTemplate jdbc) {
        this.access = access;
        this.examines = examines;
        this.groups = groups;
        this.configs = configs;
        this.configUsers = configUsers;
        this.nodes = nodes;
        this.nodeUsers = nodeUsers;
        this.nodeFields = nodeFields;
        this.fields = fields;
        this.jdbc = jdbc;
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

    private UnExamine examineInTenant(long tenantId, long examineId) {
        UnExamine examine = examines.selectById(examineId);
        if (examine == null || !eq(examine.getTenantId(), tenantId)) notFound("审批定义不存在");
        return examine;
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

    private boolean eq(Long id, Long expected) { return id != null && id.equals(expected); }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
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
    public record ExamineDetail(UnExamine examine, UnExamineConfig config, List<UnExamineConfigUser> configUsers,
                                List<UnExamineNode> nodes, List<UnExamineNodeUser> users, List<UnExamineNodeField> fields) {}
}
