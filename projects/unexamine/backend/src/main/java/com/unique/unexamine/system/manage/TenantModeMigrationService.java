package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.backgroundjobs.base.entity.JobBackground;
import com.unique.unexamine.backgroundjobs.base.entity.JobItemResult;
import com.unique.unexamine.backgroundjobs.base.service.JobBackgroundBaseService;
import com.unique.unexamine.backgroundjobs.base.service.JobItemResultBaseService;
import com.unique.unexamine.foundation.base.entity.CoreCacheEpoch;
import com.unique.unexamine.foundation.base.service.CoreCacheEpochBaseService;
import com.unique.unexamine.runtimedata.base.entity.BizTenantShare;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.service.BizTenantShareBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SysTenantMigration;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SysTenantMigrationBaseService;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TenantModeMigrationService {
    private static final List<String> STEPS = List.of(
            "检查默认主租户和全部租户上下文",
            "检查业务数据、共享和非主租户冲突",
            "检查唯一约束与应用授权的租户维度",
            "更新模式并重建权限、模块和业务壳缓存");

    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final BusinessRecordBaseService recordService;
    private final BizTenantShareBaseService shareService;
    private final SysTenantMigrationBaseService migrationService;
    private final JobBackgroundBaseService jobService;
    private final JobItemResultBaseService itemService;
    private final CoreCacheEpochBaseService cacheEpochService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public TenantModeMigrationService(
            SystemDefinitionBaseService systemService,
            SystemTenantBaseService tenantService,
            BusinessRecordBaseService recordService,
            BizTenantShareBaseService shareService,
            SysTenantMigrationBaseService migrationService,
            JobBackgroundBaseService jobService,
            JobItemResultBaseService itemService,
            CoreCacheEpochBaseService cacheEpochService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.recordService = recordService;
        this.shareService = shareService;
        this.migrationService = migrationService;
        this.jobService = jobService;
        this.itemService = itemService;
        this.cacheEpochService = cacheEpochService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TenantModeMigrationPreflight preflight(AuthenticatedContext context, String toMode) {
        SystemDefinition system = requireSystem(context);
        return inspect(system, toMode);
    }

    @Transactional(readOnly = true)
    public List<TenantModeMigrationView> list(AuthenticatedContext context) {
        requireSystem(context);
        return migrationService.selectList(Wrappers.<SysTenantMigration>lambdaQuery()
                        .eq(SysTenantMigration::getSystemId, context.systemId()))
                .stream().sorted(Comparator.comparing(SysTenantMigration::getId).reversed())
                .map(this::view).toList();
    }

    @Transactional
    public TenantModeMigrationView request(
            AuthenticatedContext context, TenantModeMigrationRequest input, String traceId) {
        SystemDefinition system = requireSystem(context);
        TenantModeMigrationPreflight preflight = inspect(system, input.toMode());
        if (!preflight.allowed()) {
            throw new DomainException("TENANT_MODE_PREFLIGHT_FAILED",
                    "租户模式发布检查未通过：" + String.join("；", preflight.blockers()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean pending = migrationService.selectList(Wrappers.<SysTenantMigration>lambdaQuery()
                        .eq(SysTenantMigration::getSystemId, context.systemId())
                        .in(SysTenantMigration::getStatus, List.of("PENDING_APPROVAL", "RUNNING")))
                .stream().findAny().isPresent();
        if (pending) {
            throw new DomainException("TENANT_MODE_MIGRATION_EXISTS", "已有待审批或执行中的租户模式迁移",
                    HttpStatus.CONFLICT);
        }
        SysTenantMigration migration = new SysTenantMigration();
        migration.setSystemId(context.systemId());
        migration.setFromMode(system.getTenantMode());
        migration.setToMode(input.toMode());
        migration.setImpactSnapshotJson(toJson(Map.of(
                "impact", preflight.impact(), "blockers", preflight.blockers(), "steps", preflight.migrationSteps())));
        migration.setStatus("PENDING_APPROVAL");
        migration.setRequestedByMemberId(context.memberId());
        migrationService.insert(migration);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_MODE_MIGRATION_REQUESTED", "TENANT_MIGRATION", migration.getId().toString(),
                "PENDING_APPROVAL", Map.of("fromMode", migration.getFromMode(), "toMode", migration.getToMode(),
                        "impact", preflight.impact()));
        return view(migrationService.selectById(migration.getId()));
    }

    @Transactional
    public TenantModeMigrationView decide(
            AuthenticatedContext context,
            Long migrationId,
            TenantModeMigrationDecisionRequest input,
            String traceId) {
        SystemDefinition system = requireSystem(context);
        SysTenantMigration migration = requireMigration(context.systemId(), migrationId);
        if (!"PENDING_APPROVAL".equals(migration.getStatus())) {
            throw new DomainException("TENANT_MODE_MIGRATION_STATE_INVALID", "迁移申请已经处理", HttpStatus.CONFLICT);
        }
        if (!input.expectedVersion().equals(migration.getVersion())) {
            throw conflict();
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("approvedByMemberId", context.memberId());
        decision.put("comment", input.comment());
        decision.put("previousMode", migration.getFromMode());
        if (!input.approved()) {
            migration.setStatus("REJECTED");
            migration.setFinishedAt(LocalDateTime.now());
            migration.setRollbackSnapshotJson(toJson(decision));
            migrationService.updateById(migration);
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "TENANT_MODE_MIGRATION_DECIDED", "TENANT_MIGRATION", migrationId.toString(), "REJECTED",
                    decision);
            return view(migrationService.selectById(migrationId));
        }

        TenantModeMigrationPreflight current = inspect(system, migration.getToMode());
        if (!current.allowed()) {
            migration.setStatus("BLOCKED");
            migration.setFinishedAt(LocalDateTime.now());
            decision.put("blockers", current.blockers());
            migration.setRollbackSnapshotJson(toJson(decision));
            migrationService.updateById(migration);
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "TENANT_MODE_MIGRATION_DECIDED", "TENANT_MIGRATION", migrationId.toString(), "BLOCKED",
                    decision);
            return view(migrationService.selectById(migrationId));
        }

        JobBackground job = createJob(context, migration, current);
        migration.setJobId(job.getId());
        migration.setStatus("RUNNING");
        migration.setStartedAt(LocalDateTime.now());
        migration.setRollbackSnapshotJson(toJson(decision));
        migrationService.updateById(migration);

        addItem(job.getId(), 1, "MAIN_TENANT", "默认主租户和租户上下文有效");
        addItem(job.getId(), 2, "DATA_CONFLICTS", "业务数据与共享冲突检查通过");
        addItem(job.getId(), 3, "CONSTRAINTS_AND_APPLICATIONS", "唯一约束与应用授权均保持租户维度");
        system.setTenantMode(migration.getToMode());
        if (systemService.updateById(system) != 1) {
            throw conflict();
        }
        bumpCache(context.systemId(), "AUTHORIZATION", migrationId);
        bumpCache(context.systemId(), "MODULE_CATALOG", migrationId);
        bumpCache(context.systemId(), "SYSTEM_SHELL", migrationId);
        addItem(job.getId(), 4, "CACHE_REBUILD", "权限、模块目录和业务壳缓存版本已推进");

        LocalDateTime finishedAt = LocalDateTime.now();
        job.setStatus("SUCCEEDED");
        job.setProgressCurrent(4L);
        job.setResultSummaryJson(toJson(Map.of("fromMode", migration.getFromMode(),
                "toMode", migration.getToMode(), "cacheNamespaces", 3, "steps", 4)));
        job.setFinishedAt(finishedAt);
        jobService.updateById(job);
        migration.setStatus("COMPLETED");
        migration.setFinishedAt(finishedAt);
        migrationService.updateById(migration);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "TENANT_MODE_MIGRATION_COMPLETED", "TENANT_MIGRATION", migrationId.toString(), "SUCCESS",
                Map.of("jobId", job.getId(), "fromMode", migration.getFromMode(), "toMode", migration.getToMode(),
                        "approvedByMemberId", context.memberId()));
        return view(migrationService.selectById(migrationId));
    }

    private TenantModeMigrationPreflight inspect(SystemDefinition system, String toMode) {
        if (!List.of("SINGLE", "MULTI").contains(toMode)) {
            throw new DomainException("TENANT_MODE_INVALID", "租户模式无效", HttpStatus.BAD_REQUEST);
        }
        List<SystemTenant> tenants = tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                .eq(SystemTenant::getSystemId, system.getId()));
        SystemTenant main = tenants.stream().filter(item -> Boolean.TRUE.equals(item.getMain()))
                .findFirst().orElse(null);
        long secondaryTenants = tenants.stream().filter(item -> !Boolean.TRUE.equals(item.getMain())).count();
        long nonMainRecords = main == null ? 0 : recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, system.getId())
                .ne(BusinessRecord::getTenantId, main.getId())).size();
        long activeShares = shareService.selectList(Wrappers.<BizTenantShare>lambdaQuery()
                .eq(BizTenantShare::getSystemId, system.getId())
                .eq(BizTenantShare::getStatus, "ACTIVE")).size();
        List<String> blockers = new ArrayList<>();
        if (system.getTenantMode().equals(toMode)) {
            blockers.add("系统已经处于目标租户模式");
        }
        if (main == null || !"ACTIVE".equals(main.getStatus())) {
            blockers.add("默认主租户缺失或未启用");
        }
        if ("SINGLE".equals(toMode) && secondaryTenants > 0) {
            blockers.add("存在其他租户，必须先执行独立租户合并方案");
        }
        if ("SINGLE".equals(toMode) && nonMainRecords > 0) {
            blockers.add("存在非主租户业务数据");
        }
        if ("SINGLE".equals(toMode) && activeShares > 0) {
            blockers.add("存在有效跨租户共享");
        }
        Map<String, Long> impact = new LinkedHashMap<>();
        impact.put("tenantCount", (long) tenants.size());
        impact.put("secondaryTenantCount", secondaryTenants);
        impact.put("nonMainRecordCount", nonMainRecords);
        impact.put("activeShareCount", activeShares);
        return new TenantModeMigrationPreflight(system.getTenantMode(), toMode, blockers.isEmpty(), blockers,
                impact, STEPS);
    }

    private JobBackground createJob(
            AuthenticatedContext context, SysTenantMigration migration, TenantModeMigrationPreflight preflight) {
        JobBackground job = new JobBackground();
        job.setContextType("SYSTEM");
        job.setPlatformId(context.platformId());
        job.setSystemId(context.systemId());
        job.setTenantId(context.tenantId());
        job.setJobType("TENANT_MODE_MIGRATION");
        job.setSourceType("TENANT_MIGRATION");
        job.setSourceId(migration.getId().toString());
        job.setParameterJson(toJson(Map.of("fromMode", migration.getFromMode(), "toMode", migration.getToMode(),
                "impact", preflight.impact())));
        job.setAuthorizationSnapshotJson(toJson(Map.of("memberId", context.memberId(), "roleIds", context.roleIds())));
        job.setStatus("RUNNING");
        job.setProgressCurrent(0L);
        job.setProgressTotal(4L);
        job.setMaxAttempts(1);
        job.setAttemptCount(1);
        job.setCreatedByAccountId(context.accountId());
        job.setStartedAt(LocalDateTime.now());
        jobService.insert(job);
        return job;
    }

    private void addItem(Long jobId, long row, String key, String message) {
        JobItemResult item = new JobItemResult();
        item.setJobId(jobId);
        item.setItemKey(key);
        item.setRowNumber(row);
        item.setStatus("SUCCEEDED");
        item.setResultJson(toJson(Map.of("message", message)));
        itemService.insert(item);
        JobBackground job = jobService.selectById(jobId);
        job.setProgressCurrent(row);
        job.setHeartbeatAt(LocalDateTime.now());
        jobService.updateById(job);
    }

    private void bumpCache(Long systemId, String namespace, Long migrationId) {
        String contextKey = "system:" + systemId;
        CoreCacheEpoch epoch = cacheEpochService.selectList(Wrappers.<CoreCacheEpoch>lambdaQuery()
                        .eq(CoreCacheEpoch::getContextKey, contextKey)
                        .eq(CoreCacheEpoch::getCacheNamespace, namespace))
                .stream().findFirst().orElse(null);
        if (epoch == null) {
            epoch = new CoreCacheEpoch();
            epoch.setContextKey(contextKey);
            epoch.setCacheNamespace(namespace);
            epoch.setEpochValue(1L);
            epoch.setReason("tenant-mode-migration:" + migrationId);
            cacheEpochService.insert(epoch);
        } else {
            epoch.setEpochValue(epoch.getEpochValue() + 1);
            epoch.setReason("tenant-mode-migration:" + migrationId);
            cacheEpochService.updateById(epoch);
        }
    }

    private SystemDefinition requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统后台", HttpStatus.BAD_REQUEST);
        }
        SystemDefinition system = systemService.selectById(context.systemId());
        if (system == null || Boolean.TRUE.equals(system.getDeleted())) {
            throw new DomainException("SYSTEM_NOT_FOUND", "系统不存在", HttpStatus.NOT_FOUND);
        }
        return system;
    }

    private SysTenantMigration requireMigration(Long systemId, Long migrationId) {
        SysTenantMigration migration = migrationService.selectById(migrationId);
        if (migration == null || !systemId.equals(migration.getSystemId())) {
            throw new DomainException("TENANT_MODE_MIGRATION_NOT_FOUND", "租户模式迁移不存在", HttpStatus.NOT_FOUND);
        }
        return migration;
    }

    private TenantModeMigrationView view(SysTenantMigration migration) {
        return new TenantModeMigrationView(migration.getId(), migration.getFromMode(), migration.getToMode(),
                migration.getStatus(), migration.getJobId(), migration.getRequestedByMemberId(),
                migration.getImpactSnapshotJson(), migration.getRollbackSnapshotJson(), migration.getStartedAt(),
                migration.getFinishedAt(), migration.getVersion());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException("TENANT_MODE_MIGRATION_VALUE_INVALID", "租户模式迁移数据无法保存",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private DomainException conflict() {
        return new DomainException("CONCURRENT_MODIFICATION", "租户模式迁移已被其他操作修改，请刷新后重试",
                HttpStatus.CONFLICT);
    }
}
