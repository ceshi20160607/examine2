package com.unique.examine.module.systemfield;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public final class SystemFieldValueService {
    private final AutoNumberSequence autoNumberSequence;

    public SystemFieldValueService(AutoNumberSequence autoNumberSequence) {
        this.autoNumberSequence = Objects.requireNonNull(autoNumberSequence, "autoNumberSequence");
    }

    public Map<String, Object> onCreate(
            Scope scope,
            List<SystemFieldDefinition> definitions,
            Map<String, ?> clientValues,
            ActorContext actor
    ) {
        validate(scope, definitions, clientValues, actor);
        var values = new LinkedHashMap<String, Object>();
        for (var definition : definitions) {
            var value = switch (definition.type()) {
                case TENANT -> scope.tenantId();
                case AUTO_NUMBER -> formatAutoNumber(definition,
                        autoNumberSequence.next(scope.systemId(), scope.tenantId(), scope.moduleId(),
                                definition.fieldId()));
                case CREATED_BY, UPDATED_BY -> actor.memberId();
                case CREATED_AT, UPDATED_AT -> actor.occurredAt();
            };
            values.put(definition.fieldCode(), value);
        }
        return Map.copyOf(values);
    }

    public void assertClientValuesAllowed(
            Scope scope,
            List<SystemFieldDefinition> definitions,
            Map<String, ?> clientValues,
            ActorContext actor
    ) {
        validate(scope, definitions, clientValues, actor);
    }

    public Map<String, Object> onUpdate(
            Scope scope,
            List<SystemFieldDefinition> definitions,
            Map<String, ?> clientValues,
            ActorContext actor
    ) {
        validate(scope, definitions, clientValues, actor);
        var values = new LinkedHashMap<String, Object>();
        definitions.stream()
                .filter(definition -> definition.type() == SystemFieldDefinition.Type.UPDATED_BY
                        || definition.type() == SystemFieldDefinition.Type.UPDATED_AT)
                .forEach(definition -> values.put(definition.fieldCode(),
                        definition.type() == SystemFieldDefinition.Type.UPDATED_BY
                                ? actor.memberId()
                                : actor.occurredAt()));
        return Map.copyOf(values);
    }

    private static void validate(
            Scope scope,
            List<SystemFieldDefinition> definitions,
            Map<String, ?> clientValues,
            ActorContext actor
    ) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(clientValues, "clientValues");
        Objects.requireNonNull(actor, "actor");
        var systemCodes = definitions.stream().map(SystemFieldDefinition::fieldCode).collect(Collectors.toSet());
        var spoofed = clientValues.keySet().stream().filter(systemCodes::contains).sorted().toList();
        if (!spoofed.isEmpty()) {
            throw new BusinessException(
                    "SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN",
                    "System-managed fields cannot be supplied by the client: " + String.join(", ", spoofed),
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        var duplicates = definitions.stream().collect(Collectors.groupingBy(
                        SystemFieldDefinition::fieldCode, Collectors.counting()))
                .entrySet().stream().filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).sorted().toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate system field codes: " + String.join(", ", duplicates));
        }
    }

    private static String formatAutoNumber(SystemFieldDefinition definition, long sequence) {
        var number = Long.toString(sequence);
        var padding = Math.max(0, definition.autoNumberDigits() - number.length());
        return definition.autoNumberPrefix() + "0".repeat(padding) + number;
    }

    public record Scope(long systemId, long tenantId, long moduleId) {
        public Scope {
            if (systemId <= 0 || tenantId <= 0 || moduleId <= 0) {
                throw new IllegalArgumentException("systemId, tenantId and moduleId must be positive");
            }
        }
    }

    public record ActorContext(long memberId, LocalDateTime occurredAt) {
        public ActorContext {
            if (memberId <= 0) {
                throw new IllegalArgumentException("memberId must be positive");
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
