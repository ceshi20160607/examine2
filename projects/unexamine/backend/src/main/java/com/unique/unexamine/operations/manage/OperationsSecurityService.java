package com.unique.unexamine.operations.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.operations.base.entity.OpsSecretRef;
import com.unique.unexamine.operations.base.entity.OpsSecretRotation;
import com.unique.unexamine.operations.base.entity.OpsVerificationFinding;
import com.unique.unexamine.operations.base.entity.OpsVerificationRun;
import com.unique.unexamine.operations.base.service.OpsSecretRefBaseService;
import com.unique.unexamine.operations.base.service.OpsSecretRotationBaseService;
import com.unique.unexamine.operations.base.service.OpsVerificationFindingBaseService;
import com.unique.unexamine.operations.base.service.OpsVerificationRunBaseService;
import com.unique.unexamine.operations.repository.OperationsSecurityProbeRepository;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class OperationsSecurityService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Set<String> SECRET_TYPES = Set.of("APPLICATION", "SSO", "WEBHOOK", "MODEL");

    private final OpsSecretRefBaseService refs;
    private final OpsSecretRotationBaseService rotations;
    private final OpsVerificationRunBaseService verificationRuns;
    private final OpsVerificationFindingBaseService findings;
    private final StartupPreflightChecks preflight;
    private final OperationsSecurityProbeRepository probes;
    private final OperationsSecretCipher cipher;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final SecureRandom secureRandom = new SecureRandom();

    public OperationsSecurityService(
            OpsSecretRefBaseService refs,
            OpsSecretRotationBaseService rotations,
            OpsVerificationRunBaseService verificationRuns,
            OpsVerificationFindingBaseService findings,
            StartupPreflightChecks preflight,
            OperationsSecurityProbeRepository probes,
            OperationsSecretCipher cipher,
            ObjectMapper objectMapper,
            AuditRecorder auditRecorder) {
        this.refs = refs;
        this.rotations = rotations;
        this.verificationRuns = verificationRuns;
        this.findings = findings;
        this.preflight = preflight;
        this.probes = probes;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public OperationsSecurityModels.Overview overview(AuthenticatedContext context) {
        requireSystem(context);
        List<OpsSecretRef> secretRefs = refs.selectList(new LambdaQueryWrapper<OpsSecretRef>()
                .eq(OpsSecretRef::getSystemId, context.systemId()).orderByDesc(OpsSecretRef::getId));
        Set<Long> refIds = secretRefs.stream().map(OpsSecretRef::getId).collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<OpsSecretRotation> rotationRows = refIds.isEmpty() ? List.of()
                : rotations.selectList(new LambdaQueryWrapper<OpsSecretRotation>()
                        .in(OpsSecretRotation::getSecretRefId, refIds).orderByDesc(OpsSecretRotation::getId).last("limit 100"));
        Map<Long, OpsSecretRef> refById = new LinkedHashMap<>();
        secretRefs.forEach(ref -> refById.put(ref.getId(), ref));
        List<OpsVerificationRun> runs = verificationRuns.selectList(new LambdaQueryWrapper<OpsVerificationRun>()
                .eq(OpsVerificationRun::getSystemId, context.systemId()).orderByDesc(OpsVerificationRun::getId).last("limit 100"));
        return new OperationsSecurityModels.Overview(
                secretRefs.stream().map(this::secretView).toList(),
                rotationRows.stream().map(row -> rotationView(row, refById.get(row.getSecretRefId()))).toList(),
                runs.stream().filter(run -> "SECURITY".equals(run.getVerificationType())).map(this::runView).toList(),
                runs.stream().filter(run -> "PERFORMANCE".equals(run.getVerificationType())).map(this::runView).toList(),
                "明文仅在创建或准备轮换的响应中显示一次；列表、日志、审计和后续读回只显示引用与版本。",
                "仅执行有界真实样本；百万级容量和长并发压测需另行立项。/index/paging/timeout/queue/resource");
    }

    @Transactional
    public OperationsSecurityModels.OneTimeSecretIssue createSecret(
            AuthenticatedContext context, OperationsSecurityModels.CreateSecretRequest input, String requestId) {
        requireSystem(context);
        String code = input.secretCode().strip();
        String type = input.secretType().strip().toUpperCase(Locale.ROOT);
        if (!SECRET_TYPES.contains(type)) throw invalid("SECRET_TYPE_INVALID", "密钥类型不受支持");
        if (!input.confirmation().equals("CREATE SECRET " + code)) {
            throw invalid("SECRET_CONFIRMATION_MISMATCH", "确认文本必须为 CREATE SECRET " + code);
        }
        List<String> consumers = consumers(input.consumerCodes());
        OpsSecretRef existing = refs.selectList(new LambdaQueryWrapper<OpsSecretRef>()
                .eq(OpsSecretRef::getSystemId, context.systemId()).eq(OpsSecretRef::getSecretCode, code).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (existing != null) throw conflict("SECRET_REF_EXISTS", "同一系统已存在该密钥引用");

        LocalDateTime now = LocalDateTime.now();
        OpsSecretRef ref = new OpsSecretRef();
        ref.setContextType("SYSTEM");
        ref.setPlatformId(context.platformId());
        ref.setSystemId(context.systemId());
        ref.setSecretCode(code);
        ref.setProvider("INTERNAL_ENCRYPTED_" + type);
        ref.setReferencePath("ops-secret://system/" + context.systemId() + "/" + code + "/v1");
        ref.setCurrentVersion("v1");
        ref.setStatus("ACTIVE");
        ref.setLastVerifiedAt(now);
        ref.setCreatedAt(now);
        ref.setUpdatedAt(now);
        ref.setVersion(0);
        refs.insert(ref);

        String secret = issueSecret();
        OpsSecretRotation rotation = new OpsSecretRotation();
        rotation.setSecretRefId(ref.getId());
        rotation.setFromVersion("NONE");
        rotation.setToVersion("v1");
        rotation.setStatus("ACTIVE");
        rotation.setVerificationJson(json(materialEvidence(ref.getId(), "v1", secret, consumers,
                Map.of("approvalReference", input.approvalReference().strip(), "initialVersion", true,
                        "oldVersionStatus", "NONE", "switched", true))));
        rotation.setRequestedByAccountId(context.accountId());
        rotation.setStartedAt(now);
        rotation.setFinishedAt(now);
        rotation.setCreatedAt(now);
        rotation.setVersion(0);
        rotations.insert(rotation);
        audit(context, requestId, "OPERATIONS_SECRET_CREATED", "OPS_SECRET_REF", ref.getId(), "SUCCESS",
                Map.of("secretCode", code, "secretType", type, "version", "v1", "consumers", consumers,
                        "approvalReference", input.approvalReference().strip(), "plaintextStored", false));
        return issue(ref, rotation, secret);
    }

    @Transactional
    public OperationsSecurityModels.OneTimeSecretIssue prepareRotation(
            AuthenticatedContext context, long refId, OperationsSecurityModels.PrepareRotationRequest input,
            String requestId) {
        OpsSecretRef ref = requireRef(context, refId);
        if (!input.confirmation().equals("ROTATE " + ref.getSecretCode())) {
            throw invalid("SECRET_ROTATION_CONFIRMATION_MISMATCH",
                    "确认文本必须为 ROTATE " + ref.getSecretCode());
        }
        boolean pending = rotations.selectList(new LambdaQueryWrapper<OpsSecretRotation>()
                        .eq(OpsSecretRotation::getSecretRefId, refId).eq(OpsSecretRotation::getStatus, "AWAITING_CANARY"))
                .stream().findAny().isPresent();
        if (pending) throw conflict("SECRET_ROTATION_ALREADY_PENDING", "已有待灰度验证的新版本");
        List<String> consumers = consumers(input.consumerCodes());
        String target = "v" + (versionNumber(ref.getCurrentVersion()) + 1);
        String secret = issueSecret();
        LocalDateTime now = LocalDateTime.now();
        OpsSecretRotation rotation = new OpsSecretRotation();
        rotation.setSecretRefId(refId);
        rotation.setFromVersion(ref.getCurrentVersion());
        rotation.setToVersion(target);
        rotation.setStatus("AWAITING_CANARY");
        rotation.setVerificationJson(json(materialEvidence(refId, target, secret, consumers,
                Map.of("approvalReference", input.approvalReference().strip(), "oldVersionStatus", "ACTIVE",
                        "switched", false, "oneTimeSecretDisplayed", true))));
        rotation.setRequestedByAccountId(context.accountId());
        rotation.setStartedAt(now);
        rotation.setCreatedAt(now);
        rotation.setVersion(0);
        rotations.insert(rotation);
        ref.setStatus("ROTATION_PENDING");
        ref.setUpdatedAt(now);
        refs.updateById(ref);
        audit(context, requestId, "OPERATIONS_SECRET_ROTATION_PREPARED", "OPS_SECRET_ROTATION", rotation.getId(),
                "AWAITING_CANARY", Map.of("secretRefId", refId, "fromVersion", ref.getCurrentVersion(),
                        "toVersion", target, "consumers", consumers, "plaintextStored", false));
        return issue(ref, rotation, secret);
    }

    @Transactional
    public OperationsSecurityModels.RotationView activateRotation(
            AuthenticatedContext context, long rotationId, OperationsSecurityModels.ActivateRotationRequest input,
            String requestId) {
        requireSystem(context);
        OpsSecretRotation rotation = rotations.selectById(rotationId);
        if (rotation == null || !"AWAITING_CANARY".equals(rotation.getStatus())) {
            throw conflict("SECRET_ROTATION_NOT_PENDING", "轮换任务不存在或已结束");
        }
        OpsSecretRef ref = requireRef(context, rotation.getSecretRefId());
        if (!input.confirmation().equals("ACTIVATE ROTATION " + rotationId)) {
            throw invalid("SECRET_ACTIVATION_CONFIRMATION_MISMATCH",
                    "确认文本必须为 ACTIVATE ROTATION " + rotationId);
        }
        Map<String, Object> evidence = parse(rotation.getVerificationJson());
        evidence.put("securityRunId", input.securityRunId());
        evidence.put("consumerChecks", input.consumerChecks().stream().map(check -> Map.of(
                "consumerCode", check.consumerCode().strip(), "status", check.status(),
                "evidenceReference", check.evidenceReference().strip())).toList());
        OpsVerificationRun securityRun = verificationRuns.selectById(input.securityRunId());
        if (securityRun == null || !Objects.equals(securityRun.getSystemId(), context.systemId())
                || !"SECURITY".equals(securityRun.getVerificationType()) || !"PASSED".equals(securityRun.getStatus())) {
            return abortRotation(context, requestId, ref, rotation, evidence, "BLOCKED_SECURITY",
                    "SECURITY_BASELINE_NOT_PASSED");
        }
        Set<String> expected = new LinkedHashSet<>(strings(evidence.get("consumerCodes")));
        Map<String, String> provided = new LinkedHashMap<>();
        input.consumerChecks().forEach(check -> provided.put(check.consumerCode().strip(), check.status()));
        boolean canaryPassed = expected.equals(provided.keySet()) && provided.values().stream().allMatch("PASSED"::equals);
        if (!canaryPassed) {
            return abortRotation(context, requestId, ref, rotation, evidence, "FAILED_CANARY",
                    "CONSUMER_CANARY_FAILED");
        }
        try {
            String material = cipher.decrypt(String.valueOf(evidence.get("materialReference")), ref.getId(),
                    rotation.getToVersion());
            if (!fingerprint(material).equals(evidence.get("materialFingerprint"))) {
                return abortRotation(context, requestId, ref, rotation, evidence, "FAILED_CANARY",
                        "SECRET_MATERIAL_INTEGRITY_FAILED");
            }
        } catch (RuntimeException exception) {
            return abortRotation(context, requestId, ref, rotation, evidence, "FAILED_CANARY",
                    "SECRET_MATERIAL_INTEGRITY_FAILED");
        }

        rotations.selectList(new LambdaQueryWrapper<OpsSecretRotation>()
                        .eq(OpsSecretRotation::getSecretRefId, ref.getId())
                        .eq(OpsSecretRotation::getToVersion, rotation.getFromVersion()).orderByDesc(OpsSecretRotation::getId)
                        .last("limit 1"))
                .stream().findFirst().ifPresent(previous -> {
                    previous.setStatus("DISABLED");
                    rotations.updateById(previous);
                });
        LocalDateTime now = LocalDateTime.now();
        evidence.put("switched", true);
        evidence.put("activeVersion", rotation.getToVersion());
        evidence.put("oldVersionStatus", "DISABLED");
        evidence.put("activatedAt", format(now));
        rotation.setStatus("ACTIVE");
        rotation.setVerificationJson(json(evidence));
        rotation.setFinishedAt(now);
        rotations.updateById(rotation);
        ref.setCurrentVersion(rotation.getToVersion());
        ref.setReferencePath("ops-secret://system/" + context.systemId() + "/" + ref.getSecretCode() + "/"
                + rotation.getToVersion());
        ref.setStatus("ACTIVE");
        ref.setLastVerifiedAt(now);
        ref.setUpdatedAt(now);
        refs.updateById(ref);
        audit(context, requestId, "OPERATIONS_SECRET_ROTATION_ACTIVATED", "OPS_SECRET_ROTATION", rotationId,
                "SUCCESS", Map.of("secretRefId", ref.getId(), "fromVersion", rotation.getFromVersion(),
                        "toVersion", rotation.getToVersion(), "oldVersionStatus", "DISABLED",
                        "securityRunId", input.securityRunId(), "plaintextExposed", false));
        return rotationView(rotation, ref);
    }

    @Transactional
    public OperationsSecurityModels.VerificationRunView runSecurity(
            AuthenticatedContext context, OperationsSecurityModels.SecurityRunRequest input, String requestId) {
        requireSystem(context);
        if (!"RUN SECURITY BASELINE".equals(input.confirmation())) {
            throw invalid("SECURITY_CONFIRMATION_MISMATCH", "确认文本必须为 RUN SECURITY BASELINE");
        }
        OpsVerificationRun run = startRun(context, "SECURITY", "SECURITY_BASELINE",
                Map.of("approvalReference", input.approvalReference().strip(), "requestId", requestId),
                Map.of("policy", "NO_PLAINTEXT_STRONG_HASH_SIGNED_ACCESS_SCOPE_ISOLATION"));
        List<Map<String, Object>> checks = new ArrayList<>();
        for (StartupPreflightChecks.Check check : preflight.inspect()) {
            checks.add(Map.of("code", check.code(), "status", check.status(), "message", check.message(),
                    "metric", check.metric()));
            if (!check.passed()) addFinding(run, "CRITICAL", "SECURITY_" + check.code(),
                    "安全基线阻断：" + check.name(), check.message(), check.metric());
        }
        OperationsSecurityProbeRepository.SecuritySignals signals = probes.securitySignals(context.systemId());
        checkCount(run, checks, "PASSWORD_HASH", "账号密码存在弱散列或非散列值", signals.weakPasswords());
        checkCount(run, checks, "APPLICATION_SECRET_REFERENCE", "启用中的应用凭证缺少加密引用",
                signals.unreferencedApplicationSecrets());
        checkCount(run, checks, "SYSTEM_TENANT_ISOLATION", "系统与租户成员边界存在孤立映射",
                signals.boundaryViolations());
        boolean passed = findingRows(run.getId()).isEmpty();
        run.setStatus(passed ? "PASSED" : "FAILED");
        run.setResultJson(json(Map.of("passed", passed, "checkCount", checks.size(), "checks", checks,
                "rotationSwitchAllowed", passed)));
        run.setFinishedAt(LocalDateTime.now());
        verificationRuns.updateById(run);
        audit(context, requestId, "OPERATIONS_SECURITY_BASELINE_EXECUTED", "OPS_VERIFICATION_RUN", run.getId(),
                run.getStatus(), Map.of("checkCount", checks.size(), "findingCount", findingRows(run.getId()).size(),
                        "rotationSwitchAllowed", passed));
        return runView(run);
    }

    @Transactional
    public OperationsSecurityModels.VerificationRunView runPerformance(
            AuthenticatedContext context, OperationsSecurityModels.PerformanceRunRequest input, String requestId) {
        requireSystem(context);
        Map<String, Object> thresholds = Map.of(
                "LIST_QUERY", input.listThresholdMillis(), "FILE_STREAM", input.fileThresholdMillis(),
                "JOB_QUEUE", input.queueThresholdMillis(), "STATISTICS", input.statisticsThresholdMillis(),
                "timeoutMillis", input.timeoutMillis());
        OpsVerificationRun run = startRun(context, "PERFORMANCE", "BOUNDED_CRITICAL_PATHS",
                Map.of("pageSize", input.pageSize(), "fileProbeBytes", input.fileProbeBytes(),
                        "approvalReference", input.approvalReference().strip(), "requestId", requestId,
                        "sampleMode", "BOUNDED_REAL_DEPENDENCIES"), thresholds);
        List<Map<String, Object>> results = new ArrayList<>();
        evaluateProbe(run, results, "LIST_QUERY", input.listThresholdMillis(),
                () -> probes.listQuery(context.systemId(), context.tenantId(), input.pageSize(), input.timeoutMillis()));
        evaluateProbe(run, results, "FILE_STREAM", input.fileThresholdMillis(),
                () -> probes.fileStream(input.fileProbeBytes(), input.timeoutMillis()));
        evaluateProbe(run, results, "JOB_QUEUE", input.queueThresholdMillis(),
                () -> probes.jobQueue(context.systemId(), context.tenantId(), input.timeoutMillis()));
        evaluateProbe(run, results, "STATISTICS", input.statisticsThresholdMillis(),
                () -> probes.statistics(context.systemId(), context.tenantId(), input.timeoutMillis()));
        int findingCount = findingRows(run.getId()).size();
        run.setStatus(findingCount == 0 ? "PASSED" : "THRESHOLD_EXCEEDED");
        run.setResultJson(json(Map.of("passed", findingCount == 0, "paths", results,
                "optimizationTaskCount", findingCount, "boundedSample", true)));
        run.setFinishedAt(LocalDateTime.now());
        verificationRuns.updateById(run);
        audit(context, requestId, "OPERATIONS_PERFORMANCE_BASELINE_EXECUTED", "OPS_VERIFICATION_RUN", run.getId(),
                run.getStatus(), Map.of("pathCount", results.size(), "optimizationTaskCount", findingCount,
                        "boundedSample", true));
        return runView(run);
    }

    private void evaluateProbe(OpsVerificationRun run, List<Map<String, Object>> results, String code,
                               long thresholdMillis, Supplier<OperationsSecurityProbeRepository.ProbeResult> supplier) {
        try {
            OperationsSecurityProbeRepository.ProbeResult probe = supplier.get();
            boolean passed = probe.latencyMillis() <= thresholdMillis;
            Map<String, Object> value = new LinkedHashMap<>(probe.metric());
            value.put("code", code);
            value.put("thresholdMillis", thresholdMillis);
            value.put("status", passed ? "PASSED" : "THRESHOLD_EXCEEDED");
            results.add(value);
            if (!passed) addFinding(run, "HIGH", "PERFORMANCE_" + code + "_EXCEEDED",
                    "优化任务：" + pathName(code) + "超过基线",
                    "实际 " + probe.latencyMillis() + "ms，阈值 " + thresholdMillis + "ms",
                    Map.of("path", code, "latencyMillis", probe.latencyMillis(),
                            "thresholdMillis", thresholdMillis, "metric", probe.metric()));
        } catch (RuntimeException exception) {
            Map<String, Object> value = Map.of("code", code, "status", "FAILED", "thresholdMillis", thresholdMillis,
                    "failureType", exception.getClass().getSimpleName());
            results.add(value);
            addFinding(run, "CRITICAL", "PERFORMANCE_" + code + "_FAILED",
                    "优化任务：" + pathName(code) + "探针失败", "真实依赖探针未完成",
                    Map.of("path", code, "failureType", exception.getClass().getSimpleName()));
        }
    }

    private OperationsSecurityModels.RotationView abortRotation(
            AuthenticatedContext context, String requestId, OpsSecretRef ref, OpsSecretRotation rotation,
            Map<String, Object> evidence, String status, String failureCode) {
        evidence.remove("materialReference");
        evidence.put("materialDestroyed", true);
        evidence.put("failureCode", failureCode);
        evidence.put("switched", false);
        evidence.put("activeVersion", rotation.getFromVersion());
        evidence.put("oldVersionStatus", "ACTIVE");
        rotation.setStatus(status);
        rotation.setVerificationJson(json(evidence));
        rotation.setFinishedAt(LocalDateTime.now());
        rotations.updateById(rotation);
        ref.setStatus("ACTIVE");
        ref.setUpdatedAt(LocalDateTime.now());
        refs.updateById(ref);
        audit(context, requestId, "OPERATIONS_SECRET_ROTATION_BLOCKED", "OPS_SECRET_ROTATION", rotation.getId(),
                failureCode, Map.of("secretRefId", ref.getId(), "activeVersion", ref.getCurrentVersion(),
                        "targetVersion", rotation.getToVersion(), "oldVersionStatus", "ACTIVE",
                        "materialDestroyed", true));
        return rotationView(rotation, ref);
    }

    private OpsVerificationRun startRun(AuthenticatedContext context, String type, String scenario,
                                        Map<String, Object> input, Map<String, Object> threshold) {
        OpsVerificationRun run = new OpsVerificationRun();
        run.setVerificationType(type);
        run.setContextType("SYSTEM");
        run.setSystemId(context.systemId());
        run.setScenarioCode(scenario);
        run.setStatus("RUNNING");
        run.setInputJson(json(input));
        run.setThresholdJson(json(threshold));
        run.setStartedByAccountId(context.accountId());
        run.setStartedAt(LocalDateTime.now());
        verificationRuns.insert(run);
        return run;
    }

    private void checkCount(OpsVerificationRun run, List<Map<String, Object>> checks, String code,
                            String failureTitle, long failedCount) {
        checks.add(Map.of("code", code, "status", failedCount == 0 ? "PASSED" : "FAILED",
                "failedCount", failedCount, "valueExposed", false));
        if (failedCount > 0) addFinding(run, "CRITICAL", "SECURITY_" + code,
                "安全基线阻断：" + failureTitle, failureTitle,
                Map.of("failedCount", failedCount, "valueExposed", false));
    }

    private void addFinding(OpsVerificationRun run, String severity, String code, String title,
                            String detail, Map<String, Object> evidence) {
        OpsVerificationFinding finding = new OpsVerificationFinding();
        finding.setVerificationRunId(run.getId());
        finding.setSeverity(severity);
        finding.setFindingCode(code);
        finding.setTitle(title);
        finding.setDetailText(detail);
        finding.setEvidenceJson(json(evidence));
        finding.setStatus("OPEN");
        finding.setCreatedAt(LocalDateTime.now());
        finding.setVersion(0);
        findings.insert(finding);
    }

    private OperationsSecurityModels.OneTimeSecretIssue issue(
            OpsSecretRef ref, OpsSecretRotation rotation, String secret) {
        return new OperationsSecurityModels.OneTimeSecretIssue(secretView(ref), rotationView(rotation, ref), secret,
                true, "请立即复制到目标使用方。关闭后无法再次读取，系统仅持久化加密材料引用、版本和审计。" );
    }

    private Map<String, Object> materialEvidence(long refId, String version, String secret,
                                                 List<String> consumers, Map<String, Object> extra) {
        Map<String, Object> evidence = new LinkedHashMap<>(extra);
        evidence.put("consumerCodes", consumers);
        evidence.put("materialReference", cipher.encrypt(secret, refId, version));
        evidence.put("materialFingerprint", fingerprint(secret));
        evidence.put("plaintextStored", false);
        return evidence;
    }

    private String issueSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private OperationsSecurityModels.SecretRefView secretView(OpsSecretRef ref) {
        String type = ref.getProvider().replace("INTERNAL_ENCRYPTED_", "");
        return new OperationsSecurityModels.SecretRefView(ref.getId(), ref.getSecretCode(), type,
                "INTERNAL_ENCRYPTED", ref.getReferencePath(), ref.getCurrentVersion(), ref.getStatus(),
                format(ref.getLastVerifiedAt()), format(ref.getUpdatedAt()));
    }

    private OperationsSecurityModels.RotationView rotationView(OpsSecretRotation rotation, OpsSecretRef ref) {
        Map<String, Object> evidence = parse(rotation.getVerificationJson());
        return new OperationsSecurityModels.RotationView(rotation.getId(), rotation.getSecretRefId(),
                ref == null ? "" : ref.getSecretCode(), rotation.getFromVersion(), rotation.getToVersion(),
                rotation.getStatus(), Boolean.TRUE.equals(evidence.get("switched")),
                text(evidence.getOrDefault("activeVersion", ref == null ? rotation.getFromVersion() : ref.getCurrentVersion())),
                strings(evidence.get("consumerCodes")), maps(evidence.get("consumerChecks")),
                number(evidence.get("securityRunId")), text(evidence.getOrDefault("oldVersionStatus", "ACTIVE")),
                text(evidence.get("failureCode")), format(rotation.getCreatedAt()), format(rotation.getFinishedAt()));
    }

    private OperationsSecurityModels.VerificationRunView runView(OpsVerificationRun run) {
        return new OperationsSecurityModels.VerificationRunView(run.getId(), run.getVerificationType(),
                run.getScenarioCode(), run.getStatus(), parse(run.getInputJson()), parse(run.getResultJson()),
                parse(run.getThresholdJson()), format(run.getStartedAt()), format(run.getFinishedAt()),
                findingRows(run.getId()).stream().map(this::findingView).toList());
    }

    private OperationsSecurityModels.VerificationFindingView findingView(OpsVerificationFinding finding) {
        return new OperationsSecurityModels.VerificationFindingView(finding.getId(), finding.getSeverity(),
                finding.getFindingCode(), finding.getTitle(), finding.getDetailText(), parse(finding.getEvidenceJson()),
                finding.getStatus(), format(finding.getCreatedAt()));
    }

    private List<OpsVerificationFinding> findingRows(long runId) {
        return findings.selectList(new LambdaQueryWrapper<OpsVerificationFinding>()
                .eq(OpsVerificationFinding::getVerificationRunId, runId).orderByAsc(OpsVerificationFinding::getId));
    }

    private OpsSecretRef requireRef(AuthenticatedContext context, long refId) {
        requireSystem(context);
        OpsSecretRef ref = refs.selectById(refId);
        if (ref == null || !Objects.equals(ref.getSystemId(), context.systemId())) {
            throw new DomainException("SECRET_REF_NOT_FOUND", "密钥引用不存在", HttpStatus.NOT_FOUND);
        }
        return ref;
    }

    private List<String> consumers(List<String> values) {
        List<String> result = values.stream().map(String::strip).filter(value -> !value.isBlank()).distinct().toList();
        if (result.isEmpty()) throw invalid("SECRET_CONSUMERS_REQUIRED", "至少需要一个明确使用方");
        return result;
    }

    private int versionNumber(String version) {
        try {
            return Integer.parseInt(version.substring(1));
        } catch (RuntimeException exception) {
            throw conflict("SECRET_VERSION_INVALID", "当前密钥版本格式无效");
        }
    }

    private String pathName(String code) {
        return switch (code) {
            case "LIST_QUERY" -> "列表查询";
            case "FILE_STREAM" -> "文件流";
            case "JOB_QUEUE" -> "后台作业队列";
            case "STATISTICS" -> "统计聚合";
            default -> code;
        };
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先切换到系统和租户上下文", HttpStatus.CONFLICT);
        }
    }

    private void audit(AuthenticatedContext context, String requestId, String eventCode, String objectType,
                       Object objectId, String resultCode, Map<String, ?> detail) {
        auditRecorder.recordWithPermissionSnapshot(requestId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), eventCode, objectType, String.valueOf(objectId), resultCode,
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions()), detail);
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.BAD_REQUEST);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize operations evidence", exception);
        }
    }

    private Map<String, Object> parse(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot parse operations evidence", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }
}
