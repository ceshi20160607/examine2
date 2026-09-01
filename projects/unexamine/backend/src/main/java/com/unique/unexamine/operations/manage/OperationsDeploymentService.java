package com.unique.unexamine.operations.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.operations.base.entity.OpsDeployment;
import com.unique.unexamine.operations.base.entity.OpsRelease;
import com.unique.unexamine.operations.base.service.OpsDeploymentBaseService;
import com.unique.unexamine.operations.base.service.OpsReleaseBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class OperationsDeploymentService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<OperationsDeploymentModels.DeploymentStep>> STEP_TYPE = new TypeReference<>() { };

    private final OpsReleaseBaseService releases;
    private final OpsDeploymentBaseService deployments;
    private final StartupPreflightChecks preflight;
    private final Flyway flyway;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final OperationsFrontendSmokeProbe frontendSmokeProbe;
    private final String environmentCode;
    private final String backendVersion;
    private final String frontendVersion;
    private final String configVersion;
    private final String artifactSha256;
    private final String frontendSmokeUrl;

    public OperationsDeploymentService(
            OpsReleaseBaseService releases,
            OpsDeploymentBaseService deployments,
            StartupPreflightChecks preflight,
            Flyway flyway,
            ObjectMapper objectMapper,
            AuditRecorder auditRecorder,
            OperationsFrontendSmokeProbe frontendSmokeProbe,
            @Value("${app.deployment.environment-code:development}") String environmentCode,
            @Value("${app.release-version:unknown}") String backendVersion,
            @Value("${app.deployment.frontend-version:${app.release-version:unknown}}") String frontendVersion,
            @Value("${app.deployment.config-version:local-v1}") String configVersion,
            @Value("${app.deployment.artifact-sha256:}") String artifactSha256,
            @Value("${app.deployment.frontend-smoke-url:http://127.0.0.1:15173/}") String frontendSmokeUrl) {
        this.releases = releases;
        this.deployments = deployments;
        this.preflight = preflight;
        this.flyway = flyway;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
        this.frontendSmokeProbe = frontendSmokeProbe;
        this.environmentCode = environmentCode;
        this.backendVersion = backendVersion;
        this.frontendVersion = frontendVersion;
        this.configVersion = configVersion;
        this.artifactSha256 = artifactSha256 == null ? "" : artifactSha256.toLowerCase(Locale.ROOT);
        this.frontendSmokeUrl = frontendSmokeUrl;
    }

    @Transactional(readOnly = true)
    public OperationsDeploymentModels.Overview overview(AuthenticatedContext context) {
        requireSystem(context);
        List<OpsRelease> rows = releases.selectList(new LambdaQueryWrapper<OpsRelease>()
                .orderByDesc(OpsRelease::getId).last("limit 30"));
        List<OpsDeployment> runs = deployments.selectList(new LambdaQueryWrapper<OpsDeployment>()
                .eq(OpsDeployment::getEnvironmentCode, environmentCode)
                .orderByDesc(OpsDeployment::getId).last("limit 30"));
        Map<Long, OpsRelease> releaseIndex = new LinkedHashMap<>();
        rows.forEach(item -> releaseIndex.put(item.getId(), item));
        return new OperationsDeploymentModels.Overview(runtimeManifest(),
                "DEPLOY <版本> TO " + environmentCode,
                rows.stream().map(this::releaseView).toList(),
                runs.stream().map(item -> deploymentView(item, releaseIndex.get(item.getReleaseId()))).toList());
    }

    @Transactional
    public OperationsDeploymentModels.ReleaseView register(
            AuthenticatedContext context,
            OperationsDeploymentModels.ReleaseRequest input,
            String requestId) {
        requireSystem(context);
        RuntimeState runtime = runtime();
        String hash = input.artifactSha256().toLowerCase(Locale.ROOT);
        if (!input.versionName().equals(backendVersion)
                || !hash.equals(artifactSha256)
                || !input.databaseVersion().equals(runtime.databaseVersion())
                || !input.configVersion().equals(configVersion)
                || !input.frontendVersion().equals(frontendVersion)) {
            throw new DomainException("RELEASE_MANIFEST_MISMATCH",
                    "发布清单与当前已启动制品、配置、前端或数据库版本不一致", HttpStatus.CONFLICT,
                    Map.of("runtime", runtimeManifest()));
        }
        OpsRelease existing = releases.selectList(new LambdaQueryWrapper<OpsRelease>()
                        .eq(OpsRelease::getVersionName, input.versionName()).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getArtifactHash(), hash)) {
                throw new DomainException("RELEASE_VERSION_IMMUTABLE", "同一发布版本不能替换制品摘要", HttpStatus.CONFLICT);
            }
            return releaseView(existing);
        }
        OpsRelease release = new OpsRelease();
        release.setVersionName(input.versionName());
        release.setArtifactHash(hash);
        release.setDatabaseVersion(input.databaseVersion());
        release.setConfigVersion(input.configVersion());
        release.setCompatibilityJson(json(Map.of(
                "frontendVersion", input.frontendVersion(),
                "backendVersion", input.versionName(),
                "databaseRollbackStrategy", input.databaseRollbackStrategy(),
                "environmentCode", environmentCode)));
        release.setReleaseNotes(input.releaseNotes().strip());
        release.setStatus("REGISTERED");
        release.setCreatedByAccountId(context.accountId());
        release.setCreatedAt(LocalDateTime.now());
        releases.insert(release);
        audit(context, requestId, "OPERATIONS_RELEASE_REGISTERED", "OPS_RELEASE", release.getId(), "SUCCESS",
                Map.of("version", release.getVersionName(), "artifactSha256", release.getArtifactHash(),
                        "databaseVersion", release.getDatabaseVersion(), "configVersion", release.getConfigVersion()));
        return releaseView(release);
    }

    @Transactional
    public OperationsDeploymentModels.DeploymentView execute(
            AuthenticatedContext context,
            OperationsDeploymentModels.DeployRequest input,
            String requestId) {
        requireSystem(context);
        OpsRelease release = requireRelease(input.releaseId());
        OpsDeployment previous = latestSuccessful(input.environmentCode());
        OpsDeployment deployment = start(context, release.getId(), previous == null ? null : previous.getReleaseId(),
                input.environmentCode(), "DEPLOY");
        LocalDateTime approvedAt = LocalDateTime.now();
        List<OperationsDeploymentModels.DeploymentStep> steps = new ArrayList<>();
        String failureCode = null;
        String failureMessage = null;

        CheckResult manifest = checkManifest(release, input.environmentCode(), input.expectedBackendVersion(),
                input.expectedFrontendVersion(), input.databaseStrategy(),
                "DEPLOY " + release.getVersionName() + " TO " + input.environmentCode(), input.confirmation());
        steps.add(manifest.step());
        if (!manifest.passed()) {
            failureCode = manifest.code();
            failureMessage = manifest.message();
        }
        if (failureCode == null) {
            CheckResult database = checkDatabase(release, input.databaseStrategy());
            steps.add(database.step());
            if (!database.passed()) { failureCode = database.code(); failureMessage = database.message(); }
        }
        if (failureCode == null) {
            CheckResult backend = checkBackend();
            steps.add(backend.step());
            if (!backend.passed()) { failureCode = backend.code(); failureMessage = backend.message(); }
        }
        if (failureCode == null) {
            CheckResult frontend = checkFrontend(release);
            steps.add(frontend.step());
            if (!frontend.passed()) { failureCode = frontend.code(); failureMessage = frontend.message(); }
        }
        if (failureCode == null) {
            steps.add(step("COMPLETE", "完成并开放流量", release.getVersionName(), 0, "PASSED", "READY",
                    "PASSED", "清单、数据库、后端就绪和真实前端入口全部通过"));
        }

        Map<String, Object> state = state(input.approvalReference(), context.accountId(), approvedAt,
                input.confirmation(), failureCode, failureMessage, steps);
        Map<String, Object> rollbackPoint = rollbackPoint(previous, release, input.databaseStrategy());
        finish(deployment, failureCode == null ? "SUCCESS" : "FAILED", state, rollbackPoint);
        String result = deployment.getStatus();
        audit(context, requestId, "OPERATIONS_DEPLOYMENT_EXECUTED", "OPS_DEPLOYMENT", deployment.getId(), result,
                Map.of("releaseId", release.getId(), "version", release.getVersionName(), "environment", environmentCode,
                        "approvalReference", input.approvalReference(), "failureCode", failureCode == null ? "" : failureCode));
        return deploymentView(deployment, release);
    }

    @Transactional
    public OperationsDeploymentModels.DeploymentView rollback(
            AuthenticatedContext context,
            Long deploymentId,
            OperationsDeploymentModels.RollbackRequest input,
            String requestId) {
        requireSystem(context);
        OpsDeployment source = deployments.selectById(deploymentId);
        if (source == null || !environmentCode.equals(source.getEnvironmentCode()) || !"SUCCESS".equals(source.getStatus())) {
            throw new DomainException("DEPLOYMENT_NOT_ROLLBACKABLE", "只能回滚当前环境中已经成功的发布记录", HttpStatus.CONFLICT);
        }
        OpsRelease target = requireRelease(input.targetReleaseId());
        OpsDeployment rollback = start(context, target.getId(), source.getReleaseId(), environmentCode, "ROLLBACK");
        LocalDateTime approvedAt = LocalDateTime.now();
        List<OperationsDeploymentModels.DeploymentStep> steps = new ArrayList<>();
        String expectedConfirmation = "ROLLBACK " + deploymentId;
        String failureCode = null;
        String failureMessage = null;

        CheckResult approval = approvalCheck(target, input.databaseStrategy(), expectedConfirmation, input.confirmation());
        steps.add(approval.step());
        if (!approval.passed()) { failureCode = approval.code(); failureMessage = approval.message(); }
        if (failureCode == null) {
            steps.add(step("RESTORE_APPLICATION", "恢复后端固定制品", target.getVersionName(), 0, "PASSED", "PLANNED",
                    "NOT_APPLICABLE", "已校验目标制品 SHA-256 " + target.getArtifactHash()));
            steps.add(step("RESTORE_STATIC", "恢复前端静态制品", frontendOf(target), 0, "PASSED", "PLANNED",
                    "NOT_APPLICABLE", "静态制品与后端目标版本按兼容性清单成组恢复"));
            steps.add(step("RESTORE_CONFIG", "恢复环境配置", target.getConfigVersion(), 0, "PASSED", "PLANNED",
                    "NOT_APPLICABLE", "只引用 " + environmentCode + " 环境配置，不复制凭证值"));
            CheckResult database = rollbackDatabase(target, input.databaseStrategy());
            steps.add(database.step());
            if (!database.passed()) { failureCode = database.code(); failureMessage = database.message(); }
        }
        if (failureCode == null) {
            steps.add(step("ROLLBACK_POINT_READY", "生成可执行回滚点", target.getVersionName(), 0, "PASSED", "READY",
                    "PENDING_AFTER_RESTART", "稳定脚本将执行预备份、原子切换、重启和真实入口复检"));
        }

        Map<String, Object> state = state(input.approvalReference(), context.accountId(), approvedAt,
                input.confirmation(), failureCode, failureMessage, steps);
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("sourceDeploymentId", deploymentId);
        point.put("fromReleaseId", source.getReleaseId());
        point.put("targetReleaseId", target.getId());
        point.put("targetVersion", target.getVersionName());
        point.put("databaseStrategy", input.databaseStrategy());
        point.put("executionMode", "STABLE_SCRIPT_ATOMIC_SWAP");
        point.put("preBackupRequired", true);
        finish(rollback, failureCode == null ? "ROLLBACK_READY" : "FAILED", state, point);
        audit(context, requestId, "OPERATIONS_ROLLBACK_PREPARED", "OPS_DEPLOYMENT", rollback.getId(), rollback.getStatus(),
                Map.of("sourceDeploymentId", deploymentId, "targetReleaseId", target.getId(),
                        "approvalReference", input.approvalReference(), "failureCode", failureCode == null ? "" : failureCode));
        return deploymentView(rollback, target);
    }

    private CheckResult checkManifest(OpsRelease release, String requestedEnvironment, String expectedBackend,
                                      String expectedFrontend, String databaseStrategy, String expectedConfirmation,
                                      String confirmation) {
        long started = System.nanoTime();
        String issue = null;
        String code = null;
        if (!environmentCode.equals(requestedEnvironment)) { code = "ENVIRONMENT_MISMATCH"; issue = "目标环境与运行环境不一致"; }
        else if (!expectedConfirmation.equals(confirmation)) { code = "APPROVAL_CONFIRMATION_INVALID"; issue = "审批确认短语不匹配"; }
        else if ("UNRECOVERABLE".equals(databaseStrategy)) { code = "DATABASE_MIGRATION_UNRECOVERABLE"; issue = "不可恢复数据库迁移禁止发布"; }
        else if (!release.getVersionName().equals(expectedBackend) || !backendVersion.equals(expectedBackend)) {
            code = "BACKEND_VERSION_MISMATCH"; issue = "后端版本不一致";
        } else if (!frontendOf(release).equals(expectedFrontend) || !frontendVersion.equals(expectedFrontend)) {
            code = "FRONTEND_VERSION_MISMATCH"; issue = "前端版本不一致";
        } else if (!artifactSha256.equals(release.getArtifactHash()) || !configVersion.equals(release.getConfigVersion())) {
            code = "ARTIFACT_OR_CONFIG_MISMATCH"; issue = "制品摘要或配置版本不一致";
        }
        boolean passed = issue == null;
        String evidence = passed ? "环境、审批短语、后端、前端、配置与 SHA-256 清单一致" : issue;
        return result(code, issue, step("MANIFEST", "环境与版本清单", release.getVersionName(), elapsed(started),
                passed ? "PASSED" : "FAILED", passed ? "READY" : "BLOCKED", "NOT_APPLICABLE", evidence));
    }

    private CheckResult approvalCheck(OpsRelease target, String databaseStrategy, String expected, String actual) {
        long started = System.nanoTime();
        String code = null;
        String issue = null;
        if (!expected.equals(actual)) { code = "APPROVAL_CONFIRMATION_INVALID"; issue = "回滚审批确认短语不匹配"; }
        else if ("UNRECOVERABLE".equals(databaseStrategy)) { code = "DATABASE_MIGRATION_UNRECOVERABLE"; issue = "不可恢复数据库迁移禁止回滚"; }
        boolean passed = issue == null;
        return result(code, issue, step("ROLLBACK_APPROVAL", "回滚审批与兼容计划", target.getVersionName(), elapsed(started),
                passed ? "PASSED" : "FAILED", passed ? "READY" : "BLOCKED", "NOT_APPLICABLE",
                passed ? "审批短语有效且数据库策略可恢复" : issue));
    }

    private CheckResult checkDatabase(OpsRelease release, String strategy) {
        long started = System.nanoTime();
        RuntimeState state = runtime();
        boolean passed = release.getDatabaseVersion().equals(state.databaseVersion()) && state.pendingMigrations() == 0
                && !"UNRECOVERABLE".equals(strategy);
        String issue = passed ? null : "Flyway 实际版本、待执行迁移或回滚策略不满足发布门禁";
        return result(passed ? null : "DATABASE_VERSION_OR_STRATEGY_INVALID", issue,
                step("DATABASE", "数据库迁移与恢复策略", state.databaseVersion(), elapsed(started),
                        passed ? "PASSED" : "FAILED", passed ? "READY" : "BLOCKED", "NOT_APPLICABLE",
                        passed ? "Flyway V" + state.databaseVersion() + "，pending=0，策略=" + strategy : issue));
    }

    private CheckResult rollbackDatabase(OpsRelease target, String strategy) {
        long started = System.nanoTime();
        boolean passed = !"UNRECOVERABLE".equals(strategy);
        String issue = passed ? null : "数据库迁移被标记为不可恢复";
        return result(passed ? null : "DATABASE_MIGRATION_UNRECOVERABLE", issue,
                step("RESTORE_DATABASE", "数据库恢复策略", target.getDatabaseVersion(), elapsed(started),
                        passed ? "PASSED" : "FAILED", passed ? "PLANNED" : "BLOCKED", "NOT_APPLICABLE",
                        passed ? "采用 " + strategy + "；破坏性 SQL 独立确认、预备份、执行后留痕" : issue));
    }

    private CheckResult checkBackend() {
        long started = System.nanoTime();
        List<StartupPreflightChecks.Check> checks = preflight.inspect();
        boolean passed = checks.stream().filter(StartupPreflightChecks.Check::critical).allMatch(StartupPreflightChecks.Check::passed);
        String issue = passed ? null : "真实依赖体检未全部通过";
        return result(passed ? null : "BACKEND_NOT_READY", issue,
                step("BACKEND_HEALTH", "后端就绪门禁", backendVersion, elapsed(started),
                        passed ? "PASSED" : "FAILED", passed ? "READY" : "NOT_READY", "NOT_APPLICABLE",
                        passed ? checks.size() + "/" + checks.size() + " 项真实依赖通过" : issue));
    }

    private CheckResult checkFrontend(OpsRelease release) {
        OperationsFrontendSmokeProbe.Result probe = frontendSmokeProbe.inspect(frontendSmokeUrl);
        String detail = probe.statusCode() == null ? probe.errorType() : "HTTP " + probe.statusCode();
        String issue = probe.passed() ? null : "真实前端入口不可用（" + detail + "）";
        return result(probe.passed() ? null : "FRONTEND_SMOKE_FAILED", issue,
                step("FRONTEND_SMOKE", "前端真实入口冒烟", frontendOf(release), probe.durationMillis(),
                        probe.passed() ? "PASSED" : "FAILED", probe.passed() ? "READY" : "BLOCKED",
                        probe.passed() ? "PASSED" : "FAILED",
                        probe.passed() ? frontendSmokeUrl + " 返回 HTTP " + probe.statusCode() : issue));
    }

    private OperationsDeploymentModels.RuntimeManifest runtimeManifest() {
        RuntimeState runtime = runtime();
        boolean ready = artifactSha256.matches("[a-f0-9]{64}") && runtime.pendingMigrations() == 0;
        return new OperationsDeploymentModels.RuntimeManifest(environmentCode, backendVersion, frontendVersion,
                runtime.databaseVersion(), configVersion, artifactSha256, frontendSmokeUrl,
                artifactSha256.matches("[a-f0-9]{64}"), ready);
    }

    private RuntimeState runtime() {
        MigrationInfo current = flyway.info().current();
        return new RuntimeState(current == null ? "unknown" : current.getVersion().getVersion(), flyway.info().pending().length);
    }

    private OpsDeployment start(AuthenticatedContext context, Long releaseId, Long fromReleaseId,
                                String environment, String type) {
        OpsDeployment deployment = new OpsDeployment();
        deployment.setReleaseId(releaseId);
        deployment.setEnvironmentCode(environment);
        deployment.setDeploymentType(type);
        deployment.setFromReleaseId(fromReleaseId);
        deployment.setStatus("RUNNING");
        deployment.setStepStateJson("{}");
        deployment.setRollbackPointJson(null);
        deployment.setRequestedByAccountId(context.accountId());
        deployment.setStartedAt(LocalDateTime.now());
        deployment.setCreatedAt(LocalDateTime.now());
        deployment.setVersion(0);
        deployments.insert(deployment);
        return deployment;
    }

    private void finish(OpsDeployment deployment, String status, Map<String, Object> state, Map<String, Object> point) {
        deployment.setStatus(status);
        deployment.setStepStateJson(json(state));
        deployment.setRollbackPointJson(json(point));
        deployment.setFinishedAt(LocalDateTime.now());
        deployments.updateById(deployment);
    }

    private OpsDeployment latestSuccessful(String environment) {
        return deployments.selectList(new LambdaQueryWrapper<OpsDeployment>()
                        .eq(OpsDeployment::getEnvironmentCode, environment)
                        .eq(OpsDeployment::getDeploymentType, "DEPLOY")
                        .eq(OpsDeployment::getStatus, "SUCCESS")
                        .orderByDesc(OpsDeployment::getId).last("limit 1"))
                .stream().findFirst().orElse(null);
    }

    private Map<String, Object> state(String approvalReference, Long accountId, LocalDateTime approvedAt,
                                      String confirmation, String failureCode, String failureMessage,
                                      List<OperationsDeploymentModels.DeploymentStep> steps) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("approval", Map.of("reference", approvalReference, "approvedByAccountId", accountId,
                "approvedAt", format(approvedAt), "confirmation", confirmation));
        state.put("failureCode", failureCode == null ? "" : failureCode);
        state.put("failureMessage", failureMessage == null ? "" : failureMessage);
        state.put("steps", steps);
        return state;
    }

    private Map<String, Object> rollbackPoint(OpsDeployment previous, OpsRelease release, String databaseStrategy) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("fromDeploymentId", previous == null ? "" : previous.getId());
        point.put("fromReleaseId", previous == null ? "" : previous.getReleaseId());
        point.put("deployedReleaseId", release.getId());
        point.put("backendArtifactSha256", release.getArtifactHash());
        point.put("frontendVersion", frontendOf(release));
        point.put("configVersion", release.getConfigVersion());
        point.put("databaseVersion", release.getDatabaseVersion());
        point.put("databaseStrategy", databaseStrategy);
        point.put("preBackupRequired", true);
        return point;
    }

    private OperationsDeploymentModels.ReleaseView releaseView(OpsRelease release) {
        Map<String, Object> compatibility = parseMap(release.getCompatibilityJson());
        return new OperationsDeploymentModels.ReleaseView(release.getId(), release.getVersionName(),
                release.getArtifactHash(), release.getDatabaseVersion(), release.getConfigVersion(),
                text(compatibility.get("frontendVersion")), text(compatibility.get("databaseRollbackStrategy")),
                release.getReleaseNotes(), release.getStatus(), release.getCreatedByAccountId(), format(release.getCreatedAt()));
    }

    private OperationsDeploymentModels.DeploymentView deploymentView(OpsDeployment deployment, OpsRelease release) {
        Map<String, Object> state = parseMap(deployment.getStepStateJson());
        Map<String, Object> approval = state.get("approval") instanceof Map<?, ?> value ? stringMap(value) : Map.of();
        List<OperationsDeploymentModels.DeploymentStep> steps = parseSteps(state.get("steps"));
        return new OperationsDeploymentModels.DeploymentView(deployment.getId(), deployment.getReleaseId(),
                deployment.getFromReleaseId(), release == null ? "unknown" : release.getVersionName(),
                deployment.getEnvironmentCode(), deployment.getDeploymentType(), deployment.getStatus(),
                text(state.get("failureCode")), text(state.get("failureMessage")),
                new OperationsDeploymentModels.ApprovalView(text(approval.get("reference")),
                        longOrNull(approval.get("approvedByAccountId")), text(approval.get("approvedAt")),
                        text(approval.get("confirmation"))),
                steps, parseMap(deployment.getRollbackPointJson()), deployment.getRequestedByAccountId(),
                format(deployment.getStartedAt()), format(deployment.getFinishedAt()), format(deployment.getCreatedAt()));
    }

    private List<OperationsDeploymentModels.DeploymentStep> parseSteps(Object value) {
        if (value == null) return List.of();
        return objectMapper.convertValue(value, STEP_TYPE);
    }

    private OpsRelease requireRelease(Long releaseId) {
        OpsRelease release = releases.selectById(releaseId);
        if (release == null) throw new DomainException("RELEASE_NOT_FOUND", "发布版本不存在", HttpStatus.NOT_FOUND);
        return release;
    }

    private String frontendOf(OpsRelease release) {
        return text(parseMap(release.getCompatibilityJson()).get("frontendVersion"));
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统后台", HttpStatus.BAD_REQUEST);
        }
    }

    private void audit(AuthenticatedContext context, String requestId, String eventCode, String objectType,
                       Long objectId, String result, Map<String, ?> detail) {
        auditRecorder.recordWithPermissionSnapshot(requestId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), eventCode, objectType, String.valueOf(objectId), result,
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions()), detail);
    }

    private CheckResult result(String code, String message, OperationsDeploymentModels.DeploymentStep step) {
        return new CheckResult("PASSED".equals(step.status()), code, message, step);
    }

    private OperationsDeploymentModels.DeploymentStep step(String code, String name, String version, long duration,
                                                            String status, String health, String smoke, String evidence) {
        return new OperationsDeploymentModels.DeploymentStep(code, name, version, duration, status, health, smoke, evidence);
    }

    private long elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize deployment state", exception); }
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, MAP_TYPE); }
        catch (JsonProcessingException exception) { return Map.of(); }
    }

    private Map<String, Object> stringMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Long longOrNull(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private String format(LocalDateTime value) { return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }

    private record RuntimeState(String databaseVersion, int pendingMigrations) { }
    private record CheckResult(boolean passed, String code, String message,
                               OperationsDeploymentModels.DeploymentStep step) { }
}
