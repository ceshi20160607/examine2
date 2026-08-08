package com.unique.examine.todo.port;

public record TodoSourceActionResult(Code code, long sourceVersion, String message) {
    public enum Code { SUCCESS, STALE, DENIED, CONFLICT, FAILED }

    public TodoSourceActionResult {
        if (code == null || sourceVersion <= 0 || message == null
                || message.isBlank() || message.length() > 500) {
            throw new IllegalArgumentException("Todo source action result is invalid");
        }
        message = message.trim();
    }
}
