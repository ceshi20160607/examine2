package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Immutable evidence accepted for one exact approval history event.
 */
public record ApprovalDecisionEvidence(
        long id,
        long instanceId,
        int historySequence,
        String branchCode,
        int stageIndex,
        ApprovalInstance.Decision decision,
        List<ApprovalDecisionEvidenceFile> attachments,
        Signature signature,
        TemplateSelection template,
        long actorId,
        long representedMemberId,
        Long delegationRuleId,
        Instant decidedAt
) {
    public ApprovalDecisionEvidence {
        if (id <= 0 || instanceId <= 0 || historySequence < 1
                || stageIndex < 0 || stageIndex > 9
                || actorId <= 0 || representedMemberId <= 0) {
            throw new IllegalArgumentException(
                    "Decision evidence identity and audit facts are invalid");
        }
        if (branchCode != null
                && !branchCode.matches("^[a-z][a-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(
                    "Decision evidence branch code is invalid");
        }
        Objects.requireNonNull(decision, "decision");
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        if (attachments.size()
                > ApprovalDecisionEvidencePolicy.MAXIMUM_ATTACHMENTS
                || attachments.stream().anyMatch(Objects::isNull)
                || attachments.stream()
                .map(ApprovalDecisionEvidenceFile::fileId)
                .distinct().count() != attachments.size()) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.EVIDENCE_INVALID,
                    "Decision evidence attachments must be distinct and bounded");
        }
        if (actorId == representedMemberId) {
            if (delegationRuleId != null) {
                throw new IllegalArgumentException(
                        "Direct evidence audit cannot contain a delegation rule");
            }
        } else if (delegationRuleId == null || delegationRuleId <= 0) {
            throw new IllegalArgumentException(
                    "Delegated evidence audit requires its authority rule");
        }
        Objects.requireNonNull(decidedAt, "decidedAt");
    }

    public List<ApprovalDecisionEvidenceFile> referencedFiles() {
        var values = new LinkedHashMap<Long, ApprovalDecisionEvidenceFile>();
        attachments.forEach(file -> values.put(file.fileId(), file));
        if (signature != null && signature.file() != null) {
            var existing = values.putIfAbsent(
                    signature.file().fileId(), signature.file());
            if (existing != null && !existing.equals(signature.file())) {
                throw new IllegalStateException(
                        "Repeated evidence file metadata is inconsistent");
            }
        }
        return List.copyOf(values.values());
    }

    public record Signature(
            Kind kind,
            ApprovalDecisionEvidenceFile file,
            String typedValue
    ) {
        public Signature {
            Objects.requireNonNull(kind, "kind");
            if (kind == Kind.FILE) {
                if (file == null || typedValue != null) {
                    throw new IllegalArgumentException(
                            "File signature requires exactly one file snapshot");
                }
            } else {
                if (file != null || typedValue == null
                        || typedValue.isBlank()) {
                    throw new IllegalArgumentException(
                            "Typed signature requires exactly one display value");
                }
                typedValue = typedValue.strip();
                if (typedValue.codePointCount(0, typedValue.length()) > 120) {
                    throw new IllegalArgumentException(
                            "Typed signature accepts at most 120 characters");
                }
            }
        }

        public static Signature file(ApprovalDecisionEvidenceFile file) {
            return new Signature(Kind.FILE, file, null);
        }

        public static Signature typed(String value) {
            return new Signature(Kind.TYPED, null, value);
        }

        public enum Kind {
            FILE,
            TYPED
        }
    }

    public record TemplateSelection(
            long templateId,
            int version,
            String name
    ) {
        public TemplateSelection {
            if (templateId <= 0 || version < 1) {
                throw new IllegalArgumentException(
                        "Evidence template identity must be positive");
            }
            name = ApprovalDecisionCommentTemplateVersion.bounded(
                    name, "Evidence template name", 80);
        }
    }
}
