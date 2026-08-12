package com.unique.examine.todo.domain;

public record TodoCounts(long open, long task, long approval, long today, long overdue) {
    public TodoCounts {
        if (open < 0 || task < 0 || approval < 0 || today < 0 || overdue < 0) {
            throw new IllegalArgumentException("Todo counts cannot be negative");
        }
    }
}
