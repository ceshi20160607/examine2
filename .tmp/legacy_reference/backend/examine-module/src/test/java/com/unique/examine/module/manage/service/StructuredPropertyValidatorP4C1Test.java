package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredPropertyValidatorP4C1Test {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredPropertyValidator validator = new StructuredPropertyValidator(objectMapper);

    @Test
    void acceptsCanonicalP4C1Properties() throws Exception {
        assertThat(validator.field(FieldType.MONEY,
                objectMapper.readTree("{\"currencies\":[\"CNY\",\"USD\"]}")))
                .contains("CNY", "USD");
        assertThat(validator.field(FieldType.PERCENT,
                objectMapper.readTree("{\"scale\":4,\"minimum\":0,\"maximum\":100}")))
                .contains("\"scale\":4");
        assertThat(validator.field(FieldType.PROGRESS,
                objectMapper.readTree("{\"scale\":2,\"step\":0.25}")))
                .contains("\"scale\":2");
        assertThat(validator.field(FieldType.RATING,
                objectMapper.readTree("{\"maxRating\":5,\"step\":1}")))
                .contains("\"maxRating\":5");
        assertThat(validator.field(FieldType.MULTI_SELECT,
                objectMapper.readTree("{\"maxSelections\":100}")))
                .contains("\"maxSelections\":100");
    }

    @Test
    void rejectsPropertiesThatWouldChangeCanonicalBehavior() throws Exception {
        assertInvalid(FieldType.MONEY, "{}");
        assertInvalid(FieldType.MONEY, "{\"currencies\":[\"CNY\",\"CNY\"]}");
        assertInvalid(FieldType.MONEY, "{\"currency\":\"ZZZ\"}");
        assertInvalid(FieldType.PERCENT, "{\"scale\":5}");
        assertInvalid(FieldType.PROGRESS, "{\"scale\":3}");
        assertInvalid(FieldType.RATING, "{\"maxRating\":10}");
        assertInvalid(FieldType.MULTI_SELECT, "{\"maxSelections\":101}");
    }

    private void assertInvalid(FieldType type, String json) throws Exception {
        assertThatThrownBy(() -> validator.field(type, objectMapper.readTree(json)))
                .isInstanceOf(BusinessException.class);
    }
}
