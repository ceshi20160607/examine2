package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationFieldFacadeTest {

    @Test
    void exposesOnlyExactScalarTypesAndFixedSettingsShape() {
        assertThat(AiConfigurationFieldFacade.FieldType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "TEXT", "LONG_TEXT", "INTEGER", "DECIMAL",
                        "BOOLEAN", "DATE", "DATETIME");
        assertThat(Arrays.stream(AiConfigurationFieldFacade.ScalarSettings.class
                        .getRecordComponents()).map(RecordComponent::getName))
                .containsExactly(
                        "minLength", "maxLength", "trim", "rows",
                        "minimum", "maximum", "precision", "scale",
                        "format", "timezone");
        assertThat(Arrays.stream(AiConfigurationFieldFacade.FieldDraft.class
                        .getRecordComponents()).map(RecordComponent::getType))
                .doesNotContain(Map.class);
    }

    @Test
    void settingsAreStrictlyTypeAppropriate() {
        var text = new AiConfigurationFieldFacade.ScalarSettings(
                1, 200, true, null, null, null,
                null, null, null, null);
        new AiConfigurationFieldFacade.FieldDraft(
                "customer_name", "Customer name",
                AiConfigurationFieldFacade.FieldType.TEXT, true, text);

        assertThatThrownBy(() -> new AiConfigurationFieldFacade.FieldDraft(
                "customer_name", "Customer name",
                AiConfigurationFieldFacade.FieldType.BOOLEAN, false, text))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiConfigurationFieldFacade.FieldDraft(
                "amount", "Amount",
                AiConfigurationFieldFacade.FieldType.INTEGER, false,
                new AiConfigurationFieldFacade.ScalarSettings(
                        null, null, null, null, "1.5", "10",
                        10, 0, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiConfigurationFieldFacade.FieldDraft(
                "created_on", "Created on",
                AiConfigurationFieldFacade.FieldType.DATE, false,
                new AiConfigurationFieldFacade.ScalarSettings(
                        null, null, null, null,
                        "2026-13-01", null, null, null,
                        "yyyy-MM-dd", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
