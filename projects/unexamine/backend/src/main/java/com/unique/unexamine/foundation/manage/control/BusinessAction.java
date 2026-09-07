package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import java.util.UUID;

/** The business boundary, independent of physical Base writes and delivery retries. */
public final class BusinessAction {
    private BusinessAction() {}

    public enum Mode { DIRECT, AFTER_EXECUTION, AFTER_APPROVAL, FLOW_ONLY }

    public record Options(String idempotencyKey, String channel, String applicationCode,
                          String causationId, int depth) {
        public static Options internal() { return new Options(null, "INTERNAL", "runtime", null, 0); }
        public static Options page(String key) { return new Options(key, "PAGE", "runtime", null, 0); }
        public String effectiveKey() {
            return idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey.strip();
        }
    }

    public record Input(AuthenticatedContext actor, Long dataTenantId, Long moduleId, String moduleCode,
                        Long configVersionId, String actionCode, Long recordId, Integer expectedVersion,
                        Object payload, Options options, String traceId) {}

    public record Result<T>(long idempotencyRecordId, long outboxId, String idempotencyKey,
                            String status, boolean replayed, Mode mode, T value) {}
}
