package com.unique.examine.app.manage.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unique.examine.app.manage.service.OpenApiExternalService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiExternalControllerTest {

    private OpenApiExternalService openApiExternalService;

    private OpenApiExternalController controller;

    @BeforeEach
    void setUp() {
        openApiExternalService = mock(OpenApiExternalService.class);
        controller = new OpenApiExternalController(openApiExternalService);
    }

    @Test
    void shouldPassRawBodyToRecordQueryForSignature() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String rawBody = "{\"moduleCode\":\"order\",\"filters\":[]}";

        controller.queryRecords(request, rawBody);

        verify(openApiExternalService).queryRecords(request, rawBody);
    }

    @Test
    void shouldPassRawBodyToFlowActionForSignature() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String rawBody = "{\"action\":\"APPROVE\",\"taskVersion\":1}";

        controller.handleTask(request, 20L, rawBody);

        verify(openApiExternalService).handleTask(request, 20L, rawBody);
    }
}
