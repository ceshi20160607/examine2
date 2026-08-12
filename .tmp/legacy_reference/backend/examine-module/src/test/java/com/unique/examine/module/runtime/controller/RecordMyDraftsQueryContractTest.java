package com.unique.examine.module.runtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RecordMyDraftsQueryContractTest {
    @Test
    void freezesReadOnlyPostRouteBodyAndServiceShape() throws Exception {
        var controllerMethod = ModuleRuntimeController.class.getMethod(
                "queryMyDrafts",
                long.class,
                String.class,
                RecordRuntimeViews.MyDraftsQueryRequest.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = controllerMethod.getAnnotation(PostMapping.class);
        var requestBody = controllerMethod.getParameters()[2].getAnnotation(RequestBody.class);
        var serviceMethod = RecordRuntimeService.class.getMethod(
                "myDraftsQuery",
                RuntimeSession.class,
                String.class,
                RecordRuntimeViews.MyDraftsQueryRequest.class);

        assertThat(mapping.value())
                .containsExactly("/modules/{moduleCode}/records:my-drafts-query");
        assertThat(requestBody).isNotNull();
        assertThat(Arrays.stream(controllerMethod.getParameters())
                .noneMatch(parameter -> parameter.isAnnotationPresent(RequestHeader.class))).isTrue();
        assertThat(serviceMethod.getReturnType()).isEqualTo(RecordRuntimeViews.RecordPage.class);
        assertThat(serviceMethod.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void freezesRequestAndExistingRecordPageResponseProjection() throws Exception {
        var mapper = new ObjectMapper();
        var request = mapper.readTree(mapper.writeValueAsString(
                new RecordRuntimeViews.MyDraftsQueryRequest(1, 50, "草稿")));
        var response = mapper.readTree(mapper.writeValueAsString(
                new RecordRuntimeViews.RecordPage(
                        java.util.List.of(new RecordRuntimeViews.RecordSummary(
                                "9007199254740994",
                                "R-001",
                                3,
                                "DRAFT",
                                "草稿标题",
                                java.util.List.of())),
                        1,
                        50,
                        1)));

        assertThat(request.fieldNames()).toIterable().containsExactly("page", "size", "q");
        assertThat(response.path("rows").get(0).path("recordId").asText())
                .isEqualTo("9007199254740994");
        assertThat(response.path("rows").get(0).path("status").asText()).isEqualTo("DRAFT");
        assertThat(response.path("page").asInt()).isEqualTo(1);
        assertThat(response.path("size").asInt()).isEqualTo(50);
        assertThat(response.path("total").asLong()).isEqualTo(1);
    }
}
