package com.unique.examine.file.domain;

public final class FileDomainException extends RuntimeException {
    private final String code;

    public FileDomainException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("File error code is required");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
