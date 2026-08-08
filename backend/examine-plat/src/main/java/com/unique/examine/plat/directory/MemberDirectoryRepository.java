package com.unique.examine.plat.directory;

import java.util.List;

interface MemberDirectoryRepository {
    long count(long systemId, long tenantId, String keyword);

    List<MemberDirectoryApi.Member> find(
            long systemId,
            long tenantId,
            String keyword,
            long offset,
            int limit
    );
}
