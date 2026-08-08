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

class RecordBatchTrashContractTest {
    @Test
    void freezesRouteHeaderTransactionAndZeroVersionRequest() throws Exception {
        var controllerMethod = ModuleRuntimeController.class.getMethod(
                "batchTrashRecords",
                long.class,
                String.class,
                RecordRuntimeViews.BatchCommandRequest.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = controllerMethod.getAnnotation(PostMapping.class);
        var idempotencyHeader = controllerMethod.getParameters()[3].getAnnotation(RequestHeader.class);
        var serviceMethod = RecordRuntimeService.class.getMethod(
                "batchTrash",
                RuntimeSession.class,
                String.class,
                RecordRuntimeViews.BatchCommandRequest.class,
                String.class,
                String.class,
                String.class);

        assertThat(mapping.value()).containsExactly("/modules/{moduleCode}/records:batch-trash");
        assertThat(idempotencyHeader.name()).isEqualTo("Idempotency-Key");
        assertThat(serviceMethod.getAnnotation(Transactional.class)).isNotNull();

        var request = new RecordRuntimeViews.BatchCommandRequest(List.of(
                new RecordRuntimeViews.BatchRecordRef("9007199254740994", 0)));
        assertThat(request.items().getFirst().expectedVersion()).isZero();
    }

    @Test
    void successShapeIsTrashedAndContainsNoHistoryId() throws Exception {
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(
                new RecordRuntimeViews.BatchMutationResponse(true, List.of(
                        RecordRuntimeViews.BatchMutationItem.applied("20", 1, "TRASHED")))));

        assertThat(json.path("allApplied").asBoolean()).isTrue();
        assertThat(json.path("items").get(0).path("recordId").asText()).isEqualTo("20");
        assertThat(json.path("items").get(0).path("resultCode").asText()).isEqualTo("APPLIED");
        assertThat(json.path("items").get(0).path("newVersion").asLong()).isEqualTo(1);
        assertThat(json.path("items").get(0).path("status").asText()).isEqualTo("TRASHED");
        assertThat(json.toString()).doesNotContain("historyId");
    }
}
