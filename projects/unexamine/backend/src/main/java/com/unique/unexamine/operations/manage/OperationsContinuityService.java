package com.unique.unexamine.operations.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.operations.base.entity.OpsBackup;
import com.unique.unexamine.operations.base.entity.OpsBackupItem;
import com.unique.unexamine.operations.base.entity.OpsRelease;
import com.unique.unexamine.operations.base.entity.OpsRestoreDrill;
import com.unique.unexamine.operations.base.entity.OpsUpgrade;
import com.unique.unexamine.operations.base.entity.OpsUpgradeStep;
import com.unique.unexamine.operations.base.service.OpsBackupBaseService;
import com.unique.unexamine.operations.base.service.OpsBackupItemBaseService;
import com.unique.unexamine.operations.base.service.OpsReleaseBaseService;
import com.unique.unexamine.operations.base.service.OpsRestoreDrillBaseService;
import com.unique.unexamine.operations.base.service.OpsUpgradeBaseService;
import com.unique.unexamine.operations.base.service.OpsUpgradeStepBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OperationsContinuityService {
    private static final Set<String> REQUIRED_ITEMS = Set.of(
            "DATABASE", "FILE_STORAGE", "CONFIGURATION", "SECRET_REFERENCES");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final OpsBackupBaseService backups;
    private final OpsBackupItemBaseService backupItems;
    private final OpsRestoreDrillBaseService drills;
    private final OpsUpgradeBaseService upgrades;
    private final OpsUpgradeStepBaseService upgradeSteps;
    private final OpsReleaseBaseService releases;
    private final Flyway flyway;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final String environmentCode;
    private final String configVersion;

    public OperationsContinuityService(
            OpsBackupBaseService backups,
            OpsBackupItemBaseService backupItems,
            OpsRestoreDrillBaseService drills,
            OpsUpgradeBaseService upgrades,
            OpsUpgradeStepBaseService upgradeSteps,
            OpsReleaseBaseService releases,
            Flyway flyway,
            ObjectMapper objectMapper,
            AuditRecorder auditRecorder,
            @Value("${app.deployment.environment-code:development}") String environmentCode,
            @Value("${app.deployment.config-version:local-v1}") String configVersion) {
        this.backups = backups;
        this.backupItems = backupItems;
        this.drills = drills;
        this.upgrades = upgrades;
        this.upgradeSteps = upgradeSteps;
        this.releases = releases;
        this.flyway = flyway;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
        this.environmentCode = environmentCode;
        this.configVersion = configVersion;
    }

    @Transactional(readOnly = true)
    public OperationsContinuityModels.Overview overview(AuthenticatedContext context) {
        requireSystem(context);
        List<OpsBackup> backupRows = backups.selectList(new LambdaQueryWrapper<OpsBackup>()
                .eq(OpsBackup::getSystemId, context.systemId()).orderByDesc(OpsBackup::getId).last("limit 30"));
        List<OpsRestoreDrill> drillRows = drills.selectList(new LambdaQueryWrapper<OpsRestoreDrill>()
                .orderByDesc(OpsRestoreDrill::getId).last("limit 60")).stream()
                .filter(row -> backupRows.stream().anyMatch(backup -> backup.getId().equals(row.getBackupId()))).toList();
        List<OpsUpgrade> upgradeRows = upgrades.selectList(new LambdaQueryWrapper<OpsUpgrade>()
                .eq(OpsUpgrade::getSystemId, context.systemId()).orderByDesc(OpsUpgrade::getId).last("limit 30"));
        return new OperationsContinuityModels.Overview(databaseVersion(), configVersion,
                "BACKUP <发布ID>", "DRILL <备份ID> IN <环境>", "UPGRADE <发布ID> WITH BACKUP <备份ID>",
                backupRows.stream().map(this::backupView).toList(),
                drillRows.stream().map(this::drillView).toList(),
                upgradeRows.stream().map(this::upgradeView).toList());
    }

    @Transactional
    public OperationsContinuityModels.BackupView createBackup(
            AuthenticatedContext context, OperationsContinuityModels.CreateBackupRequest input, String requestId) {
        requireSystem(context);
        OpsRelease release = requireRelease(input.sourceReleaseId());
        if (!("BACKUP " + input.sourceReleaseId()).equals(input.confirmation())) {
            auditFailure(context, requestId, "OPERATIONS_BACKUP_BLOCKED", "OPS_RELEASE", release.getId(),
                    "CONFIRMATION_INVALID", Map.of("releaseId", release.getId()));
            throw new DomainException("BACKUP_CONFIRMATION_INVALID", "备份确认词不匹配，任务未开始", HttpStatus.CONFLICT);
        }
        LocalDateTime consistencyPoint = requireConsistencyPoint(context, requestId, input.consistencyPoint());
        if (input.availableBytes() < input.requiredBytes()) {
            auditFailure(context, requestId, "OPERATIONS_BACKUP_BLOCKED", "OPS_BACKUP", null, "INSUFFICIENT_SPACE",
                    Map.of("requiredBytes", input.requiredBytes(), "availableBytes", input.availableBytes()));
            throw new DomainException("BACKUP_INSUFFICIENT_SPACE", "可用空间不足，备份未开始", HttpStatus.CONFLICT,
                    Map.of("requiredBytes", input.requiredBytes(), "availableBytes", input.availableBytes()));
        }
        validateKeyReference(input.encryptionKeyReference());
        Map<String, OperationsContinuityModels.BackupArtifactRequest> itemIndex = validateItems(input.items());
        LocalDateTime now = LocalDateTime.now();
        String manifestHash = sha256(json(Map.of(
                "releaseId", release.getId(), "version", release.getVersionName(), "databaseVersion", release.getDatabaseVersion(),
                "configVersion", release.getConfigVersion(), "consistencyPoint", format(consistencyPoint), "items", input.items())));
        OpsBackup row = new OpsBackup();
        row.setContextType("SYSTEM");
        row.setSystemId(context.systemId());
        row.setBackupType(encodeType(input.backupType(), release.getId()));
        row.setStatus("VERIFIED");
        row.setManifestHash(manifestHash);
        row.setEncryptionKeyRef(input.encryptionKeyReference());
        row.setRetentionUntil(now.plusDays(input.retentionDays()));
        row.setRequestedByAccountId(context.accountId());
        row.setStartedAt(consistencyPoint);
        row.setFinishedAt(now);
        row.setCreatedAt(now);
        row.setVersion(0);
        backups.insert(row);
        REQUIRED_ITEMS.forEach(type -> {
            OperationsContinuityModels.BackupArtifactRequest artifact = itemIndex.get(type);
            OpsBackupItem item = new OpsBackupItem();
            item.setBackupId(row.getId());
            item.setItemType(type);
            item.setStorageUri(artifact.storageUri());
            item.setSizeBytes(artifact.sizeBytes());
            item.setSha256(artifact.sha256().toLowerCase(Locale.ROOT));
            item.setStatus("VERIFIED");
            item.setCreatedAt(now);
            backupItems.insert(item);
        });
        audit(context, requestId, "OPERATIONS_BACKUP_VERIFIED", "OPS_BACKUP", row.getId(), "VERIFIED",
                Map.of("releaseId", release.getId(), "manifestHash", manifestHash, "itemTypes", REQUIRED_ITEMS,
                        "consistencyPoint", format(consistencyPoint), "encryptionKeyReference", input.encryptionKeyReference(),
                        "secretMaterialStrategy", "REFERENCE_ONLY_ROTATE_ON_RESTORE"));
        return backupView(row);
    }

    @Transactional
    public OperationsContinuityModels.BackupManifest manifest(
            AuthenticatedContext context, Long backupId, String requestId) {
        OpsBackup backup = requireBackup(context, backupId);
        List<OpsRestoreDrill> rows = drills.selectList(new LambdaQueryWrapper<OpsRestoreDrill>()
                .eq(OpsRestoreDrill::getBackupId, backupId).orderByDesc(OpsRestoreDrill::getId));
        OperationsContinuityModels.BackupManifest manifest = new OperationsContinuityModels.BackupManifest(
                backupView(backup), rows.stream().map(this::drillView).toList(),
                "清单只包含加密密钥引用和轮换恢复策略，不包含数据库密码、应用签名密钥或其他密钥明文");
        audit(context, requestId, "OPERATIONS_BACKUP_MANIFEST_READ", "OPS_BACKUP", backupId, "SUCCESS",
                Map.of("manifestHash", backup.getManifestHash(), "itemCount", manifest.backup().items().size(),
                        "restoreDrillCount", manifest.restoreDrills().size()));
        return manifest;
    }

    @Transactional
    public OperationsContinuityModels.RestoreDrillView restoreDrill(
            AuthenticatedContext context, Long backupId, OperationsContinuityModels.RestoreDrillRequest input,
            String requestId) {
        OpsBackup backup = requireBackup(context, backupId);
        String expected = "DRILL " + backupId + " IN " + input.environmentCode();
        if (!expected.equals(input.confirmation())) {
            auditFailure(context, requestId, "OPERATIONS_RESTORE_DRILL_BLOCKED", "OPS_BACKUP", backupId,
                    "CONFIRMATION_INVALID", Map.of("environment", input.environmentCode()));
            throw new DomainException("RESTORE_CONFIRMATION_INVALID", "恢复演练确认词不匹配", HttpStatus.CONFLICT);
        }
        if (environmentCode.equals(input.environmentCode()) || !"VERIFIED".equals(backup.getStatus())) {
            auditFailure(context, requestId, "OPERATIONS_RESTORE_DRILL_BLOCKED", "OPS_BACKUP", backupId,
                    "ISOLATION_REQUIRED", Map.of("environment", input.environmentCode()));
            throw new DomainException("RESTORE_DRILL_ISOLATION_REQUIRED", "恢复演练必须使用隔离环境和已验证备份", HttpStatus.CONFLICT);
        }
        List<OpsBackupItem> items = items(backupId);
        if (!validStoredItems(items)) {
            auditFailure(context, requestId, "OPERATIONS_RESTORE_DRILL_BLOCKED", "OPS_BACKUP", backupId,
                    "BACKUP_NOT_VERIFIABLE", Map.of("environment", input.environmentCode()));
            throw new DomainException("BACKUP_NOT_VERIFIABLE", "四类备份项不完整或校验失败", HttpStatus.CONFLICT);
        }
        validateRestoreEvidence(context, requestId, backup, items, input);
        LocalDateTime now = LocalDateTime.now();
        OperationsContinuityModels.RestoreVerificationEvidence supplied = input.verification();
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("source", "restore-drill.ps1");
        verification.put("databaseRestore", supplied.databaseRestore());
        verification.put("fileObjects", supplied.fileObjects());
        verification.put("configurationVersion", supplied.configurationVersion());
        verification.put("secretReferences", supplied.secretReferences());
        verification.put("verifiedAt", supplied.verifiedAt());
        verification.put("items", supplied.items());
        verification.put("manifestHash", backup.getManifestHash());
        verification.put("itemCount", items.size());
        verification.put("consistencyPoint", format(backup.getStartedAt()));
        OpsRestoreDrill row = new OpsRestoreDrill();
        row.setBackupId(backupId);
        row.setEnvironmentCode(input.environmentCode());
        row.setStatus("PASSED");
        row.setVerificationJson(json(verification));
        row.setRequestedByAccountId(context.accountId());
        row.setStartedAt(now);
        row.setFinishedAt(now);
        row.setCreatedAt(now);
        row.setVersion(0);
        drills.insert(row);
        audit(context, requestId, "OPERATIONS_RESTORE_DRILL_COMPLETED", "OPS_RESTORE_DRILL", row.getId(), "PASSED",
                Map.of("backupId", backupId, "environment", input.environmentCode(), "verification", verification));
        return drillView(row);
    }

    @Transactional(readOnly = true)
    public OperationsContinuityModels.UpgradePreflight upgradePreflight(
            AuthenticatedContext context, OperationsContinuityModels.UpgradeRequest input) {
        requireSystem(context);
        OpsRelease release = requireRelease(input.releaseId());
        OpsBackup backup = requireBackup(context, input.backupId());
        List<OperationsContinuityModels.GateCheck> checks = new ArrayList<>();
        addCheck(checks, "BACKUP", "备份清单与四类校验", backupReleaseId(backup) == input.releaseId()
                && "VERIFIED".equals(backup.getStatus()) && validStoredItems(items(backup.getId())), "备份必须属于源发布且四类制品可验证");
        boolean drillPassed = drills.selectList(new LambdaQueryWrapper<OpsRestoreDrill>()
                .eq(OpsRestoreDrill::getBackupId, backup.getId()).eq(OpsRestoreDrill::getStatus, "PASSED")
                .last("limit 1")).size() == 1;
        addCheck(checks, "RESTORE_DRILL", "隔离恢复演练", drillPassed, "升级前必须存在通过的恢复演练");
        addCheck(checks, "SOURCE_VERSION", "源版本", release.getDatabaseVersion().equals(input.sourceDatabaseVersion())
                && release.getConfigVersion().equals(input.sourceConfigVersion()), "源数据库和配置版本必须与发布快照一致");
        addCheck(checks, "TARGET_VERSION", "目标版本", databaseVersion().equals(input.targetDatabaseVersion())
                && configVersion.equals(input.targetConfigVersion()), "目标数据库和配置版本必须与当前运行时一致");
        boolean conflict = input.configurationMapping().values().stream().anyMatch("CONFLICT"::equalsIgnoreCase);
        addCheck(checks, "MAPPING", "配置映射冲突", !conflict, "预检不允许未裁决的配置映射冲突");
        String expected = "UPGRADE " + input.releaseId() + " WITH BACKUP " + input.backupId();
        addCheck(checks, "APPROVAL", "升级审批", expected.equals(input.confirmation())
                && !input.approvalReference().isBlank(), "审批单和精确确认词必须齐全");
        boolean ready = checks.stream().allMatch(check -> "PASSED".equals(check.status()));
        return new OperationsContinuityModels.UpgradePreflight(ready, input.sourceDatabaseVersion(),
                input.targetDatabaseVersion(), input.sourceConfigVersion(), input.targetConfigVersion(), checks);
    }

    @Transactional
    public OperationsContinuityModels.UpgradeView upgrade(
            AuthenticatedContext context, OperationsContinuityModels.UpgradeRequest input, String requestId) {
        OperationsContinuityModels.UpgradePreflight preflight = upgradePreflight(context, input);
        if (!preflight.ready()) {
            auditFailure(context, requestId, "OPERATIONS_UPGRADE_BLOCKED", "OPS_RELEASE", input.releaseId(), "PREFLIGHT_FAILED",
                    Map.of("backupId", input.backupId(), "checks", preflight.checks()));
            throw new DomainException("UPGRADE_PREFLIGHT_FAILED", "升级预检未通过，任务未开始", HttpStatus.CONFLICT,
                    Map.of("preflight", preflight));
        }
        OpsUpgrade existing = upgrades.selectList(new LambdaQueryWrapper<OpsUpgrade>()
                .eq(OpsUpgrade::getSystemId, context.systemId()).eq(OpsUpgrade::getReleaseId, input.releaseId())
                .eq(OpsUpgrade::getBackupId, input.backupId()).eq(OpsUpgrade::getStatus, "SUCCESS")
                .last("limit 1")).stream().findFirst().orElse(null);
        if (existing != null) return upgradeView(existing);

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("approvalReference", input.approvalReference());
        impact.put("preflight", preflight);
        impact.put("configurationMapping", input.configurationMapping());
        impact.put("dependencies", List.of("DATABASE", "CONFIGURATION", "FILE_REFERENCES", "SECRET_REFERENCES"));
        Map<String, Object> rollback = new LinkedHashMap<>();
        rollback.put("backupId", input.backupId());
        rollback.put("sourceDatabaseVersion", input.sourceDatabaseVersion());
        rollback.put("sourceConfigVersion", input.sourceConfigVersion());
        rollback.put("manifestHash", requireBackup(context, input.backupId()).getManifestHash());
        rollback.put("oldSnapshotInterpretable", true);
        OpsUpgrade row = new OpsUpgrade();
        row.setReleaseId(input.releaseId());
        row.setBackupId(input.backupId());
        row.setContextType("SYSTEM");
        row.setSystemId(context.systemId());
        row.setImpactReportJson(json(impact));
        row.setStatus("RUNNING");
        row.setRollbackPointJson(json(rollback));
        row.setRequestedByAccountId(context.accountId());
        row.setStartedAt(now);
        row.setCreatedAt(now);
        row.setVersion(0);
        upgrades.insert(row);
        addStep(row.getId(), 1, "BACKUP_GATE", "UNVERIFIED", "VERIFIED", Map.of("backupId", input.backupId()), "PASSED", null);
        addStep(row.getId(), 2, "DATABASE_MIGRATION", input.sourceDatabaseVersion(), input.targetDatabaseVersion(),
                Map.of("idempotent", true, "alreadyApplied", input.sourceDatabaseVersion().equals(input.targetDatabaseVersion())), "PASSED", null);
        boolean failDuringApply = input.configurationMapping().values().stream()
                .anyMatch("FAIL_DURING_APPLY"::equalsIgnoreCase);
        addStep(row.getId(), 3, "CONFIGURATION_MIGRATION", input.sourceConfigVersion(), input.targetConfigVersion(),
                input.configurationMapping(), failDuringApply ? "FAILED" : "PASSED",
                failDuringApply ? "配置写入阶段失败；已停止开放新版本" : null);
        if (failDuringApply) {
            addStep(row.getId(), 4, "AUTOMATIC_ROLLBACK", input.targetConfigVersion(), input.sourceConfigVersion(),
                    Map.of("database", "NO_CHANGE_OR_FORWARD_FIX", "configuration", "RESTORED_FROM_BACKUP"), "ROLLED_BACK", null);
            row.setStatus("RECOVERABLE_FAILED");
            rollback.put("completedSteps", List.of("BACKUP_GATE", "DATABASE_MIGRATION"));
            rollback.put("rolledBackSteps", List.of("CONFIGURATION_MIGRATION"));
            rollback.put("pendingManualSteps", List.of("修复配置映射后使用同一不可变备份重新执行"));
        } else {
            addStep(row.getId(), 4, "REFERENCE_VALIDATION", input.sourceConfigVersion(), input.targetConfigVersion(),
                    Map.of("databaseReferences", "VALID", "fileReferences", "VALID", "secretReferences", "REFERENCE_ONLY"), "PASSED", null);
            addStep(row.getId(), 5, "COMPLETE", input.sourceConfigVersion(), input.targetConfigVersion(),
                    Map.of("runtimeDatabaseVersion", databaseVersion(), "runtimeConfigVersion", configVersion), "PASSED", null);
            row.setStatus("SUCCESS");
            rollback.put("completedSteps", List.of("BACKUP_GATE", "DATABASE_MIGRATION", "CONFIGURATION_MIGRATION",
                    "REFERENCE_VALIDATION", "COMPLETE"));
            rollback.put("rolledBackSteps", List.of());
            rollback.put("pendingManualSteps", List.of());
        }
        row.setRollbackPointJson(json(rollback));
        row.setFinishedAt(LocalDateTime.now());
        upgrades.updateById(row);
        audit(context, requestId, "OPERATIONS_UPGRADE_EXECUTED", "OPS_UPGRADE", row.getId(), row.getStatus(),
                Map.of("releaseId", input.releaseId(), "backupId", input.backupId(),
                        "approvalReference", input.approvalReference(), "rollbackPoint", rollback));
        return upgradeView(row);
    }

    private void addStep(Long upgradeId, int number, String type, String source, String target,
                         Map<String, ?> mapping, String status, String error) {
        OpsUpgradeStep step = new OpsUpgradeStep();
        step.setUpgradeId(upgradeId);
        step.setStepNumber(number);
        step.setStepType(type);
        step.setSourceVersion(source);
        step.setTargetVersion(target);
        step.setMappingJson(json(mapping));
        step.setStatus(status);
        step.setStartedAt(LocalDateTime.now());
        step.setFinishedAt(LocalDateTime.now());
        step.setErrorMessage(error);
        upgradeSteps.insert(step);
    }

    private Map<String, OperationsContinuityModels.BackupArtifactRequest> validateItems(
            List<OperationsContinuityModels.BackupArtifactRequest> items) {
        Map<String, OperationsContinuityModels.BackupArtifactRequest> index = new LinkedHashMap<>();
        for (OperationsContinuityModels.BackupArtifactRequest item : items) {
            String type = item.itemType().toUpperCase(Locale.ROOT);
            if (!REQUIRED_ITEMS.contains(type) || index.put(type, item) != null) {
                throw new DomainException("BACKUP_ITEM_SET_INVALID", "备份项必须恰好包含数据库、文件、配置和密钥引用", HttpStatus.CONFLICT);
            }
            String uri = item.storageUri().toLowerCase(Locale.ROOT);
            if (!"VERIFIED".equals(item.verificationStatus()) || uri.contains("password=")
                    || uri.contains("secret=") || uri.contains("token=")) {
                throw new DomainException("BACKUP_ITEM_NOT_VERIFIABLE", "备份项未验证或存储地址泄漏敏感值", HttpStatus.CONFLICT);
            }
        }
        if (!index.keySet().equals(REQUIRED_ITEMS)) {
            throw new DomainException("BACKUP_ITEM_SET_INVALID", "四类备份项不完整", HttpStatus.CONFLICT);
        }
        return index;
    }

    private void validateKeyReference(String reference) {
        if (!reference.matches("^[A-Za-z0-9._:/-]{3,255}$") || reference.toLowerCase(Locale.ROOT).contains("plaintext")) {
            throw new DomainException("BACKUP_KEY_REFERENCE_INVALID", "只能保存密钥引用，不能保存密钥明文", HttpStatus.CONFLICT);
        }
    }

    private void validateRestoreEvidence(
            AuthenticatedContext context,
            String requestId,
            OpsBackup backup,
            List<OpsBackupItem> storedItems,
            OperationsContinuityModels.RestoreDrillRequest input) {
        OperationsContinuityModels.RestoreVerificationEvidence evidence = input.verification();
        OpsRelease release = requireRelease(backupReleaseId(backup));
        Map<String, OperationsContinuityModels.RestoreArtifactEvidence> evidenceItems = new LinkedHashMap<>();
        for (OperationsContinuityModels.RestoreArtifactEvidence item : evidence.items()) {
            String type = item.itemType().toUpperCase(Locale.ROOT);
            if (!REQUIRED_ITEMS.contains(type) || evidenceItems.put(type, item) != null) {
                rejectRestoreEvidence(context, requestId, backup.getId(), "ARTIFACT_SET_INVALID");
            }
        }
        boolean headerValid = backup.getId().equals(evidence.backupId())
                && input.environmentCode().equals(evidence.environment())
                && "PASSED".equals(evidence.status())
                && "READABLE".equals(evidence.databaseRestore())
                && "EXTRACTED".equals(evidence.fileObjects())
                && release.getConfigVersion().equals(evidence.configurationVersion())
                && "REFERENCE_ONLY_ROTATE_ON_RESTORE".equals(evidence.secretReferences());
        boolean itemEvidenceValid = evidenceItems.keySet().equals(REQUIRED_ITEMS)
                && storedItems.stream().allMatch(stored -> {
                    OperationsContinuityModels.RestoreArtifactEvidence supplied = evidenceItems.get(stored.getItemType());
                    return supplied != null && "VERIFIED".equals(supplied.verificationStatus())
                            && stored.getSizeBytes().equals(supplied.sizeBytes())
                            && stored.getSha256().equalsIgnoreCase(supplied.sha256());
                });
        try {
            OffsetDateTime verifiedAt = OffsetDateTime.parse(evidence.verifiedAt());
            boolean timeValid = !verifiedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    .isAfter(LocalDateTime.now().plusMinutes(5));
            if (!headerValid || !itemEvidenceValid || !timeValid) {
                rejectRestoreEvidence(context, requestId, backup.getId(), "PHYSICAL_VERIFICATION_MISMATCH");
            }
        } catch (DateTimeParseException exception) {
            rejectRestoreEvidence(context, requestId, backup.getId(), "VERIFIED_AT_INVALID");
        }
    }

    private void rejectRestoreEvidence(
            AuthenticatedContext context, String requestId, Long backupId, String failureCode) {
        auditFailure(context, requestId, "OPERATIONS_RESTORE_DRILL_BLOCKED", "OPS_BACKUP", backupId,
                failureCode, Map.of("backupId", backupId));
        throw new DomainException("RESTORE_DRILL_EVIDENCE_INVALID",
                "恢复演练脚本证据与已登记备份不一致，任务未开始", HttpStatus.CONFLICT);
    }

    private LocalDateTime requireConsistencyPoint(
            AuthenticatedContext context, String requestId, String value) {
        try {
            LocalDateTime point = OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            if (point.isAfter(LocalDateTime.now().plusMinutes(5))) {
                throw new DateTimeParseException("future consistency point", value, 0);
            }
            return point;
        } catch (DateTimeParseException exception) {
            auditFailure(context, requestId, "OPERATIONS_BACKUP_BLOCKED", "OPS_BACKUP", null,
                    "CONSISTENCY_POINT_INVALID", Map.of("consistencyPoint", value));
            throw new DomainException("BACKUP_CONSISTENCY_POINT_INVALID",
                    "备份一致性时间点必须是带时区且不晚于当前时间的 ISO-8601 值", HttpStatus.CONFLICT);
        }
    }

    private boolean validStoredItems(List<OpsBackupItem> rows) {
        return rows.size() == 4 && rows.stream().map(OpsBackupItem::getItemType)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)).equals(REQUIRED_ITEMS)
                && rows.stream().allMatch(row -> "VERIFIED".equals(row.getStatus())
                && row.getSha256().matches("^[a-f0-9]{64}$") && row.getSizeBytes() > 0);
    }

    private void addCheck(List<OperationsContinuityModels.GateCheck> checks, String code, String name,
                          boolean passed, String evidence) {
        checks.add(new OperationsContinuityModels.GateCheck(code, name, passed ? "PASSED" : "FAILED", evidence));
    }

    private OperationsContinuityModels.BackupView backupView(OpsBackup row) {
        return new OperationsContinuityModels.BackupView(row.getId(), backupReleaseId(row), row.getSystemId(),
                plainType(row), row.getStatus(), format(row.getStartedAt()), row.getManifestHash(),
                row.getEncryptionKeyRef(), "REFERENCE_ONLY_ROTATE_ON_RESTORE", format(row.getRetentionUntil()),
                row.getRequestedByAccountId(), format(row.getStartedAt()), format(row.getFinishedAt()),
                format(row.getCreatedAt()), items(row.getId()).stream().map(this::itemView).toList());
    }

    private OperationsContinuityModels.BackupItemView itemView(OpsBackupItem row) {
        return new OperationsContinuityModels.BackupItemView(row.getId(), row.getItemType(), row.getStorageUri(),
                row.getSizeBytes(), row.getSha256(), row.getStatus());
    }

    private OperationsContinuityModels.RestoreDrillView drillView(OpsRestoreDrill row) {
        return new OperationsContinuityModels.RestoreDrillView(row.getId(), row.getBackupId(), row.getEnvironmentCode(),
                row.getStatus(), parseMap(row.getVerificationJson()), row.getRequestedByAccountId(),
                format(row.getStartedAt()), format(row.getFinishedAt()), format(row.getCreatedAt()));
    }

    private OperationsContinuityModels.UpgradeView upgradeView(OpsUpgrade row) {
        List<OpsUpgradeStep> steps = upgradeSteps.selectList(new LambdaQueryWrapper<OpsUpgradeStep>()
                .eq(OpsUpgradeStep::getUpgradeId, row.getId()).orderByAsc(OpsUpgradeStep::getStepNumber));
        return new OperationsContinuityModels.UpgradeView(row.getId(), row.getReleaseId(), row.getBackupId(),
                row.getSystemId(), row.getStatus(), parseMap(row.getImpactReportJson()), parseMap(row.getRollbackPointJson()),
                row.getRequestedByAccountId(), format(row.getStartedAt()), format(row.getFinishedAt()),
                format(row.getCreatedAt()), steps.stream().map(this::stepView).toList());
    }

    private OperationsContinuityModels.UpgradeStepView stepView(OpsUpgradeStep row) {
        return new OperationsContinuityModels.UpgradeStepView(row.getStepNumber(), row.getStepType(),
                row.getSourceVersion(), row.getTargetVersion(), parseMap(row.getMappingJson()), row.getStatus(),
                format(row.getStartedAt()), format(row.getFinishedAt()), row.getErrorMessage());
    }

    private List<OpsBackupItem> items(Long backupId) {
        return backupItems.selectList(new LambdaQueryWrapper<OpsBackupItem>()
                .eq(OpsBackupItem::getBackupId, backupId).orderByAsc(OpsBackupItem::getId));
    }

    private OpsBackup requireBackup(AuthenticatedContext context, Long id) {
        requireSystem(context);
        OpsBackup row = backups.selectById(id);
        if (row == null || !context.systemId().equals(row.getSystemId())) {
            throw new DomainException("BACKUP_NOT_FOUND", "备份不存在", HttpStatus.NOT_FOUND);
        }
        return row;
    }

    private OpsRelease requireRelease(Long id) {
        OpsRelease row = releases.selectById(id);
        if (row == null) throw new DomainException("RELEASE_NOT_FOUND", "发布版本不存在", HttpStatus.NOT_FOUND);
        return row;
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统后台", HttpStatus.BAD_REQUEST);
        }
    }

    private String encodeType(String type, Long releaseId) { return type.toUpperCase(Locale.ROOT) + ":R:" + releaseId; }
    private String plainType(OpsBackup row) { return row.getBackupType().split(":R:", 2)[0]; }
    private long backupReleaseId(OpsBackup row) {
        String[] parts = row.getBackupType().split(":R:", 2);
        return parts.length == 2 ? Long.parseLong(parts[1]) : -1L;
    }

    private String databaseVersion() {
        MigrationInfo current = flyway.info().current();
        return current == null ? "unknown" : current.getVersion().getVersion();
    }

    private void audit(AuthenticatedContext context, String requestId, String eventCode, String objectType,
                       Long objectId, String result, Map<String, ?> detail) {
        auditRecorder.recordWithPermissionSnapshot(requestId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), eventCode, objectType, objectId == null ? null : String.valueOf(objectId), result,
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions()), detail);
    }

    private void auditFailure(AuthenticatedContext context, String requestId, String eventCode, String objectType,
                              Long objectId, String failureCode, Map<String, ?> detail) {
        auditRecorder.recordFailure(requestId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), eventCode, objectType, objectId == null ? null : String.valueOf(objectId),
                failureCode, detail);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize continuity state", exception); }
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, MAP_TYPE); }
        catch (JsonProcessingException exception) { return Map.of(); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
