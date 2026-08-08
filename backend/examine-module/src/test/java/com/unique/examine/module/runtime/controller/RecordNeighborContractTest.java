package com.unique.examine.module.runtime.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordNeighborContractTest {
    @Test
    void freezesTheNeighborRouteAndStringIdAnchorContract() throws Exception {
        var root = ModuleRuntimeController.class.getAnnotation(RequestMapping.class);
        var mapping = ModuleRuntimeController.class.getMethod(
                        "recordNeighbor",
                        long.class,
                        String.class,
                        long.class,
                        RecordRuntimeViews.NeighborRequest.class,
                        Object.class,
                        HttpServletRequest.class)
                .getAnnotation(PostMapping.class);

        assertThat(root.value()).containsExactly("/api/v1/systems/{systemId}/runtime");
        assertThat(mapping.value()).containsExactly(
                "/modules/{moduleCode}/records/{recordId}:neighbors");

        var anchor = new RecordRuntimeViews.SortAnchor(
                List.of(JsonNodeFactory.instance.textNode("2026-07-27T09:00:00")),
                "9007199254740994");
        var row = new RecordRuntimeViews.RecordSummary(
                "9007199254740994",
                "WO-2",
                7,
                "ACTIVE",
                "Next work order",
                List.of(),
                anchor);
        var response = new RecordRuntimeViews.NeighborResponse(
                row,
                false,
                "opaque",
                "trace-1");

        assertThat(response.neighbor().recordId()).isEqualTo("9007199254740994");
        assertThat(response.neighbor().sortAnchor().recordId()).isEqualTo("9007199254740994");
        assertThat(response.boundary()).isFalse();
        assertThat(response.querySnapshotToken()).isEqualTo("opaque");
        assertThat(response.correlationId()).isEqualTo("trace-1");
    }
}
