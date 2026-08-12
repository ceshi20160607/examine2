package com.unique.examine.module.runtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordBatchArchiveContractTest {
    @Test
    void freezesRouteHeaderTransactionAndStringRecordIds() throws Exception {
        var root = ModuleRuntimeController.class.getAnnotation(RequestMapping.class);
        var controllerMethod = ModuleRuntimeController.class.getMethod(
                "batchArchiveRecords",
                long.class,
                String.class,
                RecordRuntimeViews.BatchCommandRequest.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = controllerMethod.getAnnotation(PostMapping.class);
        var idempotencyHeader = controllerMethod.getParameters()[3].getAnnotation(RequestHeader.class);
        var serviceMethod = RecordRuntimeService.class.getMethod(
                "batchArchive",
                com.unique.examine.module.runtime.security.RuntimeSession.class,
                String.class,
                RecordRuntimeViews.BatchCommandRequest.class,
                String.class,
                String.class,
                String.class);

        assertThat(root.value()).containsExactly("/api/v1/systems/{systemId}/runtime");
        assertThat(mapping.value()).containsExactly("/modules/{moduleCode}/records:batch-archive");
        assertThat(idempotencyHeader.name()).isEqualTo("Idempotency-Key");
        assertThat(serviceMethod.getAnnotation(Transactional.class)).isNotNull();

        var request = new RecordRuntimeViews.BatchCommandRequest(List.of(
                new RecordRuntimeViews.BatchRecordRef("9007199254740994", 3)));
        assertThat(request.items().getFirst().recordId()).isEqualTo("9007199254740994");
    }

    @Test
    void responseShapeExcludesHistoryAndBranchSpecificNullFields() throws Exception {
        var mapper = new ObjectMapper();
        var success = mapper.readTree(mapper.writeValueAsString(
                new RecordRuntimeViews.BatchMutationResponse(true, List.of(
                        RecordRuntimeViews.BatchMutationItem.applied("20", 4, "ARCHIVED")))));
        var failure = mapper.readTree(mapper.writeValueAsString(
                new RecordRuntimeViews.BatchMutationResponse(false, List.of(
                        RecordRuntimeViews.BatchMutationItem.rejected("20", "VERSION_STALE", 7L)))));

        assertThat(success.path("allApplied").asBoolean()).isTrue();
        assertThat(success.path("items").get(0).path("resultCode").asText()).isEqualTo("APPLIED");
        assertThat(success.path("items").get(0).path("status").asText()).isEqualTo("ARCHIVED");
        assertThat(success.path("items").get(0).path("newVersion").asLong()).isEqualTo(4);
        assertThat(success.path("items").get(0).has("currentVersion")).isFalse();
        assertThat(success.toString()).doesNotContain("historyId");

        assertThat(failure.path("allApplied").asBoolean()).isFalse();
        assertThat(failure.path("items").get(0).path("resultCode").asText()).isEqualTo("VERSION_STALE");
        assertThat(failure.path("items").get(0).path("currentVersion").asLong()).isEqualTo(7);
        assertThat(failure.path("items").get(0).has("newVersion")).isFalse();
        assertThat(failure.path("items").get(0).has("status")).isFalse();
        assertThat(failure.toString()).doesNotContain("historyId");
    }
}
