package com.unique.unexamine.examine.manage.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 外部 API 节点策略；按发布配置验证请求、认证和响应字段映射后返回审批待变更字段。 */
@Component
public class ApiNodeStrategy implements ExamineAutoNodeStrategy {
    private static final Pattern VALUE_TEMPLATE = Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_.]*)}");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private final ObjectMapper json;
    private volatile HttpClient client;

    public ApiNodeStrategy(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public boolean supports(String nodeType) {
        if (nodeType == null) return false;
        return Set.of("API", "API_CALL", "HTTP_API", "EXTERNAL_API").contains(nodeType.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean interactive() { return false; }

    @Override
    public boolean supportsAction(String actionCode) { return false; }

    @Override
    public boolean complete(String approvalMode, long pendingUserCount) { return true; }

    @Override
    public String userStatus(String actionCode) { return "EXECUTED"; }

    @Override
    public String nodeStatus(String actionCode) { return "EXECUTED"; }

    @Override
    public Map<String, Object> execute(ExamineAutoNodeContext context) {
        JsonNode config = context.node().path("config").isObject() ? context.node().path("config") : context.node();
        String url = config.path("url").asText("").trim();
        if (url.isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"API 节点缺少 url");
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"API 节点 url 无效", ex); }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"API 节点只允许 http 或 https 地址");
        String method = config.path("method").asText("POST").toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"API 节点请求方法不支持: " + method);
        JsonNode bodyNode = config.has("requestBody") ? config.get("requestBody") : config.get("body");
        JsonNode renderedBody = bodyNode == null || bodyNode.isNull() ? null : render(bodyNode, context.values());
        String body = renderedBody == null ? "" : renderedBody.toString();
        HttpRequest.BodyPublisher publisher = body.isBlank() ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(100, Math.min(30000, config.path("timeoutMs").asLong(10000)))))
                .method(method, publisher).header("Content-Type", "application/json");
        JsonNode headers = config.get("headers");
        if (headers != null && !headers.isNull() && !headers.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"API 节点 headers 必须是对象");
        if (headers != null && headers.isObject()) headers.fields().forEachRemaining(item -> {
            String value = renderText(item.getValue().asText(), context.values());
            validHeader(item.getKey(), value);
            request.header(item.getKey(), value);
        });
        applyAuth(request, config, context.values());
        try {
            HttpResponse<String> response = client().send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"API 节点响应状态异常: " + response.statusCode());
            JsonNode responseJson = response.body() == null || response.body().isBlank()
                    ? json.nullNode() : json.readTree(response.body());
            Map<String, Object> updates = mappedValues(config, responseJson);
            // API 节点只返回待写入的字段；运行服务把结果合并到审批快照，终态时再提交业务记录。
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.statusCode());
            result.put("updatedFields", updates.keySet());
            result.put("fieldValues", updates);
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"API 节点调用被中断", ex);
        } catch (java.io.IOException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"API 节点调用失败", ex);
        } catch (java.io.UncheckedIOException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"API 节点 HTTP 客户端不可用", ex);
        }
    }

    /** 仅在真正执行 API 节点时建立连接管理器，避免外部网络不可用阻断整个后端启动。 */
    private HttpClient client() {
        HttpClient current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            return client;
        }
    }

    private void applyAuth(HttpRequest.Builder request, JsonNode config, Map<String, Object> values) {
        JsonNode auth = config.path("auth");
        String type = auth.path("type").asText(config.path("authType").asText("TOKEN")).toUpperCase(Locale.ROOT);
        if ("NONE".equals(type)) return;
        if ("BASIC".equals(type)) {
            String username = renderText(auth.path("username").asText(""), values);
            String password = renderText(auth.path("password").asText(""), values);
            if (username.isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"API 节点 Basic 认证缺少 username");
            String encoded = Base64.getEncoder().encodeToString((username + ":" + password)
                    .getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + encoded);
            return;
        }
        String token = renderText(auth.path("token").asText(config.path("token").asText("")), values);
        if (token.isBlank())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "API 节点认证缺少 token");
        String header = renderText(auth.path("header").asText(config.path("authHeader").asText("Authorization")), values);
        String defaultPrefix = "API_KEY".equals(type) ? "" : "Bearer ";
        String prefix = renderText(auth.path("prefix").asText(config.path("tokenPrefix").asText(defaultPrefix)), values);
        String value = prefix + token;
        validHeader(header, value);
        request.header(header, value);
    }

    /** 运行时再次检查 Header，防止历史快照或人工写库绕过发布期校验。 */
    private void validHeader(String name, String value) {
        if (name == null || name.isBlank() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
                || value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "API 节点 Header 无效");
    }

    /** 递归渲染请求 JSON 中的字段占位符，值只来自当前审批运行快照。 */
    private JsonNode render(JsonNode source, Map<String, Object> values) {
        if (source == null || source.isNull()) return source;
        if (source.isTextual()) return json.getNodeFactory().textNode(renderText(source.asText(), values));
        if (source.isArray()) {
            var result = json.createArrayNode();
            source.forEach(item -> result.add(render(item, values)));
            return result;
        }
        if (source.isObject()) {
            var result = json.createObjectNode();
            source.fields().forEachRemaining(item -> result.set(item.getKey(), render(item.getValue(), values)));
            return result;
        }
        return source;
    }

    private String renderText(String source, Map<String, Object> values) {
        if (source == null || source.isEmpty()) return source;
        Matcher matcher = VALUE_TEMPLATE.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = resolveValue(values, matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 支持字段快照中的一层或多层对象路径，未找到的值按空文本处理。 */
    private Object resolveValue(Map<String, Object> values, String path) {
        if (values == null || path == null || path.isBlank()) return null;
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private Map<String, Object> mappedValues(JsonNode config, JsonNode response) {
        Map<String, Object> result = new LinkedHashMap<>();
        JsonNode mappings = config.has("fieldMappings") ? config.get("fieldMappings") : config.get("responseMappings");
        if (mappings == null) return result;
        if (mappings.isObject()) mappings.fields().forEachRemaining(item -> {
            JsonNode value = readPath(response, item.getValue().asText());
            if (value != null && !value.isMissingNode() && !value.isNull()) result.put(item.getKey(), json.convertValue(value, Object.class));
        });
        if (mappings.isArray()) mappings.forEach(item -> {
            String fieldKey = item.path("fieldKey").asText(item.path("field_key").asText(""));
            String path = item.path("responsePath").asText(item.path("response_path").asText(""));
            JsonNode value = readPath(response, path);
            if (!fieldKey.isBlank() && value != null && !value.isMissingNode() && !value.isNull())
                result.put(fieldKey, json.convertValue(value, Object.class));
        });
        return result;
    }

    private JsonNode readPath(JsonNode root, String path) {
        if (root == null || root.isNull()) return json.getNodeFactory().missingNode();
        if (path == null || path.isBlank()) return root;
        if (path.startsWith("/")) return root.at(path);
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            current = current.path(part);
            if (current.isMissingNode()) return current;
        }
        return current;
    }
}
