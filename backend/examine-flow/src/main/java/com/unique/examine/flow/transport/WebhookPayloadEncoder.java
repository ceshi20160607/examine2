package com.unique.examine.flow.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the bounded v1 receiver contract without comments, signatures,
 * mutable field values, binary content or secret/storage facts.
 */
public final class WebhookPayloadEncoder {
    public static final int VERSION = 1;
    public static final int MAXIMUM_PAYLOAD_BYTES = 32_768;

    private final ObjectMapper json;

    public WebhookPayloadEncoder(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public byte[] encode(DeliveryFacts facts) {
        Objects.requireNonNull(facts, "facts");
        var root = new LinkedHashMap<String, Object>();
        root.put("version", VERSION);
        root.put("delivery", Map.of(
                "id", Long.toString(facts.deliveryId())
        ));
        root.put("scope", Map.of(
                "systemId", Long.toString(facts.systemId()),
                "tenantId", Long.toString(facts.tenantId())
        ));
        var instance = new LinkedHashMap<String, Object>();
        instance.put("id", Long.toString(facts.instanceId()));
        instance.put("businessKey", facts.businessKey());
        instance.put("requesterId", Long.toString(facts.requesterId()));
        root.put("instance", instance);
        root.put("definition", Map.of(
                "id", Long.toString(facts.definitionId()),
                "version", facts.definitionVersion()
        ));
        root.put("completionStep", Map.of(
                "ordinal", facts.ordinal(),
                "code", facts.stepCode(),
                "name", facts.stepName(),
                "type", facts.stepType()
        ));
        if (facts.trigger() != null) {
            root.put("trigger", Map.of(
                    "moduleCode", facts.trigger().moduleCode(),
                    "event", facts.trigger().event()
            ));
        }
        if (facts.record() != null) {
            root.put("record", Map.of(
                    "moduleCode", facts.record().moduleCode(),
                    "recordId", Long.toString(facts.record().recordId())
            ));
        }
        var decisions = new ArrayList<Map<String, Object>>();
        facts.decisions().stream()
                .sorted(java.util.Comparator.comparingInt(
                        DecisionReference::historySequence))
                .forEach(decision -> {
                    var value = new LinkedHashMap<String, Object>();
                    value.put(
                            "historySequence",
                            decision.historySequence());
                    value.put("decision", decision.decision());
                    if (decision.evidenceId() != null) {
                        value.put(
                                "evidenceId",
                                Long.toString(decision.evidenceId()));
                    }
                    decisions.add(value);
                });
        root.put("decisions", decisions);
        final byte[] bytes;
        try {
            bytes = json.writeValueAsBytes(root);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(
                    "Webhook delivery facts cannot be serialized", failure);
        }
        if (bytes.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "Webhook payload exceeds 32768 UTF-8 bytes");
        }
        // Defend against a non-UTF-8 custom mapper/factory.
        new String(bytes, StandardCharsets.UTF_8);
        return bytes;
    }

    public record DeliveryFacts(
            long deliveryId,
            long systemId,
            long tenantId,
            long instanceId,
            long definitionId,
            int definitionVersion,
            int ordinal,
            String stepCode,
            String stepName,
            String stepType,
            String businessKey,
            long requesterId,
            TriggerReference trigger,
            RecordReference record,
            List<DecisionReference> decisions
    ) {
        public DeliveryFacts(
                long deliveryId,
                long systemId,
                long tenantId,
                long instanceId,
                long definitionId,
                int definitionVersion,
                int ordinal,
                String stepCode,
                String stepName,
                String businessKey,
                long requesterId,
                TriggerReference trigger,
                RecordReference record,
                List<DecisionReference> decisions
        ) {
            this(
                    deliveryId, systemId, tenantId, instanceId,
                    definitionId, definitionVersion, ordinal, stepCode,
                    stepName, "WEBHOOK", businessKey, requesterId,
                    trigger, record, decisions
            );
        }

        public DeliveryFacts {
            if (deliveryId <= 0
                    || systemId <= 0
                    || tenantId <= 0
                    || instanceId <= 0
                    || definitionId <= 0
                    || definitionVersion < 1
                    || ordinal < 0
                    || ordinal > 7
                    || requesterId <= 0) {
                throw new IllegalArgumentException(
                        "Webhook delivery identity is invalid");
            }
            stepCode = bounded(
                    stepCode, "Webhook step code", 64);
            stepName = bounded(
                    stepName, "Webhook step name", 80);
            if (!"WEBHOOK".equals(stepType)
                    && !"EXTERNAL_TASK".equals(stepType)
                    && !"SUBFLOW".equals(stepType)) {
                throw new IllegalArgumentException(
                        "Completion step type is invalid");
            }
            businessKey = bounded(
                    businessKey, "Webhook business key", 160);
            decisions = decisions == null
                    ? List.of()
                    : List.copyOf(decisions);
            if (decisions.stream().anyMatch(Objects::isNull)
                    || decisions.stream()
                            .map(DecisionReference::historySequence)
                            .distinct().count() != decisions.size()) {
                throw new IllegalArgumentException(
                        "Webhook decisions must have unique history sequences");
            }
        }
    }

    public record TriggerReference(String moduleCode, String event) {
        public TriggerReference {
            moduleCode = bounded(
                    moduleCode, "Trigger module code", 64);
            event = bounded(event, "Trigger event", 32);
        }
    }

    public record RecordReference(String moduleCode, long recordId) {
        public RecordReference {
            moduleCode = bounded(
                    moduleCode, "Record module code", 64);
            if (recordId <= 0) {
                throw new IllegalArgumentException(
                        "Record id must be positive");
            }
        }
    }

    public record DecisionReference(
            int historySequence,
            String decision,
            Long evidenceId
    ) {
        public DecisionReference {
            if (historySequence < 1
                    || evidenceId != null && evidenceId <= 0) {
                throw new IllegalArgumentException(
                        "Decision reference identity is invalid");
            }
            if (!"APPROVED".equals(decision)
                    && !"REJECTED".equals(decision)) {
                throw new IllegalArgumentException(
                        "Decision reference type is invalid");
            }
        }
    }

    private static String bounded(
            String value,
            String label,
            int maximum
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(
                    label + " accepts at most " + maximum + " characters");
        }
        return value;
    }
}
