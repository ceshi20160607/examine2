package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.event.MessageDeliveryTransactions;
import com.unique.unexamine.core.module.ExamineTodoNotifier;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMessage;
import com.unique.unexamine.sys.base.entity.UnSysMessageDeliveryLog;
import com.unique.unexamine.sys.base.entity.UnSysMessageTemplate;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysMessageDeliveryLogService;
import com.unique.unexamine.sys.base.service.IUnSysMessageService;
import com.unique.unexamine.sys.base.service.IUnSysMessageTemplateService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.DeliveryView;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.SaveRequest;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.SendRequest;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.TemplateSearch;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.TemplateView;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统消息模板的配置、发布和站内投递；业务消息只在所属系统租户内落地。 */
@Service
public class SystemMessageTemplateService implements ExamineTodoNotifier {
    private static final Pattern VARIABLE = Pattern.compile("[$][{]([A-Za-z][A-Za-z0-9_]*)[}]");
    /** 跳转只允许当前系统成员身份可解析的业务对象。 */
    private static final Set<String> TARGETS = Set.of("MODULE_RECORD", "WORK_TASK", "FLOW_TASK",
            "EXAMINE_TASK", "SYSTEM_NOTICE", "IMPORT_JOB", "EXPORT_JOB", "APP_EVENT", "SYSTEM_AGENT");
    private final SystemManageService systems;
    private final IUnSysMessageTemplateService templates;
    private final IUnSysMessageDeliveryLogService deliveries;
    private final IUnSysMessageService messages;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final ObjectMapper json;
    private final MessageDeliveryTransactions transactions;

    public SystemMessageTemplateService(SystemManageService systems,
            IUnSysMessageTemplateService templates, IUnSysMessageDeliveryLogService deliveries,
            IUnSysMessageService messages, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, ObjectMapper json,
            MessageDeliveryTransactions transactions) {
        this.systems = systems; this.templates = templates; this.deliveries = deliveries;
        this.messages = messages; this.members = members; this.tenantMembers = tenantMembers;
        this.json = json; this.transactions = transactions;
    }

