package com.unique.unexamine.plat.manage.service;

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
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatMessage;
import com.unique.unexamine.plat.base.entity.UnPlatMessageDeliveryLog;
import com.unique.unexamine.plat.base.entity.UnPlatMessageTemplate;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatMessageDeliveryLogService;
import com.unique.unexamine.plat.base.service.IUnPlatMessageService;
import com.unique.unexamine.plat.base.service.IUnPlatMessageTemplateService;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.DeliveryView;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.SaveRequest;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.SendRequest;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.TemplateSearch;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.TemplateView;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台消息按独立模板发送，只允许平台业务或系统切换引导目标。 */
@Service
public class PlatformMessageTemplateService implements ExamineTodoNotifier {
    private static final Pattern VARIABLE = Pattern.compile("[$][{]([A-Za-z][A-Za-z0-9_]*)[}]");
    private static final Set<String> TARGETS = Set.of(
            "PLATFORM_AUTH", "PLATFORM_TASK", "PLATFORM_FLOW_TASK", "PLATFORM_EXAMINE_TASK", "PLATFORM_LOG",
            "SYSTEM_SWITCH", "PLATFORM_AGENT");
    private final AuthManageService auth;
    private final IUnPlatMessageTemplateService templates;
    private final IUnPlatMessageDeliveryLogService deliveries;
    private final IUnPlatMessageService messages;
    private final IUnPlatAccountService accounts;
    private final ObjectMapper json;
    private final MessageDeliveryTransactions transactions;

    public PlatformMessageTemplateService(AuthManageService auth, IUnPlatMessageTemplateService templates,
            IUnPlatMessageDeliveryLogService deliveries, IUnPlatMessageService messages,
            IUnPlatAccountService accounts, ObjectMapper json, MessageDeliveryTransactions transactions) {
        this.auth = auth; this.templates = templates; this.deliveries = deliveries;
        this.messages = messages; this.accounts = accounts; this.json = json; this.transactions = transactions;
    }

