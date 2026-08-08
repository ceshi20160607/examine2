package com.unique.examine.file.domain;

import java.util.List;

public record RuntimeRecordFilePage(
        List<RuntimeRecordFile> items,
        int page,
        int size,
        long total
) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public RuntimeRecordFilePage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || size > MAX_SIZE || total < 0) {
            throw new IllegalArgumentException("Runtime record file page metadata is invalid");
        }
    }

    public static void validate(int page, int size) {
        if (page < 1) {
            throw new FileDomainException(
                    "RECORD_FILE_PAGE_INVALID",
                    "page must be at least 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new FileDomainException(
                    "RECORD_FILE_SIZE_INVALID",
                    "size must be within 1.." + MAX_SIZE);
        }
    }
}
