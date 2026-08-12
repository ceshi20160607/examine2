package com.unique.examine.work.domain;

public record WorkProjectQuery(
        String keyword,
        StatusFilter status,
        int page,
        int size
) {
    public static final int MAX_KEYWORD_CHARACTERS = 100;
    public static final int MAX_SIZE = 100;

    public WorkProjectQuery {
        keyword = keyword == null ? "" : keyword.strip();
        if (keyword.codePointCount(0, keyword.length())
                > MAX_KEYWORD_CHARACTERS) {
            throw invalid("keyword cannot exceed "
                    + MAX_KEYWORD_CHARACTERS + " characters");
        }
        if (status == null || page < 1 || size < 1 || size > MAX_SIZE) {
            throw invalid("Project query paging or status is invalid");
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page - 1, size);
    }

    private static WorkDomainException invalid(String message) {
        return new WorkDomainException("WORK_PROJECT_QUERY_INVALID", message);
    }

    public enum StatusFilter {
        ALL,
        ACTIVE,
        ARCHIVED
    }
}
