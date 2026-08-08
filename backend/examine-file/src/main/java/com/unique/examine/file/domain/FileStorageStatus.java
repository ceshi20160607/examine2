package com.unique.examine.file.domain;

public record FileStorageStatus(
        Mode mode,
        String location,
        long maxSingleUploadBytes,
        long maxMultipartUploadBytes,
        long maxPartBytes,
        boolean available
) {
    public FileStorageStatus {
        if (mode == null || location == null || location.isBlank() || location.length() > 200
                || maxSingleUploadBytes < 0 || maxMultipartUploadBytes < 0 || maxPartBytes < 0) {
            throw new IllegalArgumentException("File storage status is invalid");
        }
        location = location.strip();
    }

    public enum Mode {
        LOCAL,
        S3,
        UNAVAILABLE
    }
}
