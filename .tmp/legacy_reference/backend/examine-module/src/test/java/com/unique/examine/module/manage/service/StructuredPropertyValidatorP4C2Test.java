package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredPropertyValidatorP4C2Test {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredPropertyValidator validator = new StructuredPropertyValidator(objectMapper);

    @Test
    void acceptsFrozenP4C2Properties() throws Exception {
        assertThat(validate(FieldType.PHONE, "{\"defaultCountry\":\"CN\"}"))
                .contains("defaultCountry");
        assertThat(validate(FieldType.IDENTITY,
                "{\"identityKind\":\"CN_RESIDENT_ID\"}"))
                .contains("CN_RESIDENT_ID");
        assertThat(validate(FieldType.BARCODE,
                "{\"symbologies\":[\"CODE128\",\"EAN13\"]}"))
                .contains("EAN13");
        assertThat(validate(FieldType.JSON,
                "{\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\",\"maxLength\":32}}},"
                        + "\"queryPaths\":[{\"pathSnapshotId\":101,\"path\":\"$.code\",\"type\":\"STRING\"}]}"))
                .contains("pathSnapshotId");
        assertThat(validate(FieldType.STATUS,
                "{\"initialStateIds\":[\"101\"],\"transitions\":[{\"from\":\"101\",\"to\":\"102\"}]}"))
                .contains("initialStateIds");
    }

    @Test
    void rejectsPropertiesThatWeakenP4C2CanonicalBehavior() {
        assertInvalid(FieldType.PHONE, "{\"defaultCountry\":\"XX\"}");
        assertInvalid(FieldType.IDENTITY, "{}");
        assertInvalid(FieldType.IDENTITY,
                "{\"identityKind\":\"GENERIC\",\"pattern\":\".*\"}");
        assertInvalid(FieldType.GEO, "{\"coordinateSystem\":\"GCJ02\"}");
        assertInvalid(FieldType.BARCODE, "{\"symbologies\":[\"QR\"]}");
        assertInvalid(FieldType.RICH_TEXT, "{\"sanitize\":false}");
        assertInvalid(FieldType.JSON,
                "{\"queryPaths\":[{\"pathSnapshotId\":1,\"path\":\"$..code\",\"type\":\"STRING\"}]}");
        assertInvalid(FieldType.JSON,
                "{\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\"}}},"
                        + "\"queryPaths\":[{\"pathSnapshotId\":1,\"path\":\"$.code\",\"type\":\"STRING\"}]}");
        assertInvalid(FieldType.SECRET, "{\"trim\":true}");
        assertInvalid(FieldType.STATUS,
                "{\"initialStateIds\":[\"101\"],\"transitions\":[{\"from\":\"101\",\"to\":\"101\"}]}");
    }

    private String validate(FieldType type, String json) throws Exception {
        return validator.field(type, objectMapper.readTree(json));
    }

    private void assertInvalid(FieldType type, String json) {
        assertThatThrownBy(() -> validate(type, json)).isInstanceOf(BusinessException.class);
    }
}
