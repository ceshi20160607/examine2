package com.unique.examine.core.api;

import java.util.List;
import java.util.Set;

/**
 * File-owned bridge used by Flow to validate immutable decision evidence and
 * attach durable references without depending on File persistence or storage.
 * Calls participate in the caller's transaction.
 */
public interface ApprovalEvidenceFileFacade {
    int MAX_DISTINCT_FILES = 6;
    String AGGREGATE_TYPE = "FLOW_DECISION_EVIDENCE";

    List<FileMetadata> validateSelection(
            EvidenceActor actor,
            List<Long> fileIds
    );

    void attachEvidence(
            EvidenceActor actor,
            long evidenceId,
            List<Long> fileIds
    );

    record EvidenceActor(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> permissions
    ) {
        public EvidenceActor {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
                throw new IllegalArgumentException(
                        "Approval evidence actor scope is incomplete");
            }
            permissions = permissions == null
                    ? Set.of()
                    : Set.copyOf(permissions);
        }
    }

    record FileMetadata(
            long fileId,
            String originalName,
            String contentType,
            long size,
            String sha256
    ) {
        public FileMetadata {
            if (fileId <= 0
                    || originalName == null
                    || originalName.isBlank()
                    || contentType == null
                    || contentType.isBlank()
                    || size < 0
                    || sha256 == null
                    || !sha256.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException(
                        "Approval evidence file metadata is invalid");
            }
        }
    }

    /**
     * Stable module-neutral error contract. Flow must not observe File domain
     * exception types through this facade.
     */
    final class EvidenceFileException extends RuntimeException {
        private final String code;

        public EvidenceFileException(String code, String message) {
            super(message);
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException(
                        "Approval evidence file error code is required");
            }
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
