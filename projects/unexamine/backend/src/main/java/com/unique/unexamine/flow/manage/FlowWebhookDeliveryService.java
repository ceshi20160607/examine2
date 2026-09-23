package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.operations.manage.OperationsSecretResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程回调只发送稳定业务引用，由接收应用按自身授权读取数据；不把流程变量、密钥或内部快照外发。
 */
@Service
public class FlowWebhookDeliveryService {
    private final OperationsSecretResolver secretResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FlowWebhookDeliveryService(OperationsSecretResolver secretResolver, ObjectMapper objectMapper) {
        this.secretResolver = secretResolver;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 同一 eventId 的请求体和幂等头保持稳定，接收方可在超时重试时防止重复处理。 */
    public DeliveryResult deliver(
            Long systemId,
            FlowInstance instance,
            String nodeKey,
            String nodeName,
            Map<String, Object> frozenConfig,
            String eventId,
            String occurredTime) {
        FlowExternalCallbackPolicy.Frozen callback;
        try {
            callback = FlowExternalCallbackPolicy.readFrozen(frozenConfig);
        } catch (IllegalArgumentException invalid) {
            throw failure("FLOW_WEBHOOK_CONFIG_INVALID", invalid.getMessage());
        }
        String secret = secretResolver.resolveWebhookMaterial(systemId, callback.signingSecretRef());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("eventId", eventId);
        payload.put("eventCode", "FLOW.NODE.REACHED");
        payload.put("occurredTime", occurredTime);
        payload.put("applicationCode", callback.applicationCode());
        payload.put("applicationVersion", callback.applicationVersionNumber());
        payload.put("flowInstanceId", instance.getId());
        payload.put("flowVersionId", instance.getFlowVersionId());
        payload.put("nodeKey", nodeKey);
        payload.put("nodeName", nodeName);
        payload.put("businessType", instance.getBusinessType());
        payload.put("businessId", instance.getBusinessId());
        payload.put("title", instance.getTitle());
        String body = json(payload);
        String payloadHash = sha256(body);
        String deliveryTime = java.time.Instant.now().toString();
        String signature = hmac(secret, deliveryTime + "\n" + eventId + "\n" + payloadHash);
        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(callback.url()))
                .timeout(Duration.ofMillis(callback.timeoutMillis()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "unexamine-flow-webhook/1")
                .header("X-Unexamine-Event-Id", eventId)
                .header("X-Unexamine-Timestamp", deliveryTime)
                .header("X-Unexamine-Signature", "v1=" + signature)
                .header("X-Unexamine-Application", callback.applicationCode())
                .header("X-Unexamine-Application-Version", String.valueOf(callback.applicationVersionNumber()))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failure("FLOW_WEBHOOK_RESPONSE_REJECTED", "外部服务返回非成功状态");
            }
            return new DeliveryResult(response.statusCode(), payloadHash, callback.applicationCode(),
                    callback.applicationVersionNumber(), callback.callbackId());
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw failure("FLOW_WEBHOOK_TIMEOUT", "外部服务响应超时");
        } catch (ConnectException unavailable) {
            throw failure("FLOW_WEBHOOK_UNAVAILABLE", "外部服务当前不可连接");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure("FLOW_WEBHOOK_INTERRUPTED", "外部服务调用被中断");
        } catch (IOException unavailable) {
            throw failure("FLOW_WEBHOOK_UNAVAILABLE", "外部服务当前不可用");
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Webhook payload cannot be serialized", failure);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Webhook signature cannot be generated", failure);
        }
    }

    private DomainException failure(String code, String message) {
        return new DomainException(code, message, HttpStatus.BAD_GATEWAY);
    }

    public record DeliveryResult(
            int responseStatus,
            String payloadHash,
            String applicationCode,
            Integer applicationVersion,
            Long callbackId) { }
}
