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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
                .sorted(Comparator.comparing(AnaKpi::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(kpi -> view(context, kpi, false, null)).toList());
    }

    public KpiModels.Overview runtimeOverview(AuthenticatedContext context, String traceId) {
        return new KpiModels.Overview(owned(context).stream().filter(kpi -> "ACTIVE".equals(kpi.getStatus()))
                .filter(kpi -> allows(context, readMap(kpi.getDimensionJson()), "visibilityPermission"))
                .sorted(Comparator.comparing(AnaKpi::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
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
        kpi.setCreateTime(LocalDateTime.now());
        kpi.setUpdateTime(LocalDateTime.now());
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
        kpi.setUpdateTime(LocalDateTime.now());
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
        PeriodBounds period = periodBounds(kpi.getCalculationSchedule(), periodKey);
        ReportModels.Result source = reportService.executeVersionForPeriod(context,
                kpi.getDataSourceVersionId(), period.from(), period.to(), traceId);
        BigDecimal actual = number(source.value());
        if (actual == null) throw invalid("KPI_ACTUAL_VALUE_INVALID", "数据源指标没有返回可计算数值");
        Target target = target(kpi);
        BigDecimal rate = achievement(actual, target);
        String status = achieved(actual, target) ? "ACHIEVED" : "UNDER_TARGET";
        return new KpiModels.Preview(kpiId, kpi.getVersion(), periodKey, target.value(), actual, rate, status,
                target.operator() + " " + target.value().stripTrailingZeros().toPlainString(),
                "数据源发布版本 #" + source.dataSourceVersionId() + " / 统计日期 " + period.from()
                        + " 至 " + period.to() + " / 目标归属 " + kpi.getTargetSubjectType()
                        + " #" + kpi.getTargetSubjectId() + "；目标归属不改变报表已发布的数据范围。 / "
                        + source.metricDefinition(),
                LocalDateTime.now(), source);
    }

    @Transactional
    public KpiModels.ResultView calculate(
            AuthenticatedContext context, Long kpiId, String periodKey, String traceId) {
        requireAdmin(context);
        AnaKpi kpi = requireKpi(context, kpiId);
        validatePeriod(kpi, periodKey);
        if (!"ACTIVE".equals(kpi.getStatus())) {
            throw invalid("KPI_INACTIVE", "停用的 KPI 不能产生计算结果或提醒");
        }
        Target target = target(kpi);
        PeriodBounds period = periodBounds(kpi.getCalculationSchedule(), periodKey);
        AnaKpiResult existing = result(kpiId, periodKey, "ALL");
        ReportModels.Result source;
        try {
            source = reportService.executeVersionForPeriod(context, kpi.getDataSourceVersionId(),
                    period.from(), period.to(), traceId);
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
            explanation.put("lastSuccessfulCalculatedAt", readMap(existing.getExplanationJson())
                    .getOrDefault("lastSuccessfulCalculatedAt", existing.getCalculateTime()));
            // 失效也追加计算记录，保留上次成功值及来源，不覆盖可追溯的成功快照。
            AnaKpiResult stale = new AnaKpiResult();
            stale.setKpiId(kpiId);
            stale.setScopeType(kpi.getScopeType());
            stale.setScopeOwnerId(kpi.getScopeOwnerId());
            stale.setPeriodKey(periodKey);
            stale.setDimensionKey("ALL");
            stale.setCalculationBatchKey(UUID.randomUUID().toString());
            stale.setCalculationRevision(existing.getCalculationRevision() + 1);
            stale.setKpiConfigVersion(kpi.getVersion());
            stale.setDataSourceVersionId(existing.getDataSourceVersionId());
            stale.setSourceScopeType(existing.getSourceScopeType());
            stale.setSourceScopeOwnerId(existing.getSourceScopeOwnerId());
            stale.setActualValue(existing.getActualValue());
            stale.setTargetValue(existing.getTargetValue());
            stale.setAchievementRate(existing.getAchievementRate());
            stale.setStatus("STALE");
            stale.setExplanationJson(writeJson(explanation));
            stale.setCalculateTime(LocalDateTime.now());
            stale.setCreateTime(LocalDateTime.now());
            resultService.insert(stale);
            audit(context, traceId, "KPI_CALCULATION_STALE", kpiId, Map.of(
                    "resultId", stale.getId(), "sourceErrorCode", exception.code(), "reminderSent", false));
            return resultView(context, kpi, stale, false, traceId);
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
        explanation.put("periodFrom", period.from());
        explanation.put("periodTo", period.to());
        explanation.put("targetSubjectType", kpi.getTargetSubjectType());
        explanation.put("targetSubjectId", kpi.getTargetSubjectId());
        // 责任对象负责目标；统计记录仅由已发布报表的过滤与时间字段决定。
        explanation.put("dataScope", "已发布报表口径及当前计算人权限；目标归属不自动过滤业务记录");
        explanation.put("dataSourceVersionId", source.dataSourceVersionId());
        explanation.put("dataSourceVersionNumber", source.dataSourceVersionNumber());
        explanation.put("definitionHash", source.definitionHash());
        explanation.put("metricDefinition", source.metricDefinition());
        explanation.put("sourceFields", source.sourceFields());
        explanation.put("permissionFilters", source.permissionFilters());
        explanation.put("sourceRecordIds", source.items().stream().map(item -> item.get("id")).toList());
        explanation.put("calculatedAt", source.updatedAt());
        AnaKpiResult result = new AnaKpiResult();
        result.setKpiId(kpiId);
        // 计算结果继承 KPI 的租户归属，避免相同周期的跨租户结果混用。
        result.setScopeType(kpi.getScopeType());
        result.setScopeOwnerId(kpi.getScopeOwnerId());
        result.setPeriodKey(periodKey);
        result.setDimensionKey("ALL");
        result.setCalculationBatchKey(UUID.randomUUID().toString());
        result.setCalculationRevision(existing == null ? 1 : existing.getCalculationRevision() + 1);
        result.setKpiConfigVersion(kpi.getVersion());
        result.setDataSourceVersionId(kpi.getDataSourceVersionId());
        result.setSourceScopeType(kpi.getSourceScopeType());
        result.setSourceScopeOwnerId(kpi.getSourceScopeOwnerId());
        result.setActualValue(actual);
        result.setTargetValue(target.value());
        result.setAchievementRate(rate);
        result.setStatus(status);
        result.setExplanationJson(writeJson(explanation));
        result.setCalculateTime(LocalDateTime.now());
        result.setCreateTime(LocalDateTime.now());
        resultService.insert(result);
        if ("UNDER_TARGET".equals(status)) sendReminders(context, kpi, result, rate, traceId);
        else resolveReminders(kpiId, periodKey);
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
            reminder.setAcknowledgeTime(LocalDateTime.now());
            reminder.setUpdateTime(LocalDateTime.now());
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
        // 同一 KPI 周期未结束的提醒只投递一次，重算产生的新结果不重复通知。
        List<AnaKpiReminder> existingReminders = periodReminders(kpi.getId(), result.getPeriodKey());
        List<KpiRecipient> pending = recipients.stream().filter(recipient ->
                existingReminders.stream()
                        .noneMatch(reminder -> recipient.accountId().equals(reminder.getRecipientAccountId())
                                && !"RESOLVED".equals(reminder.getStatus()))).toList();
        if (pending.isEmpty()) return;
        MessageModels.MessageView message = messageService.notifyKpiUnderTarget(context, kpi.getId(), result.getId(),
                kpi.getName(), pending.stream()
                        .map(recipient -> new MessageModels.RecipientRef("TENANT_MEMBER", recipient.tenantMemberId()))
                        .toList(), traceId);
        for (KpiRecipient recipient : pending) {
            AnaKpiReminder reminder = new AnaKpiReminder();
            reminder.setKpiResultId(result.getId());
            reminder.setContextType("SYSTEM");
            reminder.setPlatformId(context.platformId());
            reminder.setSystemId(context.systemId());
            reminder.setTenantId(context.tenantId());
            reminder.setSystemMessageId(message.id());
            reminder.setRecipientAccountId(recipient.accountId());
            reminder.setRecipientSystemMemberId(recipient.memberId());
            reminder.setStatus("SENT");
            reminder.setSendTime(LocalDateTime.now());
            reminder.setCreateTime(LocalDateTime.now());
            reminder.setUpdateTime(LocalDateTime.now());
            reminder.setVersion(0);
            reminderService.insert(reminder);
        }
    }

    private void resolveReminders(Long kpiId, String periodKey) {
        for (AnaKpiReminder reminder : periodReminders(kpiId, periodKey)) {
            if ("RESOLVED".equals(reminder.getStatus())) continue;
            reminder.setStatus("RESOLVED");
            reminder.setAcknowledgeTime(LocalDateTime.now());
            reminder.setUpdateTime(LocalDateTime.now());
            reminderService.updateById(reminder);
        }
    }

    private KpiModels.KpiView view(
            AuthenticatedContext context, AnaKpi kpi, boolean includeDrill, String traceId) {
        Target target = target(kpi);
        Map<String, Object> dimension = readMap(kpi.getDimensionJson());
        Map<String, Object> reminder = readMap(kpi.getReminderPolicyJson());
        AnaKpiResult latest = results(kpi.getId()).stream()
                .max(Comparator.comparing(AnaKpiResult::getCalculateTime)).orElse(null);
        return new KpiModels.KpiView(kpi.getId(), kpi.getCode(), kpi.getName(), kpi.getDataSourceVersionId(),
                target.value(), target.operator(), kpi.getCalculationSchedule(),
                string(dimension.get("responsibleType")), longList(dimension.get("responsibleIds")),
                readMapValue(dimension.get("visibilityPermission")), readMapValue(dimension.get("drillPermission")),
                booleanValue(reminder.get("enabled")), longList(reminder.get("recipientTenantMemberIds")),
                number(reminder.get("belowPercent")), kpi.getStatus(), kpi.getVersion(), kpi.getUpdateTime(),
                latest == null ? null : resultView(context, kpi, latest, includeDrill, traceId));
    }

    private KpiModels.ResultView resultView(
            AuthenticatedContext context, AnaKpi kpi, AnaKpiResult result,
            boolean includeDrill, String traceId) {
        boolean drillAllowed = includeDrill && allows(context, readMap(kpi.getDimensionJson()), "drillPermission");
        List<Map<String, Object>> drillItems = List.of();
        BigDecimal actual = result.getActualValue();
        BigDecimal targetValue = result.getTargetValue();
        BigDecimal rate = result.getAchievementRate();
        String status = result.getStatus();
        Map<String, Object> explanation = readMap(result.getExplanationJson());
        boolean stale = "STALE".equals(status) || booleanValue(explanation.get("stale"));
        LocalDateTime calculatedAt = result.getCalculateTime();
        if (includeDrill) {
            try {
                PeriodBounds period = periodBounds(kpi.getCalculationSchedule(), result.getPeriodKey());
                // 管理员计算的快照可能包含其他负责人的数据；运行态必须按当前访问者权限重新聚合。
                ReportModels.Result visible = reportService.executeVersionForPeriod(context,
                        result.getDataSourceVersionId(), period.from(), period.to(), traceId);
                actual = number(visible.value());
                if (actual == null) throw invalid("KPI_ACTUAL_VALUE_INVALID", "数据源指标没有返回可计算数值");
                Target visibleTarget = target(kpi);
                targetValue = visibleTarget.value();
                rate = achievement(actual, visibleTarget);
                status = achieved(actual, visibleTarget) ? "ACHIEVED" : "UNDER_TARGET";
                stale = false;
                calculatedAt = visible.updatedAt();
                explanation = new LinkedHashMap<>();
                explanation.put("dataScope", "按当前访问成员的数据和字段权限实时计算");
                explanation.put("dataSourceVersionId", visible.dataSourceVersionId());
                explanation.put("metricDefinition", visible.metricDefinition());
                explanation.put("sourceRecordIds", drillAllowed
                        ? visible.items().stream().map(item -> item.get("id")).toList() : List.of());
                if (drillAllowed) drillItems = visible.items();
            } catch (DomainException exception) {
                // 来源不可访问时不能回退显示管理员快照，否则会泄露其汇总值和来源记录。
                actual = null;
                rate = null;
                status = "STALE";
                stale = true;
                explanation = Map.of("sourceErrorCode", exception.code());
                drillAllowed = false;
            }
        }
        return new KpiModels.ResultView(result.getId(), result.getPeriodKey(), result.getDimensionKey(),
                actual, targetValue, rate, status, stale,
                explanation, calculatedAt, drillItems, drillAllowed,
                periodReminders(kpi.getId(), result.getPeriodKey()).stream()
                        .filter(reminder -> permissionChecker.allows(context, "CONFIG", "SYSTEM", "MANAGE")
                                || context.accountId().equals(reminder.getRecipientAccountId()))
                        .map(reminder -> reminderView(context, reminder)).toList());
    }

    private KpiModels.ReminderView reminderView(AuthenticatedContext context, AnaKpiReminder reminder) {
        return new KpiModels.ReminderView(reminder.getId(), reminder.getSystemTodoId(), reminder.getSystemMessageId(),
                tenantMemberId(context, reminder.getRecipientAccountId()), reminder.getStatus(), reminder.getSendTime(),
                reminder.getAcknowledgeTime());
    }

    private void validate(AuthenticatedContext context, KpiModels.KpiRequest input) {
        AnaDataSourceVersion version = sourceVersionService.selectById(input.dataSourceVersionId());
        if (version == null) throw invalid("KPI_SOURCE_VERSION_INVALID", "必须选择存在的结构化数据源发布版本");
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        Map<String, Object> timeField = readMapValue(readMapValue(snapshot.get("definition")).get("timeField"));
        if (string(timeField.get("alias")).isBlank() || string(timeField.get("fieldCode")).isBlank()) {
            throw invalid("KPI_PERIOD_TIME_FIELD_REQUIRED", "KPI 来源必须在已发布报表中配置可查询的日期字段");
        }
        PeriodBounds currentPeriod = currentPeriodBounds(input.periodType());
        reportService.executeVersionForPeriod(context, input.dataSourceVersionId(),
                currentPeriod.from(), currentPeriod.to(), "kpi-definition-validation");
        validateResponsible(context, input.responsibleType(), input.responsibleIds());
        if (input.reminderEnabled() && (input.reminderRecipientTenantMemberIds() == null
                || input.reminderRecipientTenantMemberIds().isEmpty())) {
            throw invalid("KPI_REMINDER_RECIPIENT_REQUIRED", "启用未达标提醒时必须选择当前工作空间成员");
        }
        resolveRecipients(context, input.reminderRecipientTenantMemberIds() == null
                ? List.of() : input.reminderRecipientTenantMemberIds());
    }

    private void bind(AnaKpi kpi, AuthenticatedContext context, KpiModels.KpiRequest input) {
        AnaDataSourceVersion sourceVersion = sourceVersionService.selectById(input.dataSourceVersionId());
        kpi.setContextType("SYSTEM");
        kpi.setPlatformId(context.platformId());
        kpi.setSystemId(context.systemId());
        kpi.setOwnerTenantId(context.tenantId());
        // KPI 属于当前系统的租户空间，业务保存时写入表结构要求的归属键。
        kpi.setScopeType("TENANT");
        kpi.setScopeOwnerId(context.tenantId());
        kpi.setCode(input.code());
        kpi.setName(input.name().strip());
        // 固定数据源本体与发布版本，便于按业务来源查找 KPI，版本由前置校验保证存在。
        kpi.setDataSourceId(sourceVersion.getDataSourceId());
        kpi.setDataSourceVersionId(input.dataSourceVersionId());
        kpi.setSourceScopeType(sourceVersion.getScopeType());
        kpi.setSourceScopeOwnerId(sourceVersion.getScopeOwnerId());
        // 一项 KPI 对应一个人员、部门或角色目标；来源报表明确决定实际值的业务范围。
        kpi.setTargetSubjectType(input.responsibleType());
        kpi.setTargetSubjectId(input.responsibleIds().getFirst());
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
        if (ids == null || ids.size() != 1 || ids.getFirst() == null) {
            throw invalid("KPI_RESPONSIBLE_INVALID", "每项 KPI 必须选择一个当前工作空间的目标对象");
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
        if (valid != 1) {
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
            return new KpiRecipient(id, member.getAccountId(), member.getId());
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

    private record KpiRecipient(Long tenantMemberId, Long accountId, Long memberId) {
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

    private PeriodBounds currentPeriodBounds(String periodType) {
        LocalDate today = LocalDate.now();
        String key = switch (periodType) {
            case "MONTH" -> YearMonth.from(today).toString();
            case "QUARTER" -> today.getYear() + "-Q" + ((today.getMonthValue() - 1) / 3 + 1);
            case "YEAR" -> String.valueOf(today.getYear());
            default -> throw invalid("KPI_PERIOD_INVALID", "KPI 周期类型不受支持");
        };
        return periodBounds(periodType, key);
    }

    private PeriodBounds periodBounds(String periodType, String periodKey) {
        LocalDate from = switch (periodType) {
            case "MONTH" -> YearMonth.parse(periodKey).atDay(1);
            case "QUARTER" -> LocalDate.of(Integer.parseInt(periodKey.substring(0, 4)),
                    (Integer.parseInt(periodKey.substring(6)) - 1) * 3 + 1, 1);
            case "YEAR" -> LocalDate.of(Integer.parseInt(periodKey), 1, 1);
            default -> throw invalid("KPI_PERIOD_INVALID", "KPI 周期类型不受支持");
        };
        LocalDate to = switch (periodType) {
            case "MONTH" -> from.plusMonths(1).minusDays(1);
            case "QUARTER" -> from.plusMonths(3).minusDays(1);
            case "YEAR" -> from.plusYears(1).minusDays(1);
            default -> throw invalid("KPI_PERIOD_INVALID", "KPI 周期类型不受支持");
        };
        return new PeriodBounds(from.toString(), to.toString());
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
        return !type.isBlank() && !code.isBlank() && !action.isBlank()
                && permissionChecker.allows(context, type, code, action);
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
                        .eq(AnaKpiResult::getDimensionKey, dimensionKey)
                        .orderByDesc(AnaKpiResult::getCalculationRevision)).stream().findFirst().orElse(null);
    }

    private List<AnaKpiReminder> reminders(Long resultId) {
        return reminderService.selectList(Wrappers.<AnaKpiReminder>lambdaQuery()
                .eq(AnaKpiReminder::getKpiResultId, resultId).orderByAsc(AnaKpiReminder::getId));
    }

    private List<AnaKpiReminder> periodReminders(Long kpiId, String periodKey) {
        return results(kpiId).stream().filter(result -> periodKey.equals(result.getPeriodKey()))
                .flatMap(result -> reminders(result.getId()).stream()).toList();
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

    private record PeriodBounds(String from, String to) { }
}
