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

class RecordBatchTransferContractTest {
    @Test
    void freezesRouteHeaderTransactionAndRequestShape() throws Exception {
        var controllerMethod = ModuleRuntimeController.class.getMethod(
                "batchTransferRecords",
                long.class,
                String.class,
                RecordRuntimeViews.BatchTransferRequest.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = controllerMethod.getAnnotation(PostMapping.class);
        var idempotencyHeader = controllerMethod.getParameters()[3].getAnnotation(RequestHeader.class);
        var serviceMethod = RecordRuntimeService.class.getMethod(
                "batchTransfer",
                RuntimeSession.class,
                String.class,
                RecordRuntimeViews.BatchTransferRequest.class,
                String.class,
                String.class,
                String.class);

        assertThat(mapping.value()).containsExactly("/modules/{moduleCode}/records:batch-transfer");
        assertThat(idempotencyHeader.name()).isEqualTo("Idempotency-Key");
        assertThat(serviceMethod.getAnnotation(Transactional.class)).isNotNull();

        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(
                new RecordRuntimeViews.BatchTransferRequest(
                        List.of(new RecordRuntimeViews.BatchRecordRef("9007199254740994", 1)),
                        "9007199254740995")));
        assertThat(json.fieldNames()).toIterable().containsExactly("items", "targetMemberId");
        assertThat(json.path("items").get(0).path("recordId").asText())
                .isEqualTo("9007199254740994");
        assertThat(json.path("targetMemberId").asText()).isEqualTo("9007199254740995");
    }

    @Test
    void reusesBatchMutationSuccessShapeWithUnchangedActiveStatus() throws Exception {
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(
                new RecordRuntimeViews.BatchMutationResponse(true, List.of(
                        RecordRuntimeViews.BatchMutationItem.applied("20", 4, "ACTIVE")))));

        assertThat(json.path("allApplied").asBoolean()).isTrue();
        assertThat(json.path("items").get(0).path("recordId").asText()).isEqualTo("20");
        assertThat(json.path("items").get(0).path("resultCode").asText()).isEqualTo("APPLIED");
        assertThat(json.path("items").get(0).path("newVersion").asLong()).isEqualTo(4);
        assertThat(json.path("items").get(0).path("status").asText()).isEqualTo("ACTIVE");
    }
}
