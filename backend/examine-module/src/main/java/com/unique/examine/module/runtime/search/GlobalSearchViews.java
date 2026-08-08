package com.unique.examine.module.runtime.search;

import java.util.List;

public final class GlobalSearchViews {
    private GlobalSearchViews() {
    }

    public record SearchItem(
            String moduleCode,
            String moduleName,
            String recordId,
            String recordNo,
            String displayLabel,
            String status,
            List<String> matchedFieldCodes
    ) {
        public SearchItem {
            matchedFieldCodes = List.copyOf(matchedFieldCodes);
        }
    }

    public record SearchPage(List<SearchItem> items, int page, int size, long total) {
        public SearchPage {
            items = List.copyOf(items);
        }
    }
}
