package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredPropertyValidatorP4C3Test {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredPropertyValidator validator = new StructuredPropertyValidator(objectMapper);

    @Test
    void acceptsExactRelationReferenceAndSubtableProperties() throws Exception {
        assertThat(validate(FieldType.RELATION,
                "{\"multiple\":false,\"displayFieldId\":\"101\",\"allowCreate\":true,"
                        + "\"reverseRelation\":true}"))
                .contains("displayFieldId");
        assertThat(validate(FieldType.REFERENCE,
                "{\"sourceFieldId\":\"201\",\"targetFieldId\":\"101\"}"))
                .contains("targetFieldId");
        assertThat(validate(FieldType.SUBTABLE,
                "{\"minRows\":1,\"maxRows\":200,\"columnFieldIds\":[\"301\",\"302\"],"
                        + "\"allowRowCreate\":true,\"allowRowUpdate\":true,\"allowRowDelete\":true,"
                        + "\"allowRowReorder\":true,\"aggregates\":[{\"id\":\"total\","
                        + "\"function\":\"SUM\",\"columnFieldId\":\"302\"}]}"))
                .contains("aggregates");
    }

    @Test
    void rejectsLegacyDangerousOrOutOfBoundaryProperties() {
        assertInvalid(FieldType.RELATION,
                "{\"multiple\":false,\"cascadeDelete\":true}");
        assertInvalid(FieldType.RELATION,
                "{\"multiple\":false,\"valueFieldId\":\"101\"}");
        assertInvalid(FieldType.REFERENCE,
                "{\"sourceFieldId\":\"201\"}");
        assertInvalid(FieldType.REFERENCE,
                "{\"sourceFieldId\":\"201\",\"targetFieldId\":\"101\",\"multiple\":true}");
        assertInvalid(FieldType.SUBTABLE,
                "{\"minRows\":0,\"maxRows\":201,\"columnFieldIds\":[\"301\"]}");
        assertInvalid(FieldType.SUBTABLE,
                "{\"minRows\":0,\"maxRows\":10,\"columnFieldIds\":[]}");
        assertInvalid(FieldType.SUBTABLE,
                "{\"minRows\":0,\"maxRows\":10,\"columnFieldIds\":[\"301\"],\"allowImport\":true}");
        assertInvalid(FieldType.SUBTABLE,
                "{\"minRows\":0,\"maxRows\":10,\"columnFieldIds\":[\"301\"],"
                        + "\"aggregates\":[{\"id\":\"total\",\"function\":\"SUM\","
                        + "\"columnFieldId\":\"999\"}]}");
    }

    private String validate(FieldType type, String json) throws Exception {
        return validator.field(type, objectMapper.readTree(json));
    }

    private void assertInvalid(FieldType type, String json) {
        assertThatThrownBy(() -> validate(type, json)).isInstanceOf(BusinessException.class);
    }
}
