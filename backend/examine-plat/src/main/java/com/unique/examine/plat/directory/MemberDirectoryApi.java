package com.unique.examine.plat.directory;

import java.util.List;

public final class MemberDirectoryApi {
    private MemberDirectoryApi() {
    }

    public record Member(
            String memberId,
            String memberCode,
            String displayName
    ) {
    }

    public record Page(
            List<Member> items,
            int page,
            int size,
            long total
    ) {
        public Page {
            items = List.copyOf(items);
        }
    }
}
