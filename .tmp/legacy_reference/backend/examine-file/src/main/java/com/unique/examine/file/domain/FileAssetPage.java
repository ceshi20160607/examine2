package com.unique.examine.file.domain;

import java.util.List;

public record FileAssetPage(
        List<FileAsset> items,
        int page,
        int size,
        long total
) {
    public FileAssetPage {
        if (items == null || page < 1 || size < 1 || total < 0
                || items.size() > size || items.size() > total) {
            throw new IllegalArgumentException("File asset page is invalid");
        }
        items = List.copyOf(items);
    }

    public long totalPages() {
        return total == 0 ? 0 : (total + size - 1) / size;
    }
}
