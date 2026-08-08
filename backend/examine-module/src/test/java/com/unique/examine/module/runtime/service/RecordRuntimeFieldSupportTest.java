package com.unique.examine.module.runtime.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordRuntimeFieldSupportTest {

    @Test
    void exposesTheSameSupportedTypesToModuleAvailabilityChecks() {
        assertThat(RecordRuntimeService.supportsFieldType("TEXT")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("SECRET")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("RELATION")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("REFERENCE")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("SUBTABLE")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("TENANT")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("AUTO_NUMBER")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("CREATED_BY")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("UPDATED_AT")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("AI_FILL")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("ATTACHMENT")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("IMAGE")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("FILE_GROUP")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("SIGNATURE")).isTrue();
        assertThat(RecordRuntimeService.supportsFieldType("NOT_IMPLEMENTED")).isFalse();
    }

    @Test
    void excludesAiFillFromEveryOrdinaryRecordWriter() throws Exception {
        var field = RecordRuntimeService.class.getDeclaredField("P4_C4_DERIVED_TYPES");
        field.setAccessible(true);

        var derivedTypes = (java.util.Set<?>) field.get(null);
        assertThat(derivedTypes.stream().anyMatch("AI_FILL"::equals)).isTrue();
    }
}
