package com.unique.examine.plat.directory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class MemberDirectoryService {
    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final MemberDirectoryRepository repository;

    MemberDirectoryService(MemberDirectoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public MemberDirectoryApi.Page members(
            long systemId,
            long tenantId,
            String keyword,
            Integer requestedPage,
            Integer requestedSize
    ) {
        var page = normalizePage(requestedPage);
        var size = normalizeSize(requestedSize);
        var normalizedKeyword = normalizeKeyword(keyword);
        var total = repository.count(systemId, tenantId, normalizedKeyword);
        var offset = (long) (page - 1) * size;
        var items = total == 0 || offset >= total
                ? List.<MemberDirectoryApi.Member>of()
                : repository.find(systemId, tenantId, normalizedKeyword, offset, size);
        return new MemberDirectoryApi.Page(items, page, size, total);
    }

    static int normalizePage(Integer value) {
        return value == null ? DEFAULT_PAGE : Math.max(value, 1);
    }

    static int normalizeSize(Integer value) {
        return value == null ? DEFAULT_SIZE : Math.min(Math.max(value, 1), MAX_SIZE);
    }

    static String normalizeKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
