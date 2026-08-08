package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordContextFacadeTest {

    @Test
    void snapshotsRequestAndCarriesDisplayValuesOnly() {
        var permissions = new HashSet<>(Set.of("system.runtime.access"));
        var fields = new ArrayList<>(List.of("name", "secret_note"));
        var request = new AiRecordContextFacade.Request(
                11, 13, 17, permissions, "work_order", "23", fields);
        permissions.clear();
        fields.clear();

        assertThat(request.effectivePermissions()).containsExactly("system.runtime.access");
        assertThat(request.outboundFieldCodes()).containsExactly("name", "secret_note");
        assertThat(AiRecordContextFacade.DisplayValue.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("fieldCode", "displayValue");
    }

    @Test
    void rejectsInvalidIdentityAndOutboundFieldShapes() {
        assertThatThrownBy(() -> request("0", List.of("name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordId");
        assertThatThrownBy(() -> request("23", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outboundFieldCodes");
        assertThatThrownBy(() -> request("23", List.of("name", "name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outboundFieldCodes");
        assertThatThrownBy(() -> request("23", List.of("not-valid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outboundFieldCodes");
    }

    private static AiRecordContextFacade.Request request(
            String recordId, List<String> fields
    ) {
        return new AiRecordContextFacade.Request(
                11, 13, 17, Set.of(), "work_order", recordId, fields);
    }
}
