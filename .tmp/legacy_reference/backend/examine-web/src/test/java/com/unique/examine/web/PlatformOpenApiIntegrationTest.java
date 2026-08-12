package com.unique.examine.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PlatformOpenApiPrincipalFacade;
import com.unique.examine.openapi.domain.*;
import com.unique.examine.openapi.repository.jdbc.JdbcPlatformOpenApiRepository;
import com.unique.examine.openapi.security.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;import java.nio.file.Path;import java.time.*;import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL/Flyway proof for the isolated platform HMAC, nonce and rate path. */
@Testcontainers
class PlatformOpenApiIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44")).withDatabaseName("platform_openapi").withUsername("platform_openapi").withPassword("test-password").withCommand("--log-bin-trust-function-creators=1");
    @Test void migratesAndAuthenticatesPlatformTaskScopeWithReplayProtection(){
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).locations("filesystem:"+migrationRoot().toString().replace('\\','/')).load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()));var now=Instant.parse("2026-08-07T08:00:00Z");
        jdbc.update("INSERT INTO un_plat_account(id,account_code,username,username_normalized,display_name,status,created_at,updated_at,version) VALUES(1,'svc-platform','svc-platform','svc-platform','Platform service','ACTIVE',?,?,0)",java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        var repository=new JdbcPlatformOpenApiRepository(jdbc,new ObjectMapper());var secret="platform-secret-material".getBytes(StandardCharsets.UTF_8);var app=new PlatformOpenApiApplication(101,1,"platformAppKey_123456789","Platform tasks",PlatformOpenApiApplication.Status.ACTIVE,Set.of("platform.task.read"),List.of("127.0.0.1"),2,1,now,1,now,1,0);repository.insertApplication(app);repository.insertCredential(new OpenApiCredential(102,101,1,"env://PLATFORM_TEST_SECRET",OpenApiCredential.Status.ACTIVE,now,null,now,1));
        PlatformOpenApiPrincipalFacade principals=id->new PlatformOpenApiPrincipalFacade.Principal(id,7,true,Set.of("platform.task.read"));var authenticator=new PlatformOpenApiAuthenticator(repository,ref->Optional.of(secret.clone()),principals,Clock.fixed(now,ZoneOffset.UTC));var route=PlatformOpenApiRoutePolicy.resolve("GET","/openapi/v1/platform/tasks").orElseThrow();var headers=headers(secret,now,"nonce-platform-0001");var request=new PlatformOpenApiAuthenticator.Request("GET","/openapi/v1/platform/tasks","limit=20",new byte[0],"127.0.0.1",headers,route);
        var authenticated=authenticator.authenticate(request,new OpenApiAttempt());assertThat(authenticated.session().contextType().name()).isEqualTo("PLATFORM");assertThat(authenticated.session().accountId()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT request_count FROM un_platform_openapi_rate_bucket WHERE application_id=101",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->authenticator.authenticate(request,new OpenApiAttempt())).isInstanceOf(com.unique.examine.core.error.BusinessException.class).hasMessageContaining("nonce");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_platform_openapi_nonce WHERE application_id=101",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'un_platform_openapi_%'",Integer.class)).isEqualTo(5);
    }
    private static OpenApiHeaders headers(byte[] secret,Instant now,String nonce){var key="platformAppKey_123456789";var idempotency="platform-read-1";var canonical=OpenApiCanonicalRequest.canonical("GET","/openapi/v1/platform/tasks","limit=20",new byte[0],Long.toString(now.getEpochSecond()),nonce,idempotency);return new OpenApiHeaders(key,Long.toString(now.getEpochSecond()),nonce,OpenApiCanonicalRequest.signature(secret,canonical),idempotency);}
    private static Path migrationRoot(){for(var p=Path.of("").toAbsolutePath();p!=null;p=p.getParent()){var c=p.resolve("sql").resolve("migration");if(java.nio.file.Files.isDirectory(c))return c;}throw new IllegalStateException("migration root missing");}
}
