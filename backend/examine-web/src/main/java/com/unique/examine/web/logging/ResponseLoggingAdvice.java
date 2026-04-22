package com.unique.examine.web.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Component
@ControllerAdvice
public class ResponseLoggingAdvice implements ResponseBodyAdvice<Object> {

    private static final int MAX_LEN = 2000;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                 Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                 ServerHttpRequest request, ServerHttpResponse response) {
        try {
            if (!(request instanceof ServletServerHttpRequest sr) || !(response instanceof ServletServerHttpResponse)) {
                return body;
            }
            HttpServletRequest servletReq = sr.getServletRequest();
            Object rid = servletReq.getAttribute(com.unique.examine.web.security.RequestContextFilter.ATTR_REQUEST_ID);
            String requestId = rid == null ? "" : String.valueOf(rid);

            // 二进制/文件下载类返回不打
            if (body instanceof byte[] || body instanceof org.springframework.core.io.Resource) {
                return body;
            }

            String json = toJson(body);
            if (json.length() > MAX_LEN) {
                json = json.substring(0, MAX_LEN) + "...(truncated)";
            }
            org.slf4j.LoggerFactory.getLogger("TRACE.RESP").info("requestId={} resp={}", requestId, json);
        } catch (Exception ignore) {
            // ignore
        }
        return body;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }
}

