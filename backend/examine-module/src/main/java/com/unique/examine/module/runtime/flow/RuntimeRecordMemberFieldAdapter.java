package com.unique.examine.module.runtime.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.runtime.RuntimeRecordMemberFieldFacade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Module-owned implementation of Flow's narrow record MEMBER field port.
 *
 * <p>Current-record resolution locks the exact record/value tuple and then
 * locks the active tenant membership. Snapshot resolution never falls back to
 * the current persisted value.</p>
 */
@Component
public class RuntimeRecordMemberFieldAdapter implements RuntimeRecordMemberFieldFacade {
    private final RecordMemberFieldStore store;
    private final RuntimeActiveMemberFacade activeMembers;
    private final ObjectMapper objectMapper;

    public RuntimeRecordMemberFieldAdapter(
            RecordMemberFieldStore store,
            RuntimeActiveMemberFacade activeMembers,
            ObjectMapper objectMapper
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.activeMembers = Objects.requireNonNull(activeMembers, "activeMembers");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishedFieldCatalog> publishedEligibleFields(
            long systemId,
            String moduleCode
    ) {
        if (systemId <= 0 || !validModuleCode(moduleCode)) {
            return Optional.empty();
        }
        var schema = store.activeSchema(systemId, moduleCode).orElse(null);
        if (schema == null) {
            return Optional.empty();
        }
        var snapshot = parsePublished(schema.configSnapshotJson());
        if (!enabledModule(snapshot, schema.logicalModuleId(), schema.moduleCode())) {
            return Optional.empty();
        }
        var fields = new ArrayList<EligibleField>();
        for (var projected : store.projectedFields(
                schema.systemId(),
                schema.schemaVersionId(),
                schema.moduleSnapshotId(),
                schema.logicalModuleId())) {
            if (eligibility(snapshot, schema.logicalModuleId(), projected.logicalFieldId(), projected)
                    == Eligibility.ELIGIBLE) {
                fields.add(new EligibleField(
                        projected.logicalFieldId(),
                        projected.fieldCode(),
                        projected.fieldName()));
            }
        }
        fields.sort(Comparator.comparing(EligibleField::fieldName)
                .thenComparingLong(EligibleField::fieldId));
        return Optional.of(new PublishedFieldCatalog(
                schema.systemId(),
                schema.schemaVersionId(),
                schema.moduleSnapshotId(),
                schema.logicalModuleId(),
                schema.moduleCode(),
                fields));
    }

    @Override
    @Transactional
    public Resolution resolveCurrent(CurrentRecordRequest request) {
        Objects.requireNonNull(request, "request");
        var record = store.lockRecord(
                        request.systemId(),
                        request.tenantId(),
                        request.moduleCode(),
                        request.recordId())
                .orElse(null);
        if (record == null) {
            return Resolution.sourceMissing();
        }
        var snapshot = parsePublished(record.configSnapshotJson());
        var projected = store.projectedField(
                        record.systemId(),
                        record.schemaVersionId(),
                        record.moduleSnapshotId(),
                        record.logicalModuleId(),
                        request.fieldId())
                .orElse(null);
        var eligibility = eligibility(
                snapshot, record.logicalModuleId(), request.fieldId(), projected);
        if (eligibility != Eligibility.ELIGIBLE) {
            return unresolved(eligibility);
        }
        var values = store.lockValues(
                record.systemId(),
                record.tenantId(),
                record.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                record.logicalModuleId(),
                projected.fieldSnapshotId());
        if (values.isEmpty()) {
            return Resolution.sourceEmpty();
        }
        if (values.size() != 1) {
            return Resolution.sourceInvalid();
        }
        var value = values.getFirst();
        if (value.ordinal() != 0
                || !"MEMBER".equals(value.fieldType())
                || !"RECORD".equals(value.fieldScope())
                || value.referenceValue() == null
                || value.referenceValue() <= 0) {
            return Resolution.sourceInvalid();
        }
        return active(value.referenceValue(), record.systemId(), record.tenantId());
    }

    @Override
    @Transactional
    public Resolution resolveSnapshot(SnapshotRequest request) {
        Objects.requireNonNull(request, "request");
        var schema = store.activeSchema(request.systemId(), request.moduleCode()).orElse(null);
        if (schema == null) {
            return Resolution.sourceMissing();
        }
        var snapshot = parsePublished(schema.configSnapshotJson());
        var projected = store.projectedFields(
                        schema.systemId(),
                        schema.schemaVersionId(),
                        schema.moduleSnapshotId(),
                        schema.logicalModuleId())
                .stream()
                .filter(field -> field.logicalFieldId() == request.fieldId())
                .findFirst()
                .orElse(null);
        var eligibility = eligibility(
                snapshot, schema.logicalModuleId(), request.fieldId(), projected);
        if (eligibility != Eligibility.ELIGIBLE) {
            return unresolved(eligibility);
        }
        if (!request.valuesJson().containsKey(projected.fieldCode())) {
            return Resolution.sourceEmpty();
        }
        var encoded = request.valuesJson().get(projected.fieldCode());
        var memberId = snapshotMemberId(encoded);
        if (memberId.empty()) {
            return Resolution.sourceEmpty();
        }
        if (memberId.invalid()) {
            return Resolution.sourceInvalid();
        }
        return active(memberId.value(), request.systemId(), request.tenantId());
    }

    private Resolution active(long memberId, long systemId, long tenantId) {
        return activeMembers.lockActiveMember(systemId, tenantId, memberId)
                .map(member -> Resolution.resolved(member.memberId()))
                .orElseGet(Resolution::memberInactive);
    }

    private MemberValue snapshotMemberId(String encoded) {
        if (encoded == null
                || encoded.isBlank()
                || containsJsonWhitespaceOutsideString(encoded)) {
            return MemberValue.invalidValue();
        }
        final JsonNode value;
        try {
            value = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(encoded);
        } catch (JsonProcessingException exception) {
            return MemberValue.invalidValue();
        }
        if (value == null || value.isNull()) {
            return MemberValue.emptyValue();
        }
        if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0) {
            return MemberValue.valid(value.longValue());
        }
        if (value.isTextual() && value.textValue().matches("^[1-9][0-9]{0,18}$")) {
            try {
                return MemberValue.valid(Long.parseLong(value.textValue()));
            } catch (NumberFormatException ignored) {
                return MemberValue.invalidValue();
            }
        }
        return MemberValue.invalidValue();
    }

