package com.unique.examine.web.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class ControllerParamLoggingAspect {

    private static final int MAX_LEN = 2000;

    private final ObjectMapper objectMapper;

    public ControllerParamLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Before("within(@org.springframework.web.bind.annotation.RestController *)")
    public void logControllerArgs(JoinPoint jp) {
        Object[] args = jp.getArgs();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", jp.getSignature().toShortString());
        payload.put("args", sanitizeArgs(args));

        String json = toJson(payload);
        if (json.length() > MAX_LEN) {
            json = json.substring(0, MAX_LEN) + "...(truncated)";
        }
        // 入参单独一行（便于按 requestId 对齐链路摘要）
        org.slf4j.LoggerFactory.getLogger("TRACE.PARAM").info("params={}", json);

        // 同时把入参摘要挂到 request 上，方便最终输出“调用链摘要”时拼起来
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null) {
            ra.setAttribute("traceParams", json, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private Object sanitizeArgs(Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a == null) {
                out[i] = null;
                continue;
            }
            String cn = a.getClass().getName();
            // 避免把 request/response/stream 这种对象塞进日志
            if (cn.startsWith("jakarta.servlet.") || cn.startsWith("org.springframework.web.multipart.")) {
                out[i] = cn;
                continue;
            }
            // 简单脱敏：password 字段在 DTO 的 toString/json 里也可能暴露，这里做字符串层兜底
            String s = toJson(a);
            s = s.replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
            s = s.replaceAll("(?i)\"pass\"\\s*:\\s*\"[^\"]*\"", "\"pass\":\"***\"");
            out[i] = s;
        }
        return out;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }
}

