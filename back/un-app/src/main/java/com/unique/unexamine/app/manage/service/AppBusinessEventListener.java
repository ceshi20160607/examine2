package com.unique.unexamine.app.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.app.base.entity.UnApp;
import com.unique.unexamine.app.base.entity.UnAppEvent;
import com.unique.unexamine.app.base.service.IUnAppEventService;
import com.unique.unexamine.app.base.service.IUnAppService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventListener;
import com.unique.unexamine.core.exception.BaseException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 将已发布的应用事件同步投递到回调地址。
 *
 * <p>事件监听器运行在当前业务事务内，回调失败会向上抛出并触发事务回滚；
 * 回调只使用事件上下文快照，不读取未提交的业务记录。发布快照决定回调地址、
 * 重试次数、超时和过滤条件，草稿修改不会影响已经运行的事件。</p>
 */
@Component
public class AppBusinessEventListener implements BusinessEventListener {
    private final IUnAppService apps;
    private final IUnAppEventService events;
    private final ObjectMapper json;
    private volatile HttpClient http;

    /** 注入应用事件配置；HTTP 客户端在首次实际回调时建立。 */
    public AppBusinessEventListener(IUnAppService apps, IUnAppEventService events, ObjectMapper json) {
        this.apps = apps;
        this.events = events;
        this.json = json;
    }

    @Override
    public void onEvent(BusinessEventContext context) {
        Resource resource = resource(context);
        List<UnAppEvent> configured = events.list(new LambdaQueryWrapper<UnAppEvent>()
                .eq(UnAppEvent::getEventCode, context.eventCode())
                .eq(UnAppEvent::getResourceType, resource.type())
                .eq(UnAppEvent::getStatus, "PUBLISHED")
                .isNotNull(UnAppEvent::getPublishedConfigJson));
        if (configured.isEmpty()) return;
        // 一次业务事件发布对应一个稳定实例标识；同一订阅的不同业务事件不能复用配置记录主键。
        String occurrenceId = UUID.randomUUID().toString();
        List<Long> appIds = configured.stream().map(UnAppEvent::getApplicationId).distinct().toList();
        Map<Long, UnApp> appById = apps.listByIds(appIds).stream()
                .filter(app -> app.getDeletedTime() == null)
                .collect(java.util.stream.Collectors.toMap(UnApp::getId, app -> app));
        for (UnAppEvent event : configured) {
            if (event.getResourceId() != null && !Objects.equals(event.getResourceId(), resource.id())) continue;
            UnApp app = appById.get(event.getApplicationId());
            if (app == null || !("PUBLISHED".equals(app.getStatus()) || "ACTIVE".equals(app.getStatus()))) continue;
            if (!scopeMatches(app, context)) continue;
            Published published = published(event);
            if (!matchesFilters(published.filters(), context)) continue;
            deliver(app, event, published, context, resource, occurrenceId);
        }
    }

    /** 事件资源类型由业务事件前缀和完成元数据稳定推导，避免调用方伪造资源范围。 */
    private Resource resource(BusinessEventContext context) {
        String code = context.eventCode();
        String type;
        Long id;
        if (code.startsWith("FLOW_")) {
            type = context.systemId() == null && context.tenantId() == null
                    ? "PLATFORM_FLOW" : "FLOW";
            id = longValue(context.metadata().get("flowId"));
        } else if (code.startsWith("EXAMINE_")) {
            type = context.systemId() == null && context.moduleId() == null
                    ? "PLATFORM_EXAMINE" : "EXAMINE";
            id = longValue(context.metadata().get("examineId"));
        } else {
            type = "MODULE";
            id = context.moduleId();
        }
        return new Resource(type, id);
    }

    private boolean scopeMatches(UnApp app, BusinessEventContext context) {
        if ("PLATFORM".equalsIgnoreCase(app.getScopeType()))
            return context.systemId() == null && context.tenantId() == null;
        return "SYSTEM".equalsIgnoreCase(app.getScopeType())
                && context.systemId() != null && context.tenantId() != null
                && Objects.equals(app.getSystemId(), context.systemId())
                && Objects.equals(app.getTenantId(), context.tenantId());
    }

