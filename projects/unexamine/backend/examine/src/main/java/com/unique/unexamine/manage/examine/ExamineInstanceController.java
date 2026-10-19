package com.unique.unexamine.manage.examine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

/** 审批实例、待办及处理入口；实例始终使用发起时冻结的配置快照。 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/examines")
public class ExamineInstanceController {
    private final SessionAccess access;
    private final UnExamineMapper examines;
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

    public ExamineInstanceController(SessionAccess access, UnExamineMapper examines,
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

    /** 发起前读取当前定义的完整配置，快照保存后实例不再依赖定义变更。 */
    private ExamineController.ExamineDetail definition(long tenantId, long examineId) {
        UnExamine examine = examineInTenant(tenantId, examineId);
        List<UnExamineNode> nodeList = nodes.selectList(new LambdaQueryWrapper<UnExamineNode>()
                .eq(UnExamineNode::getExamineId, examineId).orderByAsc(UnExamineNode::getSortNum));
        List<UnExamineNodeUser> users = new ArrayList<>();
        List<UnExamineNodeField> fieldList = new ArrayList<>();
        for (UnExamineNode node : nodeList) {
            users.addAll(nodeUsers.selectList(new LambdaQueryWrapper<UnExamineNodeUser>()
                    .eq(UnExamineNodeUser::getNodeId, node.getId()).orderByAsc(UnExamineNodeUser::getSortNum)));
            fieldList.addAll(nodeFields.selectList(new LambdaQueryWrapper<UnExamineNodeField>()
                    .eq(UnExamineNodeField::getNodeId, node.getId()).orderByAsc(UnExamineNodeField::getSortNum)));
        }
        return new ExamineController.ExamineDetail(examine,
                configs.selectOne(new LambdaQueryWrapper<UnExamineConfig>().eq(UnExamineConfig::getExamineId, examineId)),
                configUsers.selectList(new LambdaQueryWrapper<UnExamineConfigUser>().eq(UnExamineConfigUser::getExamineId, examineId)),
                nodeList, users, fieldList);
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
        ExamineController.ExamineDetail detail = definition(tenantId, examineId);
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
    public record ExampleHistory(UnExamineExample example, List<UnExamineExampleNode> nodes,
                                 List<UnExamineExampleNodeUser> users, List<UnExamineException> exceptions) {}
}
