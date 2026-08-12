package com.unique.examine.flow.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.EVIDENCE_INVALID;

/**
 * Optional definition-time requirements for evidence attached to one decision.
 * A null policy preserves the legacy comment-only contract.
 */
public record ApprovalDecisionEvidencePolicy(
        int minimumAttachments,
        int maximumAttachments,
        Set<MimeFamily> allowedMimeFamilies,
        SignatureMode signatureMode
) {
    public static final int MAXIMUM_ATTACHMENTS = 5;

    public ApprovalDecisionEvidencePolicy {
        if (minimumAttachments < 0
                || minimumAttachments > MAXIMUM_ATTACHMENTS
                || maximumAttachments < minimumAttachments
                || maximumAttachments > MAXIMUM_ATTACHMENTS) {
            throw new IllegalArgumentException(
                    "Decision evidence attachments must be bounded between 0 and 5");
        }
        if (allowedMimeFamilies != null) {
            if (allowedMimeFamilies.isEmpty()
                    || allowedMimeFamilies.stream().anyMatch(
                    java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Decision evidence MIME allow-list must not be empty");
            }
            allowedMimeFamilies = Collections.unmodifiableSet(
                    EnumSet.copyOf(allowedMimeFamilies));
        }
        signatureMode = signatureMode == null ? SignatureMode.NONE : signatureMode;
    }

    public void validate(
            List<ApprovalDecisionEvidenceFile> attachments,
            ApprovalDecisionEvidence.Signature signature
    ) {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        var distinctFiles = attachments.stream()
                .map(ApprovalDecisionEvidenceFile::fileId)
                .distinct()
                .count();
        if (attachments.size() < minimumAttachments
                || attachments.size() > maximumAttachments
                || distinctFiles != attachments.size()) {
            throw invalid("Decision evidence attachment count is invalid");
        }
        if (allowedMimeFamilies != null && attachments.stream().anyMatch(
                file -> !allowedMimeFamilies.contains(file.mimeFamily()))) {
            throw invalid("Decision evidence contains a disallowed MIME family");
        }
        if (signature != null
                && signature.file() != null
                && allowedMimeFamilies != null
                && !allowedMimeFamilies.contains(
                signature.file().mimeFamily())) {
            throw invalid("Decision signature contains a disallowed MIME family");
        }
        if (signatureMode == SignatureMode.NONE && signature != null) {
            throw invalid("This decision does not accept signature evidence");
        }
        if (signatureMode == SignatureMode.REQUIRED && signature == null) {
            throw invalid("This decision requires signature evidence");
        }
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(EVIDENCE_INVALID, message);
    }

    public enum MimeFamily {
        IMAGE,
        PDF,
        DOCUMENT,
        ARCHIVE,
        OTHER
    }

    public enum SignatureMode {
        NONE,
        OPTIONAL,
        REQUIRED
    }
}
