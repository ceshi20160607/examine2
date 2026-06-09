package com.unique.examine.core.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.common.response.ApiResponse;
import com.unique.examine.core.common.response.ApiResponseFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Controller 成功响应统一包装。
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class UnifiedResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    /**
     * 判断是否需要统一包装响应。
     *
     * @param returnType Controller 返回类型
     * @param converterType 消息转换器类型
     * @return true 表示进入统一包装
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        return !ResponseEntity.class.isAssignableFrom(parameterType)
                && !Resource.class.isAssignableFrom(parameterType);
    }

    /**
     * 将成功响应包装为 ApiResponse。
     *
     * @param body 原始响应体
     * @param returnType Controller 返回类型
     * @param selectedContentType 响应媒体类型
     * @param selectedConverterType 消息转换器类型
     * @param request 当前请求
     * @param response 当前响应
     * @return 统一响应体
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        ApiResponse<Object> wrapped = ApiResponseFactory.success(body);
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                return objectMapper.writeValueAsString(wrapped);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to serialize unified response", exception);
            }
        }
        return wrapped;
    }
}
