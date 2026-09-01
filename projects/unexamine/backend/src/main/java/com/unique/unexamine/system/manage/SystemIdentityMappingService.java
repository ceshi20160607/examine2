package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.backgroundjobs.base.entity.JobBackground;
import com.unique.unexamine.backgroundjobs.base.entity.JobItemResult;
import com.unique.unexamine.backgroundjobs.base.service.JobBackgroundBaseService;
import com.unique.unexamine.backgroundjobs.base.service.JobItemResultBaseService;
import com.unique.unexamine.foundation.base.entity.CoreSetting;
import com.unique.unexamine.foundation.base.service.CoreSettingBaseService;
import com.unique.unexamine.platform.base.entity.PlatSsoIdentity;
import com.unique.unexamine.platform.base.entity.PlatSsoProvider;
import com.unique.unexamine.platform.base.entity.PlatSsoProviderVersion;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatSsoIdentityBaseService;
import com.unique.unexamine.platform.base.service.PlatSsoProviderBaseService;
import com.unique.unexamine.platform.base.service.PlatSsoProviderVersionBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SysAccessRequest;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SysAccessRequestBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SystemIdentityMappingService {
    private static final String SETTING_CATEGORY = "ORGANIZATION";
    private static final String SETTING_KEY = "identity.inheritance";
    private static final Set<String> MATCH_KEYS = Set.of("EXTERNAL_USER_ID", "EMAIL", "MOBILE", "EMPLOYEE_NO");

    private final CoreSettingBaseService settingService;
    private final PlatSsoProviderBaseService providerService;
    private final PlatSsoProviderVersionBaseService providerVersionService;
    private final PlatSsoIdentityBaseService identityService;
    private final PlatformAccountBaseService accountService;
    private final SystemDepartmentBaseService departmentService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SysAccessRequestBaseService accessRequestService;
    private final JobBackgroundBaseService jobService;
    private final JobItemResultBaseService jobItemService;
    private final AuditEventBaseService auditService;
    private final ObjectMapper objectMapper;

    public SystemIdentityMappingService(
            CoreSettingBaseService settingService,
            PlatSsoProviderBaseService providerService,
            PlatSsoProviderVersionBaseService providerVersionService,
            PlatSsoIdentityBaseService identityService,
            PlatformAccountBaseService accountService,
            SystemDepartmentBaseService departmentService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            SysAccessRequestBaseService accessRequestService,
            JobBackgroundBaseService jobService,
            JobItemResultBaseService jobItemService,
            AuditEventBaseService auditService,
            ObjectMapper objectMapper) {
        this.settingService = settingService;
        this.providerService = providerService;
        this.providerVersionService = providerVersionService;
        this.identityService = identityService;
        this.accountService = accountService;
        this.departmentService = departmentService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.accessRequestService = accessRequestService;
        this.jobService = jobService;
        this.jobItemService = jobItemService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SystemIdentityProviderOption> providers(AuthenticatedContext context) {
        requireSystemContext(context);
        return providerService.selectList(Wrappers.<PlatSsoProvider>lambdaQuery()
                        .eq(PlatSsoProvider::getStatus, "PUBLISHED")
                        .isNotNull(PlatSsoProvider::getPublishedVersionId)
                        .orderByAsc(PlatSsoProvider::getName, PlatSsoProvider::getId))
                .stream().map(this::providerOption).toList();
    }

    @Transactional(readOnly = true)
    public List<SystemDepartmentOption> departments(AuthenticatedContext context) {
        requireSystemContext(context);
        return departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                        .eq(SystemDepartment::getSystemId, context.systemId())
                        .eq(SystemDepartment::getTenantId, context.tenantId())
                        .eq(SystemDepartment::getStatus, "ACTIVE")
                        .orderByAsc(SystemDepartment::getSortOrder, SystemDepartment::getId))
                .stream().map(department -> new SystemDepartmentOption(department.getId(), department.getCode(),
                        department.getName(), department.getPathCode())).toList();
    }

    @Transactional(readOnly = true)
    public SystemIdentityMappingView configuration(AuthenticatedContext context) {
        requireSystemContext(context);
        CoreSetting setting = findSetting(context);
        return setting == null ? null : view(context, setting);
    }

    @Transactional
    public SystemIdentityMappingView save(
            AuthenticatedContext context,
            SaveSystemIdentityMappingRequest input,
            String traceId) {
        requireSystemContext(context);
        ProviderSelection provider = requirePublishedProvider(input.providerId(), input.providerVersionId());
        String tenantDomain = normalizeDomain(input.tenantDomain());
        validateDomain(provider.version(), tenantDomain);
        Map<String, Long> departmentMappings = normalizeDepartmentMappings(context, input.departmentMappings());
        List<String> matchOrder = normalizeMatchOrder(input.matchOrder());
        String jitPolicy = input.jitPolicy().strip().toUpperCase(Locale.ROOT);

        CoreSetting setting = findSetting(context);
        MappingState state = new MappingState(provider.provider().getId(), provider.version().getId(), tenantDomain,
                departmentMappings, matchOrder, jitPolicy, null, null, null);
        if (setting == null) {
            if (input.expectedVersion() != null) {
                throw conflict("新建身份映射不能指定历史版本");
            }
            setting = new CoreSetting();
            setting.setContextType("SYSTEM");
            setting.setPlatformId(context.platformId());
            setting.setSystemId(context.systemId());
            setting.setTenantId(context.tenantId());
            setting.setCategory(SETTING_CATEGORY);
            setting.setSettingKey(SETTING_KEY);
            setting.setValueType("JSON");
            setting.setValueJson(toJson(state));
            setting.setSensitive(false);
            setting.setStatus("ACTIVE");
            settingService.insert(setting);
        } else {
            requireVersion(setting, input.expectedVersion());
            setting.setValueJson(toJson(state));
            if (settingService.updateById(setting) != 1) {
                throw conflict("身份映射已被其他管理员修改，请刷新后重试");
            }
        }
        CoreSetting current = settingService.selectById(setting.getId());
        recordAudit(context, traceId, traceId, "SYSTEM_IDENTITY_MAPPING_DRAFT_SAVED", "SUCCESS",
                provider.provider().getCode(), null, null, null, null, null, null, current.getId());
        return view(context, current);
    }

    @Transactional
    public IdentityMappingPreflightReport preflight(
            AuthenticatedContext context,
            RunIdentityMappingPreflightRequest input,
            String traceId) {
        requireSystemContext(context);
        CoreSetting setting = requireSetting(context);
        MappingState state = readState(setting);
        ProviderSelection provider = requirePublishedProvider(state.providerId(), state.providerVersionId());
        validateDomain(provider.version(), state.tenantDomain());

        List<IdentityMappingPreflightItem> items = new ArrayList<>();
        int row = 1;
        for (IdentityMappingSample sample : input.samples()) {
            items.add(plan(context, state, provider, sample, row++));
        }
        Map<String, Long> summary = new LinkedHashMap<>();
        items.forEach(item -> summary.merge(item.outcome(), 1L, Long::sum));
        IdentityMappingPreflightReport report = new IdentityMappingPreflightReport(
                UUID.randomUUID().toString(), provider.provider().getId(), provider.version().getId(),
                provider.provider().getCode(), state.tenantDomain(), LocalDateTime.now().toString(), summary,
                List.copyOf(items));
        MappingState updated = new MappingState(state.providerId(), state.providerVersionId(), state.tenantDomain(),
                state.departmentMappings(), state.matchOrder(), state.jitPolicy(), report, null, null);
        setting.setValueJson(toJson(updated));
        if (settingService.updateById(setting) != 1) {
            throw conflict("身份映射在预检期间被修改，请重新预检");
        }
        recordAudit(context, traceId, traceId, "SYSTEM_IDENTITY_MAPPING_PREFLIGHT", "SUCCESS",
                provider.provider().getCode(), null, null, null, null, null, null, setting.getId());
        return report;
    }

    @Transactional
    public IdentityMappingJobView confirm(
            AuthenticatedContext context,
            ConfirmIdentityMappingRequest input,
            String traceId) {
        requireSystemContext(context);
        CoreSetting setting = requireSetting(context);
        MappingState state = readState(setting);
        IdentityMappingPreflightReport report = state.latestPreflight();
        if (report == null || !report.preflightId().equals(input.preflightId())) {
            throw new DomainException("IDENTITY_PREFLIGHT_STALE", "预检报告已失效，请重新运行预检", HttpStatus.CONFLICT);
        }
        ProviderSelection provider = requirePublishedProvider(state.providerId(), state.providerVersionId());

        JobBackground job = new JobBackground();
        job.setContextType("SYSTEM");
        job.setPlatformId(context.platformId());
        job.setSystemId(context.systemId());
        job.setTenantId(context.tenantId());
        job.setJobType("SYSTEM_IDENTITY_MAPPING_SYNC");
        job.setSourceType("CORE_SETTING");
        job.setSourceId(setting.getId().toString());
        job.setParameterJson(toJson(Map.of("preflightId", report.preflightId(), "providerId", state.providerId(),
                "providerVersionId", state.providerVersionId())));
        job.setAuthorizationSnapshotJson(toJson(Map.of("accountId", context.accountId(), "memberId", context.memberId(),
                "roleIds", context.roleIds())));
        job.setStatus("RUNNING");
        job.setProgressCurrent(0L);
        job.setProgressTotal((long) report.items().size());
        job.setMaxAttempts(1);
        job.setAttemptCount(1);
        job.setStartedAt(LocalDateTime.now());
        job.setCreatedByAccountId(context.accountId());
        jobService.insert(job);

        long completed = 0;
        long skipped = 0;
        long accessRequests = 0;
        for (IdentityMappingPreflightItem item : report.items()) {
            IdentityMappingJobItemView result = execute(context, state, provider, job.getId(), item, traceId);
            JobItemResult persisted = new JobItemResult();
            persisted.setJobId(job.getId());
            persisted.setItemKey(item.externalUserId());
            persisted.setRowNumber((long) item.rowNumber());
            persisted.setStatus(result.status());
            persisted.setResultJson(toJson(result));
            persisted.setErrorCode(result.errorCode());
            persisted.setErrorMessage(result.message());
            jobItemService.insert(persisted);
            if ("SKIPPED".equals(result.status())) {
                skipped++;
            } else {
                completed++;
            }
            if (result.accessRequestId() != null) {
                accessRequests++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("completed", completed);
        summary.put("skipped", skipped);
        summary.put("accessRequests", accessRequests);
        summary.put("confirmedByMemberId", context.memberId());
        job.setProgressCurrent((long) report.items().size());
        job.setStatus(skipped == 0 ? "SUCCEEDED" : "SUCCEEDED_WITH_WARNINGS");
        job.setResultSummaryJson(toJson(summary));
        job.setFinishedAt(LocalDateTime.now());
        if (jobService.updateById(job) != 1) {
            throw conflict("身份同步任务状态更新冲突");
        }

        MappingState confirmed = new MappingState(state.providerId(), state.providerVersionId(), state.tenantDomain(),
                state.departmentMappings(), state.matchOrder(), state.jitPolicy(), state.latestPreflight(), job.getId(),
                LocalDateTime.now().toString());
        setting.setValueJson(toJson(confirmed));
        if (settingService.updateById(setting) != 1) {
            throw conflict("身份映射确认状态更新冲突");
        }
        recordAudit(context, traceId, traceId, "SYSTEM_IDENTITY_MAPPING_SYNC_CONFIRMED", job.getStatus(),
                provider.provider().getCode(), null, null, null, null, null, job.getId(), setting.getId());
        return jobView(context, jobService.selectById(job.getId()));
    }

    @Transactional(readOnly = true)
    public List<IdentityMappingJobView> jobs(AuthenticatedContext context) {
        requireSystemContext(context);
        return jobService.selectList(Wrappers.<JobBackground>lambdaQuery()
                        .eq(JobBackground::getSystemId, context.systemId())
                        .eq(JobBackground::getTenantId, context.tenantId())
                        .eq(JobBackground::getJobType, "SYSTEM_IDENTITY_MAPPING_SYNC")
                        .orderByDesc(JobBackground::getCreatedAt, JobBackground::getId))
                .stream().map(job -> jobView(context, job)).toList();
    }

    @Transactional(readOnly = true)
    public List<IdentityMappingLogView> logs(
            AuthenticatedContext context,
            String identityProvider,
            String externalUserId,
            String mfaLevel,
            String device,
            String requestId,
            String traceId,
            String failureReason) {
        requireSystemContext(context);
        return auditService.selectList(Wrappers.<AuditEvent>lambdaQuery()
                        .eq(AuditEvent::getSystemId, context.systemId())
                        .eq(AuditEvent::getTenantId, context.tenantId())
                        .eq(AuditEvent::getEventCode, "SYSTEM_IDENTITY_LOGIN_MAPPING")
                        .eq(hasText(traceId), AuditEvent::getTraceId, strip(traceId))
                        .eq(hasText(requestId), AuditEvent::getRequestId, strip(requestId))
                        .orderByDesc(AuditEvent::getOccurredAt, AuditEvent::getId)
                        .last("LIMIT 500"))
                .stream().map(this::logView)
                .filter(log -> matches(log.identityProvider(), identityProvider))
                .filter(log -> matches(log.externalUserId(), externalUserId))
                .filter(log -> matches(log.mfaLevel(), mfaLevel))
                .filter(log -> contains(log.device(), device))
                .filter(log -> contains(log.failureReason(), failureReason))
                .toList();
    }

    private IdentityMappingPreflightItem plan(
            AuthenticatedContext context,
            MappingState state,
            ProviderSelection provider,
            IdentityMappingSample sample,
            int rowNumber) {
        List<String> reasons = new ArrayList<>();
        String externalDepartment = sample.externalDepartment().strip();
        Long departmentId = state.departmentMappings().get(externalDepartment);
        SystemDepartment department = departmentId == null ? null : departmentService.selectById(departmentId);
        if (department == null || !context.systemId().equals(department.getSystemId())
                || !context.tenantId().equals(department.getTenantId()) || !"ACTIVE".equals(department.getStatus())) {
            reasons.add("DEPARTMENT_UNMAPPED");
            department = null;
            departmentId = null;
        }

        Map<Long, Set<String>> matchedAccounts = resolveAccounts(context, state, provider, sample);
        if (matchedAccounts.size() > 1) {
            reasons.add("MAPPING_CONFLICT:" + matchedAccounts);
        }
        Long accountId = matchedAccounts.size() == 1 ? matchedAccounts.keySet().iterator().next() : null;
        SystemMember member = accountId == null ? null : first(memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, context.systemId())
                .eq(SystemMember::getAccountId, accountId)));
        if (member != null && !"ACTIVE".equals(member.getStatus())) {
            reasons.add("SYSTEM_MEMBER_DISABLED");
        }
        SystemTenantMember tenantMember = member == null ? null : first(tenantMemberService.selectList(
                Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, member.getId())));

        String outcome;
        String action;
        if (!reasons.isEmpty()) {
            outcome = matchedAccounts.size() > 1 ? "CONFLICT" : "MANUAL_REVIEW";
            action = "MANUAL_REVIEW";
        } else if (accountId == null && "CREATE_ACCOUNT_AND_MEMBER".equals(state.jitPolicy())) {
            outcome = "READY";
            action = "CREATE_ACCOUNT_AND_MEMBER";
        } else if (accountId == null) {
            outcome = "MANUAL_REVIEW";
            action = "MANUAL_REVIEW";
            reasons.add("ACCOUNT_NOT_FOUND");
        } else if (member == null && "ACCESS_REQUEST".equals(state.jitPolicy())) {
            outcome = "ACCESS_REQUEST";
            action = "CREATE_ACCESS_REQUEST";
        } else if (member == null) {
            outcome = "READY";
            action = "CREATE_MEMBER";
        } else if (tenantMember == null) {
            outcome = "READY";
            action = "CREATE_TENANT_MEMBERSHIP";
        } else if (!"ACTIVE".equals(tenantMember.getStatus())
                || !departmentId.equals(tenantMember.getDepartmentId())) {
            outcome = "READY";
            action = "UPDATE_TENANT_MEMBERSHIP";
        } else {
            outcome = "READY";
            action = "BIND_EXISTING_MEMBER";
        }
        return new IdentityMappingPreflightItem(rowNumber, sample.externalUserId().strip(), externalDepartment,
                strip(sample.email()), strip(sample.mobile()), strip(sample.employeeNo()), sample.displayName().strip(),
                defaultValue(sample.mfaLevel(), "UNKNOWN"), defaultValue(sample.device(), "UNKNOWN"),
                defaultValue(sample.requestId(), "identity-" + UUID.randomUUID()), accountId,
                member == null ? null : member.getId(), tenantMember == null ? null : tenantMember.getId(), departmentId,
                department == null ? null : department.getName(), outcome, action, List.copyOf(reasons));
    }

    private Map<Long, Set<String>> resolveAccounts(
            AuthenticatedContext context,
            MappingState state,
            ProviderSelection provider,
            IdentityMappingSample sample) {
        Map<Long, Set<String>> matches = new LinkedHashMap<>();
        for (String key : state.matchOrder()) {
            switch (key) {
                case "EXTERNAL_USER_ID" -> identityService.selectList(Wrappers.<PlatSsoIdentity>lambdaQuery()
                                .eq(PlatSsoIdentity::getProviderId, provider.provider().getId())
                                .eq(PlatSsoIdentity::getExternalSubject, sample.externalUserId().strip()))
                        .forEach(identity -> addMatch(matches, identity.getAccountId(), key));
                case "EMAIL" -> {
                    if (hasText(sample.email())) {
                        accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                                        .eq(PlatformAccount::getEmail, sample.email().strip().toLowerCase(Locale.ROOT))
                                        .eq(PlatformAccount::getStatus, "ACTIVE"))
                                .forEach(account -> addMatch(matches, account.getId(), key));
                    }
                }
                case "MOBILE" -> {
                    if (hasText(sample.mobile())) {
                        accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                                        .eq(PlatformAccount::getMobile, sample.mobile().strip())
                                        .eq(PlatformAccount::getStatus, "ACTIVE"))
                                .forEach(account -> addMatch(matches, account.getId(), key));
                    }
                }
                case "EMPLOYEE_NO" -> {
                    if (hasText(sample.employeeNo())) {
                        memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                                        .eq(SystemMember::getSystemId, context.systemId())
                                        .eq(SystemMember::getEmployeeNumber, sample.employeeNo().strip()))
                                .forEach(member -> addMatch(matches, member.getAccountId(), key));
                    }
                }
                default -> throw new DomainException("IDENTITY_MATCH_KEY_INVALID", "身份匹配顺序包含未知字段", HttpStatus.BAD_REQUEST);
            }
        }
        return matches;
    }

    private IdentityMappingJobItemView execute(
            AuthenticatedContext context,
            MappingState state,
            ProviderSelection provider,
            Long jobId,
            IdentityMappingPreflightItem item,
            String traceId) {
        if ("MANUAL_REVIEW".equals(item.plannedAction())) {
            String reason = String.join(",", item.reasons());
            recordAudit(context, traceId, item.requestId(), "SYSTEM_IDENTITY_LOGIN_MAPPING", item.outcome(),
                    provider.provider().getCode(), item.externalUserId(), item.mfaLevel(), item.device(), reason,
                    item.systemMemberId(), jobId, null);
            return new IdentityMappingJobItemView(item.rowNumber(), item.externalUserId(), "SKIPPED",
                    item.plannedAction(), item.accountId(), item.systemMemberId(), item.tenantMemberId(), null,
                    item.outcome(), reason);
        }

        Long accountId = item.accountId();
        Long memberId = item.systemMemberId();
        Long tenantMemberId = item.tenantMemberId();
        Long accessRequestId = null;
        switch (item.plannedAction()) {
            case "CREATE_ACCOUNT_AND_MEMBER" -> {
                PlatformAccount account = createAccount(provider.provider(), item);
                accountId = account.getId();
                ensureIdentity(provider.provider().getId(), item.externalUserId(), accountId, item);
                SystemMember member = createMember(context, accountId, item);
                memberId = member.getId();
                tenantMemberId = createTenantMember(context, memberId, item.departmentId()).getId();
            }
            case "CREATE_MEMBER" -> {
                ensureIdentity(provider.provider().getId(), item.externalUserId(), accountId, item);
                SystemMember member = createMember(context, accountId, item);
                memberId = member.getId();
                tenantMemberId = createTenantMember(context, memberId, item.departmentId()).getId();
            }
            case "CREATE_TENANT_MEMBERSHIP" -> {
                ensureIdentity(provider.provider().getId(), item.externalUserId(), accountId, item);
                tenantMemberId = createTenantMember(context, memberId, item.departmentId()).getId();
            }
            case "UPDATE_TENANT_MEMBERSHIP", "BIND_EXISTING_MEMBER" -> {
                ensureIdentity(provider.provider().getId(), item.externalUserId(), accountId, item);
                SystemTenantMember membership = tenantMemberService.selectById(tenantMemberId);
                membership.setDepartmentId(item.departmentId());
                membership.setStatus("ACTIVE");
                if (tenantMemberService.updateById(membership) != 1) {
                    throw conflict("成员绑定状态已变化，请重新预检");
                }
            }
            case "CREATE_ACCESS_REQUEST" -> {
                ensureIdentity(provider.provider().getId(), item.externalUserId(), accountId, item);
                SysAccessRequest request = ensureAccessRequest(context, provider.provider(), item, accountId, traceId);
                accessRequestId = request.getId();
            }
            default -> throw new DomainException("IDENTITY_SYNC_ACTION_INVALID", "预检产生了未知同步动作", HttpStatus.CONFLICT);
        }
        String resultCode = accessRequestId == null ? "SUCCESS" : "ACCESS_REQUEST_PENDING";
        recordAudit(context, traceId, item.requestId(), "SYSTEM_IDENTITY_LOGIN_MAPPING", resultCode,
                provider.provider().getCode(), item.externalUserId(), item.mfaLevel(), item.device(), null,
                memberId, jobId, null);
        return new IdentityMappingJobItemView(item.rowNumber(), item.externalUserId(), "SUCCEEDED",
                item.plannedAction(), accountId, memberId, tenantMemberId, accessRequestId, null,
                accessRequestId == null ? "成员映射已确认" : "未建立系统成员，已创建访问申请");
    }

    private PlatformAccount createAccount(PlatSsoProvider provider, IdentityMappingPreflightItem item) {
        PlatformAccount account = new PlatformAccount();
        account.setUsername(uniqueUsername(provider.getCode(), item.externalUserId()));
        account.setEmail(lower(item.email()));
        account.setMobile(strip(item.mobile()));
        account.setDisplayName(item.displayName());
        account.setLocale("zh-CN");
        account.setTimezone("Asia/Shanghai");
        account.setStatus("ACTIVE");
        accountService.insert(account);
        return account;
    }

    private String uniqueUsername(String providerCode, String externalUserId) {
        String stem = (providerCode + "_" + externalUserId).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        if (stem.length() > 90) {
            stem = stem.substring(0, 90);
        }
        String candidate = stem;
        int suffix = 1;
        while (!accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                .eq(PlatformAccount::getUsername, candidate)).isEmpty()) {
            candidate = stem + "_" + suffix++;
        }
        return candidate;
    }

    private void ensureIdentity(Long providerId, String externalUserId, Long accountId, IdentityMappingPreflightItem item) {
        List<PlatSsoIdentity> identities = identityService.selectList(Wrappers.<PlatSsoIdentity>lambdaQuery()
                .eq(PlatSsoIdentity::getProviderId, providerId)
                .eq(PlatSsoIdentity::getExternalSubject, externalUserId));
        if (!identities.isEmpty()) {
            if (!accountId.equals(identities.getFirst().getAccountId())) {
                throw new DomainException("IDENTITY_MAPPING_CONFLICT", "外部身份已绑定其他平台账号", HttpStatus.CONFLICT);
            }
            return;
        }
        PlatSsoIdentity identity = new PlatSsoIdentity();
        identity.setProviderId(providerId);
        identity.setExternalSubject(externalUserId);
        identity.setAccountId(accountId);
        identity.setAttributesJson(toJson(Map.of("email", defaultValue(item.email(), ""),
                "mobile", defaultValue(item.mobile(), ""), "employeeNo", defaultValue(item.employeeNo(), ""),
                "externalDepartment", item.externalDepartment())));
        identityService.insert(identity);
    }

    private SystemMember createMember(AuthenticatedContext context, Long accountId, IdentityMappingPreflightItem item) {
        SystemMember member = new SystemMember();
        member.setSystemId(context.systemId());
        member.setAccountId(accountId);
        member.setEmployeeNumber(strip(item.employeeNo()));
        member.setDisplayName(item.displayName());
        member.setStatus("ACTIVE");
        member.setJoinedAt(LocalDateTime.now());
        memberService.insert(member);
        return member;
    }

    private SystemTenantMember createTenantMember(AuthenticatedContext context, Long memberId, Long departmentId) {
        SystemTenantMember membership = new SystemTenantMember();
        membership.setSystemId(context.systemId());
        membership.setTenantId(context.tenantId());
        membership.setSystemMemberId(memberId);
        membership.setDepartmentId(departmentId);
        membership.setTenantAdmin(false);
        membership.setStatus("ACTIVE");
        tenantMemberService.insert(membership);
        return membership;
    }

    private SysAccessRequest ensureAccessRequest(
            AuthenticatedContext context,
            PlatSsoProvider provider,
            IdentityMappingPreflightItem item,
            Long accountId,
            String traceId) {
        List<SysAccessRequest> pending = accessRequestService.selectList(Wrappers.<SysAccessRequest>lambdaQuery()
                .eq(SysAccessRequest::getSystemId, context.systemId())
                .eq(SysAccessRequest::getTenantId, context.tenantId())
                .eq(SysAccessRequest::getAccountId, accountId)
                .eq(SysAccessRequest::getStatus, "PENDING"));
        if (!pending.isEmpty()) {
            return pending.getFirst();
        }
        SysAccessRequest request = new SysAccessRequest();
        request.setSystemId(context.systemId());
        request.setTenantId(context.tenantId());
        request.setAccountId(accountId);
        request.setIdentityProvider(provider.getCode());
        request.setExternalUserId(item.externalUserId());
        request.setRequestReason("企业身份映射未获得 systemMemberId，等待系统管理员授权");
        request.setRequestedRole(null);
        request.setStatus("PENDING");
        request.setRequestTraceId(traceId);
        accessRequestService.insert(request);
        return request;
    }

    private SystemIdentityProviderOption providerOption(PlatSsoProvider provider) {
        PlatSsoProviderVersion version = providerVersionService.selectById(provider.getPublishedVersionId());
        if (version == null || !provider.getId().equals(version.getProviderId()) || !"PUBLISHED".equals(version.getStatus())) {
            throw new DomainException("SSO_PROVIDER_PUBLICATION_INVALID", "平台身份源发布版本无效", HttpStatus.CONFLICT);
        }
        return new SystemIdentityProviderOption(provider.getId(), provider.getCode(), provider.getName(),
                provider.getProtocol(), version.getId(), version.getVersionNumber(), allowedDomains(version));
    }

    private ProviderSelection requirePublishedProvider(Long providerId, Long versionId) {
        PlatSsoProvider provider = providerService.selectById(providerId);
        PlatSsoProviderVersion version = providerVersionService.selectById(versionId);
        if (provider == null || !"PUBLISHED".equals(provider.getStatus())
                || !versionId.equals(provider.getPublishedVersionId()) || version == null
                || !providerId.equals(version.getProviderId()) || !"PUBLISHED".equals(version.getStatus())) {
            throw new DomainException("SSO_PROVIDER_NOT_ALLOWED", "系统只能继承平台允许的已发布身份源版本", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return new ProviderSelection(provider, version);
    }

    private void validateDomain(PlatSsoProviderVersion version, String tenantDomain) {
        List<String> allowed = allowedDomains(version).stream().map(this::normalizeDomain).toList();
        if (!allowed.isEmpty() && allowed.stream().noneMatch(domain -> tenantDomain.equals(domain)
                || tenantDomain.endsWith("." + domain))) {
            throw new DomainException("SSO_TENANT_DOMAIN_NOT_ALLOWED", "租户域名不在平台身份源允许范围内", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private List<String> allowedDomains(PlatSsoProviderVersion version) {
        try {
            return objectMapper.readValue(version.getAllowedDomainsJson(), new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new DomainException("SSO_PROVIDER_CONFIG_INVALID", "平台身份源允许域名配置无法读取", HttpStatus.CONFLICT);
        }
    }

    private Map<String, Long> normalizeDepartmentMappings(AuthenticatedContext context, Map<String, Long> mappings) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : mappings.entrySet()) {
            String external = entry.getKey().strip();
            SystemDepartment department = departmentService.selectById(entry.getValue());
            if (department == null || !context.systemId().equals(department.getSystemId())
                    || !context.tenantId().equals(department.getTenantId()) || !"ACTIVE".equals(department.getStatus())) {
                throw new DomainException("IDENTITY_DEPARTMENT_INVALID", "部门映射只能指向当前租户的有效部门", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            result.put(external, entry.getValue());
        }
        return Map.copyOf(result);
    }

    private List<String> normalizeMatchOrder(List<String> input) {
        List<String> result = input.stream().map(value -> value.strip().toUpperCase(Locale.ROOT)).toList();
        if (new LinkedHashSet<>(result).size() != result.size() || !MATCH_KEYS.containsAll(result)) {
            throw new DomainException("IDENTITY_MATCH_ORDER_INVALID", "身份匹配字段必须有效且不能重复", HttpStatus.BAD_REQUEST);
        }
        return List.copyOf(result);
    }

    private SystemIdentityMappingView view(AuthenticatedContext context, CoreSetting setting) {
        MappingState state = readState(setting);
        PlatSsoProvider provider = providerService.selectById(state.providerId());
        return new SystemIdentityMappingView(setting.getId(), setting.getVersion(), context.systemId(), context.tenantId(),
                state.providerId(), state.providerVersionId(), provider == null ? null : provider.getCode(),
                provider == null ? null : provider.getName(), state.tenantDomain(), state.departmentMappings(),
                state.matchOrder(), state.jitPolicy(), state.latestPreflight(), state.confirmedJobId(),
                state.confirmedAt(), setting.getUpdatedAt());
    }

    private IdentityMappingJobView jobView(AuthenticatedContext context, JobBackground job) {
        if (job == null || !context.systemId().equals(job.getSystemId()) || !context.tenantId().equals(job.getTenantId())) {
            throw new DomainException("IDENTITY_SYNC_JOB_NOT_FOUND", "身份同步任务不存在", HttpStatus.NOT_FOUND);
        }
        List<IdentityMappingJobItemView> items = jobItemService.selectList(Wrappers.<JobItemResult>lambdaQuery()
                        .eq(JobItemResult::getJobId, job.getId())
                        .orderByAsc(JobItemResult::getRowNumber, JobItemResult::getId))
                .stream().map(item -> read(item.getResultJson(), IdentityMappingJobItemView.class)).toList();
        Map<String, Object> summary = job.getResultSummaryJson() == null
                ? Map.of() : read(job.getResultSummaryJson(), new TypeReference<>() {
                });
        return new IdentityMappingJobView(job.getId(), job.getStatus(), defaultLong(job.getProgressCurrent()),
                defaultLong(job.getProgressTotal()), summary, items, job.getCreatedAt(), job.getFinishedAt());
    }

    private IdentityMappingLogView logView(AuditEvent event) {
        Map<String, Object> detail = read(event.getDetailJson(), new TypeReference<>() {
        });
        return new IdentityMappingLogView(event.getId(), event.getOccurredAt(), string(detail.get("identityProvider")),
                string(detail.get("externalUserId")), string(detail.get("mfaLevel")), string(detail.get("device")),
                event.getRequestId(), event.getTraceId(), event.getResultCode(), string(detail.get("failureReason")),
                longValue(detail.get("systemMemberId")), longValue(detail.get("jobId")));
    }

    private void recordAudit(
            AuthenticatedContext context,
            String traceId,
            String requestId,
            String eventCode,
            String resultCode,
            String identityProvider,
            String externalUserId,
            String mfaLevel,
            String device,
            String failureReason,
            Long systemMemberId,
            Long jobId,
            Long settingId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("identityProvider", identityProvider);
        detail.put("externalUserId", externalUserId);
        detail.put("mfaLevel", mfaLevel);
        detail.put("device", device);
        detail.put("requestId", requestId);
        detail.put("failureReason", failureReason);
        detail.put("systemMemberId", systemMemberId);
        detail.put("jobId", jobId);
        detail.put("settingId", settingId);
        AuditEvent event = new AuditEvent();
        event.setTraceId(traceId);
        event.setRequestId(requestId);
        event.setContextType("SYSTEM");
        event.setActorAccountId(context.accountId());
        event.setPlatformId(context.platformId());
        event.setSystemId(context.systemId());
        event.setTenantId(context.tenantId());
        event.setMemberId(context.memberId());
        event.setEventCategory(eventCode.contains("LOGIN") ? "SECURITY" : "CONFIGURATION");
        event.setEventCode(eventCode);
        event.setObjectType(jobId == null ? "SYSTEM_IDENTITY_MAPPING" : "IDENTITY_MAPPING_JOB");
        event.setObjectId(jobId == null ? (settingId == null ? null : settingId.toString()) : jobId.toString());
        event.setResultCode(resultCode);
        event.setDetailJson(toJson(detail));
        auditService.insert(event);
    }

    private CoreSetting findSetting(AuthenticatedContext context) {
        return first(settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                .eq(CoreSetting::getContextType, "SYSTEM")
                .eq(CoreSetting::getPlatformId, context.platformId())
                .eq(CoreSetting::getSystemId, context.systemId())
                .eq(CoreSetting::getTenantId, context.tenantId())
                .eq(CoreSetting::getCategory, SETTING_CATEGORY)
                .eq(CoreSetting::getSettingKey, SETTING_KEY)
                .eq(CoreSetting::getStatus, "ACTIVE")
                .orderByDesc(CoreSetting::getId)));
    }

    private CoreSetting requireSetting(AuthenticatedContext context) {
        CoreSetting setting = findSetting(context);
        if (setting == null) {
            throw new DomainException("IDENTITY_MAPPING_NOT_CONFIGURED", "请先保存身份继承映射草稿", HttpStatus.NOT_FOUND);
        }
        return setting;
    }

    private MappingState readState(CoreSetting setting) {
        return read(setting.getValueJson(), MappingState.class);
    }

    private void requireVersion(CoreSetting setting, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(setting.getVersion())) {
            throw conflict("身份映射已被其他管理员修改，请刷新后重试");
        }
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.platformId() == null || context.systemId() == null || context.tenantId() == null
                || context.memberId() == null || context.tenantMemberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统后台", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeDomain(String value) {
        String domain = value.strip().toLowerCase(Locale.ROOT);
        if (domain.startsWith("http://") || domain.startsWith("https://") || domain.contains("/") || domain.contains(" ")) {
            throw new DomainException("SSO_TENANT_DOMAIN_INVALID", "租户域名只能填写主机名", HttpStatus.BAD_REQUEST);
        }
        return domain;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException("IDENTITY_MAPPING_VALUE_INVALID", "身份映射数据无法保存", HttpStatus.BAD_REQUEST);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new DomainException("IDENTITY_MAPPING_VALUE_INVALID", "身份映射数据无法读取：" + exception.getOriginalMessage(),
                    HttpStatus.CONFLICT);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new DomainException("IDENTITY_MAPPING_VALUE_INVALID", "身份映射数据无法读取：" + exception.getOriginalMessage(),
                    HttpStatus.CONFLICT);
        }
    }

    private DomainException conflict(String message) {
        return new DomainException("CONCURRENT_MODIFICATION", message, HttpStatus.CONFLICT);
    }

    private void addMatch(Map<Long, Set<String>> matches, Long accountId, String source) {
        if (accountId != null) {
            matches.computeIfAbsent(accountId, ignored -> new LinkedHashSet<>()).add(source);
        }
    }

    private <T> T first(List<T> values) {
        return values.isEmpty() ? null : values.getFirst();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String strip(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String lower(String value) {
        String stripped = strip(value);
        return stripped == null ? null : stripped.toLowerCase(Locale.ROOT);
    }

    private String defaultValue(String value, String fallback) {
        return hasText(value) ? value.strip() : fallback;
    }

    private boolean matches(String actual, String expected) {
        return !hasText(expected) || expected.strip().equalsIgnoreCase(actual);
    }

    private boolean contains(String actual, String expected) {
        return !hasText(expected) || actual != null && actual.toLowerCase(Locale.ROOT)
                .contains(expected.strip().toLowerCase(Locale.ROOT));
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private long defaultLong(Long value) {
        return value == null ? 0 : value;
    }

    private record ProviderSelection(PlatSsoProvider provider, PlatSsoProviderVersion version) {
    }

    private record MappingState(
            Long providerId,
            Long providerVersionId,
            String tenantDomain,
            Map<String, Long> departmentMappings,
            List<String> matchOrder,
            String jitPolicy,
            IdentityMappingPreflightReport latestPreflight,
            Long confirmedJobId,
            String confirmedAt) {
    }
}
