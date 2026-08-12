package com.unique.examine.todo.domain;

import java.time.Instant;

public record TodoQuery(
        CategoryFilter category,
        StateFilter state,
        TimeFilter time,
        int page,
        int size,
        Instant todayStart,
        Instant tomorrowStart
) {
    public static final int MAX_SIZE = 100;
    public enum CategoryFilter { ALL, APPROVAL, TASK, REMINDER, CC }
    public enum StateFilter { ALL, OPEN, CLOSED }
    public enum TimeFilter { ALL, TODAY, OVERDUE }

    public TodoQuery {
        if (category == null || state == null || time == null) {
            throw new TodoException("TODO_QUERY_INVALID", "Todo filters are required");
        }
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw new TodoException("TODO_QUERY_INVALID", "Todo page is outside its bounds");
        }
        if (time != TimeFilter.ALL && (todayStart == null || tomorrowStart == null
                || !tomorrowStart.isAfter(todayStart))) {
            throw new TodoException("TODO_QUERY_INVALID", "Todo day boundaries are required");
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page - 1, size);
    }
}
