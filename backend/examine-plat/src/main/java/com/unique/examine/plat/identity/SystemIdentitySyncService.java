package com.unique.examine.plat.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.ClientRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SystemIdentitySyncService {
    static final String JOB_TYPE = "SYSTEM_IDENTITY_SYNC";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final int MAX_ITEMS = 5_000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdService ids;
    private final DurableJobFacade jobs;
    private final AuditFacade audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public SystemIdentitySyncService(JdbcTemplate jdbc, ObjectMapper json, IdService ids,
                                     DurableJobFacade jobs, AuditFacade audit, Clock clock,
                                     PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.ids = ids;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<SystemIdentitySyncApi.InheritedProvider> providers(long systemId) {
        requireSystem(systemId);
        return jdbc.query("""
                SELECT id,provider_code,name,protocol,allowed_domains_json,jit_account,mfa_policy,version
                FROM un_plat_identity_provider
                WHERE status='PUBLISHED' AND system_id IS NULL AND tenant_id IS NULL
                ORDER BY name,id
                """, (r, row) -> new SystemIdentitySyncApi.InheritedProvider(
                text(r.getLong("id")), r.getString("provider_code"), r.getString("name"),
                r.getString("protocol"), strings(r.getString("allowed_domains_json")),
                r.getBoolean("jit_account"), r.getString("mfa_policy"), r.getLong("version")));
    }

    @Transactional(readOnly = true)
    public List<SystemIdentitySyncApi.PolicyView> policies(long systemId, Long tenantId) {
        requireSystem(systemId);
        var sql = """
                SELECT p.*,i.provider_code,i.name provider_name
                FROM un_plat_system_identity_policy p
                JOIN un_plat_identity_provider i ON i.id=p.provider_id
                WHERE p.system_id=?
                """ + (tenantId == null ? "" : " AND p.tenant_id=?") + " ORDER BY p.updated_at DESC,p.id DESC";
        return tenantId == null ? jdbc.query(sql, this::policyView, systemId)
                : jdbc.query(sql, this::policyView, systemId, tenantId);
    }

    @Transactional
    public SystemIdentitySyncApi.PolicyView savePolicy(AuthenticatedSession session, long systemId,
                                                        SystemIdentitySyncApi.PolicyCommand raw,
                                                        ClientRequest request) {
        Objects.requireNonNull(raw, "policy command is required");
        var tenantId = id(raw.tenantId(), "tenantId");
        var providerId = id(raw.providerId(), "providerId");
        requireTenant(systemId, tenantId);
        var provider = provider(providerId);
        if (provider.systemId() != null || !"PUBLISHED".equals(provider.status())) {
            throw conflict("IDENTITY_PROVIDER_NOT_INHERITABLE", "只能继承平台已发布身份源");
        }
        var domains = domains(raw.allowedDomains());
        validateDomains(systemId, tenantId, provider.allowedDomains(), domains);
        var unmatched = raw.unmatchedAction() == null ? "REQUIRE_REVIEW"
                : raw.unmatchedAction().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("REQUIRE_REVIEW", "SKIP").contains(unmatched)) {
            throw validation("unmatchedAction 无效");
        }
        Integer interval = null;
        Instant next = null;
        if (raw.scheduleEnabled()) {
            interval = raw.scheduleIntervalMinutes();
            if (interval == null || interval < 15 || interval > 10_080) {
                throw validation("计划同步间隔必须在 15..10080 分钟之间");
            }
            next = now().plus(Duration.ofMinutes(interval));
        }
        var existing = policyByScope(systemId, tenantId, providerId);
        long policyId;
        if (existing == null) {
            if (raw.expectedVersion() != null && raw.expectedVersion() != 0) throw versionConflict();
            policyId = ids.nextId();
            jdbc.update("""
                    INSERT INTO un_plat_system_identity_policy(
                      id,system_id,tenant_id,provider_id,allowed_domains_json,jit_system_member,
                      unmatched_action,schedule_enabled,schedule_interval_minutes,next_sync_at,status,
                      created_at,created_by,updated_at,updated_by,version)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,0)
                    """, policyId, systemId, tenantId, providerId, write(domains), raw.jitSystemMember(),
                    unmatched, raw.scheduleEnabled(), interval, timestamp(next), timestamp(now()),
                    session.accountId(), timestamp(now()), session.accountId());
        } else {
            if (raw.expectedVersion() == null || raw.expectedVersion() != existing.version()) {
                throw versionConflict();
            }
            policyId = existing.id();
            var changed = jdbc.update("""
                    UPDATE un_plat_system_identity_policy
                    SET allowed_domains_json=?,jit_system_member=?,unmatched_action=?,schedule_enabled=?,
                      schedule_interval_minutes=?,next_sync_at=?,status='DRAFT',updated_at=?,updated_by=?,
                      version=version+1
                    WHERE id=? AND system_id=? AND tenant_id=? AND version=?
                    """, write(domains), raw.jitSystemMember(), unmatched, raw.scheduleEnabled(), interval,
                    timestamp(next), timestamp(now()), session.accountId(), policyId, systemId, tenantId,
                    existing.version());
            if (changed != 1) throw versionConflict();
        }
        var result = requirePolicy(systemId, tenantId, policyId);
        security("SYSTEM_IDENTITY_POLICY_SAVED", session.accountId(), systemId, tenantId,
                "SUCCESS", null, request, Map.of("policyId", text(policyId), "providerId", text(providerId),
                        "version", result.version()));
        return result;
    }

    @Transactional
    public SystemIdentitySyncApi.SnapshotView preflight(AuthenticatedSession session, long systemId,
                                                         long policyId,
                                                         SystemIdentitySyncApi.PreflightCommand raw,
                                                         ClientRequest request) {
        Objects.requireNonNull(raw, "preflight command is required");
        var sourceVersion = token(raw.sourceVersion(), "sourceVersion", 128);
        var policy = policy(systemId, policyId);
        if (policy.version() != raw.expectedPolicyVersion()) throw versionConflict();
        validateDomains(systemId, policy.tenantId(), policy.providerDomains(), policy.allowedDomains());
        var departments = raw.departments() == null ? List.<SystemIdentitySyncApi.ExternalDepartment>of()
                : List.copyOf(raw.departments());
        var employees = raw.employees() == null ? List.<SystemIdentitySyncApi.ExternalEmployee>of()
                : List.copyOf(raw.employees());
        if (departments.size() + employees.size() > MAX_ITEMS) throw validation("单次同步最多 5000 条");
        var old = snapshotBySource(policyId, sourceVersion);
        if (old != null) return snapshot(systemId, policyId, old.id());

        var now = now();
        var snapshotId = ids.nextId();
        var itemRows = new ArrayList<ItemDraft>();
        var departmentIds = new LinkedHashSet<String>();
        for (var rawDepartment : departments) {
            if (rawDepartment == null) throw validation("部门项不能为空");
            var externalId = token(rawDepartment.externalId(), "department.externalId", 255);
            if (!departmentIds.add(externalId)) throw validation("外部部门 externalId 重复");
        }
        for (var rawDepartment : departments) {
            var externalId = rawDepartment.externalId().strip();
            var name = token(rawDepartment.name(), "department.name", 160);
            var parent = optional(rawDepartment.parentExternalId(), 255);
            Long target = optionalId(rawDepartment.targetDepartmentId());
            String issue = null;
            if (parent != null && !departmentIds.contains(parent)) issue = "PARENT_DEPARTMENT_UNMATCHED";
            if (target != null && !departmentInScope(systemId, policy.tenantId(), target)) {
                issue = "TARGET_DEPARTMENT_SCOPE_MISMATCH"; target = null;
            }
            if (target == null && issue == null) target = findDepartment(systemId, policy.tenantId(), externalId);
            var action = issue != null ? "UNMATCHED" : target == null ? "CREATE" : "BIND";
            itemRows.add(new ItemDraft(ids.nextId(), "DEPARTMENT", externalId, name, parent,
                    null, null, target, null, null, action, issue, immutable(rawDepartment.attributes())));
        }
        var employeeIds = new LinkedHashSet<String>();
        for (var rawEmployee : employees) {
            if (rawEmployee == null) throw validation("员工项不能为空");
            var externalId = token(rawEmployee.externalUserId(), "employee.externalUserId", 255);
            if (!employeeIds.add(externalId)) throw validation("外部员工 externalUserId 重复");
            var email = email(rawEmployee.email());
            var display = token(rawEmployee.displayName(), "employee.displayName", 160);
            var department = optional(rawEmployee.departmentExternalId(), 255);
            Long memberId = optionalId(rawEmployee.targetMemberId());
            Long accountId = null;
            String issue = null;
            if (email == null || !allowed(email, policy.allowedDomains())) issue = "EMPLOYEE_DOMAIN_DENIED";
            if (department != null && !departmentIds.contains(department)) issue = "EMPLOYEE_DEPARTMENT_UNMATCHED";
            if (memberId != null) {
                var member = member(systemId, policy.tenantId(), memberId);
                if (member == null) { issue = "TARGET_MEMBER_SCOPE_MISMATCH"; memberId = null; }
                else accountId = member.accountId();
            }
            if (memberId == null && email != null) {
                accountId = findAccount(email);
                if (accountId != null) memberId = findMember(systemId, policy.tenantId(), accountId);
            }
            String action;
            if (issue != null) action = "UNMATCHED";
            else if (memberId != null) action = "BIND";
            else if (policy.jitSystemMember() && (accountId != null || policy.providerJitAccount())) action = "CREATE";
            else { action = "UNMATCHED"; issue = accountId == null
                    ? "PLATFORM_ACCOUNT_UNMATCHED" : "SYSTEM_MEMBER_UNMATCHED"; }
            itemRows.add(new ItemDraft(ids.nextId(), "EMPLOYEE", externalId, display, null,
                    email, department, null, accountId, memberId, action, issue,
                    withEmployeeAttributes(rawEmployee)));
        }
        int matched = 0, creates = 0, unmatched = 0;
        for (var item : itemRows) {
            if ("CREATE".equals(item.action())) creates++;
            else if ("UNMATCHED".equals(item.action())) unmatched++;
            else matched++;
        }
        jdbc.update("""
                INSERT INTO un_plat_identity_sync_snapshot(
                  id,policy_id,source_version,status,department_count,employee_count,matched_count,
                  create_count,unmatched_count,created_at,created_by,updated_at,version)
                VALUES(?,?,?,'DRAFT',?,?,?,?,?,?,?,?,0)
                """, snapshotId, policyId, sourceVersion, departments.size(), employees.size(), matched,
                creates, unmatched, timestamp(now), session.accountId(), timestamp(now));
        for (var item : itemRows) insertItem(snapshotId, item, now);
        var result = snapshot(systemId, policyId, snapshotId);
        security("SYSTEM_IDENTITY_SYNC_PREFLIGHT", session.accountId(), systemId, policy.tenantId(),
                "SUCCESS", null, request, Map.of("policyId", text(policyId), "snapshotId", text(snapshotId),
                        "sourceVersion", sourceVersion, "unmatchedCount", unmatched));
        return result;
    }

    @Transactional
    public SystemIdentitySyncApi.SnapshotView confirm(AuthenticatedSession session, long systemId,
                                                       long policyId, long snapshotId,
                                                       SystemIdentitySyncApi.ConfirmCommand command,
                                                       ClientRequest request) {
        Objects.requireNonNull(command, "confirm command is required");
        var policy = policy(systemId, policyId);
        var snapshot = snapshotRow(policyId, snapshotId);
        if (policy.version() != command.expectedPolicyVersion()
                || snapshot.version() != command.expectedSnapshotVersion()) throw versionConflict();
        if (!"DRAFT".equals(snapshot.status())) throw conflict("IDENTITY_SNAPSHOT_NOT_DRAFT", "预检快照已处理");
        if (snapshot.unmatchedCount() > 0 && !command.approveUnmatched()) {
            throw conflict("IDENTITY_UNMATCHED_CONFIRMATION_REQUIRED", "存在未匹配项，必须人工确认跳过后才能同步");
        }
        var now = now();
        if (jdbc.update("""
                UPDATE un_plat_identity_sync_snapshot
                SET status='CONFIRMED',confirmed_at=?,confirmed_by=?,updated_at=?,version=version+1
                WHERE id=? AND policy_id=? AND status='DRAFT' AND version=?
                """, timestamp(now), session.accountId(), timestamp(now), snapshotId, policyId,
                snapshot.version()) != 1) throw versionConflict();
        if (jdbc.update("""
                UPDATE un_plat_system_identity_policy
                SET status='ACTIVE',last_confirmed_snapshot_id=?,updated_at=?,updated_by=?,version=version+1
                WHERE id=? AND system_id=? AND version=?
                """, snapshotId, timestamp(now), session.accountId(), policyId, systemId,
                policy.version()) != 1) throw versionConflict();
        security("SYSTEM_IDENTITY_SYNC_CONFIRMED", session.accountId(), systemId, policy.tenantId(),
                "SUCCESS", null, request, Map.of("policyId", text(policyId), "snapshotId", text(snapshotId),
                        "approvedUnmatched", command.approveUnmatched()));
        return snapshot(systemId, policyId, snapshotId);
    }

    @Transactional
    public SystemIdentitySyncApi.TaskView start(AuthenticatedSession session, long systemId, long policyId,
                                                 long snapshotId, SystemIdentitySyncApi.StartCommand command,
                                                 ClientRequest request) {
        Objects.requireNonNull(command, "start command is required");
        var policy = policy(systemId, policyId);
        var snapshot = snapshotRow(policyId, snapshotId);
        if (snapshot.version() != command.expectedSnapshotVersion()) throw versionConflict();
        if (!"CONFIRMED".equals(snapshot.status())) {
            throw conflict("IDENTITY_SNAPSHOT_NOT_CONFIRMED", "快照尚未确认或同步任务已创建");
        }
        if (jdbc.update("UPDATE un_plat_identity_sync_snapshot SET status='QUEUED',updated_at=?,version=version+1 "
                        + "WHERE id=? AND policy_id=? AND status='CONFIRMED' AND version=?",
                timestamp(now()), snapshotId, policyId, snapshot.version()) != 1) throw versionConflict();
        var job = enqueue(policy, snapshotId, "MANUAL", session.accountId(), request.requestId(), request.traceId());
        security("SYSTEM_IDENTITY_SYNC_QUEUED", session.accountId(), systemId, policy.tenantId(),
                "SUCCESS", null, request, Map.of("jobId", text(job.id()), "snapshotId", text(snapshotId)));
        return task(job);
    }

    @Transactional(readOnly = true)
    public List<SystemIdentitySyncApi.SnapshotView> snapshots(long systemId, long policyId) {
        var policy = policy(systemId, policyId);
        return jdbc.query("SELECT id FROM un_plat_identity_sync_snapshot WHERE policy_id=? "
                        + "ORDER BY created_at DESC,id DESC LIMIT 20",
                (r, row) -> snapshot(systemId, policy.id(), r.getLong(1)), policyId);
    }

    @Transactional(readOnly = true)
    public List<SystemIdentitySyncApi.TaskView> tasks(long systemId, long policyId) {
        var policy = policy(systemId, policyId);
        return jdbc.query("SELECT id FROM un_sys_job WHERE job_type=? AND owner_type='IDENTITY_POLICY' "
                        + "AND owner_id=? AND system_id=? AND tenant_id=? ORDER BY created_at DESC,id DESC LIMIT 50",
                (r, row) -> task(jobs.require(r.getLong(1))), JOB_TYPE, text(policyId), systemId, policy.tenantId());
    }

    @Transactional
    public int enqueueDueSchedules() {
        var due = jdbc.query("""
                SELECT id,system_id,tenant_id,provider_id,allowed_domains_json,jit_system_member,
                  unmatched_action,schedule_enabled,schedule_interval_minutes,next_sync_at,status,
                  last_confirmed_snapshot_id,last_sync_at,version
                FROM un_plat_system_identity_policy p
                WHERE p.status='ACTIVE' AND p.schedule_enabled=TRUE AND p.next_sync_at<=?
                  AND p.last_confirmed_snapshot_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM un_sys_job j
                    WHERE j.job_type='SYSTEM_IDENTITY_SYNC' AND j.owner_type='IDENTITY_POLICY'
                      AND CAST(j.owner_id AS UNSIGNED)=p.id
                      AND j.status IN ('QUEUED','RUNNING')
                  )
                ORDER BY p.next_sync_at,p.id LIMIT 20 FOR UPDATE SKIP LOCKED
                """, (r, row) -> policyRow(r), timestamp(now()));
        for (var policy : due) {
            enqueue(policy, policy.lastSnapshotId(), "SCHEDULED", null,
                    "identity-schedule-" + policy.id() + "-" + now().toEpochMilli(),
                    "identity-schedule-" + policy.id());
            jdbc.update("UPDATE un_plat_system_identity_policy SET next_sync_at=?,updated_at=?,version=version+1 "
                            + "WHERE id=? AND version=?",
                    timestamp(now().plus(Duration.ofMinutes(policy.intervalMinutes()))), timestamp(now()),
                    policy.id(), policy.version());
        }
        return due.size();
    }

    public int executeNext() {
        var claimed = jobs.claim(JOB_TYPE, Duration.ofMinutes(5));
        if (claimed.isEmpty()) return 0;
        var job = claimed.get();
        try {
            var result = execute(job);
            jobs.succeed(job.id(), job.version(), result);
        } catch (Exception failure) {
            jobs.fail(job.id(), job.version(), failureCode(failure), Duration.ofSeconds(20));
        }
        return 1;
    }

    @Transactional
    protected Map<String, Object> execute(DurableJobFacade.JobRecord job) {
        var snapshotId = number(job.input().get("snapshotId"));
        var policyId = number(job.input().get("policyId"));
        var policy = policy(job.systemId(), policyId);
        if (policy.tenantId() != job.tenantId()) throw conflict("IDENTITY_JOB_SCOPE_MISMATCH", "同步任务租户范围不一致");
        var snapshot = snapshotRow(policyId, snapshotId);
        if (!Set.of("QUEUED", "APPLIED", "PARTIAL_FAILED").contains(snapshot.status())) {
            throw conflict("IDENTITY_SNAPSHOT_EXECUTION_DENIED", "同步快照状态不可执行");
        }
        transactions.executeWithoutResult(status ->
                jdbc.update("DELETE FROM un_plat_identity_sync_failure WHERE job_id=?", job.id()));
        var items = itemRows(snapshotId);
        var failures = new ArrayList<Failure>();
        var departmentTargets = new LinkedHashMap<String, Long>();
        var pending = new ArrayList<>(items.stream().filter(i -> "DEPARTMENT".equals(i.kind())
                && !Set.of("SKIP", "UNMATCHED").contains(i.action())).toList());
        while (!pending.isEmpty()) {
            var progressed = false;
            for (var item : List.copyOf(pending)) {
                if (item.parentExternalId() != null && !departmentTargets.containsKey(item.parentExternalId())) continue;
                try {
                    var target = transactions.execute(status -> applyDepartment(
                            policy, item, departmentTargets.get(item.parentExternalId()), job.requestedBy()));
                    if (target == null) throw new IllegalStateException("DEPARTMENT_APPLY_ROLLED_BACK");
                    departmentTargets.put(item.externalId(), target);
                } catch (Exception error) { failures.add(failure(job.id(), item, error)); }
                pending.remove(item); progressed = true;
            }
            if (!progressed) {
                for (var item : pending) failures.add(failure(job.id(), item,
                        new IllegalStateException("PARENT_DEPARTMENT_NOT_APPLIED")));
                pending.clear();
            }
        }
        for (var item : items) {
            if (!"EMPLOYEE".equals(item.kind())) continue;
            if (Set.of("SKIP", "UNMATCHED").contains(item.action())) {
                failures.add(failure(job.id(), item, new IllegalStateException(
                        item.issueCode() == null ? "UNMATCHED_NOT_APPLIED" : item.issueCode())));
                continue;
            }
            try { transactions.executeWithoutResult(status ->
                    applyEmployee(policy, item, departmentTargets, job.requestedBy())); }
            catch (Exception error) { failures.add(failure(job.id(), item, error)); }
        }
        var applied = items.size() - failures.size();
        var outcome = failures.isEmpty() ? "SUCCEEDED" : "PARTIAL_FAILED";
        var request = new ClientRequest(string(job.input().get("requestId")), string(job.input().get("traceId")),
                "job-worker", "system-identity-sync");
        transactions.executeWithoutResult(status -> {
            for (var failure : failures) insertFailure(failure);
            jdbc.update("UPDATE un_plat_identity_sync_snapshot SET status=?,applied_at=?,updated_at=?,version=version+1 "
                            + "WHERE id=? AND policy_id=?", failures.isEmpty() ? "APPLIED" : "PARTIAL_FAILED",
                    timestamp(now()), timestamp(now()), snapshotId, policyId);
            jdbc.update("UPDATE un_plat_system_identity_policy SET last_sync_at=?,updated_at=?,version=version+1 "
                    + "WHERE id=?", timestamp(now()), timestamp(now()), policyId);
            security("SYSTEM_IDENTITY_SYNC_COMPLETED", job.requestedBy(), policy.systemId(), policy.tenantId(),
                    failures.isEmpty() ? "SUCCESS" : "FAILED", failures.isEmpty() ? null : "IDENTITY_SYNC_PARTIAL",
                    request, Map.of("jobId", text(job.id()), "snapshotId", text(snapshotId),
                            "appliedCount", applied, "failedCount", failures.size(), "outcome", outcome));
        });
        return Map.of("outcome", outcome, "snapshotId", text(snapshotId), "totalCount", items.size(),
                "appliedCount", applied, "failedCount", failures.size());
    }

    private long applyDepartment(PolicyRow policy, ItemRow item, Long parentId, Long actorValue) {
        if (item.targetDepartmentId() != null) {
            if (!departmentInScope(policy.systemId(), policy.tenantId(), item.targetDepartmentId())) {
                throw new IllegalStateException("TARGET_DEPARTMENT_GONE");
            }
            return item.targetDepartmentId();
        }
        var existing = findDepartment(policy.systemId(), policy.tenantId(), item.externalId());
        if (existing != null) return existing;
        var actor = actorValue == null ? systemOwner(policy.systemId()) : actorValue;
        var departmentId = ids.nextId();
        var code = externalCode("IDP_", policy.tenantId() + ":" + item.externalId());
        try {
            jdbc.update("""
                    INSERT INTO un_plat_department(id,scope_type,scope_key,system_id,tenant_id,parent_id,
                      department_code,name,sort_order,status,created_at,created_by,updated_at,updated_by,version)
                    VALUES(?,'SYSTEM',?,?,?,?,?,?,0,'ACTIVE',?,?,?,?,0)
                    """, departmentId, policy.systemId(), policy.systemId(), policy.tenantId(), parentId,
                    code, item.displayName(), local(now()), actor, local(now()), actor);
            insertClosure(policy, departmentId, parentId, actor);
            return departmentId;
        } catch (DuplicateKeyException duplicate) {
            var raced = findDepartment(policy.systemId(), policy.tenantId(), item.externalId());
            if (raced != null) return raced;
            throw duplicate;
        }
    }

    private void insertClosure(PolicyRow policy, long departmentId, Long parentId, long actor) {
        jdbc.update("INSERT INTO un_plat_department_closure(id,scope_type,scope_key,tenant_id,ancestor_id,"
                        + "descendant_id,depth,created_at,created_by) VALUES(?,'SYSTEM',?,?,?,?,0,?,?)",
                ids.nextId(), policy.systemId(), policy.tenantId(), departmentId, departmentId, local(now()), actor);
        if (parentId == null) return;
        var ancestors = jdbc.query("SELECT ancestor_id,depth FROM un_plat_department_closure "
                        + "WHERE scope_type='SYSTEM' AND scope_key=? AND tenant_key=? AND descendant_id=?",
                (r, row) -> Map.entry(r.getLong(1), r.getInt(2)), policy.systemId(), policy.tenantId(), parentId);
        for (var ancestor : ancestors) {
            jdbc.update("INSERT INTO un_plat_department_closure(id,scope_type,scope_key,tenant_id,ancestor_id,"
                            + "descendant_id,depth,created_at,created_by) VALUES(?,'SYSTEM',?,?,?,?,?,?,?)",
                    ids.nextId(), policy.systemId(), policy.tenantId(), ancestor.getKey(), departmentId,
                    ancestor.getValue() + 1, local(now()), actor);
        }
    }

    private void applyEmployee(PolicyRow policy, ItemRow item, Map<String, Long> departments, Long actorValue) {
        var actor = actorValue == null ? systemOwner(policy.systemId()) : actorValue;
        Long accountId = item.targetAccountId();
        if (accountId == null && item.email() != null) accountId = findAccount(item.email());
        if (accountId == null) {
            if (!policy.providerJitAccount()) throw new IllegalStateException("PLATFORM_ACCOUNT_UNMATCHED");
            accountId = createJitAccount(policy, item, actor);
        }
        Long memberId = item.targetMemberId();
        if (memberId == null) memberId = findMember(policy.systemId(), policy.tenantId(), accountId);
        if (memberId == null) {
            if (!policy.jitSystemMember()) throw new IllegalStateException("SYSTEM_MEMBER_UNMATCHED");
            memberId = createMember(policy, item, accountId, actor);
        }
        bind(policy, item, accountId, memberId);
        if (item.departmentExternalId() != null) {
            var departmentId = departments.get(item.departmentExternalId());
            if (departmentId == null) throw new IllegalStateException("EMPLOYEE_DEPARTMENT_NOT_APPLIED");
            assignDepartment(policy, memberId, departmentId, actor);
        }
    }

    private long createJitAccount(PolicyRow policy, ItemRow item, long actor) {
        var accountId = ids.nextId();
        var username = item.email() == null ? "sso_" + policy.providerId() + "_" + digest(item.externalId(), 20)
                : item.email();
        try {
            jdbc.update("""
                    INSERT INTO un_plat_account(id,account_code,username,username_normalized,email,email_normalized,
                      display_name,locale,time_zone,status,created_at,created_by,updated_at,updated_by,version)
                    VALUES(?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,?,0)
                    """, accountId, "ACC_" + accountId, username, username.toLowerCase(Locale.ROOT),
                    item.email(), item.email(), item.displayName(), "zh-CN", "Asia/Shanghai",
                    local(now()), actor, local(now()), actor);
            return accountId;
        } catch (DuplicateKeyException duplicate) {
            var raced = item.email() == null ? null : findAccount(item.email());
            if (raced != null) return raced;
            throw duplicate;
        }
    }

    private long createMember(PolicyRow policy, ItemRow item, long accountId, long actor) {
        var existing = findMember(policy.systemId(), policy.tenantId(), accountId);
        if (existing != null) return existing;
        var memberId = ids.nextId();
        try {
            jdbc.update("""
                    INSERT INTO un_plat_member(id,system_id,account_id,member_code,display_name,default_tenant_id,
                      status,joined_at,created_at,created_by,updated_at,updated_by,version)
                    VALUES(?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,0)
                    """, memberId, policy.systemId(), accountId,
                    externalCode("SSO_", policy.tenantId() + ":" + item.externalId()),
                    item.displayName(), policy.tenantId(), local(now()), local(now()), actor, local(now()), actor);
            jdbc.update("""
                    INSERT INTO un_plat_member_tenant(id,system_id,member_id,tenant_id,status,granted_at,granted_by,
                      created_at,created_by,updated_at,updated_by,version)
                    VALUES(?,?,?,?,'ACTIVE',?,?,?,?,?,?,0)
                    """, ids.nextId(), policy.systemId(), memberId, policy.tenantId(), local(now()), actor,
                    local(now()), actor, local(now()), actor);
            return memberId;
        } catch (DuplicateKeyException duplicate) {
            var raced = findMember(policy.systemId(), policy.tenantId(), accountId);
            if (raced != null) return raced;
            throw duplicate;
        }
    }

    private void bind(PolicyRow policy, ItemRow item, long accountId, long memberId) {
        jdbc.update("""
                INSERT INTO un_plat_identity_binding(id,provider_id,external_user_id,account_id,system_id,tenant_id,
                  member_id,email_normalized,external_department_id,attributes_json,status,last_login_at,
                  created_at,updated_at,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE',NULL,?,?,0)
                ON DUPLICATE KEY UPDATE account_id=VALUES(account_id),member_id=VALUES(member_id),
                  email_normalized=VALUES(email_normalized),external_department_id=VALUES(external_department_id),
                  attributes_json=VALUES(attributes_json),status='ACTIVE',updated_at=VALUES(updated_at),
                  version=version+1
                """, ids.nextId(), policy.providerId(), item.externalId(), accountId, policy.systemId(),
                policy.tenantId(), memberId, item.email(), item.departmentExternalId(), write(item.attributes()),
                timestamp(now()), timestamp(now()));
    }

    private void assignDepartment(PolicyRow policy, long memberId, long departmentId, long actor) {
        jdbc.update("UPDATE un_plat_member_department SET deleted_at=?,deleted_by=?,updated_at=?,updated_by=?,"
                        + "version=version+1 WHERE scope_type='SYSTEM' AND scope_key=? AND member_id=? "
                        + "AND is_primary=TRUE AND deleted_at IS NULL AND department_id<>?",
                local(now()), actor, local(now()), actor, policy.systemId(), memberId, departmentId);
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_member_department WHERE scope_type='SYSTEM' "
                        + "AND scope_key=? AND member_id=? AND department_id=? AND deleted_at IS NULL",
                Integer.class, policy.systemId(), memberId, departmentId);
        if (count != null && count > 0) {
            jdbc.update("UPDATE un_plat_member_department SET is_primary=TRUE,updated_at=?,updated_by=?,version=version+1 "
                            + "WHERE scope_type='SYSTEM' AND scope_key=? AND member_id=? AND department_id=? "
                            + "AND deleted_at IS NULL",
                    local(now()), actor, policy.systemId(), memberId, departmentId);
            return;
        }
        jdbc.update("""
                INSERT INTO un_plat_member_department(id,scope_type,scope_key,system_id,tenant_id,account_id,
                  member_id,department_id,is_primary,created_at,created_by,updated_at,updated_by,version)
                VALUES(?,'SYSTEM',?,?,?,NULL,?,?,TRUE,?,?,?,?,0)
                """, ids.nextId(), policy.systemId(), policy.systemId(), policy.tenantId(), memberId,
                departmentId, local(now()), actor, local(now()), actor);
    }

    private DurableJobFacade.JobRecord enqueue(PolicyRow policy, long snapshotId, String trigger,
                                                Long actor, String requestId, String traceId) {
        return jobs.enqueue(new DurableJobFacade.EnqueueCommand(JOB_TYPE, "IDENTITY_POLICY", text(policy.id()),
                policy.systemId(), policy.tenantId(), actor,
                Map.of("policyId", text(policy.id()), "snapshotId", text(snapshotId), "trigger", trigger,
                        "requestId", safeCorrelation(requestId), "traceId", safeCorrelation(traceId)), 3));
    }

    private SystemIdentitySyncApi.TaskView task(DurableJobFacade.JobRecord job) {
        var failures = jdbc.query("""
                SELECT i.id,i.item_kind,i.external_id,f.failure_code,f.failure_message,f.created_at
                FROM un_plat_identity_sync_failure f
                JOIN un_plat_identity_sync_item i ON i.id=f.snapshot_item_id
                WHERE f.job_id=? ORDER BY f.created_at,f.id
                """, (r, row) -> new SystemIdentitySyncApi.FailureView(text(r.getLong("id")),
                r.getString("item_kind"), r.getString("external_id"), r.getString("failure_code"),
                r.getString("failure_message"), instant(r, "created_at")), job.id());
        var outcome = string(job.result().get("outcome"));
        var status = outcome == null ? job.status() : outcome;
        return new SystemIdentitySyncApi.TaskView(text(job.id()), string(job.input().get("snapshotId")),
                string(job.input().get("trigger")), status, job.progressPercent(), job.attemptCount(),
                job.maxAttempts(), job.lastError(), job.result(), job.availableAt(), job.startedAt(),
                job.finishedAt(), job.createdAt(), failures);
    }

    private SystemIdentitySyncApi.SnapshotView snapshot(long systemId, long policyId, long snapshotId) {
        policy(systemId, policyId);
        var row = snapshotRow(policyId, snapshotId);
        var items = itemRows(snapshotId).stream().map(this::itemView).toList();
        return new SystemIdentitySyncApi.SnapshotView(text(row.id()), text(policyId), row.sourceVersion(),
                row.status(), row.departmentCount(), row.employeeCount(), row.matchedCount(), row.createCount(),
                row.unmatchedCount(), row.confirmedAt(), text(row.confirmedBy()), row.appliedAt(), row.version(), items);
    }

    private SystemIdentitySyncApi.SyncItemView itemView(ItemRow row) {
        return new SystemIdentitySyncApi.SyncItemView(text(row.id()), row.kind(), row.externalId(), row.displayName(),
                row.parentExternalId(), row.email(), row.departmentExternalId(), text(row.targetDepartmentId()),
                text(row.targetAccountId()), text(row.targetMemberId()), row.action(), row.issueCode(), row.attributes());
    }

    private void insertItem(long snapshotId, ItemDraft item, Instant now) {
        jdbc.update("""
                INSERT INTO un_plat_identity_sync_item(id,snapshot_id,item_kind,external_id,display_name,
                  parent_external_id,email_normalized,department_external_id,target_department_id,target_account_id,
                  target_member_id,proposed_action,issue_code,attributes_json,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, item.id(), snapshotId, item.kind(), item.externalId(), item.displayName(),
                item.parentExternalId(), item.email(), item.departmentExternalId(), item.targetDepartmentId(),
                item.targetAccountId(), item.targetMemberId(), item.action(), item.issueCode(),
                write(item.attributes()), timestamp(now));
    }

    private Failure failure(long jobId, ItemRow item, Exception error) {
        var code = failureCode(error);
        return new Failure(ids.nextId(), jobId, item.id(), code,
                truncate(error.getMessage() == null ? code : error.getMessage(), 500), now());
    }

    private void insertFailure(Failure failure) {
        jdbc.update("INSERT INTO un_plat_identity_sync_failure(id,job_id,snapshot_item_id,failure_code,"
                        + "failure_message,created_at) VALUES(?,?,?,?,?,?)",
                failure.id(), failure.jobId(), failure.itemId(), failure.code(), failure.message(),
                timestamp(failure.createdAt()));
    }

    private PolicyRow policy(long systemId, long policyId) {
        return jdbc.query("""
                SELECT p.id,p.system_id,p.tenant_id,p.provider_id,p.allowed_domains_json,p.jit_system_member,
                  p.unmatched_action,p.schedule_enabled,p.schedule_interval_minutes,p.next_sync_at,p.status,
                  p.last_confirmed_snapshot_id,p.last_sync_at,p.version,i.allowed_domains_json provider_domains,
                  i.jit_account provider_jit_account,i.status provider_status
                FROM un_plat_system_identity_policy p
                JOIN un_plat_identity_provider i ON i.id=p.provider_id
                WHERE p.id=? AND p.system_id=?
                """, (r, row) -> policyJoinedRow(r), policyId, systemId).stream().findFirst()
                .orElseThrow(() -> notFound("IDENTITY_POLICY_NOT_FOUND", "身份继承策略不存在"));
    }

    private PolicyRow policyByScope(long systemId, long tenantId, long providerId) {
        return jdbc.query("""
                SELECT p.id,p.system_id,p.tenant_id,p.provider_id,p.allowed_domains_json,p.jit_system_member,
                  p.unmatched_action,p.schedule_enabled,p.schedule_interval_minutes,p.next_sync_at,p.status,
                  p.last_confirmed_snapshot_id,p.last_sync_at,p.version,i.allowed_domains_json provider_domains,
                  i.jit_account provider_jit_account,i.status provider_status
                FROM un_plat_system_identity_policy p
                JOIN un_plat_identity_provider i ON i.id=p.provider_id
                WHERE p.system_id=? AND p.tenant_id=? AND p.provider_id=?
                """, (r, row) -> policyJoinedRow(r), systemId, tenantId, providerId).stream().findFirst().orElse(null);
    }

    private SystemIdentitySyncApi.PolicyView requirePolicy(long systemId, long tenantId, long policyId) {
        return jdbc.query("""
                SELECT p.*,i.provider_code,i.name provider_name
                FROM un_plat_system_identity_policy p JOIN un_plat_identity_provider i ON i.id=p.provider_id
                WHERE p.id=? AND p.system_id=? AND p.tenant_id=?
                """, this::policyView, policyId, systemId, tenantId).stream().findFirst()
                .orElseThrow(() -> notFound("IDENTITY_POLICY_NOT_FOUND", "身份继承策略不存在"));
    }

    private SystemIdentitySyncApi.PolicyView policyView(ResultSet r, int row) throws SQLException {
        return new SystemIdentitySyncApi.PolicyView(text(r.getLong("id")), text(r.getLong("system_id")),
                text(r.getLong("tenant_id")), text(r.getLong("provider_id")), r.getString("provider_code"),
                r.getString("provider_name"), strings(r.getString("allowed_domains_json")),
                r.getBoolean("jit_system_member"), r.getString("unmatched_action"),
                r.getBoolean("schedule_enabled"), nullableInt(r, "schedule_interval_minutes"),
                instant(r, "next_sync_at"), r.getString("status"),
                text(nullableLong(r, "last_confirmed_snapshot_id")), instant(r, "last_sync_at"),
                r.getLong("version"));
    }

    private SnapshotRow snapshotBySource(long policyId, String sourceVersion) {
        return jdbc.query("SELECT * FROM un_plat_identity_sync_snapshot WHERE policy_id=? AND source_version=?",
                (r, row) -> snapshotRow(r), policyId, sourceVersion).stream().findFirst().orElse(null);
    }

    private SnapshotRow snapshotRow(long policyId, long snapshotId) {
        return jdbc.query("SELECT * FROM un_plat_identity_sync_snapshot WHERE id=? AND policy_id=?",
                (r, row) -> snapshotRow(r), snapshotId, policyId).stream().findFirst()
                .orElseThrow(() -> notFound("IDENTITY_SNAPSHOT_NOT_FOUND", "同步预检快照不存在"));
    }

    private SnapshotRow snapshotRow(ResultSet r) throws SQLException {
        return new SnapshotRow(r.getLong("id"), r.getLong("policy_id"), r.getString("source_version"),
                r.getString("status"), r.getInt("department_count"), r.getInt("employee_count"),
                r.getInt("matched_count"), r.getInt("create_count"), r.getInt("unmatched_count"),
                instant(r, "confirmed_at"), nullableLong(r, "confirmed_by"), instant(r, "applied_at"),
                r.getLong("version"));
    }

    private List<ItemRow> itemRows(long snapshotId) {
        return jdbc.query("SELECT * FROM un_plat_identity_sync_item WHERE snapshot_id=? "
                        + "ORDER BY CASE item_kind WHEN 'DEPARTMENT' THEN 0 ELSE 1 END,id",
                (r, row) -> new ItemRow(r.getLong("id"), r.getString("item_kind"),
                        r.getString("external_id"), r.getString("display_name"),
                        r.getString("parent_external_id"), r.getString("email_normalized"),
                        r.getString("department_external_id"), nullableLong(r, "target_department_id"),
                        nullableLong(r, "target_account_id"), nullableLong(r, "target_member_id"),
                        r.getString("proposed_action"), r.getString("issue_code"),
                        objects(r.getString("attributes_json"))), snapshotId);
    }

    private ProviderRow provider(long providerId) {
        return jdbc.query("SELECT id,system_id,tenant_id,status,allowed_domains_json,jit_account "
                        + "FROM un_plat_identity_provider WHERE id=?",
                (r, row) -> new ProviderRow(r.getLong("id"), nullableLong(r, "system_id"),
                        nullableLong(r, "tenant_id"), r.getString("status"),
                        strings(r.getString("allowed_domains_json")), r.getBoolean("jit_account")), providerId)
                .stream().findFirst().orElseThrow(() -> notFound("IDENTITY_PROVIDER_NOT_FOUND", "身份源不存在"));
    }

    private void validateDomains(long systemId, long tenantId, List<String> providerDomains, List<String> domains) {
        if (domains.isEmpty()) throw validation("租户域名限制不能为空");
        if (!providerDomains.isEmpty() && !providerDomains.containsAll(domains)) {
            throw validation("租户域名必须是身份源域名白名单的子集");
        }
        var verified = jdbc.query("SELECT domain_normalized FROM un_plat_tenant_domain WHERE system_id=? "
                        + "AND tenant_id=? AND status='VERIFIED' AND deleted_at IS NULL",
                (r, row) -> r.getString(1), systemId, tenantId);
        if (!verified.containsAll(domains)) throw conflict("IDENTITY_TENANT_DOMAIN_UNVERIFIED", "租户域名尚未验证");
    }

    private void requireSystem(long systemId) {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_system WHERE id=? AND status='ACTIVE' "
                + "AND deleted_at IS NULL", Integer.class, systemId);
        if (count == null || count != 1) throw notFound("SYSTEM_NOT_FOUND", "系统不存在");
    }

    private void requireTenant(long systemId, long tenantId) {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant WHERE system_id=? AND id=? "
                + "AND status='ACTIVE' AND deleted_at IS NULL", Integer.class, systemId, tenantId);
        if (count == null || count != 1) throw notFound("TENANT_NOT_FOUND", "租户不存在");
    }

    private boolean departmentInScope(long systemId, long tenantId, long departmentId) {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_department WHERE id=? AND system_id=? "
                        + "AND tenant_id=? AND status='ACTIVE' AND deleted_at IS NULL",
                Integer.class, departmentId, systemId, tenantId);
        return count != null && count == 1;
    }

    private Long findDepartment(long systemId, long tenantId, String externalId) {
        return jdbc.query("SELECT id FROM un_plat_department WHERE system_id=? AND tenant_id=? "
                        + "AND department_code=? AND status='ACTIVE' AND deleted_at IS NULL",
                (r, row) -> r.getLong(1), systemId, tenantId,
                externalCode("IDP_", tenantId + ":" + externalId))
                .stream().findFirst().orElse(null);
    }

    private MemberRow member(long systemId, long tenantId, long memberId) {
        return jdbc.query("""
                SELECT m.id,m.account_id FROM un_plat_member m
                JOIN un_plat_member_tenant mt ON mt.system_id=m.system_id AND mt.member_id=m.id
                WHERE m.id=? AND m.system_id=? AND mt.tenant_id=? AND m.deleted_at IS NULL
                  AND mt.deleted_at IS NULL AND m.status='ACTIVE' AND mt.status='ACTIVE'
                """, (r, row) -> new MemberRow(r.getLong(1), r.getLong(2)), memberId, systemId, tenantId)
                .stream().findFirst().orElse(null);
    }

    private Long findMember(long systemId, long tenantId, long accountId) {
        return jdbc.query("""
                SELECT m.id FROM un_plat_member m
                JOIN un_plat_member_tenant mt ON mt.system_id=m.system_id AND mt.member_id=m.id
                WHERE m.system_id=? AND mt.tenant_id=? AND m.account_id=? AND m.status='ACTIVE'
                  AND mt.status='ACTIVE' AND m.deleted_at IS NULL AND mt.deleted_at IS NULL
                """, (r, row) -> r.getLong(1), systemId, tenantId, accountId).stream().findFirst().orElse(null);
    }

    private Long findAccount(String email) {
        return jdbc.query("SELECT id FROM un_plat_account WHERE email_normalized=? AND status='ACTIVE' "
                + "AND deleted_at IS NULL", (r, row) -> r.getLong(1), email).stream().findFirst().orElse(null);
    }

    private long systemOwner(long systemId) {
        return jdbc.queryForObject("SELECT owner_account_id FROM un_plat_system WHERE id=?", Long.class, systemId);
    }

    private void security(String event, Long accountId, long systemId, long tenantId, String result,
                          String failureCode, ClientRequest request, Map<String, ?> detail) {
        audit.recordSecurity(new AuditEvent(event, accountId, null, systemId, tenantId, "WEB",
                request.remoteAddress(), request.userAgent(), safeCorrelation(request.requestId()),
                safeCorrelation(request.traceId()), result, failureCode, write(detail)));
    }

    private Map<String, Object> withEmployeeAttributes(SystemIdentitySyncApi.ExternalEmployee employee) {
        var result = new LinkedHashMap<String, Object>();
        if (employee.attributes() != null) result.putAll(employee.attributes());
        if (employee.employeeNo() != null && !employee.employeeNo().isBlank()) {
            result.put("employeeNo", employee.employeeNo().strip());
        }
        return Map.copyOf(result);
    }

    private PolicyRow policyJoinedRow(ResultSet r) throws SQLException {
        return new PolicyRow(r.getLong("id"), r.getLong("system_id"), r.getLong("tenant_id"),
                r.getLong("provider_id"), strings(r.getString("allowed_domains_json")),
                r.getBoolean("jit_system_member"), r.getString("unmatched_action"),
                r.getBoolean("schedule_enabled"), nullableInt(r, "schedule_interval_minutes"),
                instant(r, "next_sync_at"), r.getString("status"),
                nullableLong(r, "last_confirmed_snapshot_id"), instant(r, "last_sync_at"), r.getLong("version"),
                strings(r.getString("provider_domains")), r.getBoolean("provider_jit_account"),
                r.getString("provider_status"));
    }

    private PolicyRow policyRow(ResultSet r) throws SQLException {
        var provider = provider(r.getLong("provider_id"));
        return new PolicyRow(r.getLong("id"), r.getLong("system_id"), r.getLong("tenant_id"),
                r.getLong("provider_id"), strings(r.getString("allowed_domains_json")),
                r.getBoolean("jit_system_member"), r.getString("unmatched_action"),
                r.getBoolean("schedule_enabled"), nullableInt(r, "schedule_interval_minutes"),
                instant(r, "next_sync_at"), r.getString("status"),
                nullableLong(r, "last_confirmed_snapshot_id"), instant(r, "last_sync_at"), r.getLong("version"),
                provider.allowedDomains(), provider.jitAccount(), provider.status());
    }

    private List<String> domains(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(v -> token(v, "allowedDomain", 253).toLowerCase(Locale.ROOT))
                .distinct().sorted().toList();
    }

    private List<String> strings(String value) {
        try { return value == null ? List.of() : List.copyOf(json.readValue(value, STRING_LIST)); }
        catch (Exception e) { throw new IllegalStateException("Stored identity domain JSON is invalid", e); }
    }

    private Map<String, Object> objects(String value) {
        try { return value == null ? Map.of() : Map.copyOf(json.readValue(value, OBJECT_MAP)); }
        catch (Exception e) { throw new IllegalStateException("Stored identity attributes JSON is invalid", e); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception e) { throw new IllegalArgumentException("Identity synchronization data is not JSON", e); }
    }

    private static Map<String, Object> immutable(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }

    private static boolean allowed(String email, List<String> domains) {
        var at = email.lastIndexOf('@');
        return at > 0 && domains.contains(email.substring(at + 1));
    }

    private static String email(String value) {
        if (value == null || value.isBlank()) return null;
        var normalized = value.strip().toLowerCase(Locale.ROOT);
        return normalized.matches("^[^@\\s]+@[^@\\s]+$") && normalized.length() <= 254 ? normalized : null;
    }

    private static long id(String value, String field) {
        var result = optionalId(value);
        if (result == null) throw validation(field + " 无效");
        return result;
    }

    private static Long optionalId(String value) {
        if (value == null || value.isBlank()) return null;
        try { var parsed = Long.parseLong(value); return parsed > 0 ? parsed : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String token(String value, String field, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw validation(field + " 无效");
        return value.strip();
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.strip().length() > max) throw validation("字段长度超限");
        return value.strip();
    }

    private static String externalCode(String prefix, String externalId) {
        return prefix + digest(externalId, 24);
    }

    private static String digest(String value, int length) {
        try {
            var hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
            return hash.substring(0, length).toUpperCase(Locale.ROOT);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static long number(Object value) {
        try { var parsed = Long.parseLong(String.valueOf(value)); if (parsed > 0) return parsed; }
        catch (Exception ignored) { }
        throw conflict("IDENTITY_JOB_INPUT_INVALID", "同步任务输入无效");
    }

    private static String failureCode(Exception error) {
        if (error instanceof BusinessException business) return truncate(business.code(), 96);
        var message = error.getMessage();
        if (message != null && message.matches("^[A-Z][A-Z0-9_]{2,95}$")) return message;
        return "IDENTITY_SYNC_ITEM_FAILED";
    }

    private static String safeCorrelation(String value) {
        if (value == null || value.isBlank()) return "identity-sync";
        return truncate(value.strip(), 64);
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String text(Long value) { return value == null ? null : Long.toString(value); }
    private static String text(long value) { return Long.toString(value); }
    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static LocalDateTime local(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(ResultSet r, String column) throws SQLException {
        var value = r.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static Long nullableLong(ResultSet r, String column) throws SQLException {
        var value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Integer nullableInt(ResultSet r, String column) throws SQLException {
        var value = r.getInt(column); return r.wasNull() ? null : value;
    }
    private Instant now() { return clock.instant(); }

    private static BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static BusinessException versionConflict() {
        return new BusinessException("VERSION_CONFLICT", "版本已变化，请刷新后重试", HttpStatus.CONFLICT);
    }
    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }
    private static BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }

    private record ProviderRow(long id, Long systemId, Long tenantId, String status,
                               List<String> allowedDomains, boolean jitAccount) { }
    private record PolicyRow(long id, long systemId, long tenantId, long providerId,
                             List<String> allowedDomains, boolean jitSystemMember, String unmatchedAction,
                             boolean scheduleEnabled, Integer intervalMinutes, Instant nextSyncAt, String status,
                             Long lastSnapshotId, Instant lastSyncAt, long version,
                             List<String> providerDomains, boolean providerJitAccount, String providerStatus) { }
    private record SnapshotRow(long id, long policyId, String sourceVersion, String status,
                               int departmentCount, int employeeCount, int matchedCount, int createCount,
                               int unmatchedCount, Instant confirmedAt, Long confirmedBy,
                               Instant appliedAt, long version) { }
    private record ItemDraft(long id, String kind, String externalId, String displayName,
                             String parentExternalId, String email, String departmentExternalId,
                             Long targetDepartmentId, Long targetAccountId, Long targetMemberId,
                             String action, String issueCode, Map<String, Object> attributes) { }
    private record ItemRow(long id, String kind, String externalId, String displayName,
                           String parentExternalId, String email, String departmentExternalId,
                           Long targetDepartmentId, Long targetAccountId, Long targetMemberId,
                           String action, String issueCode, Map<String, Object> attributes) { }
    private record MemberRow(long id, long accountId) { }
    private record Failure(long id, long jobId, long itemId, String code, String message, Instant createdAt) { }
}
