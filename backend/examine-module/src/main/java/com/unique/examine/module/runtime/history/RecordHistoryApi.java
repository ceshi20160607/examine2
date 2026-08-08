package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class RecordHistoryApi {
    private RecordHistoryApi() {
    }

    public record DiffResponse(
            String fieldCode,
            JsonNode beforeValue,
            JsonNode afterValue,
            boolean masked
    ) {
        static DiffResponse from(RecordHistoryDiff diff) {
            return new DiffResponse(
                    diff.fieldCode(),
                    diff.beforeValue(),
                    diff.afterValue(),
                    diff.masked());
        }
    }

    public record ItemResponse(
            String historyId,
            long recordVersion,
            String action,
            String actorMemberId,
            String occurredAt,
            List<DiffResponse> diff
    ) {
        public ItemResponse {
            diff = List.copyOf(diff);
        }

        static ItemResponse from(RecordHistoryEntry history) {
            return new ItemResponse(
                    history.historyId(),
                    history.recordVersion(),
                    history.action(),
                    history.actorMemberId(),
                    history.occurredAt().toString(),
                    history.diff().stream().map(DiffResponse::from).toList());
        }
    }

    public record PageResponse(
            List<ItemResponse> items,
            int page,
            int size,
            long total
    ) {
        public PageResponse {
            items = List.copyOf(items);
        }

        static PageResponse from(RecordHistoryPage page) {
            return new PageResponse(
                    page.items().stream().map(ItemResponse::from).toList(),
                    page.page(),
                    page.size(),
                    page.total());
        }
    }
}
