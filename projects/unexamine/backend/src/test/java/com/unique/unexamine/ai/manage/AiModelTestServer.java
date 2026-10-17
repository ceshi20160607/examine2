package com.unique.unexamine.ai.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 两个 HTTP 集成用例共用的本地模型端点，返回可核对的授权事实与字段提取结果。 */
final class AiModelTestServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;

    private AiModelTestServer(HttpServer server) {
        this.server = server;
    }

    static AiModelTestServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", AiModelTestServer::complete);
        server.start();
        return new AiModelTestServer(server);
    }

    String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
    }

    private static void complete(HttpExchange exchange) throws IOException {
        JsonNode request = JSON.readTree(exchange.getRequestBody());
        JsonNode messages = request.path("messages");
        String prompt = messages.path(messages.size() - 1).path("content").asText();
        String answer;
        if (prompt.contains("用户输入：")) {
            String source = prompt.substring(prompt.indexOf("用户输入：") + "用户输入：".length());
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            add(fields, "标题", source, "标题");
            add(fields, "记录编号", source, "记录编号");
            add(fields, "customer_name", source, "客户名称");
            answer = fields.entrySet().stream().map(item -> item.getKey() + ": " + item.getValue())
                    .reduce((left, right) -> left + "\n" + right).orElse("");
        } else {
            JsonNode facts = JSON.readTree(prompt);
            StringBuilder titles = new StringBuilder();
            facts.path("sources").forEach(source -> {
                if (titles.length() > 0) titles.append("、");
                titles.append(source.path("title").asText());
            });
            answer = "当前权限范围内有 " + facts.path("total").asInt() + " 条客户记录。" + titles;
        }
        byte[] response = JSON.writeValueAsBytes(Map.of(
                "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", answer))),
                "usage", Map.of("prompt_tokens", 30, "completion_tokens", 15)));
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, response.length);
        try (var body = exchange.getResponseBody()) { body.write(response); }
    }

    private static void add(Map<String, String> fields, String output, String source, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "[：:]\\s*([^；;\\r\\n]+)").matcher(source);
        if (matcher.find()) fields.put(output, matcher.group(1).strip());
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
