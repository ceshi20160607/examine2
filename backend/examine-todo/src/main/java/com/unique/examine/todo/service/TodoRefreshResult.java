package com.unique.examine.todo.service;

public record TodoRefreshResult(int discovered, int created, int updated, int closed) {
    public TodoRefreshResult {
        if (discovered < 0 || created < 0 || updated < 0 || closed < 0) {
            throw new IllegalArgumentException("Todo refresh counts cannot be negative");
        }
    }
}
