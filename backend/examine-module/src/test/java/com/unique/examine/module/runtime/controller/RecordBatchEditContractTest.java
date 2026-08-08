package com.unique.examine.module.runtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordBatchEditContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void freezesRouteHeaderTransactionAndRequestShape() throws Exception {
        var controllerMethod = ModuleRuntimeController.class.getMethod(
                "batchEditRecords",
                long.class,
                String.class,
                RecordRuntimeViews.BatchEditRequest.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = controllerMethod.getAnnotation(PostMapping.class);
        var idempotencyHeader = controllerMethod.getParameters()[3].getAnnotation(RequestHeader.class);
        var serviceMethod = RecordRuntimeService.class.getMethod(
                "batchEdit",
                RuntimeSession.class,
                String.class,
                RecordRuntimeViews.BatchEditRequest.class,
                String.class,
                String.class,
                String.class);

        assertThat(mapping.value()).containsExactly("/modules/{moduleCode}/records:batch-edit");
        assertThat(idempotencyHeader.name()).isEqualTo("Idempotency-Key");
        assertThat(serviceMethod.getAnnotation(Transactional.class)).isNotNull();

        var json = objectMapper.readTree(objectMapper.writeValueAsString(
                new RecordRuntimeViews.BatchEditRequest(
                        List.of(new RecordRuntimeViews.BatchRecordRef("9007199254740994", 2)),
                        List.of(
                                new RecordRuntimeViews.BatchFieldChange(
                                        "priority", "SET", objectMapper.getNodeFactory().textNode("HIGH")),
                                new RecordRuntimeViews.BatchFieldChange("note", "CLEAR", null)))));
        assertThat(json.fieldNames()).toIterable().containsExactly("items", "changes");
        assertThat(json.path("items").get(0).path("recordId").asText())
                .isEqualTo("9007199254740994");
        assertThat(json.path("changes").get(0).fieldNames()).toIterable()
                .containsExactly("fieldCode", "operation", "value");
        assertThat(json.path("changes").get(1).fieldNames()).toIterable()
                .containsExactly("fieldCode", "operation");
    }

    @Test
    void reusesActiveBatchMutationResponseInRequestOrder() throws Exception {
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
                new RecordRuntimeViews.BatchMutationResponse(true, List.of(
                        RecordRuntimeViews.BatchMutationItem.applied("20", 3, "ACTIVE"),
                        RecordRuntimeViews.BatchMutationItem.applied("3", 5, "ACTIVE")))));

        assertThat(json.path("allApplied").asBoolean()).isTrue();
        assertThat(json.path("items").get(0).path("recordId").asText()).isEqualTo("20");
        assertThat(json.path("items").get(0).path("resultCode").asText()).isEqualTo("APPLIED");
        assertThat(json.path("items").get(0).path("newVersion").asLong()).isEqualTo(3);
        assertThat(json.path("items").get(0).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(json.path("items").get(1).path("recordId").asText()).isEqualTo("3");
    }
}
