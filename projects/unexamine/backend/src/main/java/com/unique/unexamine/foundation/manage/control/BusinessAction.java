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

        /** 将缺省请求标识固化一次，保证同一动作事务及其失败事实使用同一个稳定键。 */
        public Options materialized() {
            return new Options(effectiveKey(), channel, applicationCode, causationId, depth);
        }
    }

    public record Input(AuthenticatedContext actor, Long dataTenantId, Long moduleId, String moduleCode,
                        Long configVersionId, String actionCode, Long recordId, Integer expectedVersion,
                        Object payload, Options options, String traceId) {}

    public record Result<T>(long idempotencyRecordId, long outboxId, String idempotencyKey,
                            String status, boolean replayed, Mode mode, T value, Long flowInstanceId) {}

    public record Delivery(long actionId, String actionCode, Mode mode, String status, Long flowInstanceId,
                           String businessStatus, String errorCode, String message, Long recordId) {}

    /** Only the locked approval coordinator can issue a permit; callers cannot select a bypass mode. */
    public static final class ApprovedExecution {
        private final Input input;
        private final com.fasterxml.jackson.databind.JsonNode configurationSnapshot;
        ApprovedExecution(Input input, com.fasterxml.jackson.databind.JsonNode configurationSnapshot) {
            this.input = input;
            this.configurationSnapshot = configurationSnapshot;
        }
        public Input input() { return input; }
        public com.fasterxml.jackson.databind.JsonNode configurationSnapshot() { return configurationSnapshot; }
    }

    static com.fasterxml.jackson.databind.ObjectMapper storageMapper(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var dates = new com.fasterxml.jackson.databind.module.SimpleModule("business-action-storage-v1");
        dates.addSerializer(java.time.LocalDateTime.class,
                new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return mapper.copy().registerModule(dates);
    }
}
