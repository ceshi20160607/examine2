package com.unique.examine.todo.domain;

public record TodoActionReservation(TodoActionLog log, boolean acquired) {
    public TodoActionReservation {
        if (log == null) throw new IllegalArgumentException("Todo action log is required");
    }
}
