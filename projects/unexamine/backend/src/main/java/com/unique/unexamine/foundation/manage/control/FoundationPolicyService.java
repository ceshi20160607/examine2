package com.unique.unexamine.foundation.manage.control;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreCacheEpoch;
import com.unique.unexamine.foundation.base.entity.CoreFeatureFlag;
import com.unique.unexamine.foundation.base.entity.CoreQuotaPolicy;
import com.unique.unexamine.foundation.base.entity.CoreQuotaUsage;
import com.unique.unexamine.foundation.base.entity.CoreSequence;
import com.unique.unexamine.foundation.base.service.CoreCacheEpochBaseService;
import com.unique.unexamine.foundation.base.service.CoreFeatureFlagBaseService;
import com.unique.unexamine.foundation.base.service.CoreQuotaPolicyBaseService;
import com.unique.unexamine.foundation.base.service.CoreQuotaUsageBaseService;
import com.unique.unexamine.foundation.base.service.CoreSequenceBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FoundationPolicyService {
    private static final Set<String> FLAG_TARGETS = Set.of("SYSTEM", "APPLICATION", "MODULE", "ROLE");
    private static final Set<String> QUOTA_PERIODS = Set.of("TOTAL", "DAY", "MONTH");
    private static final Set<String> SEQUENCE_PERIODS = Set.of("NONE", "DAY", "MONTH", "YEAR");
    private static final Pattern SEQUENCE_TOKEN = Pattern.compile("\\{seq:(\\d{1,2})}");
    private static final int MAX_RETRIES = 20;

    private final CoreFeatureFlagBaseService featureFlagService;
    private final CoreQuotaPolicyBaseService quotaPolicyService;
    private final CoreQuotaUsageBaseService quotaUsageService;
    private final CoreSequenceBaseService sequenceService;
    private final CoreCacheEpochBaseService cacheEpochService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public FoundationPolicyService(CoreFeatureFlagBaseService featureFlagService,
                                   CoreQuotaPolicyBaseService quotaPolicyService,
                                   CoreQuotaUsageBaseService quotaUsageService,
                                   CoreSequenceBaseService sequenceService,
                                   CoreCacheEpochBaseService cacheEpochService,
                                   AuditRecorder auditRecorder,
                                   ObjectMapper objectMapper,
                                   JdbcTemplate jdbc) {
        this.featureFlagService = featureFlagService;
        this.quotaPolicyService = quotaPolicyService;
        this.quotaUsageService = quotaUsageService;
        this.sequenceService = sequenceService;
        this.cacheEpochService = cacheEpochService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<FoundationControlModels.FeatureFlagView> featureFlags(AuthenticatedContext context) {
        requireContext(context);
        return featureFlagService.selectList(Wrappers.<CoreFeatureFlag>lambdaQuery()
                        .eq(CoreFeatureFlag::getContextType, "SYSTEM")
                        .isNull(CoreFeatureFlag::getPlatformId)
                        .eq(CoreFeatureFlag::getSystemId, context.systemId())
                        .eq(CoreFeatureFlag::getTenantId, context.tenantId())
                        .orderByAsc(CoreFeatureFlag::getFlagKey))
                .stream().map(this::flagView).toList();
    }

    @Transactional
    public FoundationControlModels.FeatureFlagView saveFeatureFlag(
            AuthenticatedContext context, FoundationControlModels.SaveFeatureFlagRequest input, String traceId) {
        requireContext(context);
        String targetType = input.targetType().strip().toUpperCase(Locale.ROOT);
        if (!FLAG_TARGETS.contains(targetType)) {
            throw invalid("FEATURE_FLAG_TARGET_INVALID", "特性开关目标类型不受支持");
        }
        if (input.effectiveFrom() != null && input.effectiveUntil() != null
                && !input.effectiveUntil().isAfter(input.effectiveFrom())) {
            throw invalid("FEATURE_FLAG_WINDOW_INVALID", "特性开关结束时间必须晚于开始时间");
        }
        List<String> targets = input.targetCodes().stream().map(String::strip).filter(value -> !value.isBlank())
                .distinct().sorted().toList();
        Rollout rollout = new Rollout(targetType, targets, input.effectiveFrom(), input.effectiveUntil(),
                input.fallbackEnabled(), input.stableVariant() == null ? "default" : input.stableVariant().strip());
        CoreFeatureFlag flag;
        if (input.id() == null) {
            flag = new CoreFeatureFlag();
            flag.setContextType("SYSTEM");
            flag.setSystemId(context.systemId());
            flag.setTenantId(context.tenantId());
            flag.setFlagKey(input.flagKey().strip().toLowerCase(Locale.ROOT));
            flag.setEnabled(input.enabled());
            flag.setRolloutJson(json(rollout));
            flag.setStatus("ACTIVE");
            try {
                featureFlagService.insert(flag);
            } catch (DataIntegrityViolationException duplicate) {
                throw new DomainException("FEATURE_FLAG_EXISTS", "当前系统租户已存在同名特性开关", HttpStatus.CONFLICT);
            }
        } else {
            flag = requireFlag(context, input.id());
            requireVersion(input.expectedVersion(), flag.getVersion(), "FEATURE_FLAG_VERSION_CONFLICT");
            flag.setFlagKey(input.flagKey().strip().toLowerCase(Locale.ROOT));
            flag.setEnabled(input.enabled());
            flag.setRolloutJson(json(rollout));
            flag.setStatus("ACTIVE");
            if (featureFlagService.updateById(flag) != 1) {
                throw versionConflict("FEATURE_FLAG_VERSION_CONFLICT", "特性开关已被其他管理员更新");
            }
        }
        bumpEpoch(context, "FEATURE_FLAG", "feature flag " + flag.getFlagKey() + " updated");
        CoreFeatureFlag saved = featureFlagService.selectById(flag.getId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FOUNDATION_FEATURE_FLAG_SAVED", "FEATURE_FLAG", saved.getId().toString(), "SUCCESS",
                Map.of("flagKey", saved.getFlagKey(), "enabled", saved.getEnabled(), "version", saved.getVersion()));
        return flagView(saved);
    }

    @Transactional(readOnly = true)
    public FoundationControlModels.FeatureFlagResolution resolveFeatureFlag(
            AuthenticatedContext context, String flagKey, String targetCode) {
        requireContext(context);
        CoreFeatureFlag flag = featureFlagService.selectList(Wrappers.<CoreFeatureFlag>lambdaQuery()
                        .eq(CoreFeatureFlag::getContextType, "SYSTEM")
                        .eq(CoreFeatureFlag::getSystemId, context.systemId())
                        .eq(CoreFeatureFlag::getTenantId, context.tenantId())
                        .eq(CoreFeatureFlag::getFlagKey, flagKey.strip().toLowerCase(Locale.ROOT))
                        .eq(CoreFeatureFlag::getStatus, "ACTIVE"))
                .stream().findFirst().orElse(null);
        if (flag == null) {
            return new FoundationControlModels.FeatureFlagResolution(flagKey, false, true, "FALLBACK",
                    "未配置开关，使用安全关闭值", 0);
        }
        Rollout rollout = rollout(flag);
        LocalDateTime now = LocalDateTime.now();
        boolean activeWindow = (rollout.effectiveFrom() == null || !now.isBefore(rollout.effectiveFrom()))
                && (rollout.effectiveUntil() == null || now.isBefore(rollout.effectiveUntil()));
        boolean targeted = rollout.targetCodes().isEmpty()
                || (targetCode != null && rollout.targetCodes().contains(targetCode.strip()));
        boolean enabled = activeWindow && targeted ? Boolean.TRUE.equals(flag.getEnabled()) : rollout.fallbackEnabled();
        String reason = !activeWindow ? "当前时间不在生效窗口" : !targeted ? "当前目标不在灰度范围" : "命中已发布配置";
        return new FoundationControlModels.FeatureFlagResolution(flag.getFlagKey(), enabled, true, "DATABASE",
                reason, flag.getVersion());
    }

    @Transactional(readOnly = true)
    public List<FoundationControlModels.QuotaView> quotas(AuthenticatedContext context) {
        requireContext(context);
        return quotaPolicyService.selectList(Wrappers.<CoreQuotaPolicy>lambdaQuery()
                        .eq(CoreQuotaPolicy::getContextType, "SYSTEM")
                        .eq(CoreQuotaPolicy::getSystemId, context.systemId())
                        .eq(CoreQuotaPolicy::getTenantId, context.tenantId())
                        .orderByAsc(CoreQuotaPolicy::getQuotaCode))
                .stream().map(this::quotaView).toList();
    }

    @Transactional
    public FoundationControlModels.QuotaView saveQuota(
            AuthenticatedContext context, FoundationControlModels.SaveQuotaRequest input, String traceId) {
        requireContext(context);
        String period = input.periodType().strip().toUpperCase(Locale.ROOT);
        if (!QUOTA_PERIODS.contains(period)) throw invalid("QUOTA_PERIOD_INVALID", "配额周期不受支持");
        if (input.warningThreshold() != null && input.warningThreshold() > input.hardLimit()) {
            throw invalid("QUOTA_WARNING_INVALID", "预警值不能超过硬限制");
        }
        CoreQuotaPolicy policy;
        if (input.id() == null) {
            policy = new CoreQuotaPolicy();
            policy.setContextType("SYSTEM");
            policy.setSystemId(context.systemId());
            policy.setTenantId(context.tenantId());
            policy.setQuotaCode(input.quotaCode().strip().toUpperCase(Locale.ROOT));
            policy.setPeriodType(period);
            policy.setHardLimit(input.hardLimit());
            policy.setWarningThreshold(input.warningThreshold());
            policy.setStatus("ACTIVE");
            try {
                quotaPolicyService.insert(policy);
            } catch (DataIntegrityViolationException duplicate) {
                throw new DomainException("QUOTA_POLICY_EXISTS", "当前系统租户已存在同名配额", HttpStatus.CONFLICT);
            }
        } else {
            policy = requireQuota(context, input.id());
            requireVersion(input.expectedVersion(), policy.getVersion(), "QUOTA_VERSION_CONFLICT");
            policy.setPeriodType(period);
            policy.setHardLimit(input.hardLimit());
            policy.setWarningThreshold(input.warningThreshold());
            policy.setStatus("ACTIVE");
            if (quotaPolicyService.updateById(policy) != 1) {
                throw versionConflict("QUOTA_VERSION_CONFLICT", "配额策略已被其他管理员更新");
            }
        }
        CoreQuotaPolicy saved = quotaPolicyService.selectById(policy.getId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FOUNDATION_QUOTA_SAVED", "QUOTA_POLICY", saved.getId().toString(), "SUCCESS",
                Map.of("quotaCode", saved.getQuotaCode(), "hardLimit", saved.getHardLimit(), "version", saved.getVersion()));
        return quotaView(saved);
    }

    @Transactional
    public FoundationControlModels.QuotaView consumeQuota(AuthenticatedContext context, long policyId,
                                                           FoundationControlModels.ConsumeQuotaRequest input,
                                                           String traceId) {
        requireContext(context);
        CoreQuotaPolicy policy = requireQuota(context, policyId);
        if (!"ACTIVE".equals(policy.getStatus())) throw invalid("QUOTA_POLICY_INACTIVE", "配额策略未启用");
        String periodKey = quotaPeriodKey(policy.getPeriodType());
        jdbc.update("insert into core_quota_usage(quota_policy_id,period_key,used_value,reserved_value) "
                        + "values (?,?,0,0) on duplicate key update quota_policy_id=values(quota_policy_id)",
                policy.getId(), periodKey);
        int updated = jdbc.update("update core_quota_usage set used_value=used_value+?, version=version+1 "
                        + "where quota_policy_id=? and period_key=? and used_value+reserved_value+?<=?",
                input.amount(), policy.getId(), periodKey, input.amount(), policy.getHardLimit());
        Map<String, Object> usage = jdbc.queryForMap(
                "select used_value,reserved_value from core_quota_usage where quota_policy_id=? and period_key=? for update",
                policy.getId(), periodKey);
        long used = ((Number) usage.get("used_value")).longValue();
        long reserved = ((Number) usage.get("reserved_value")).longValue();
        if (updated != 1) {
            throw new DomainException("QUOTA_EXCEEDED", "本次用量会超过配额硬限制", HttpStatus.TOO_MANY_REQUESTS,
                    Map.of("quotaCode", policy.getQuotaCode(), "hardLimit", policy.getHardLimit(),
                            "used", used, "requested", input.amount(),
                            "remaining", Math.max(0, policy.getHardLimit() - used - reserved)));
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FOUNDATION_QUOTA_CONSUMED", "QUOTA_POLICY", policy.getId().toString(), "SUCCESS",
                Map.of("quotaCode", policy.getQuotaCode(), "amount", input.amount(),
                        "used", used, "reference", safe(input.reference())));
        return quotaView(policy);
    }

    @Transactional(readOnly = true)
    public List<FoundationControlModels.SequenceView> sequences(AuthenticatedContext context) {
        requireContext(context);
        return sequenceService.selectList(Wrappers.<CoreSequence>lambdaQuery()
                        .eq(CoreSequence::getSystemId, context.systemId())
                        .eq(CoreSequence::getTenantId, context.tenantId())
                        .orderByAsc(CoreSequence::getSequenceCode))
                .stream().map(this::sequenceView).toList();
    }

    @Transactional
    public FoundationControlModels.SequenceView saveSequence(
            AuthenticatedContext context, FoundationControlModels.SaveSequenceRequest input, String traceId) {
        requireContext(context);
        String reset = input.resetPeriod().strip().toUpperCase(Locale.ROOT);
        if (!SEQUENCE_PERIODS.contains(reset)) throw invalid("SEQUENCE_PERIOD_INVALID", "序列重置周期不受支持");
        Matcher matcher = SEQUENCE_TOKEN.matcher(input.pattern());
        if (!matcher.find()) throw invalid("SEQUENCE_PATTERN_INVALID", "序列格式必须包含 {seq:位数}");
        CoreSequence sequence;
        if (input.id() == null) {
            sequence = new CoreSequence();
            sequence.setSystemId(context.systemId());
            sequence.setTenantId(context.tenantId());
            sequence.setSequenceCode(input.sequenceCode().strip().toLowerCase(Locale.ROOT));
            sequence.setPattern(input.pattern().strip());
            sequence.setResetPeriod(reset);
            sequence.setCurrentPeriodKey(sequencePeriodKey(reset));
            sequence.setCurrentValue(0L);
            sequence.setStepValue(input.stepValue());
            sequence.setStatus("ACTIVE");
            try {
                sequenceService.insert(sequence);
            } catch (DataIntegrityViolationException duplicate) {
                throw new DomainException("SEQUENCE_EXISTS", "当前租户已存在同名序列", HttpStatus.CONFLICT);
            }
        } else {
            sequence = requireSequence(context, input.id());
            requireVersion(input.expectedVersion(), sequence.getVersion(), "SEQUENCE_VERSION_CONFLICT");
            sequence.setPattern(input.pattern().strip());
            sequence.setResetPeriod(reset);
            sequence.setStepValue(input.stepValue());
            sequence.setStatus("ACTIVE");
            if (sequenceService.updateById(sequence) != 1) {
                throw versionConflict("SEQUENCE_VERSION_CONFLICT", "序列配置已被其他管理员更新");
            }
        }
        CoreSequence saved = sequenceService.selectById(sequence.getId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FOUNDATION_SEQUENCE_SAVED", "SEQUENCE", saved.getId().toString(), "SUCCESS",
                Map.of("sequenceCode", saved.getSequenceCode(), "pattern", saved.getPattern(), "version", saved.getVersion()));
        return sequenceView(saved);
    }

    @Transactional
    public FoundationControlModels.SequenceValue nextSequence(AuthenticatedContext context, long sequenceId,
                                                               String traceId) {
        requireContext(context);
        CoreSequence sequence = requireSequence(context, sequenceId);
        if (!"ACTIVE".equals(sequence.getStatus())) throw invalid("SEQUENCE_INACTIVE", "序列未启用");
        String periodKey = sequencePeriodKey(sequence.getResetPeriod());
        int updated = jdbc.update("update core_sequence set current_value=if(current_period_key=?,current_value+step_value,step_value), "
                        + "current_period_key=?, version=version+1 where id=? and system_id=? and tenant_id=? and status='ACTIVE'",
                periodKey, periodKey, sequenceId, context.systemId(), context.tenantId());
        if (updated != 1) {
            throw versionConflict("SEQUENCE_ALLOCATION_CONFLICT", "序列并发分配失败，请刷新后重试");
        }
        Map<String, Object> allocated = jdbc.queryForMap(
                "select sequence_code,pattern,current_value,version from core_sequence where id=? for update", sequenceId);
        long next = ((Number) allocated.get("current_value")).longValue();
        int version = ((Number) allocated.get("version")).intValue();
        String sequenceCode = (String) allocated.get("sequence_code");
        String value = renderSequence((String) allocated.get("pattern"), next);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FOUNDATION_SEQUENCE_ALLOCATED", "SEQUENCE", String.valueOf(sequenceId), "SUCCESS",
                Map.of("sequenceCode", sequenceCode, "value", value, "numericValue", next));
        return new FoundationControlModels.SequenceValue(sequenceCode, value, periodKey, next, version);
    }

    private FoundationControlModels.FeatureFlagView flagView(CoreFeatureFlag flag) {
        Rollout rollout = rollout(flag);
        return new FoundationControlModels.FeatureFlagView(flag.getId(), flag.getFlagKey(), flag.getEnabled(),
                rollout.targetType(), rollout.targetCodes(), rollout.effectiveFrom(), rollout.effectiveUntil(),
                rollout.fallbackEnabled(), rollout.stableVariant(), flag.getStatus(), flag.getVersion(), flag.getUpdatedAt());
    }

    private FoundationControlModels.QuotaView quotaView(CoreQuotaPolicy policy) {
        String periodKey = quotaPeriodKey(policy.getPeriodType());
        CoreQuotaUsage usage = quotaUsageService.selectList(Wrappers.<CoreQuotaUsage>lambdaQuery()
                        .eq(CoreQuotaUsage::getQuotaPolicyId, policy.getId())
                        .eq(CoreQuotaUsage::getPeriodKey, periodKey))
                .stream().findFirst().orElse(null);
        long used = usage == null || usage.getUsedValue() == null ? 0 : usage.getUsedValue();
        long reserved = usage == null || usage.getReservedValue() == null ? 0 : usage.getReservedValue();
        return new FoundationControlModels.QuotaView(policy.getId(), policy.getQuotaCode(), policy.getPeriodType(),
                policy.getHardLimit(), policy.getWarningThreshold(), periodKey, used, reserved,
                Math.max(0, policy.getHardLimit() - used - reserved), policy.getStatus(), policy.getVersion());
    }

    private FoundationControlModels.SequenceView sequenceView(CoreSequence sequence) {
        return new FoundationControlModels.SequenceView(sequence.getId(), sequence.getSequenceCode(), sequence.getPattern(),
                sequence.getResetPeriod(), sequence.getCurrentPeriodKey(), sequence.getCurrentValue(),
                sequence.getStepValue(), sequence.getStatus(), sequence.getVersion());
    }

    private CoreFeatureFlag requireFlag(AuthenticatedContext context, long id) {
        CoreFeatureFlag flag = featureFlagService.selectById(id);
        if (flag == null || !context.systemId().equals(flag.getSystemId()) || !context.tenantId().equals(flag.getTenantId())) {
            throw new DomainException("FEATURE_FLAG_NOT_FOUND", "特性开关不存在", HttpStatus.NOT_FOUND);
        }
        return flag;
    }

    private CoreQuotaPolicy requireQuota(AuthenticatedContext context, long id) {
        CoreQuotaPolicy policy = quotaPolicyService.selectById(id);
        if (policy == null || !context.systemId().equals(policy.getSystemId()) || !context.tenantId().equals(policy.getTenantId())) {
            throw new DomainException("QUOTA_POLICY_NOT_FOUND", "配额策略不存在", HttpStatus.NOT_FOUND);
        }
        return policy;
    }

    private CoreSequence requireSequence(AuthenticatedContext context, long id) {
        CoreSequence sequence = sequenceService.selectById(id);
        if (sequence == null || !context.systemId().equals(sequence.getSystemId()) || !context.tenantId().equals(sequence.getTenantId())) {
            throw new DomainException("SEQUENCE_NOT_FOUND", "序列不存在", HttpStatus.NOT_FOUND);
        }
        return sequence;
    }

    private CoreQuotaUsage usage(long policyId, String periodKey) {
        CoreQuotaUsage existing = findUsage(policyId, periodKey);
        if (existing != null) return existing;
        CoreQuotaUsage usage = new CoreQuotaUsage();
        usage.setQuotaPolicyId(policyId);
        usage.setPeriodKey(periodKey);
        usage.setUsedValue(0L);
        usage.setReservedValue(0L);
        try {
            quotaUsageService.insert(usage);
            return usage;
        } catch (DataIntegrityViolationException duplicate) {
            return requireUsage(policyId, periodKey);
        }
    }

    private CoreQuotaUsage requireUsage(long policyId, String periodKey) {
        CoreQuotaUsage usage = findUsage(policyId, periodKey);
        if (usage == null) throw new DomainException("QUOTA_USAGE_UNCERTAIN", "无法确认配额用量，已安全拒绝", HttpStatus.CONFLICT);
        return usage;
    }

    private CoreQuotaUsage findUsage(long policyId, String periodKey) {
        return quotaUsageService.selectList(Wrappers.<CoreQuotaUsage>lambdaQuery()
                        .eq(CoreQuotaUsage::getQuotaPolicyId, policyId)
                        .eq(CoreQuotaUsage::getPeriodKey, periodKey))
                .stream().findFirst().orElse(null);
    }

    private void bumpEpoch(AuthenticatedContext context, String namespace, String reason) {
        String contextKey = "system:" + context.systemId() + ":tenant:" + context.tenantId();
        CoreCacheEpoch epoch = cacheEpochService.selectList(Wrappers.<CoreCacheEpoch>lambdaQuery()
                        .eq(CoreCacheEpoch::getContextKey, contextKey)
                        .eq(CoreCacheEpoch::getCacheNamespace, namespace))
                .stream().findFirst().orElse(null);
        if (epoch == null) {
            epoch = new CoreCacheEpoch();
            epoch.setContextKey(contextKey);
            epoch.setCacheNamespace(namespace);
            epoch.setEpochValue(1L);
            epoch.setReason(reason);
            try {
                cacheEpochService.insert(epoch);
                return;
            } catch (DataIntegrityViolationException ignored) {
                epoch = cacheEpochService.selectList(Wrappers.<CoreCacheEpoch>lambdaQuery()
                                .eq(CoreCacheEpoch::getContextKey, contextKey)
                                .eq(CoreCacheEpoch::getCacheNamespace, namespace))
                        .stream().findFirst().orElseThrow();
            }
        }
        epoch.setEpochValue(epoch.getEpochValue() + 1);
        epoch.setReason(reason);
        if (cacheEpochService.updateById(epoch) != 1) {
            throw versionConflict("CACHE_EPOCH_CONFLICT", "配置缓存版本推进冲突，请重试");
        }
    }

    private Rollout rollout(CoreFeatureFlag flag) {
        try {
            return flag.getRolloutJson() == null ? new Rollout("SYSTEM", List.of(), null, null, false, "default")
                    : objectMapper.readValue(flag.getRolloutJson(), Rollout.class);
        } catch (JsonProcessingException exception) {
            throw new DomainException("FEATURE_FLAG_CONFIG_INVALID", "特性开关配置损坏，已安全关闭", HttpStatus.CONFLICT);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("FOUNDATION_POLICY_INVALID", "策略配置无法保存");
        }
    }

    private String quotaPeriodKey(String period) {
        return switch (period) {
            case "DAY" -> LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            case "MONTH" -> YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            default -> "TOTAL";
        };
    }

    private String sequencePeriodKey(String period) {
        return switch (period) {
            case "DAY" -> LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            case "MONTH" -> YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "YEAR" -> String.valueOf(LocalDate.now().getYear());
            default -> "NONE";
        };
    }

    private String renderSequence(String pattern, long value) {
        Matcher matcher = SEQUENCE_TOKEN.matcher(pattern);
        if (!matcher.find()) throw invalid("SEQUENCE_PATTERN_INVALID", "序列格式缺少数字占位符");
        int width = Integer.parseInt(matcher.group(1));
        String rendered = matcher.replaceFirst(Matcher.quoteReplacement(String.format("%0" + width + "d", value)));
        LocalDate today = LocalDate.now();
        return rendered.replace("{yyyyMMdd}", today.format(DateTimeFormatter.BASIC_ISO_DATE))
                .replace("{yyyyMM}", YearMonth.from(today).format(DateTimeFormatter.ofPattern("yyyyMM")))
                .replace("{yyyy}", String.valueOf(today.getYear()));
    }

    private void requireVersion(Integer expected, Integer actual, String code) {
        if (expected == null || !expected.equals(actual)) {
            throw versionConflict(code, "数据版本已变化，请刷新后重试");
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException versionConflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private void requireContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统租户上下文", HttpStatus.BAD_REQUEST);
        }
    }

    private record Rollout(String targetType, List<String> targetCodes, LocalDateTime effectiveFrom,
                           LocalDateTime effectiveUntil, boolean fallbackEnabled, String stableVariant) {
    }
}
