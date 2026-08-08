package com.unique.examine.module.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class SavedViewViews {
    private SavedViewViews() { }

    public record SavedViewResponse(
            String viewId,
            long version,
            String moduleCode,
            String name,
            JsonNode query,
            List<String> columns,
            String correlationId
    ) {
        public SavedViewResponse {
            columns = List.copyOf(columns);
        }
    }

    public record SavedViewListResponse(List<SavedViewResponse> items, String correlationId) {
        public SavedViewListResponse {
            items = List.copyOf(items);
        }
    }

    public record DeleteResponse(String viewId, long version, boolean deleted, String correlationId) { }
}