    private Published published(UnAppEvent event) {
        try {
            JsonNode root = json.readTree(event.getPublishedConfigJson());
            if (root == null || !root.isObject()) throw malformed();
            String callback = text(root, "callbackUrl");
            if (callback == null || callback.isBlank()) throw malformed();
            URI callbackUri = URI.create(callback);
            if (!("http".equalsIgnoreCase(callbackUri.getScheme())
                    || "https".equalsIgnoreCase(callbackUri.getScheme()))
                    || callbackUri.getHost() == null || callbackUri.getUserInfo() != null
                    || callbackUri.getFragment() != null) throw malformed();
            int retry = integer(root, "retry", 0, 0, 5);
            int timeout = integer(root, "timeoutMs", 5000, 100, 30000);
            JsonNode filters = root.get("filters");
            if (filters != null && !filters.isObject()) throw malformed();
            return new Published(callbackUri, retry, timeout, filters);
        } catch (BaseException ex) {
            throw ex;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用事件发布快照损坏", ex);
        }
    }

    private boolean matchesFilters(JsonNode filters, BusinessEventContext context) {
        if (filters == null || filters.isNull() || filters.size() == 0) return true;
        for (var fields = filters.fields(); fields.hasNext();) {
            var entry = fields.next();
            Object actual = context.metadata().containsKey(entry.getKey())
                    ? context.metadata().get(entry.getKey()) : context.values().get(entry.getKey());
            if (actual == null || !json.valueToTree(actual).equals(entry.getValue())) return false;
        }
        return true;
    }

    private void deliver(UnApp app, UnAppEvent event, Published published,
                         BusinessEventContext context, Resource resource, String occurrenceId) {
        ObjectNode body = json.createObjectNode();
        // eventId 保持为订阅配置主键；eventOccurrenceId 才是本次业务事件实例的幂等标识。
        body.put("eventId", event.getId());
        body.put("eventOccurrenceId", occurrenceId);
        body.put("eventCode", context.eventCode());
        body.put("resourceType", resource.type());
        if (resource.id() != null) body.put("resourceId", resource.id());
        put(body, "systemId", context.systemId());
        put(body, "tenantId", context.tenantId());
        put(body, "moduleId", context.moduleId());
        put(body, "recordId", context.recordId());
        put(body, "accountId", context.accountId());
        put(body, "actionMenuId", context.actionMenuId());
        body.set("values", json.valueToTree(context.values()));
        body.set("metadata", json.valueToTree(context.metadata()));
        String payload;
        try { payload = json.writeValueAsString(body); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "应用事件数据无法编码", ex); }
        RuntimeException failure = null;
        for (int attempt = 0; attempt <= published.retry(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(published.callback())
                        .timeout(Duration.ofMillis(published.timeoutMs()))
                        .header("Content-Type", "application/json")
                        .header("X-App-Id", app.getAppId())
                        .header("X-Event-Id", occurrenceId)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<Void> response = http().send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) return;
                failure = new BaseException(SystemCodeEnum.SYSTEM_ERROR,
                        "应用事件回调返回 HTTP " + response.statusCode());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "应用事件回调被中断", ex);
            } catch (Exception ex) {
                failure = new BaseException(SystemCodeEnum.SYSTEM_ERROR, "应用事件回调失败", ex);
            }
        }
        throw failure == null ? new BaseException(SystemCodeEnum.SYSTEM_ERROR, "应用事件回调失败") : failure;
    }

    /** 没有回调订阅时不创建网络连接管理器，避免影响平台普通业务启动。 */
    private HttpClient http() {
        HttpClient current = http;
        if (current != null) return current;
        synchronized (this) {
            if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            return http;
        }
    }

    private void put(ObjectNode body, String key, Long value) {
        if (value != null) body.put(key, value);
        else body.putNull(key);
    }

    private String text(JsonNode root, String key) {
        JsonNode value = root.get(key);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private int integer(JsonNode root, String key, int fallback, int min, int max) {
        JsonNode value = root.get(key);
        int result = value == null ? fallback : value.isIntegralNumber() ? value.intValue() : Integer.MIN_VALUE;
        if (result < min || result > max) throw malformed();
        return result;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) try { return Long.parseLong(text); } catch (NumberFormatException ignored) {}
        return 0L;
    }

    private BaseException malformed() {
        return new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用事件发布快照损坏");
    }

    private record Resource(String type, Long id) {}

    private record Published(URI callback, int retry, int timeoutMs, JsonNode filters) {}
}
