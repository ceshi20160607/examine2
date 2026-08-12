package com.unique.examine.module.runtime.favorite;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public final class FavoriteViews {
    private FavoriteViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FavoriteItem(
            String favoriteId,
            long version,
            String type,
            String moduleCode,
            String recordId,
            String displayLabel,
            String status,
            String updatedAt
    ) {
    }

    public record FavoritePage(List<FavoriteItem> items, int page, int size, long total) {
        public FavoritePage {
            items = List.copyOf(items);
        }
    }

    public record DeleteResponse(String favoriteId, long version, boolean deleted) {
    }
}