    private Eligibility eligibility(
            JsonNode snapshot,
            long logicalModuleId,
            long fieldId,
            RecordMemberFieldStore.ProjectedField projected
    ) {
        var module = findById(snapshot.path("modules"), logicalModuleId);
        if (module == null || !enabled(module)) {
            return Eligibility.MISSING;
        }
        var field = findById(snapshot.path("fields"), fieldId);
        if (field == null || !enabled(field)) {
            return Eligibility.MISSING;
        }
        if (!Long.toString(logicalModuleId).equals(field.path("module_id").asText())) {
            return Eligibility.INVALID;
        }
        if (projected == null) {
            return Eligibility.MISSING;
        }
        if (projected.logicalFieldId() != fieldId
                || projected.sourceFieldId() != fieldId
                || projected.fieldSnapshotId() <= 0
                || !"RECORD".equals(projected.fieldScope())
                || !"MEMBER".equals(projected.fieldType())
                || !"MEMBER".equals(field.path("field_type").asText())
                || multiple(field.path("property_json"))
                || multiple(parseProperty(projected.propertyJson()))) {
            return Eligibility.INVALID;
        }
        return Eligibility.ELIGIBLE;
    }

    private static Resolution unresolved(Eligibility eligibility) {
        return eligibility == Eligibility.MISSING
                ? Resolution.sourceMissing()
                : Resolution.sourceInvalid();
    }

    private JsonNode parsePublished(String json) {
        try {
            var value = objectMapper.readTree(json);
            if (value == null || !value.isObject()) {
                throw new IllegalStateException("Published module snapshot is invalid");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published module snapshot is invalid", exception);
        }
    }

    private JsonNode parseProperty(String json) {
        try {
            var value = objectMapper.readTree(json);
            return value == null ? objectMapper.createObjectNode() : value;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published member field property is invalid", exception);
        }
    }

    private static boolean enabledModule(
            JsonNode snapshot,
            long moduleId,
            String moduleCode
    ) {
        var module = findById(snapshot.path("modules"), moduleId);
        return module != null
                && enabled(module)
                && moduleCode.equals(module.path("module_code").asText());
    }

    private static JsonNode findById(JsonNode array, long id) {
        var expected = Long.toString(id);
        for (var value : array) {
            if (expected.equals(value.path("id").asText())) {
                return value;
            }
        }
        return null;
    }

    private static boolean enabled(JsonNode value) {
        return "ENABLED".equals(value.path("desired_status").asText());
    }

    private static boolean multiple(JsonNode property) {
        var value = property.path("multiple");
        return value.isBoolean() ? value.booleanValue() : value.asInt(0) != 0;
    }

    private static boolean validModuleCode(String value) {
        return value != null && value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    }

    private static boolean containsJsonWhitespaceOutsideString(String json) {
        var quoted = false;
        var escaped = false;
        for (var index = 0; index < json.length(); index++) {
            var character = json.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ' '
                    || character == '\t'
                    || character == '\r'
                    || character == '\n') {
                return true;
            }
        }
        return false;
    }

    private enum Eligibility {
        ELIGIBLE,
        MISSING,
        INVALID
    }

    private record MemberValue(Long value, boolean empty, boolean invalid) {
        private static MemberValue valid(long value) {
            return new MemberValue(value, false, false);
        }

        private static MemberValue emptyValue() {
            return new MemberValue(null, true, false);
        }

        private static MemberValue invalidValue() {
            return new MemberValue(null, false, true);
        }
    }
}
