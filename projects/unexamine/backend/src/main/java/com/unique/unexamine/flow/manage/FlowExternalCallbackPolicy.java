package com.unique.unexamine.flow.manage;

import com.unique.unexamine.application.manage.ApplicationFlowCallbackService;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Webhook 与外部节点共用已发布应用回调协议，设计草稿不接受地址、请求头或密钥。 */
final class FlowExternalCallbackPolicy {
    static final String APPLICATION_ID = "applicationId";
    static final String CALLBACK_ID = "callbackId";
    private static final String FROZEN = "frozen";

    private FlowExternalCallbackPolicy() { }

    static boolean supports(String nodeType) {
        return "WEBHOOK".equals(nodeType) || "EXTERNAL".equals(nodeType);
    }

    static Selection parseSelection(Map<String, Object> config) {
        Long applicationId = positiveLong(config == null ? null : config.get(APPLICATION_ID));
        Long callbackId = positiveLong(config == null ? null : config.get(CALLBACK_ID));
        if (applicationId == null || callbackId == null) {
            throw new IllegalArgumentException("请选择当前工作空间内已发布应用的业务事件回调");
        }
        return new Selection(applicationId, callbackId);
    }

    static Map<String, Object> frozen(ApplicationFlowCallbackService.PublishedCallback callback) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 1);
        value.put(FROZEN, true);
        value.put(APPLICATION_ID, callback.applicationId());
        value.put("applicationCode", callback.applicationCode());
        value.put("applicationName", callback.applicationName());
        value.put("applicationVersionId", callback.applicationVersionId());
        value.put("applicationVersionNumber", callback.applicationVersionNumber());
        value.put("applicationSnapshotHash", callback.applicationSnapshotHash());
        value.put(CALLBACK_ID, callback.callbackId());
        value.put("callbackType", callback.callbackType());
        value.put("eventCodes", callback.eventCodes());
        value.put("url", callback.url());
        value.put("signingSecretRef", callback.signingSecretRef());
        value.put("timeoutMillis", callback.timeoutMillis());
        value.put("maxAttempts", callback.maxAttempts());
        return value;
    }

    static Frozen readFrozen(Map<String, Object> config) {
        if (config == null || !Boolean.TRUE.equals(config.get(FROZEN))) {
            throw new IllegalArgumentException("外部节点缺少发布时冻结的应用回调");
        }
        Selection selection = parseSelection(config);
        Long versionId = positiveLong(config.get("applicationVersionId"));
        Integer versionNumber = positiveInt(config.get("applicationVersionNumber"));
        String applicationCode = text(config.get("applicationCode"));
        String applicationName = text(config.get("applicationName"));
        String hash = text(config.get("applicationSnapshotHash"));
        String callbackType = text(config.get("callbackType"));
        String url = text(config.get("url"));
        String signingSecretRef = text(config.get("signingSecretRef"));
        Integer timeoutMillis = positiveInt(config.get("timeoutMillis"));
        Integer maxAttempts = positiveInt(config.get("maxAttempts"));
        if (versionId == null || versionNumber == null || applicationCode.isBlank() || applicationName.isBlank()
                || hash.isBlank() || !"EVENT".equals(callbackType) || signingSecretRef.isBlank()
                || timeoutMillis == null || timeoutMillis < 100 || timeoutMillis > 60000
                || maxAttempts == null || maxAttempts > 10) {
            throw new IllegalArgumentException("外部节点的冻结应用回调不完整");
        }
        URI address;
        try {
            address = URI.create(url);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("外部节点的冻结回调地址无效");
        }
        if (!"https".equalsIgnoreCase(address.getScheme()) || address.getHost() == null) {
            throw new IllegalArgumentException("外部节点的冻结回调地址必须使用 HTTPS");
        }
        return new Frozen(selection.applicationId(), applicationCode, applicationName, versionId, versionNumber,
                hash, selection.callbackId(), strings(config.get("eventCodes")), url, signingSecretRef,
                timeoutMillis, maxAttempts);
    }

    private static Long positiveLong(Object value) {
        if (!(value instanceof Number number)) return null;
        long result = number.longValue();
        return result > 0 ? result : null;
    }

    private static Integer positiveInt(Object value) {
        if (!(value instanceof Number number)) return null;
        int result = number.intValue();
        return result > 0 ? result : null;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).map(String::strip).filter(item -> !item.isBlank()).toList();
    }

    record Selection(Long applicationId, Long callbackId) { }

    record Frozen(
            Long applicationId,
            String applicationCode,
            String applicationName,
            Long applicationVersionId,
            Integer applicationVersionNumber,
            String applicationSnapshotHash,
            Long callbackId,
            List<String> eventCodes,
            String url,
            String signingSecretRef,
            Integer timeoutMillis,
            Integer maxAttempts) { }
}
