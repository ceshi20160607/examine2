package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationFieldCommandCodecTest {
    private static final Instant EXPIRY =
            Instant.parse("2026-08-04T01:15:00Z");

    @Test
    void roundTripsOneCanonicalExactCommandIncludingZeroProviderVersion() {
        var codec = new AiConfigurationFieldCommandCodec(new ObjectMapper());
        var command = codec.command(request(), snapshot(), EXPIRY);
        var encoded = codec.encode(command);

        assertThat(codec.decode(encoded)).isEqualTo(command);
        assertThat(encoded).contains("\"providerVersion\":0");
        assertThat(encoded).doesNotContain(
                "dictionaryId", "targetModuleId", "sql", "publish");
    }

    @Test
    void rejectsUnknownKeysUnsupportedTypesAndNonCanonicalPermissionOrder()
            throws Exception {
        var json = new ObjectMapper();
        var codec = new AiConfigurationFieldCommandCodec(json);
        var encoded = codec.encode(codec.command(request(), snapshot(), EXPIRY));

        var unknown = (ObjectNode) json.readTree(encoded);
        unknown.put("publish", true);
        var unknownJson = json.writeValueAsString(unknown);
        assertInvalid(() -> codec.decode(unknownJson));

        var unsupported = (ObjectNode) json.readTree(encoded);
        ((ObjectNode) unsupported.get("field"))
                .put("fieldType", "RELATION");
        var unsupportedJson = json.writeValueAsString(unsupported);
        assertInvalid(() -> codec.decode(unsupportedJson));

        var booleanSettings = (ObjectNode) json.readTree(encoded);
        var field = (ObjectNode) booleanSettings.get("field");
        field.put("fieldType", "BOOLEAN");
        ((ObjectNode) field.get("settings")).put("minLength", 1);
        var booleanJson = json.writeValueAsString(booleanSettings);
        assertInvalid(() -> codec.decode(booleanJson));

        var reordered = (ObjectNode) json.readTree(encoded);
        var permissions = reordered.putArray("effectivePermissions");
        permissions.add("system.admin.access");
        permissions.add("module.config.manage");
        var reorderedJson = json.writeValueAsString(reordered);
        assertInvalid(() -> codec.decode(reorderedJson));
    }

    private static AiConfigurationFieldFacade.PrepareRequest request() {
        return new AiConfigurationFieldFacade.PrepareRequest(
                "proposal-1", 7, 11, 21, 17, 3,
                Set.of("system.admin.access", "module.config.manage"),
                "customers", new AiConfigurationFieldFacade.FieldDraft(
                "credit_limit", "Credit limit",
                AiConfigurationFieldFacade.FieldType.DECIMAL, false,
                new AiConfigurationFieldFacade.ScalarSettings(
                        null, null, null, null, "0", "10000",
                        18, 2, null, null)),
                "51", "61", 0, "prompt-v1", "request-1", "trace-1");
    }

    private static AiConfigurationFieldContextReader.Snapshot snapshot() {
        return new AiConfigurationFieldContextReader.Snapshot(
                31, 41, "customers", 5, 20,
                false, 3, request().effectivePermissions());
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_CONFIG_FIELD_COMMAND_INVALID");
    }
}
