package com.unique.examine.flow.domain;

/**
 * Immutable canonical metadata copied from an authorized File asset.
 */
public record ApprovalDecisionEvidenceFile(
        long fileId,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256
) {
    public ApprovalDecisionEvidenceFile {
        if (fileId <= 0) {
            throw new IllegalArgumentException("Evidence file id must be positive");
        }
        originalName = boundedText(originalName, "Evidence file name", 255);
        contentType = boundedText(contentType, "Evidence content type", 255)
                .toLowerCase(java.util.Locale.ROOT);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "Evidence file size must not be negative");
        }
        if (sha256 == null
                || !sha256.matches("^[a-fA-F0-9]{64}$")) {
            throw new IllegalArgumentException(
                    "Evidence file sha256 must contain 64 hexadecimal characters");
        }
        sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
    }

    public ApprovalDecisionEvidencePolicy.MimeFamily mimeFamily() {
        if (contentType.startsWith("image/")) {
            return ApprovalDecisionEvidencePolicy.MimeFamily.IMAGE;
        }
        if (contentType.equals("application/pdf")) {
            return ApprovalDecisionEvidencePolicy.MimeFamily.PDF;
        }
        if (contentType.equals("application/zip")
                || contentType.equals("application/x-7z-compressed")
                || contentType.equals("application/x-rar-compressed")
                || contentType.equals("application/gzip")
                || contentType.equals("application/x-tar")) {
            return ApprovalDecisionEvidencePolicy.MimeFamily.ARCHIVE;
        }
        if (contentType.startsWith("text/")
                || contentType.contains("word")
                || contentType.contains("excel")
                || contentType.contains("spreadsheet")
                || contentType.contains("powerpoint")
                || contentType.contains("presentation")
                || contentType.contains("opendocument")) {
            return ApprovalDecisionEvidencePolicy.MimeFamily.DOCUMENT;
        }
        return ApprovalDecisionEvidencePolicy.MimeFamily.OTHER;
    }

    private static String boundedText(String value, String label, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(
                    label + " accepts at most " + maximum + " characters");
        }
        return value;
    }
}
