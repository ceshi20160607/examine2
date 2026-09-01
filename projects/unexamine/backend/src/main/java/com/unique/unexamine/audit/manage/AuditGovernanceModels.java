package com.unique.unexamine.audit.manage;

import com.unique.unexamine.audit.base.entity.AuditEvent;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AuditGovernanceModels {
    private AuditGovernanceModels() {
    }

    public record FieldChangeView(Long id, String fieldCode, String valueType, String beforeValueJson,
                                  String afterValueJson, String sensitivity, boolean masked) {
    }

    public record EventDetail(AuditEvent event, List<FieldChangeView> fieldChanges,
                              boolean sensitiveValuesVisible) {
    }

    public record RetentionPreflightRequest(
            @NotBlank @Size(max = 100) String objectType,
            @NotBlank @Size(max = 100) String objectId) {
    }

    public record RetentionPreflight(String objectType, String objectId, boolean allowed,
                                     long auditEventCount, long fieldChangeCount, boolean existingMarker,
                                     boolean approvalRequired, boolean referenceImpactConfirmationRequired,
                                     List<String> blockers) {
    }

    public record CreateRetentionMarkerRequest(
            @NotBlank @Size(max = 100) String objectType,
            @NotBlank @Size(max = 100) String objectId,
            @Size(max = 255) String businessKey,
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 255) String approvalReference,
            @AssertTrue(message = "必须确认引用影响") boolean referenceImpactConfirmed) {
    }

    public record RetentionMarkerView(Long id, String objectType, String objectId, String businessKey,
                                      String snapshotHash, String purgeReason, Long purgedByAccountId,
                                      LocalDateTime purgedAt) {
    }
}
