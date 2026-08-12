package com.unique.examine.web.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityGateTest {
    @TempDir
    Path root;

    @Test
    void acceptsExplicitTlsAuthenticationAbsoluteStorageAndBoundedKeyRing() throws Exception {
        var environment = validEnvironment();

        assertThatCode(() -> ProductionSecurityGate.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void productionProfileCannotBypassTheGateWithLocalDeploymentDefault() {
        var environment = new MockEnvironment().withProperty(
                "examine.security.deployment-mode", "LOCAL");
        environment.setActiveProfiles("production");

        assertThatCode(() -> {
            if (!ProductionSecurityGate.isProduction(environment)) {
                throw new AssertionError("production profile was not recognized");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsDatabasePublicKeyRetrievalEvenWhenTlsWasRequested() throws Exception {
        var environment = validEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://db.example.test/examine?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=true");

        assertThatThrownBy(() -> ProductionSecurityGate.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFY_IDENTITY");
    }

    @Test
    void rejectsRedisWithoutTlsOrAuthentication() throws Exception {
        var environment = validEnvironment()
                .withProperty("spring.data.redis.ssl.enabled", "false")
                .withProperty("spring.data.redis.password", "");

        assertThatThrownBy(() -> ProductionSecurityGate.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production Redis TLS is required");
    }

    @Test
    void rejectsHttpS3InProductionEvenWhenLoopbackTestFlagIsPresent() throws Exception {
        var environment = validEnvironment()
                .withProperty("examine.file.storage.mode", "S3")
                .withProperty("examine.file.storage.s3.endpoint", "http://127.0.0.1:9000")
                .withProperty("examine.file.storage.s3.allow-loopback-http-for-testing", "true")
                .withProperty("examine.file.storage.s3.access-key", "test-access")
                .withProperty("examine.file.storage.s3.secret-key", "test-secret");

        assertThatThrownBy(() -> ProductionSecurityGate.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production S3 endpoint must use HTTPS");
    }

    @Test
    void rejectsAccountRecoveryMailWithoutAuthenticatedStartTls() throws Exception {
        var environment = validEnvironment()
                .withProperty("examine.event.account-recovery-mail.enabled", "true")
                .withProperty("examine.event.account-recovery-mail.host", "smtp.example.test")
                .withProperty("examine.event.account-recovery-mail.from", "noreply@example.test")
                .withProperty("examine.event.account-recovery-mail.start-tls", "false")
                .withProperty("examine.event.account-recovery-mail.smtp-auth", "false");

        assertThatThrownBy(() -> ProductionSecurityGate.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production account-recovery SMTP STARTTLS is required");
    }

    private MockEnvironment validEnvironment() throws Exception {
        var keyRing = root.resolve("sensitive-key-ring.json");
        Files.writeString(keyRing, "{\"active\":\"test\"}");
        return new MockEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://db.example.test/examine?sslMode=VERIFY_IDENTITY")
                .withProperty("spring.datasource.username", "app-user")
                .withProperty("spring.datasource.password", "deployment-secret")
                .withProperty("spring.data.redis.ssl.enabled", "true")
                .withProperty("spring.data.redis.password", "redis-secret")
                .withProperty("examine.security.secure-cookies", "true")
                .withProperty("examine.file.storage.mode", "LOCAL")
                .withProperty("examine.file.storage.local.root", root.resolve("files").toString())
                .withProperty("examine.runtime.sensitive.key-ring-root", root.toString())
                .withProperty("examine.runtime.sensitive.key-ring-file", keyRing.toString());
    }
}