    /** 平台管理员分页查看模板。 */
    public Page<TemplateView> list(String token, TemplateSearch search) {
        auth.requirePlatformAdmin(token);
        TemplateSearch criteria = search == null ? new TemplateSearch() : search;
        var where = new LambdaQueryWrapper<UnPlatMessageTemplate>().orderByDesc(UnPlatMessageTemplate::getId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            String status = criteria.getStatus().trim().toUpperCase();
            if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(status))
                throw invalid("模板状态无效");
            where.eq(UnPlatMessageTemplate::getStatus, status);
        }
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String keyword = criteria.getKeyword().trim();
            if (keyword.length() > 100) throw invalid("模板搜索内容过长");
            where.and(w -> w.like(UnPlatMessageTemplate::getTemplateCode, keyword)
                    .or().like(UnPlatMessageTemplate::getTitleTemplate, keyword));
        }
        Page<UnPlatMessageTemplate> page = templates.page(criteria.parse(), where);
        return new Page<TemplateView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 保存草稿，平台模板禁止直接跳系统业务详情。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView save(String token, SaveRequest request) {
        UnPlatAccount actor = auth.requirePlatformAdmin(token);
        validate(request);
        UnPlatMessageTemplate row = request.id() == null ? new UnPlatMessageTemplate() : owned(request.id());
        if (row.getId() == null) {
            if (templates.exists(new LambdaQueryWrapper<UnPlatMessageTemplate>()
                    .eq(UnPlatMessageTemplate::getTemplateCode, request.templateCode())))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模板编码已存在");
            row.setCreateAccountId(actor.getId()); row.setCreateTime(LocalDateTime.now());
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
        else templates.update(new LambdaUpdateWrapper<UnPlatMessageTemplate>()
                .eq(UnPlatMessageTemplate::getId, row.getId())
                .set(UnPlatMessageTemplate::getTitleTemplate, row.getTitleTemplate())
                .set(UnPlatMessageTemplate::getContentTemplate, row.getContentTemplate())
                .set(UnPlatMessageTemplate::getVariableKeysJson, row.getVariableKeysJson())
                .set(UnPlatMessageTemplate::getChannel, "IN_APP")
                .set(UnPlatMessageTemplate::getTargetType, row.getTargetType())
                .set(UnPlatMessageTemplate::getStatus, "DRAFT")
                .set(UnPlatMessageTemplate::getUpdateTime, row.getUpdateTime()));
        return view(row);
    }

    /** 发布后开始供发送接口使用。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView publish(String token, Long templateId) {
        auth.requirePlatformAdmin(token);
        UnPlatMessageTemplate row = owned(templateId);
        row.setStatus("PUBLISHED"); row.setUpdateTime(LocalDateTime.now());
        templates.addOrUpdate(row);
        return view(row);
    }

    /** 停用不影响历史收件箱。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView disable(String token, Long templateId) {
        auth.requirePlatformAdmin(token);
        UnPlatMessageTemplate row = owned(templateId);
        row.setStatus("DISABLED"); row.setUpdateTime(LocalDateTime.now());
        templates.addOrUpdate(row);
        return view(row);
    }

    /** 按平台账号和业务键去重，投递阶段独立提交以保留失败记录。 */
    public DeliveryView send(String token, SendRequest request) {
        auth.requirePlatformAdmin(token);
        return deliver(request);
    }

    /** 已授权的平台任务业务在负责人变化后按发布模板通知目标账号。 */
    public void taskAssigned(String token, Long recipientAccountId, Long taskId, Long historyId, String title) {
        auth.requireAccount(token);
        if (!templates.exists(new LambdaQueryWrapper<UnPlatMessageTemplate>()
                .eq(UnPlatMessageTemplate::getTemplateCode, "WORK_TASK_ASSIGNED")
                .eq(UnPlatMessageTemplate::getStatus, "PUBLISHED"))) return;
        deliver(new SendRequest("WORK_TASK_ASSIGNED", recipientAccountId,
                java.util.Map.of("title", title), taskId, "work-task:" + taskId + ":history:" + historyId));
    }

    /** 平台 Flow 保存内部账号候选人后投递；未发布模板时流程照常运行。 */
    public void flowTaskAvailable(Long recipientAccountId, Long flowTaskId, String nodeCode) {
        if (!templates.exists(new LambdaQueryWrapper<UnPlatMessageTemplate>()
                .eq(UnPlatMessageTemplate::getTemplateCode, "FLOW_TASK_ASSIGNED")
                .eq(UnPlatMessageTemplate::getStatus, "PUBLISHED"))) return;
        deliver(new SendRequest("FLOW_TASK_ASSIGNED", recipientAccountId,
                java.util.Map.of("nodeCode", nodeCode), flowTaskId,
                "flow-task:" + flowTaskId + ":account:" + recipientAccountId));
    }

    /** 平台审批节点只生成平台消息，不跨过系统切换进入系统详情。 */
    @Override
    public void notifyPending(Long systemId, Long tenantId, Long recipientAccountId,
            Long exampleId, Long nodeId, String nodeCode) {
        if (systemId != null || tenantId != null) return;
        if (!templates.exists(new LambdaQueryWrapper<UnPlatMessageTemplate>()
                .eq(UnPlatMessageTemplate::getTemplateCode, "EXAMINE_TASK_ASSIGNED")
                .eq(UnPlatMessageTemplate::getStatus, "PUBLISHED"))) return;
        deliver(new SendRequest("EXAMINE_TASK_ASSIGNED", recipientAccountId,
                java.util.Map.of("nodeCode", nodeCode), exampleId,
                "examine-node:" + nodeId + ":account:" + recipientAccountId));
    }

    private DeliveryView deliver(SendRequest request) {
        Reservation reservation = transactions.execute(() -> reserve(request));
        if (!reservation.created()) return delivery(reservation.log());
        return attempt(reservation.log().getId());
    }

    /** 先提交平台消息的待投递快照，保证失败后仍可人工重试。 */
    private Reservation reserve(SendRequest request) {
        if (request == null || request.templateCode() == null || request.templateCode().isBlank()
                || request.recipientAccountId() == null || request.recipientAccountId() < 1
                || request.variables() == null || request.dedupeKey() != null
                && (request.dedupeKey().isBlank() || request.dedupeKey().length() > 120)
                || request.targetId() != null && request.targetId() < 1)
            throw invalid("模板发送参数无效");
        // 锁定模板行，避免同一业务事件并发投递两次。
        UnPlatMessageTemplate template = templates.getOne(new LambdaQueryWrapper<UnPlatMessageTemplate>()
                .eq(UnPlatMessageTemplate::getTemplateCode, request.templateCode())
                .last("FOR UPDATE"), false);
        if (template == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台消息模板不存在");
        if (!"PUBLISHED".equals(template.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台消息模板尚未发布");
        if ((template.getTargetType() == null) != (request.targetId() == null))
            throw invalid("平台消息跳转目标无效");
        if (!new HashSet<>(keys(template)).equals(request.variables().keySet())
                || request.variables().values().stream().anyMatch(v -> v == null || v.length() > 3000))
            throw invalid("模板变量与发布配置不一致");
        UnPlatAccount recipient = accounts.getById(request.recipientAccountId());
        if (recipient == null || !"ACTIVE".equals(recipient.getStatus()))
            throw invalid("接收账号不是有效平台账号");
        if (request.dedupeKey() != null) {
            UnPlatMessageDeliveryLog existing = deliveries.getOne(new LambdaQueryWrapper<UnPlatMessageDeliveryLog>()
                    .eq(UnPlatMessageDeliveryLog::getRecipientAccountId, recipient.getId())
                    .eq(UnPlatMessageDeliveryLog::getDedupeKey, request.dedupeKey()), false);
            if (existing != null) {
                if (!existing.getTemplateId().equals(template.getId()))
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "去重键已用于其他平台消息模板");
                return new Reservation(existing, false);
            }
        }
        String title = render(template.getTitleTemplate(), request.variables());
        String content = render(template.getContentTemplate(), request.variables());
        if (title.isBlank() || title.length() > 200 || content.isBlank() || content.length() > 20000)
            throw invalid("模板渲染后的消息内容无效");
        LocalDateTime now = LocalDateTime.now();
        UnPlatMessageDeliveryLog log = new UnPlatMessageDeliveryLog();
        log.setTemplateId(template.getId()); log.setRecipientAccountId(recipient.getId());
        log.setDedupeKey(request.dedupeKey()); log.setChannel("IN_APP");
        log.setStatus("PENDING"); log.setAttemptCount(0);
        log.setRenderedTitle(title); log.setRenderedContent(content);
        log.setTargetType(template.getTargetType()); log.setTargetId(request.targetId());
        log.setCreateTime(now); log.setUpdateTime(now);
        if (deliveries.addOrUpdate(log) == null) throw new IllegalStateException("平台消息待投递记录保存失败");
        return new Reservation(log, true);
    }

    /** 站内消息和成功状态原子提交；失败后另行记录错误和次数。 */
    private DeliveryView attempt(Long deliveryId) {
        try {
            return transactions.execute(() -> {
                UnPlatMessageDeliveryLog log = deliveries.getOne(new LambdaQueryWrapper<UnPlatMessageDeliveryLog>()
                        .eq(UnPlatMessageDeliveryLog::getId, deliveryId).last("FOR UPDATE"), false);
                if (log == null) throw invalid("平台投递记录不存在");
                if ("DELIVERED".equals(log.getStatus())) return delivery(log);
                if (log.getAttemptCount() >= 3) throw invalid("平台消息投递已达最大重试次数");
                UnPlatAccount recipient = accounts.getById(log.getRecipientAccountId());
                if (recipient == null || !"ACTIVE".equals(recipient.getStatus()))
                    throw invalid("接收账号不是有效平台账号");
                UnPlatMessage message = new UnPlatMessage();
                message.setRecipientAccountId(log.getRecipientAccountId());
                message.setTitle(log.getRenderedTitle()); message.setContent(log.getRenderedContent());
                message.setTargetType(log.getTargetType()); message.setTargetId(log.getTargetId());
                message.setStatus("UNREAD"); message.setCreateTime(LocalDateTime.now());
                if (messages.addOrUpdate(message) == null) throw new IllegalStateException("平台站内消息写入失败");
                log.setStatus("DELIVERED"); log.setMessageId(message.getId());
                log.setAttemptCount(log.getAttemptCount() + 1); log.setErrorText("");
                log.setUpdateTime(LocalDateTime.now());
                if (deliveries.addOrUpdate(log) == null) throw new IllegalStateException("平台投递结果保存失败");
                return delivery(log);
            });
        } catch (RuntimeException failure) {
            return transactions.execute(() -> {
                UnPlatMessageDeliveryLog log = deliveries.getOne(new LambdaQueryWrapper<UnPlatMessageDeliveryLog>()
                        .eq(UnPlatMessageDeliveryLog::getId, deliveryId).last("FOR UPDATE"), false);
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

    /** 平台管理员重试失败或中断的投递，原模板修改不影响快照内容。 */
    public DeliveryView retry(String token, Long templateId, Long deliveryId) {
        auth.requirePlatformAdmin(token);
        owned(templateId);
        UnPlatMessageDeliveryLog log = deliveries.getById(deliveryId);
        if (log == null || !templateId.equals(log.getTemplateId())) throw invalid("投递记录不属于当前模板");
        if (!Set.of("PENDING", "FAILED").contains(log.getStatus())) throw invalid("该投递无需重试");
        if (log.getAttemptCount() >= 3) throw invalid("平台消息投递已达最大重试次数");
        return attempt(deliveryId);
    }

    private record Reservation(UnPlatMessageDeliveryLog log, boolean created) {}

    /** 平台管理员查看模板历史投递及消息编号。 */
    public Page<DeliveryView> deliveries(String token, Long templateId, TemplateSearch search) {
        auth.requirePlatformAdmin(token);
        owned(templateId);
        TemplateSearch criteria = search == null ? new TemplateSearch() : search;
        var where = new LambdaQueryWrapper<UnPlatMessageDeliveryLog>()
                .eq(UnPlatMessageDeliveryLog::getTemplateId, templateId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            String status = criteria.getStatus().trim().toUpperCase();
            if (!Set.of("PENDING", "DELIVERED", "FAILED").contains(status))
                throw invalid("投递状态无效");
            where.eq(UnPlatMessageDeliveryLog::getStatus, status);
        }
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String keyword = criteria.getKeyword().trim();
            if (keyword.length() > 120) throw invalid("投递搜索内容过长");
            where.like(UnPlatMessageDeliveryLog::getDedupeKey, keyword);
        }
        Page<UnPlatMessageDeliveryLog> page = deliveries.page(criteria.parse(),
                where.orderByDesc(UnPlatMessageDeliveryLog::getId));
        return new Page<DeliveryView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::delivery).toList());
    }

    private void validate(SaveRequest request) {
        if (request == null || request.templateCode() == null
                || !request.templateCode().matches("[A-Z][A-Z0-9_]{2,79}")
                || request.titleTemplate() == null || request.titleTemplate().isBlank()
                || request.titleTemplate().length() > 200
                || request.contentTemplate() == null || request.contentTemplate().isBlank()
                || request.contentTemplate().length() > 20000
                || request.variableKeys() == null || request.variableKeys().size() > 30
                || !"IN_APP".equals(request.channel())
                || request.targetType() != null && !TARGETS.contains(request.targetType()))
            throw invalid("平台消息模板配置无效");
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
                && (!"PLATFORM_TASK".equals(request.targetType()) || !declared.equals(Set.of("title"))))
            throw invalid("平台任务指派模板必须跳转平台任务并使用 title 变量");
        if ("FLOW_TASK_ASSIGNED".equals(request.templateCode())
                && (!"PLATFORM_FLOW_TASK".equals(request.targetType()) || !declared.equals(Set.of("nodeCode"))))
            throw invalid("平台 Flow 待办模板必须跳转平台待办并使用 nodeCode 变量");
        if ("EXAMINE_TASK_ASSIGNED".equals(request.templateCode())
                && (!"PLATFORM_EXAMINE_TASK".equals(request.targetType()) || !declared.equals(Set.of("nodeCode"))))
            throw invalid("平台审批待办模板必须跳转平台审批并使用 nodeCode 变量");
    }

    private UnPlatMessageTemplate owned(Long id) {
        if (id == null || id < 1) throw invalid("模板主键无效");
        UnPlatMessageTemplate row = templates.getById(id);
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台消息模板不存在");
        return row;
    }

    private String render(String source, java.util.Map<String, String> variables) {
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

    private List<String> keys(UnPlatMessageTemplate row) {
        try { return json.readValue(row.getVariableKeysJson(), new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模板变量配置损坏"); }
    }

    private TemplateView view(UnPlatMessageTemplate row) {
        return new TemplateView(row.getId(), row.getTemplateCode(), row.getTitleTemplate(),
                row.getContentTemplate(), keys(row), row.getChannel(), row.getTargetType(),
                row.getStatus(), row.getCreateTime(), row.getUpdateTime());
    }

    private DeliveryView delivery(UnPlatMessageDeliveryLog row) {
        return new DeliveryView(row.getId(), row.getTemplateId(), row.getRecipientAccountId(),
                row.getDedupeKey(), row.getChannel(), row.getStatus(), row.getMessageId(),
                row.getAttemptCount(), row.getErrorText(), row.getCreateTime());
    }

    private BaseException invalid(String message) {
        return new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, message);
    }
}
