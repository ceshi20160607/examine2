package com.unique.examine.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationMigrationStaticContractTest {
    private static final Path MIGRATION = locateMigration();
    private static final Set<String> P1_TABLES = Set.of(
            "un_sys_idempotency",
            "un_audit_security",
            "un_plat_account",
            "un_plat_credential",
            "un_plat_system",
            "un_plat_tenant",
            "un_plat_member",
            "un_plat_member_tenant",
            "un_plat_data_scope",
            "un_plat_role",
            "un_plat_permission",
            "un_plat_role_permission",
            "un_plat_member_role",
            "un_plat_account_role",
            "un_plat_authz_epoch",
            "un_plat_context_session",
            "un_plat_refresh_token"
    );

    @Test
    void migrationCreatesExactlyTheAcceptedP1Tables() throws IOException {
        var sql = Files.readString(MIGRATION);
        var matcher = Pattern.compile("(?im)^CREATE\\s+TABLE\\s+([a-z0-9_]+)\\s*\\(").matcher(sql);
        var tables = new LinkedHashSet<String>();
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }

        assertEquals(P1_TABLES, tables);
        assertFalse(sql.contains("un_plat_role_draft"));
        assertFalse(sql.contains("un_plat_authz_version"));
    }

    @Test
    void migrationIsSchemaOnlyAndContainsTheContractKeys() throws IOException {
        var sql = Files.readString(MIGRATION);
        assertFalse(Pattern.compile("(?i)\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|DROP|TRUNCATE)\\b")
                .matcher(sql).find());
        assertFalse(sql.contains("123123aa"));
        assertTrue(sql.contains("UNIQUE KEY uk_vnext_account_username (username_normalized)"));
        assertTrue(sql.contains("UNIQUE KEY uk_vnext_system_code (system_code)"));
        assertTrue(sql.contains("UNIQUE KEY uk_vnext_member_account (system_id, account_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_vnext_tenant_one_active_default (active_default_system_id)"));
        assertTrue(sql.contains("FOREIGN KEY (system_id, default_tenant_id)"));
        assertTrue(sql.contains("FOREIGN KEY (system_id, member_id, account_id)"));
        assertTrue(sql.contains("UNIQUE KEY uk_vnext_context_token (token_hash)"));
        assertTrue(sql.contains("UNIQUE KEY uk_vnext_refresh_token_hash (token_hash)"));
    }

    @Test
    void vnextProfileUsesIndependentDependenciesAndDisablesLegacyBootstrap() throws IOException {
        var sources = new YamlPropertySourceLoader().load(
                "application-vnext", new ClassPathResource("application-vnext.yml")
        );
        assertEquals(1, sources.size());
        var source = sources.getFirst();

        assertEquals("vnext", source.getProperty("spring.config.activate.on-profile"));
        assertEquals("${EXAMINE_VNEXT_DB_URL}", source.getProperty("spring.datasource.url"));
        assertEquals("${EXAMINE_VNEXT_REDIS_HOST}", source.getProperty("spring.data.redis.host"));
        assertEquals("${EXAMINE_VNEXT_FLYWAY_LOCATIONS:classpath:db/migration-vnext}",
                source.getProperty("spring.flyway.locations"));
        assertEquals(Boolean.FALSE, source.getProperty("spring.flyway.baseline-on-migrate"));
        assertEquals("", source.getProperty("examine.bootstrap.root.username"));
        assertEquals("", source.getProperty("examine.bootstrap.root.password"));
        assertEquals(Boolean.TRUE, source.getProperty("examine.foundation.vnext.enabled"));
        assertNotNull(source.getProperty("examine.foundation.vnext.bootstrap.username"));
        assertNotNull(source.getProperty("examine.foundation.vnext.bootstrap.password"));
    }

    private static Path locateMigration() {
        var candidates = List.of(
                Path.of("sql", "migration-vnext", "V1_0_0__foundation_identity_system_context.sql"),
                Path.of("..", "..", "sql", "migration-vnext", "V1_0_0__foundation_identity_system_context.sql")
        );
        return candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot locate the P1 vNext migration"));
    }
}
