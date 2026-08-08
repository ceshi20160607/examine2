package com.unique.examine.plat.manage.audit;

import com.unique.examine.core.context.ContextType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class UnifiedAuditModels {
    private UnifiedAuditModels() {
    }

    public record Scope(ContextType type, Long systemId, Long tenantId) {
        public Scope {
            if (type == null) throw new IllegalArgumentException("type is required");
            if (type == ContextType.PLATFORM && (systemId != null || tenantId != null)) {
                throw new IllegalArgumentException("platform scope cannot carry system or tenant");
            }
            if (type == ContextType.SYSTEM && (systemId == null || tenantId == null)) {
                throw new IllegalArgumentException("system scope requires system and tenant");
            }
        }

        public static Scope platform() {
            return new Scope(ContextType.PLATFORM, null, null);
        }

        public static Scope system(long systemId, long tenantId) {
            return new Scope(ContextType.SYSTEM, systemId, tenantId);
        }
    }

    public record Query(
            String requestId,
            String traceId,
            String actor,
            String object,
            String result,
            String category,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
    }

    public record Page(List<Item> items, int page, int size, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record Item(
            String id,
            String category,
            String source,
            String event,
            String actorId,
            String actorName,
            String objectType,
            String objectId,
            String result,
            String failureCode,
            String requestId,
            String traceId,
            String systemId,
            String tenantId,
            Instant occurredAt,
            Map<String, String> details
    ) {
        public Item {
            details = Map.copyOf(details);
        }
    }
}
