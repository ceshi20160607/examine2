package com.unique.examine.core.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.error.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiResponseFactoryTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void successShouldUseRequestContext() {
        RequestContextHolder.set(RequestContext.builder()
                .requestId("req_test")
                .traceId("trc_test")
                .path("/api/v1/test")
                .method("GET")
                .build());

        ApiResponse<String> response = ApiResponseFactory.success("ok");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("req_test");
        assertThat(response.getTraceId()).isEqualTo("trc_test");
        assertThat(response.getCode()).isEqualTo("COMMON_OK");
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getMeta().getPath()).isEqualTo("/api/v1/test");
        assertThat(response.getErrors()).isEmpty();
    }

    @Test
    void failureShouldContainErrorDetails() {
        RequestContextHolder.set(RequestContext.builder()
                .requestId("req_error")
                .traceId("trc_error")
                .build());
        ApiErrorDetail detail = ApiErrorDetail.builder()
                .targetType("FIELD")
                .fieldCode("name")
                .reason("REQUIRED_MISSING")
                .retryable(false)
                .userMessage("名称不能为空")
                .build();

        ApiResponse<Object> response = ApiResponseFactory.failure(CommonErrorCode.PARAM_INVALID, "参数不合法",
                List.of(detail));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getCode()).isEqualTo("COMMON_PARAM_INVALID");
        assertThat(response.getRequestId()).isEqualTo("req_error");
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getFieldCode()).isEqualTo("name");
    }
}