    /** 管理员分页查看本租户模板，草稿不会被发送接口使用。 */
    public Page<TemplateView> list(String token, Long systemId, Long tenantId, TemplateSearch search) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        TemplateSearch criteria = search == null ? new TemplateSearch() : search;
        var where = new LambdaQueryWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getSystemId, context.systemId())
                .eq(UnSysMessageTemplate::getTenantId, context.tenantId())
                .orderByDesc(UnSysMessageTemplate::getId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            String status = criteria.getStatus().trim().toUpperCase();
            if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(status))
                throw invalid("模板状态无效");
            where.eq(UnSysMessageTemplate::getStatus, status);
        }
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String keyword = criteria.getKeyword().trim();
            if (keyword.length() > 100) throw invalid("模板搜索内容过长");
            where.and(w -> w.like(UnSysMessageTemplate::getTemplateCode, keyword)
                    .or().like(UnSysMessageTemplate::getTitleTemplate, keyword));
        }
        Page<UnSysMessageTemplate> page = templates.page(criteria.parse(), where);
        return new Page<TemplateView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 保存草稿时校验变量声明和占位符一致，修改后必须重新发布。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView save(String token, Long systemId, Long tenantId, SaveRequest request) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        validate(request);
        UnSysMessageTemplate row = request.id() == null ? new UnSysMessageTemplate() : owned(context, request.id());
        if (row.getId() == null) {
            if (templates.exists(new LambdaQueryWrapper<UnSysMessageTemplate>()
                    .eq(UnSysMessageTemplate::getSystemId, context.systemId())
                    .eq(UnSysMessageTemplate::getTenantId, context.tenantId())
                    .eq(UnSysMessageTemplate::getTemplateCode, request.templateCode())))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模板编码已存在");
            row.setSystemId(context.systemId()); row.setTenantId(context.tenantId());
            row.setCreateAccountId(context.accountId()); row.setCreateTime(LocalDateTime.now());
        } else if (!row.getTemplateCode().equals(request.templateCode())) {
            throw invalid("模板编码保存后不可修改");
        }
        row.setTemplateCode(request.templateCode());
        row.setTitleTemplate(request.titleTemplate().trim());
        row.setContentTemplate(request.contentTemplate());
        row.setVariableKeysJson(writeKeys(request.variableKeys()));
        row.setChannel("IN_APP"); row.setTargetType(request.targetType());
        row.setStatus("DRAFT"); row.setUpdateTime(LocalDateTime.now());
        if (request.id() == null) templates.addOrUpdate(row);
        else templates.update(new LambdaUpdateWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getId, row.getId())
                .eq(UnSysMessageTemplate::getSystemId, context.systemId())
                .eq(UnSysMessageTemplate::getTenantId, context.tenantId())
                .set(UnSysMessageTemplate::getTitleTemplate, row.getTitleTemplate())
                .set(UnSysMessageTemplate::getContentTemplate, row.getContentTemplate())
                .set(UnSysMessageTemplate::getVariableKeysJson, row.getVariableKeysJson())
                .set(UnSysMessageTemplate::getChannel, row.getChannel())
                .set(UnSysMessageTemplate::getTargetType, row.getTargetType())
                .set(UnSysMessageTemplate::getStatus, "DRAFT")
                .set(UnSysMessageTemplate::getUpdateTime, row.getUpdateTime()));
        return view(row);
    }

    /** 发布的模板才允许投递，停用不改写已有消息。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView publish(String token, Long systemId, Long tenantId, Long templateId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnSysMessageTemplate row = owned(context, templateId);
        row.setStatus("PUBLISHED"); row.setUpdateTime(LocalDateTime.now());
        templates.addOrUpdate(row);
        return view(row);
    }

    /** 停用只阻止后续投递。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView disable(String token, Long systemId, Long tenantId, Long templateId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnSysMessageTemplate row = owned(context, templateId);
        row.setStatus("DISABLED"); row.setUpdateTime(LocalDateTime.now());
        templates.addOrUpdate(row);
        return view(row);
    }

    /** 按发布模板投递；同一接收人及业务去重键只生成一条消息。 */
    public DeliveryView send(String token, Long systemId, Long tenantId, SendRequest request) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        return deliver(context.systemId(), context.tenantId(), request);
    }

    /** 已授权的任务业务在负责人变化后按已发布模板通知，不要求任务操作者是系统管理员。 */
    public void taskAssigned(String token, Long systemId, Long tenantId, Long recipientAccountId,
            Long taskId, Long historyId, String title) {
        SystemContext context = systems.context(token, systemId, tenantId);
        if (!templates.exists(new LambdaQueryWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getSystemId, context.systemId())
                .eq(UnSysMessageTemplate::getTenantId, context.tenantId())
                .eq(UnSysMessageTemplate::getTemplateCode, "WORK_TASK_ASSIGNED")
                .eq(UnSysMessageTemplate::getStatus, "PUBLISHED"))) return;
        UnSysMember recipient = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, context.systemId())
                .eq(UnSysMember::getAccountId, recipientAccountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (recipient == null) throw invalid("任务负责人不属于当前系统");
        deliver(context.systemId(), context.tenantId(), new SendRequest("WORK_TASK_ASSIGNED", recipient.getId(),
                Map.of("title", title), taskId, "work-task:" + taskId + ":history:" + historyId));
    }

    /** Flow 已核对候选人后按当前系统租户投递，模板不存在时不阻断流程。 */
    public void flowTaskAvailable(Long systemId, Long tenantId, Long recipientAccountId,
            Long flowTaskId, String nodeCode) {
        if (!templates.exists(new LambdaQueryWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getSystemId, systemId)
                .eq(UnSysMessageTemplate::getTenantId, tenantId)
                .eq(UnSysMessageTemplate::getTemplateCode, "FLOW_TASK_ASSIGNED")
                .eq(UnSysMessageTemplate::getStatus, "PUBLISHED"))) return;
        UnSysMember recipient = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId)
                .eq(UnSysMember::getAccountId, recipientAccountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (recipient == null) throw invalid("Flow 候选人不属于当前系统");
        deliver(systemId, tenantId, new SendRequest("FLOW_TASK_ASSIGNED", recipient.getId(),
                Map.of("nodeCode", nodeCode), flowTaskId,
                "flow-task:" + flowTaskId + ":account:" + recipientAccountId));
    }

    /** 系统审批节点只通知当前租户中的内部成员。 */
    @Override
    public void notifyPending(Long systemId, Long tenantId, Long recipientAccountId,
            Long exampleId, Long nodeId, String nodeCode) {
        if (systemId == null || tenantId == null) return;
        if (!templates.exists(new LambdaQueryWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getSystemId, systemId)
                .eq(UnSysMessageTemplate::getTenantId, tenantId)
                .eq(UnSysMessageTemplate::getTemplateCode, "EXAMINE_TASK_ASSIGNED")
                .eq(UnSysMessageTemplate::getStatus, "PUBLISHED"))) return;
        UnSysMember recipient = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId)
                .eq(UnSysMember::getAccountId, recipientAccountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (recipient == null) throw invalid("审批候选人不属于当前系统");
        deliver(systemId, tenantId, new SendRequest("EXAMINE_TASK_ASSIGNED", recipient.getId(),
                Map.of("nodeCode", nodeCode), exampleId,
                "examine-node:" + nodeId + ":account:" + recipientAccountId));
    }

    /** 数据交换作业进入终态后通知发起人；未发布对应模板时不产生消息。 */
    public void exchangeCompleted(Long systemId, Long tenantId, Long recipientAccountId,
            Long moduleId, Long jobId, String jobType, String status,
            int totalCount, int successCount, int failureCount) {
        String templateCode = switch (jobType) {
            case "IMPORT" -> "MODULE_IMPORT_RESULT";
            case "EXPORT" -> "MODULE_EXPORT_RESULT";
            default -> throw invalid("数据交换作业类型无效");
        };
        if (!Set.of("SUCCESS", "PARTIAL", "FAILED").contains(status))
            throw invalid("数据交换作业尚未结束");
        if (!templates.exists(new LambdaQueryWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getSystemId, systemId)
                .eq(UnSysMessageTemplate::getTenantId, tenantId)
                .eq(UnSysMessageTemplate::getTemplateCode, templateCode)
                .eq(UnSysMessageTemplate::getStatus, "PUBLISHED"))) return;
        UnSysMember recipient = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId)
                .eq(UnSysMember::getAccountId, recipientAccountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (recipient == null) throw invalid("数据交换发起人不属于当前系统");
        deliver(systemId, tenantId, new SendRequest(templateCode, recipient.getId(),
                Map.of("moduleId", moduleId.toString(), "status", status,
                        "totalCount", Integer.toString(totalCount),
                        "successCount", Integer.toString(successCount),
                        "failureCount", Integer.toString(failureCount)),
                jobId, "module-" + jobType.toLowerCase() + "-job:" + jobId));
    }

    private DeliveryView deliver(Long systemId, Long tenantId, SendRequest request) {
        Reservation reservation = transactions.execute(() -> reserve(systemId, tenantId, request));
        if (!reservation.created()) return delivery(reservation.log());
        return attempt(reservation.log().getId());
    }

    /** 先提交待投递快照；后续写消息失败时仍可按原内容重试。 */
    private Reservation reserve(Long systemId, Long tenantId, SendRequest request) {
        if (request == null || request.templateCode() == null || request.templateCode().isBlank()
                || request.recipientMemberId() == null || request.recipientMemberId() < 1
                || request.variables() == null || request.dedupeKey() != null
                && (request.dedupeKey().isBlank() || request.dedupeKey().length() > 120)
                || request.targetId() != null && request.targetId() < 1)
            throw invalid("模板发送参数无效");
        // 同一模板上加行锁，使并发重复投递在检查去重记录前串行化。
        UnSysMessageTemplate template = templates.getOne(new LambdaQueryWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getSystemId, systemId)
                .eq(UnSysMessageTemplate::getTenantId, tenantId)
                .eq(UnSysMessageTemplate::getTemplateCode, request.templateCode())
                .last("FOR UPDATE"), false);
        if (template == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "消息模板不存在");
        if (!"PUBLISHED".equals(template.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "消息模板尚未发布");
        if ((template.getTargetType() == null) != (request.targetId() == null))
            throw invalid("消息跳转目标无效");
        if (!new HashSet<>(keys(template)).equals(request.variables().keySet())
                || request.variables().values().stream().anyMatch(v -> v == null || v.length() > 3000))
            throw invalid("模板变量与发布配置不一致");
        UnSysMember recipient = members.getById(request.recipientMemberId());
        if (recipient == null || !systemId.equals(recipient.getSystemId())
                || !"ACTIVE".equals(recipient.getStatus())
                || !tenantMembers.exists(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, systemId)
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, recipient.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE")))
            throw invalid("消息接收人不属于当前租户");
        if (request.dedupeKey() != null) {
            UnSysMessageDeliveryLog existing = deliveries.getOne(new LambdaQueryWrapper<UnSysMessageDeliveryLog>()
                    .eq(UnSysMessageDeliveryLog::getSystemId, systemId)
                    .eq(UnSysMessageDeliveryLog::getTenantId, tenantId)
                    .eq(UnSysMessageDeliveryLog::getRecipientMemberId, recipient.getId())
                    .eq(UnSysMessageDeliveryLog::getDedupeKey, request.dedupeKey()), false);
            if (existing != null) {
                if (!existing.getTemplateId().equals(template.getId()))
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "去重键已用于其他消息模板");
                return new Reservation(existing, false);
            }
        }
        String title = render(template.getTitleTemplate(), request.variables());
        String content = render(template.getContentTemplate(), request.variables());
        if (title.isBlank() || title.length() > 200 || content.isBlank() || content.length() > 15000)
            throw invalid("模板渲染后的消息内容无效");
        LocalDateTime now = LocalDateTime.now();
        UnSysMessageDeliveryLog log = new UnSysMessageDeliveryLog();
        log.setSystemId(systemId); log.setTenantId(tenantId);
        log.setTemplateId(template.getId()); log.setRecipientMemberId(recipient.getId());
        log.setDedupeKey(request.dedupeKey()); log.setChannel("IN_APP");
        log.setStatus("PENDING"); log.setAttemptCount(0);
        log.setRenderedTitle(title); log.setRenderedContent(content);
        log.setTargetType(template.getTargetType()); log.setTargetId(request.targetId());
        log.setCreateTime(now); log.setUpdateTime(now);
        if (deliveries.addOrUpdate(log) == null) throw new IllegalStateException("系统消息待投递记录保存失败");
        return new Reservation(log, true);
    }

    /** 每次尝试与消息写入同事务提交，失败则由独立事务记录错误。 */
    private DeliveryView attempt(Long deliveryId) {
        try {
            return transactions.execute(() -> {
                UnSysMessageDeliveryLog log = deliveries.getOne(new LambdaQueryWrapper<UnSysMessageDeliveryLog>()
                        .eq(UnSysMessageDeliveryLog::getId, deliveryId).last("FOR UPDATE"), false);
                if (log == null) throw invalid("投递记录不存在");
                if ("DELIVERED".equals(log.getStatus())) return delivery(log);
                if (log.getAttemptCount() >= 3) throw invalid("消息投递已达最大重试次数");
                UnSysMember recipient = members.getById(log.getRecipientMemberId());
                if (recipient == null || !log.getSystemId().equals(recipient.getSystemId())
                        || !"ACTIVE".equals(recipient.getStatus())
                        || !tenantMembers.exists(new LambdaQueryWrapper<UnSysTenantMember>()
                        .eq(UnSysTenantMember::getSystemId, log.getSystemId())
                        .eq(UnSysTenantMember::getTenantId, log.getTenantId())
                        .eq(UnSysTenantMember::getMemberId, log.getRecipientMemberId())
                        .eq(UnSysTenantMember::getStatus, "ACTIVE")))
                    throw invalid("消息接收人已不属于当前租户");
                UnSysMessage message = new UnSysMessage();
                message.setSystemId(log.getSystemId()); message.setTenantId(log.getTenantId());
                message.setRecipientMemberId(log.getRecipientMemberId());
                message.setTitle(log.getRenderedTitle()); message.setContent(log.getRenderedContent());
                message.setTargetType(log.getTargetType()); message.setTargetId(log.getTargetId());
                message.setStatus("UNREAD"); message.setCreateTime(LocalDateTime.now());
                if (messages.addOrUpdate(message) == null) throw new IllegalStateException("系统站内消息写入失败");
                log.setStatus("DELIVERED"); log.setMessageId(message.getId());
                log.setAttemptCount(log.getAttemptCount() + 1); log.setErrorText("");
                log.setUpdateTime(LocalDateTime.now());
                if (deliveries.addOrUpdate(log) == null) throw new IllegalStateException("系统投递结果保存失败");
                return delivery(log);
            });
        } catch (RuntimeException failure) {
            return transactions.execute(() -> {
                UnSysMessageDeliveryLog log = deliveries.getOne(new LambdaQueryWrapper<UnSysMessageDeliveryLog>()
                        .eq(UnSysMessageDeliveryLog::getId, deliveryId).last("FOR UPDATE"), false);
                if (log == null) throw failure;
                if ("DELIVERED".equals(log.getStatus())) return delivery(log);
                if (log.getAttemptCount() >= 3) throw failure;
                log.setStatus("FAILED"); log.setAttemptCount(log.getAttemptCount() + 1);
                log.setErrorText("站内投递失败：" + failure.getClass().getSimpleName());
                log.setUpdateTime(LocalDateTime.now());
                if (deliveries.addOrUpdate(log) == null) throw failure;
                return delivery(log);
            });
        }
    }

    /** 管理员人工重试失败或中断的投递，使用首次发送时的内容快照。 */
    public DeliveryView retry(String token, Long systemId, Long tenantId, Long templateId, Long deliveryId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        owned(context, templateId);
        UnSysMessageDeliveryLog log = deliveries.getById(deliveryId);
        if (log == null || !context.systemId().equals(log.getSystemId())
                || !context.tenantId().equals(log.getTenantId()) || !templateId.equals(log.getTemplateId()))
            throw invalid("投递记录不属于当前模板");
        if (!Set.of("PENDING", "FAILED").contains(log.getStatus())) throw invalid("该投递无需重试");
        if (log.getAttemptCount() >= 3) throw invalid("消息投递已达最大重试次数");
        return attempt(deliveryId);
    }

    private record Reservation(UnSysMessageDeliveryLog log, boolean created) {}

    /** 管理员按模板读取投递结果和生成的消息编号。 */
    public Page<DeliveryView> deliveries(String token, Long systemId, Long tenantId, Long templateId,
            TemplateSearch search) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        owned(context, templateId);
        TemplateSearch criteria = search == null ? new TemplateSearch() : search;
        var where = new LambdaQueryWrapper<UnSysMessageDeliveryLog>()
                .eq(UnSysMessageDeliveryLog::getSystemId, context.systemId())
                .eq(UnSysMessageDeliveryLog::getTenantId, context.tenantId())
                .eq(UnSysMessageDeliveryLog::getTemplateId, templateId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            String status = criteria.getStatus().trim().toUpperCase();
            if (!Set.of("PENDING", "DELIVERED", "FAILED").contains(status))
                throw invalid("投递状态无效");
            where.eq(UnSysMessageDeliveryLog::getStatus, status);
        }
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String keyword = criteria.getKeyword().trim();
            if (keyword.length() > 120) throw invalid("投递搜索内容过长");
            where.like(UnSysMessageDeliveryLog::getDedupeKey, keyword);
        }
        Page<UnSysMessageDeliveryLog> page = deliveries.page(criteria.parse(),
                where.orderByDesc(UnSysMessageDeliveryLog::getId));
        return new Page<DeliveryView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::delivery).toList());
    }

    private void validate(SaveRequest request) {
        if (request == null || request.templateCode() == null
                || !request.templateCode().matches("[A-Z][A-Z0-9_]{2,79}")
                || request.titleTemplate() == null || request.titleTemplate().isBlank()
                || request.titleTemplate().length() > 200
                || request.contentTemplate() == null || request.contentTemplate().isBlank()
                || request.contentTemplate().length() > 15000
                || request.variableKeys() == null || request.variableKeys().size() > 30
                || !"IN_APP".equals(request.channel())
                || request.targetType() != null && !TARGETS.contains(request.targetType()))
            throw invalid("消息模板配置无效");
        Set<String> declared = new HashSet<>();
        for (String key : request.variableKeys()) {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]{0,79}") || !declared.add(key))
                throw invalid("模板变量名无效或重复");
        }
        Set<String> used = new HashSet<>();
        Matcher matcher = VARIABLE.matcher(request.titleTemplate() + request.contentTemplate());
        while (matcher.find()) used.add(matcher.group(1));
        if (!used.equals(declared)) throw invalid("模板变量与占位符不一致");
        if ("WORK_TASK_ASSIGNED".equals(request.templateCode())
                && (!"WORK_TASK".equals(request.targetType()) || !declared.equals(Set.of("title"))))
            throw invalid("任务指派模板必须跳转任务并使用 title 变量");
        if ("FLOW_TASK_ASSIGNED".equals(request.templateCode())
                && (!"FLOW_TASK".equals(request.targetType()) || !declared.equals(Set.of("nodeCode"))))
            throw invalid("Flow 待办模板必须跳转待办并使用 nodeCode 变量");
        if ("EXAMINE_TASK_ASSIGNED".equals(request.templateCode())
                && (!"EXAMINE_TASK".equals(request.targetType()) || !declared.equals(Set.of("nodeCode"))))
            throw invalid("审批待办模板必须跳转审批并使用 nodeCode 变量");
        if (Set.of("MODULE_IMPORT_RESULT", "MODULE_EXPORT_RESULT").contains(request.templateCode())) {
            String target = "MODULE_IMPORT_RESULT".equals(request.templateCode()) ? "IMPORT_JOB" : "EXPORT_JOB";
            if (!target.equals(request.targetType()) || !declared.equals(Set.of(
                    "moduleId", "status", "totalCount", "successCount", "failureCount")))
                throw invalid("数据交换结果模板须配置作业跳转与完整结果变量");
        }
    }

    private UnSysMessageTemplate owned(SystemContext context, Long templateId) {
        if (templateId == null || templateId < 1) throw invalid("模板主键无效");
        UnSysMessageTemplate row = templates.getOne(new LambdaQueryWrapper<UnSysMessageTemplate>()
                .eq(UnSysMessageTemplate::getId, templateId)
                .eq(UnSysMessageTemplate::getSystemId, context.systemId())
                .eq(UnSysMessageTemplate::getTenantId, context.tenantId()), false);
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "消息模板不存在");
        return row;
    }

    private String render(String source, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(result, Matcher.quoteReplacement(variables.get(matcher.group(1))));
        matcher.appendTail(result);
        return result.toString();
    }

    private String writeKeys(List<String> keys) {
        try { return json.writeValueAsString(keys); }
        catch (JsonProcessingException ex) { throw invalid("模板变量无法保存"); }
    }

    private List<String> keys(UnSysMessageTemplate row) {
        try { return json.readValue(row.getVariableKeysJson(), new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模板变量配置损坏"); }
    }

    private TemplateView view(UnSysMessageTemplate row) {
        return new TemplateView(row.getId(), row.getTemplateCode(), row.getTitleTemplate(),
                row.getContentTemplate(), keys(row), row.getChannel(), row.getTargetType(),
                row.getStatus(), row.getCreateTime(), row.getUpdateTime());
    }

    private DeliveryView delivery(UnSysMessageDeliveryLog row) {
        return new DeliveryView(row.getId(), row.getTemplateId(), row.getRecipientMemberId(),
                row.getDedupeKey(), row.getChannel(), row.getStatus(), row.getMessageId(),
                row.getAttemptCount(), row.getErrorText(), row.getCreateTime());
    }

    private BaseException invalid(String message) {
        return new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, message);
    }
}
