package com.unique.examine.plat.manage.secret;

import com.unique.examine.core.task.AsyncTaskView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SecretRef and rotation API models.
 */
public final class SecretModels {

    private SecretModels() {
    }

    public record SecretRefVO(String secretRefId, String refType, String version, LocalDateTime expiresAt,
                              String rotationStatus, LocalDateTime lastUsedAt, String displayName) {
    }

    public record SecretRotationRequest(String newMaterialRef, String requestedVersion, Boolean dualWriteValidation,
                                        Boolean switchAfterValidation, String rollbackPlan, String idempotencyKey) {
    }

    public record SecretRotationPhaseVO(String phase, String status, String disabledReason,
                                        LocalDateTime operatedAt) {
    }

    public record SecretRotationJobVO(String jobId, SecretRefVO secretRef, String status, String newVersion,
                                      List<SecretRotationPhaseVO> phases, String rollbackPlan, AsyncTaskView task,
                                      String traceId, String auditLogId, String disabledReason,
                                      LocalDateTime createdAt) {
    }
}
