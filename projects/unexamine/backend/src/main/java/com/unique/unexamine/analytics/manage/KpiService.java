package com.unique.unexamine.analytics.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.analytics.base.entity.AnaDataSourceVersion;
import com.unique.unexamine.analytics.base.entity.AnaKpi;
import com.unique.unexamine.analytics.base.entity.AnaKpiReminder;
import com.unique.unexamine.analytics.base.entity.AnaKpiResult;
import com.unique.unexamine.analytics.base.service.AnaDataSourceVersionBaseService;
import com.unique.unexamine.analytics.base.service.AnaKpiBaseService;
import com.unique.unexamine.analytics.base.service.AnaKpiReminderBaseService;
import com.unique.unexamine.analytics.base.service.AnaKpiResultBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.notification.manage.MessageModels;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class KpiService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() { };

    private final AnaKpiBaseService kpiService;
    private final AnaKpiResultBaseService resultService;
    private final AnaKpiReminderBaseService reminderService;
    private final AnaDataSourceVersionBaseService sourceVersionService;
    private final ReportService reportService;
    private final MessageService messageService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemMemberBaseService systemMemberService;
    private final SystemDepartmentBaseService departmentService;
    private final SystemRoleBaseService roleService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public KpiService(
            AnaKpiBaseService kpiService,
            AnaKpiResultBaseService resultService,
            AnaKpiReminderBaseService reminderService,
            AnaDataSourceVersionBaseService sourceVersionService,
            ReportService reportService,
            MessageService messageService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemMemberBaseService systemMemberService,
            SystemDepartmentBaseService departmentService,
            SystemRoleBaseService roleService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.kpiService = kpiService;
        this.resultService = resultService;
        this.reminderService = reminderService;
        this.sourceVersionService = sourceVersionService;
        this.reportService = reportService;
        this.messageService = messageService;
        this.tenantMemberService = tenantMemberService;
        this.systemMemberService = systemMemberService;
        this.departmentService = departmentService;
        this.roleService = roleService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public KpiModels.Overview adminOverview(AuthenticatedContext context) {
        requireAdmin(context);
        return new KpiModels.Overview(owned(context).stream()
                .sorted(Comparator.comparing(AnaKpi::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(kpi -> view(context, kpi, false, null)).toList());
    }

    public KpiModels.Overview runtimeOverview(AuthenticatedContext context, String traceId) {
        return new KpiModels.Overview(owned(context).stream().filter(kpi -> "ACTIVE".equals(kpi.getStatus()))
                .filter(kpi -> allows(context, readMap(kpi.getDimensionJson()), "visibilityPermission"))
                .sorted(Comparator.comparing(AnaKpi::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(kpi -> view(context, kpi, true, traceId)).toList());
    }

    public KpiModels.KpiView runtimeOne(AuthenticatedContext context, Long kpiId, String traceId) {
        AnaKpi kpi = requireKpi(context, kpiId);
        if (!"ACTIVE".equals(kpi.getStatus())
                || !allows(context, readMap(kpi.getDimensionJson()), "visibilityPermission")) {
            throw forbidden("KPI_VISIBILITY_DENIED", "当前上下文不可见该 KPI");
        }
        return view(context, kpi, true, traceId);
    }

    @Transactional
    public KpiModels.KpiView create(
            AuthenticatedContext context, KpiModels.KpiRequest input, String traceId) {
        requireAdmin(context);
        validate(context, input);
        if (owned(context).stream().anyMatch(kpi -> input.code().equals(kpi.getCode()))) {
            throw conflict("KPI_CODE_EXISTS", "当前上下文已存在同编码 KPI");
        }
        AnaKpi kpi = new AnaKpi();
        bind(kpi, context, input);
        kpi.setCreatedByAccountId(context.accountId());
        kpi.setCreatedAt(LocalDateTime.now());
        kpi.setUpdatedAt(LocalDateTime.now());
        kpi.setVersion(0);
        kpiService.insert(kpi);
        audit(context, traceId, "KPI_CREATED", kpi.getId(), Map.of(
                "code", kpi.getCode(), "dataSourceVersionId", kpi.getDataSourceVersionId(),
                "periodType", kpi.getCalculationSchedule()));
        return view(context, kpi, false, traceId);
    }

    @Transactional
    public KpiModels.KpiView update(
            AuthenticatedContext context, Long kpiId, KpiModels.KpiRequest input, String traceId) {
        requireAdmin(context);
        validate(context, input);
        AnaKpi kpi = requireKpi(context, kpiId);
        if (input.expectedVersion() == null || !Objects.equals(input.expectedVersion(), kpi.getVersion())) {
            throw conflict("KPI_VERSION_CONFLICT", "KPI 配置版本已变化，请刷新后重试");
        }
        if (owned(context).stream().anyMatch(item -> input.code().equals(item.getCode())
                && !item.getId().equals(kpiId))) throw conflict("KPI_CODE_EXISTS", "当前上下文已存在同编码 KPI");
        bind(kpi, context, input);
        kpi.setUpdatedAt(LocalDateTime.now());
        if (kpiService.updateById(kpi) != 1) throw conflict("KPI_VERSION_CONFLICT", "KPI 配置已被其他用户修改");
        AnaKpi current = kpiService.selectById(kpiId);
        audit(context, traceId, "KPI_CONFIGURATION_UPDATED", kpiId, Map.of(
                "kpiVersion", current.getVersion(), "dataSourceVersionId", current.getDataSourceVersionId()));
        return view(context, current, false, traceId);
    }

    public KpiModels.Preview preview(
            AuthenticatedContext context, Long kpiId, String periodKey, String traceId) {
        requireAdmin(context);
        AnaKpi kpi = requireKpi(context, kpiId);
        validatePeriod(kpi, periodKey);
        ReportModels.Result source = reportService.executeVersion(context, kpi.getDataSourceVersionId(), traceId);
        BigDecimal actual = number(source.value());
        if (actual == null) throw invalid("KPI_ACTUAL_VALUE_INVALID", "数据源指标没有返回可计算数值");
        Target target = target(kpi);
        BigDecimal rate = achievement(actual, target);
        String status = achieved(actual, target) ? "ACHIEVED" : "UNDER_TARGET";
        return new KpiModels.Preview(kpiId, kpi.getVersion(), periodKey, target.value(), actual, rate, status,
                target.operator() + " " + target.value().stripTrailingZeros().toPlainString(),
                "数据源发布版本 #" + source.dataSourceVersionId() + " / " + source.metricDefinition(),
                LocalDateTime.now(), source);
    }

    @Transactional
    public KpiModels.ResultView calculate(
            AuthenticatedContext context, Long kpiId, String periodKey, String traceId) {
        requireAdmin(context);
        AnaKpi kpi = requireKpi(context, kpiId);
        validatePeriod(kpi, periodKey);
        Target target = target(kpi);
        AnaKpiResult existing = result(kpiId, periodKey, "ALL");
        ReportModels.Result source;
        try {
            source = reportService.executeVersion(context, kpi.getDataSourceVersionId(), traceId);
        } catch (DomainException exception) {
            if (existing == null) {
                throw new DomainException("KPI_SOURCE_UNAVAILABLE_NO_SNAPSHOT",
                        "数据源执行失败且没有可保留的上次成功快照", HttpStatus.UNPROCESSABLE_ENTITY,
                        Map.of("sourceError", exception.code()));
            }
            LinkedHashMap<String, Object> explanation = new LinkedHashMap<>(readMap(existing.getExplanationJson()));
            explanation.put("stale", true);
            explanation.put("staleAt", LocalDateTime.now());
            explanation.put("sourceErrorCode", exception.code());
            explanation.put("sourceErrorMessage", exception.getMessage());
            explanation.put("lastSuccessfulCalculatedAt", existing.getCalculatedAt());
            existing.setStatus("STALE");
            existing.setExplanationJson(writeJson(explanation));
            resultService.updateById(existing);
            audit(context, traceId, "KPI_CALCULATION_STALE", kpiId, Map.of(
                    "resultId", existing.getId(), "sourceErrorCode", exception.code(), "reminderSent", false));
            return resultView(context, kpi, existing, false, traceId);
        }
        BigDecimal actual = number(source.value());
        if (actual == null) throw invalid("KPI_ACTUAL_VALUE_INVALID", "数据源指标没有返回可计算数值");
        BigDecimal rate = achievement(actual, target);
        String status = achieved(actual, target) ? "ACHIEVED" : "UNDER_TARGET";
        LinkedHashMap<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("stale", false);
        explanation.put("kpiVersion", kpi.getVersion());
        explanation.put("targetOperator", target.operator());
        explanation.put("targetValue", target.value());
        explanation.put("periodType", kpi.getCalculationSchedule());
        explanation.put("dataSourceVersionId", source.dataSourceVersionId());
        explanation.put("dataSourceVersionNumber", source.dataSourceVersionNumber());
        explanation.put("definitionHash", source.definitionHash());
        explanation.put("metricDefinition", source.metricDefinition());
        explanation.put("sourceFields", source.sourceFields());
        explanation.put("permissionFilters", source.permissionFilters());
        explanation.put("sourceRecordIds", source.items().stream().map(item -> item.get("id")).toList());
        explanation.put("calculatedAt", source.updatedAt());
        AnaKpiResult result = existing == null ? new AnaKpiResult() : existing;
        result.setKpiId(kpiId);
        result.setPeriodKey(periodKey);
        result.setDimensionKey("ALL");
        result.setActualValue(actual);
        result.setTargetValue(target.value());
        result.setAchievementRate(rate);
        result.setStatus(status);
        result.setExplanationJson(writeJson(explanation));
        result.setCalculatedAt(LocalDateTime.now());
        if (existing == null) {
            result.setCreatedAt(LocalDateTime.now());
            resultService.insert(result);
        } else resultService.updateById(result);
        if ("UNDER_TARGET".equals(status)) sendReminders(context, kpi, result, rate, traceId);
        else resolveReminders(result.getId());
        audit(context, traceId, "KPI_CALCULATED", kpiId, Map.of(
                "resultId", result.getId(), "periodKey", periodKey, "status", status,
                "actualValue", actual, "targetValue", target.value(), "achievementRate", rate,
                "dataSourceVersionId", kpi.getDataSourceVersionId()));
        return resultView(context, kpi, result, true, traceId);
    }

    @Transactional
    public KpiModels.ReminderView acknowledge(
            AuthenticatedContext context, Long reminderId, String traceId) {
        AnaKpiReminder reminder = reminderService.selectById(reminderId);
        if (reminder == null || !context.accountId().equals(reminder.getRecipientAccountId())) {
            throw new DomainException("KPI_REMINDER_NOT_FOUND", "提醒不存在或不属于当前账号", HttpStatus.NOT_FOUND);
        }
        AnaKpiResult result = resultService.selectById(reminder.getKpiResultId());
        requireKpi(context, result == null ? null : result.getKpiId());
        if (!"ACKNOWLEDGED".equals(reminder.getStatus())) {
            reminder.setStatus("ACKNOWLEDGED");
            reminder.setAcknowledgedAt(LocalDateTime.now());
            reminder.setUpdatedAt(LocalDateTime.now());
            reminderService.updateById(reminder);
        }
        audit(context, traceId, "KPI_REMINDER_ACKNOWLEDGED", result.getKpiId(), Map.of("reminderId", reminderId));
        return reminderView(context, reminderService.selectById(reminderId));
    }

    private void sendReminders(AuthenticatedContext context, AnaKpi kpi, AnaKpiResult result,
                               BigDecimal rate, String traceId) {
        Map<String, Object> policy = readMap(kpi.getReminderPolicyJson());
        if (!booleanValue(policy.get("enabled"))) return;
        BigDecimal threshold = number(policy.get("belowPercent"));
        if (threshold != null && rate.compareTo(threshold) >= 0) return;
        List<KpiRecipient> recipients = resolveRecipients(context,
                longList(policy.get("recipientTenantMemberIds")));
        if (recipients.isEmpty()) return;
        MessageModels.MessageView message = messageService.notifyKpiUnderTarget(context, kpi.getId(), result.getId(),
                kpi.getName(), result.getActualValue().stripTrailingZeros().toPlainString(),
                result.getTargetValue().stripTrailingZeros().toPlainString(),
                rate.stripTrailingZeros().toPlainString(), recipients.stream()
                        .map(recipient -> new MessageModels.RecipientRef("TENANT_MEMBER", recipient.tenantMemberId()))
                        .toList(), traceId);
        for (KpiRecipient recipient : recipients) {
            AnaKpiReminder reminder = reminderService.selectList(Wrappers.<AnaKpiReminder>lambdaQuery()
                            .eq(AnaKpiReminder::getKpiResultId, result.getId())
                            .eq(AnaKpiReminder::getRecipientAccountId, recipient.accountId())).stream().findFirst().orElse(null);
            if (reminder != null) continue;
            reminder = new AnaKpiReminder();
            reminder.setKpiResultId(result.getId());
            reminder.setMessageId(message.id());
            reminder.setRecipientAccountId(recipient.accountId());
            reminder.setStatus("SENT");
            reminder.setSentAt(LocalDateTime.now());
            reminder.setCreatedAt(LocalDateTime.now());
            reminder.setUpdatedAt(LocalDateTime.now());
            reminder.setVersion(0);
            reminderService.insert(reminder);
        }
    }

    private void resolveReminders(Long resultId) {
        for (AnaKpiReminder reminder : reminders(resultId)) {
            if ("RESOLVED".equals(reminder.getStatus())) continue;
            reminder.setStatus("RESOLVED");
            reminder.setAcknowledgedAt(LocalDateTime.now());
            reminder.setUpdatedAt(LocalDateTime.now());
            reminderService.updateById(reminder);
        }
    }

    private KpiModels.KpiView view(
            AuthenticatedContext context, AnaKpi kpi, boolean includeDrill, String traceId) {
        Target target = target(kpi);
        Map<String, Object> dimension = readMap(kpi.getDimensionJson());
        Map<String, Object> reminder = readMap(kpi.getReminderPolicyJson());
        AnaKpiResult latest = results(kpi.getId()).stream()
                .max(Comparator.comparing(AnaKpiResult::getCalculatedAt)).orElse(null);
        return new KpiModels.KpiView(kpi.getId(), kpi.getCode(), kpi.getName(), kpi.getDataSourceVersionId(),
                target.value(), target.operator(), kpi.getCalculationSchedule(),
                string(dimension.get("responsibleType")), longList(dimension.get("responsibleIds")),
                readMapValue(dimension.get("visibilityPermission")), readMapValue(dimension.get("drillPermission")),
                booleanValue(reminder.get("enabled")), longList(reminder.get("recipientTenantMemberIds")),
                number(reminder.get("belowPercent")), kpi.getStatus(), kpi.getVersion(), kpi.getUpdatedAt(),
                latest == null ? null : resultView(context, kpi, latest, includeDrill, traceId));
    }

    private KpiModels.ResultView resultView(
            AuthenticatedContext context, AnaKpi kpi, AnaKpiResult result,
            boolean includeDrill, String traceId) {
        boolean drillAllowed = includeDrill && allows(context, readMap(kpi.getDimensionJson()), "drillPermission");
        List<Map<String, Object>> drillItems = List.of();
        if (drillAllowed) {
            try { drillItems = reportService.executeVersion(context, kpi.getDataSourceVersionId(), traceId).items(); }
            catch (DomainException exception) { drillItems = List.of(); }
        }
        return new KpiModels.ResultView(result.getId(), result.getPeriodKey(), result.getDimensionKey(),
                result.getActualValue(), result.getTargetValue(), result.getAchievementRate(), result.getStatus(),
                "STALE".equals(result.getStatus()) || booleanValue(readMap(result.getExplanationJson()).get("stale")),
                readMap(result.getExplanationJson()), result.getCalculatedAt(), drillItems, drillAllowed,
                reminders(result.getId()).stream().map(reminder -> reminderView(context, reminder)).toList());
    }

    private KpiModels.ReminderView reminderView(AuthenticatedContext context, AnaKpiReminder reminder) {
        return new KpiModels.ReminderView(reminder.getId(), reminder.getTodoId(), reminder.getMessageId(),
                tenantMemberId(context, reminder.getRecipientAccountId()), reminder.getStatus(), reminder.getSentAt(),
                reminder.getAcknowledgedAt());
    }

    private void validate(AuthenticatedContext context, KpiModels.KpiRequest input) {
        AnaDataSourceVersion version = sourceVersionService.selectById(input.dataSourceVersionId());
        if (version == null) throw invalid("KPI_SOURCE_VERSION_INVALID", "必须选择存在的结构化数据源发布版本");
        reportService.executeVersion(context, input.dataSourceVersionId(), "kpi-definition-validation");
        validateResponsible(context, input.responsibleType(), input.responsibleIds());
        if (input.reminderEnabled() && (input.reminderRecipientTenantMemberIds() == null
                || input.reminderRecipientTenantMemberIds().isEmpty())) {
            throw invalid("KPI_REMINDER_RECIPIENT_REQUIRED", "启用未达标提醒时必须选择当前工作空间成员");
        }
        resolveRecipients(context, input.reminderRecipientTenantMemberIds() == null
                ? List.of() : input.reminderRecipientTenantMemberIds());
    }

    private void bind(AnaKpi kpi, AuthenticatedContext context, KpiModels.KpiRequest input) {
        kpi.setContextType("SYSTEM");
        kpi.setPlatformId(context.platformId());
        kpi.setSystemId(context.systemId());
        kpi.setOwnerTenantId(context.tenantId());
        kpi.setCode(input.code());
        kpi.setName(input.name().strip());
        kpi.setDataSourceVersionId(input.dataSourceVersionId());
        kpi.setTargetExpression(writeJson(Map.of("operator", input.targetOperator(), "value", input.targetValue())));
        kpi.setCalculationSchedule(input.periodType());
        kpi.setDimensionJson(writeJson(Map.of("responsibleType", input.responsibleType(),
                "responsibleIds", input.responsibleIds(), "visibilityPermission", input.visibilityPermission(),
                "drillPermission", input.drillPermission())));
        kpi.setReminderPolicyJson(writeJson(Map.of("enabled", input.reminderEnabled(),
                "recipientTenantMemberIds", input.reminderRecipientTenantMemberIds() == null ? List.of()
                        : input.reminderRecipientTenantMemberIds().stream().distinct().toList(),
                "belowPercent", input.reminderBelowPercent() == null ? BigDecimal.valueOf(100)
                        : input.reminderBelowPercent())));
        kpi.setStatus(input.status());
    }

    private void validateResponsible(AuthenticatedContext context, String type, List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) {
            throw invalid("KPI_RESPONSIBLE_INVALID", "KPI 必须选择当前工作空间内的责任对象");
        }
        long valid = switch (type) {
            case "PERSON" -> tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                            .eq(SystemTenantMember::getSystemId, context.systemId())
                            .eq(SystemTenantMember::getTenantId, context.tenantId())
                            .eq(SystemTenantMember::getStatus, "ACTIVE").in(SystemTenantMember::getId, ids))
                    .stream().map(SystemTenantMember::getId).distinct().count();
            case "DEPARTMENT" -> departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                            .eq(SystemDepartment::getSystemId, context.systemId())
                            .eq(SystemDepartment::getTenantId, context.tenantId())
                            .eq(SystemDepartment::getStatus, "ACTIVE").in(SystemDepartment::getId, ids))
                    .stream().map(SystemDepartment::getId).distinct().count();
            case "ROLE" -> roleService.selectList(Wrappers.<SystemRole>lambdaQuery()
                            .eq(SystemRole::getSystemId, context.systemId())
                            .eq(SystemRole::getTenantId, context.tenantId())
                            .eq(SystemRole::getStatus, "ACTIVE").in(SystemRole::getId, ids))
                    .stream().map(SystemRole::getId).distinct().count();
            default -> 0;
        };
        if (valid != ids.stream().distinct().count()) {
            throw invalid("KPI_RESPONSIBLE_INVALID", "责任对象不存在、已停用或不属于当前工作空间");
        }
    }

    private List<KpiRecipient> resolveRecipients(AuthenticatedContext context, List<Long> tenantMemberIds) {
        if (tenantMemberIds == null || tenantMemberIds.isEmpty()) return List.of();
        List<SystemTenantMember> tenantMembers = tenantMemberService.selectList(
                Wrappers.<SystemTenantMember>lambdaQuery().eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE")
                        .in(SystemTenantMember::getId, tenantMemberIds.stream().distinct().toList()));
        Map<Long, SystemTenantMember> byId = tenantMembers.stream().collect(java.util.stream.Collectors.toMap(
                SystemTenantMember::getId, member -> member));
        if (byId.size() != tenantMemberIds.stream().distinct().count()) {
            throw invalid("KPI_REMINDER_RECIPIENT_INVALID", "提醒接收人不存在、已停用或不属于当前工作空间");
        }
        return tenantMemberIds.stream().distinct().map(id -> {
            SystemTenantMember tenantMember = byId.get(id);
            SystemMember member = systemMemberService.selectById(tenantMember.getSystemMemberId());
            if (member == null || !Objects.equals(member.getSystemId(), context.systemId())
                    || !"ACTIVE".equals(member.getStatus())) {
                throw invalid("KPI_REMINDER_RECIPIENT_INVALID", "提醒接收人的系统成员身份已失效");
            }
            return new KpiRecipient(id, member.getAccountId());
        }).toList();
    }

    private Long tenantMemberId(AuthenticatedContext context, Long accountId) {
        if (accountId == null) return null;
        SystemMember member = systemMemberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId())
                        .eq(SystemMember::getAccountId, accountId).eq(SystemMember::getStatus, "ACTIVE"))
                .stream().findFirst().orElse(null);
        if (member == null) return null;
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, member.getId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().map(SystemTenantMember::getId).findFirst().orElse(null);
    }

    private record KpiRecipient(Long tenantMemberId, Long accountId) {
    }

    private Target target(AnaKpi kpi) {
        Map<String, Object> target = readMap(kpi.getTargetExpression());
        BigDecimal value = number(target.get("value"));
        String operator = string(target.get("operator")).toUpperCase(Locale.ROOT);
        if (value == null || !List.of("GTE", "LTE").contains(operator)) {
            throw new IllegalStateException("Stored KPI target expression is invalid");
        }
        return new Target(operator, value);
    }

    private void validatePeriod(AnaKpi kpi, String periodKey) {
        String pattern = switch (kpi.getCalculationSchedule()) {
            case "MONTH" -> "[0-9]{4}-(0[1-9]|1[0-2])";
            case "QUARTER" -> "[0-9]{4}-Q[1-4]";
            case "YEAR" -> "[0-9]{4}";
            default -> throw new IllegalStateException("Stored KPI period type is invalid");
        };
        if (periodKey == null || !periodKey.matches(pattern)) {
            throw invalid("KPI_PERIOD_INVALID", "周期键与 KPI 周期类型不匹配");
        }
    }

    private boolean achieved(BigDecimal actual, Target target) {
        return "GTE".equals(target.operator()) ? actual.compareTo(target.value()) >= 0
                : actual.compareTo(target.value()) <= 0;
    }

    private BigDecimal achievement(BigDecimal actual, Target target) {
        if (target.value().compareTo(BigDecimal.ZERO) == 0) {
            return achieved(actual, target) ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        if ("LTE".equals(target.operator())) {
            if (actual.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(100);
            return target.value().multiply(BigDecimal.valueOf(100)).divide(actual, 6, RoundingMode.HALF_UP);
        }
        return actual.multiply(BigDecimal.valueOf(100)).divide(target.value(), 6, RoundingMode.HALF_UP);
    }

    private boolean allows(AuthenticatedContext context, Map<String, Object> container, String key) {
        Map<String, Object> policy = readMapValue(container.get(key));
        if (policy.isEmpty()) return true;
        if (policy.containsKey("tenantMemberIds")) {
            return longList(policy.get("tenantMemberIds")).contains(context.tenantMemberId());
        }
        String type = string(policy.get("resourceType"));
        String code = string(policy.get("resourceCode"));
        String action = string(policy.get("actionCode"));
        return type.isBlank() || code.isBlank() || action.isBlank()
                || permissionChecker.allows(context, type, code, action);
    }

    private void requireAdmin(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null
                || !permissionChecker.allows(context, "CONFIG", "SYSTEM", "MANAGE")) {
            throw forbidden("KPI_ADMIN_DENIED", "当前系统租户上下文没有 KPI 配置权限");
        }
    }

    private List<AnaKpi> owned(AuthenticatedContext context) {
        return kpiService.selectList(Wrappers.<AnaKpi>lambdaQuery()
                .eq(AnaKpi::getContextType, "SYSTEM")
                .eq(AnaKpi::getPlatformId, context.platformId())
                .eq(AnaKpi::getSystemId, context.systemId())
                .eq(AnaKpi::getOwnerTenantId, context.tenantId()));
    }

    private AnaKpi requireKpi(AuthenticatedContext context, Long kpiId) {
        AnaKpi kpi = kpiId == null ? null : kpiService.selectById(kpiId);
        if (kpi == null || !owned(context).stream().map(AnaKpi::getId).toList().contains(kpiId)) {
            throw new DomainException("KPI_NOT_FOUND", "KPI 不存在或不属于当前系统租户", HttpStatus.NOT_FOUND);
        }
        return kpi;
    }

    private List<AnaKpiResult> results(Long kpiId) {
        return resultService.selectList(Wrappers.<AnaKpiResult>lambdaQuery().eq(AnaKpiResult::getKpiId, kpiId));
    }

    private AnaKpiResult result(Long kpiId, String periodKey, String dimensionKey) {
        return resultService.selectList(Wrappers.<AnaKpiResult>lambdaQuery()
                        .eq(AnaKpiResult::getKpiId, kpiId).eq(AnaKpiResult::getPeriodKey, periodKey)
                        .eq(AnaKpiResult::getDimensionKey, dimensionKey)).stream().findFirst().orElse(null);
    }

    private List<AnaKpiReminder> reminders(Long resultId) {
        return reminderService.selectList(Wrappers.<AnaKpiReminder>lambdaQuery()
                .eq(AnaKpiReminder::getKpiResultId, resultId).orderByAsc(AnaKpiReminder::getId));
    }

    private void audit(AuthenticatedContext context, String traceId, String event, Long kpiId,
                       Map<String, Object> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                event, "ANA_KPI", kpiId.toString(), "SUCCESS", detail);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, MAP_TYPE); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot read KPI JSON", exception); }
    }

    private Map<String, Object> readMapValue(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return objectMapper.convertValue(map, MAP_TYPE);
        return Map.of();
    }

    private List<Long> longList(Object value) {
        if (value == null) return List.of();
        return objectMapper.convertValue(value, LONG_LIST);
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot write KPI JSON", exception); }
    }

    private BigDecimal number(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException exception) { return null; }
    }

    private String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean booleanValue(Object value) { return value instanceof Boolean bool ? bool : Boolean.parseBoolean(string(value)); }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }
    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private record Target(String operator, BigDecimal value) { }
}
