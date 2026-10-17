package com.unique.unexamine.authentication.manage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** 密码链接仅交给受控投递出口；开发投递箱由服务器操作员读取，不暴露给匿名接口。 */
@Component
public class PasswordResetDelivery {
    private final ObjectMapper objectMapper;
    private final String environmentCode;
    private final String configuredMode;
    private final String publicUrl;
    private final Path outboxDirectory;
    private final String webhookUrl;
    private final String webhookToken;

    public PasswordResetDelivery(ObjectMapper objectMapper,
            @Value("${app.deployment.environment-code:development}") String environmentCode,
            @Value("${APP_PASSWORD_RESET_DELIVERY_MODE:}") String configuredMode,
            @Value("${APP_PASSWORD_RESET_PUBLIC_URL:http://127.0.0.1:15173}") String publicUrl,
            @Value("${APP_PASSWORD_RESET_OUTBOX_DIR:/tmp/unexamine-password-reset-outbox}") String outboxDirectory,
            @Value("${APP_PASSWORD_RESET_WEBHOOK_URL:}") String webhookUrl,
            @Value("${APP_PASSWORD_RESET_WEBHOOK_TOKEN:}") String webhookToken) {
        this.objectMapper = objectMapper;
        this.environmentCode = environmentCode;
        this.configuredMode = configuredMode;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.outboxDirectory = Path.of(outboxDirectory).toAbsolutePath().normalize();
        this.webhookUrl = webhookUrl;
        this.webhookToken = webhookToken;
    }

    /** 投递方式必须显式配置；开发环境的本地投递箱也不能自动启用。 */
    public String availableMode() {
        boolean localEnvironment = "development".equalsIgnoreCase(environmentCode)
                || "test".equalsIgnoreCase(environmentCode);
        String mode = configuredMode.toUpperCase();
        if ("LOCAL_OUTBOX".equals(mode) && localEnvironment) return mode;
        if ("WEBHOOK".equals(mode) && webhookUrl.startsWith("https://") && !webhookToken.isBlank()
                && publicUrl.startsWith("https://")) return mode;
        throw new DomainException("PASSWORD_RESET_DELIVERY_UNAVAILABLE", "密码找回投递尚未配置，请联系平台管理员", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** 登录页只暴露是否可用，不暴露投递方式及任何服务端配置。 */
    public boolean isAvailable() {
        try {
            availableMode();
            return true;
        } catch (DomainException exception) {
            return false;
        }
    }

    public void deliver(String email, String token) {
        String mode = availableMode();
        String resetUrl = publicUrl + "/reset-password?token=" + token;
        try {
            if ("LOCAL_OUTBOX".equals(mode)) {
                writeLocalOutbox(email, resetUrl);
            } else {
                postWebhook(email, resetUrl);
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DomainException("PASSWORD_RESET_DELIVERY_FAILED", "密码找回链接投递失败，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void writeLocalOutbox(String email, String resetUrl) throws IOException {
        Files.createDirectories(outboxDirectory);
        Files.setPosixFilePermissions(outboxDirectory, PosixFilePermissions.fromString("rwx------"));
        Path file = outboxDirectory.resolve(UUID.randomUUID() + ".json");
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file, json(email, resetUrl));
    }

    private void postWebhook(String email, String resetUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + webhookToken)
                .POST(HttpRequest.BodyPublishers.ofString(json(email, resetUrl))).build();
        HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("password reset delivery rejected");
        }
    }

    private String json(String email, String resetUrl) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("recipientEmail", email, "resetUrl", resetUrl, "expiresInMinutes", 30));
    }
}
