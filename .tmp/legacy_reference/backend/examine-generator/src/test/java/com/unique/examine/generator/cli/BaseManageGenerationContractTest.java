package com.unique.examine.generator.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseManageGenerationContractTest {
    private static final List<TableSpec> P1_TABLES = List.of(
            new TableSpec("foundation_account", "Account"),
            new TableSpec("foundation_credential", "Credential"),
            new TableSpec("foundation_system", "System"),
            new TableSpec("foundation_tenant", "Tenant"),
            new TableSpec("foundation_member", "Member"),
            new TableSpec("foundation_member_tenant", "MemberTenant"),
            new TableSpec("foundation_data_scope", "DataScope"),
            new TableSpec("foundation_role", "Role"),
            new TableSpec("foundation_permission", "Permission"),
            new TableSpec("foundation_role_permission", "RolePermission"),
            new TableSpec("foundation_member_role", "MemberRole"),
            new TableSpec("foundation_account_role", "AccountRole"),
            new TableSpec("foundation_authz_epoch", "AuthzEpoch"),
            new TableSpec("foundation_context_session", "ContextSession"),
            new TableSpec("foundation_refresh_token", "RefreshToken")
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesOnlyTableShapedBasePersistenceForP1FoundationTables() throws Exception {
        var backendRoot = temporaryDirectory.resolve("backend");
        var moduleName = "generated-foundation";
        Files.createDirectories(backendRoot.resolve(moduleName));

        var url = "jdbc:h2:mem:foundation_generator;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE foundation_account (
                        id BIGINT PRIMARY KEY,
                        account_code VARCHAR(64) NOT NULL,
                        display_name VARCHAR(128) NOT NULL,
                        version INT NOT NULL DEFAULT 0,
                        deleted_at TIMESTAMP NULL
                    )
                    """);
            for (var table : P1_TABLES.subList(1, P1_TABLES.size())) {
                if (table.typeName().equals("Tenant")) {
                    statement.execute("""
                            CREATE TABLE foundation_tenant (
                                id BIGINT PRIMARY KEY,
                                system_id BIGINT NOT NULL,
                                is_default BOOLEAN NOT NULL,
                                active_default_system_id BIGINT GENERATED ALWAYS AS (
                                    CASE WHEN is_default THEN system_id ELSE NULL END
                                )
                            )
                            """);
                } else {
                    statement.execute("CREATE TABLE " + table.tableName() + " (id BIGINT PRIMARY KEY)");
                }
            }
            statement.execute("""
                    CREATE TABLE foundationXbusiness_action (
                        id BIGINT PRIMARY KEY
                    )
                    """);
            statement.execute("""
                    CREATE TABLE foundation_business_action (
                        id BIGINT PRIMARY KEY
                    )
                    """);
        }

        var javaRoot = backendRoot.resolve(moduleName)
                .resolve("src/main/java/com/unique/examine/foundation/vnext/base");
        var mapperXmlRoot = backendRoot.resolve(moduleName).resolve("src/main/resources/mapper/vnext/base");
        var legacyMapperPath = backendRoot.resolve(moduleName).resolve(
                "src/main/java/com/unique/examine/foundation/base/mapper/GeneratedFoundationAccountMapper.java"
        );
        Files.createDirectories(legacyMapperPath.getParent());
        var legacyMapper = "package com.unique.examine.foundation.base.mapper; interface GeneratedFoundationAccountMapper {}\n";
        Files.writeString(legacyMapperPath, legacyMapper);
        var mapperPath = javaRoot.resolve("mapper/VNextGeneratedFoundationAccountMapper.java");
        Files.createDirectories(mapperPath.getParent());
        Files.writeString(mapperPath, """
                package com.unique.examine.foundation.base.mapper;

                import org.apache.ibatis.annotations.Update;

                interface VNextGeneratedFoundationAccountMapper {
                    @Update("update foundation_account set display_name = 'business override'")
                    int publishAccount();
                }
                """);

        var options = new GeneratorCli.Options(
                backendRoot,
                moduleName,
                "foundation_",
                "com.unique.examine.foundation",
                "vnext.base",
                "VNext",
                P1_TABLES.stream().map(TableSpec::tableName).toList(),
                true
        );
        GeneratorCli.run(
                options,
                url,
                "sa",
                ""
        );

        var expectedFiles = new ArrayList<Path>();
        for (var table : P1_TABLES) {
            var typeName = table.typeName();
            expectedFiles.add(javaRoot.resolve("entity").resolve(typeName + ".java"));
            expectedFiles.add(javaRoot.resolve("mapper")
                    .resolve("VNextGeneratedFoundation" + typeName + "Mapper.java"));
            expectedFiles.add(javaRoot.resolve("service")
                    .resolve("IVNextGeneratedFoundation" + typeName + "Service.java"));
            expectedFiles.add(javaRoot.resolve("service/impl")
                    .resolve("VNextGeneratedFoundation" + typeName + "ServiceImpl.java"));
            expectedFiles.add(mapperXmlRoot.resolve("VNextGeneratedFoundation" + typeName + "Mapper.xml"));
        }
        assertTrue(expectedFiles.stream().allMatch(Files::isRegularFile),
                () -> "missing generated Base files: " + expectedFiles.stream()
                        .filter(path -> !Files.isRegularFile(path)).toList());
        assertEquals(P1_TABLES.size() * 5, expectedFiles.size());

        var firstGeneration = contentsOf(expectedFiles);
        var entity = firstGeneration.get(javaRoot.resolve("entity/Account.java"));
        var mapper = firstGeneration.get(mapperPath);
        var service = firstGeneration.get(
                javaRoot.resolve("service/IVNextGeneratedFoundationAccountService.java")
        );
        var serviceImplementation = firstGeneration.get(
                javaRoot.resolve("service/impl/VNextGeneratedFoundationAccountServiceImpl.java")
        );
        var mapperXml = firstGeneration.get(
                mapperXmlRoot.resolve("VNextGeneratedFoundationAccountMapper.xml")
        );
        var tenant = firstGeneration.get(javaRoot.resolve("entity/Tenant.java"));
        var tenantMapperXml = firstGeneration.get(
                mapperXmlRoot.resolve("VNextGeneratedFoundationTenantMapper.xml")
        );

        assertTrue(entity.contains("package com.unique.examine.foundation.vnext.base.entity;"));
        assertTrue(mapper.contains("package com.unique.examine.foundation.vnext.base.mapper;"));
        assertTrue(mapper.contains("extends BaseMapper<Account>"));
        assertTrue(service.contains("package com.unique.examine.foundation.vnext.base.service;"));
        assertTrue(service.contains("extends IService<Account>"));
        assertTrue(serviceImplementation.contains("package com.unique.examine.foundation.vnext.base.service.impl;"));
        assertTrue(serviceImplementation.contains(
                "extends ServiceImpl<VNextGeneratedFoundationAccountMapper, Account>"
        ));
        assertTrue(serviceImplementation.contains("implements IVNextGeneratedFoundationAccountService"));
        assertTrue(mapperXml.contains(
                "com.unique.examine.foundation.vnext.base.mapper.VNextGeneratedFoundationAccountMapper"
        ));
        assertTrue(mapperXml.contains("BaseResultMap"));
        assertTrue(mapperXml.contains("Base_Column_List"));
        assertTrue(tenant.contains("private Long activeDefaultSystemId;"));
        assertTrue(tenant.contains("insertStrategy = FieldStrategy.NEVER"));
        assertTrue(tenant.contains("updateStrategy = FieldStrategy.NEVER"));
        assertTrue(tenantMapperXml.contains("active_default_system_id"));

        var generatedJava = firstGeneration.entrySet().stream()
                .filter(entry -> entry.getKey().toString().endsWith(".java"))
                .map(Map.Entry::getValue)
                .reduce("", (left, right) -> left + "\n" + right);
        assertFalse(generatedJava.contains(".manage."), "generator must not create product behavior in Base");
        assertFalse(generatedJava.contains("java.sql."), "service/controller code must not contain inline SQL APIs");
        assertFalse(generatedJava.matches("(?is).*\\b(SELECT|INSERT|UPDATE|DELETE)\\b.*"),
                "generated Java must not contain special SQL");
        assertFalse(generatedJava.matches("(?is).*\\b(login|replacePassword|approve|publish)\\b.*"),
                "generated Base must not contain product actions");
        assertFalse(Files.exists(javaRoot.resolve("controller")),
                "generated Base controllers are disabled for product-facing actions");
        assertEquals(legacyMapper, Files.readString(legacyMapperPath),
                "vNext generation must coexist with and leave legacy Mapper registration types untouched");
        assertFalse(mapperPath.getFileName().equals(legacyMapperPath.getFileName()),
                "vNext and legacy Mapper simple names must differ for MyBatis registration");
        assertFalse(Files.exists(javaRoot.resolve("service/IAccountService.java")),
                "vNext IService simple names must not collide with legacy Spring types");
        assertFalse(Files.exists(javaRoot.resolve("service/impl/GeneratedFoundationAccountServiceImpl.java")),
                "vNext ServiceImpl simple names must not collide with legacy Spring beans");
        assertFalse(Files.exists(javaRoot.resolve("entity/BusinessAction.java")),
                "the exact P1 allow-list and literal prefix must both exclude non-contract tables");

        GeneratorCli.run(options, url, "sa", "");
        assertEquals(firstGeneration, contentsOf(expectedFiles),
                "repeated generation must produce byte-stable Base files");
    }

    private static Map<Path, String> contentsOf(List<Path> paths) throws Exception {
        var contents = new LinkedHashMap<Path, String>();
        for (var path : paths) {
            contents.put(path, Files.readString(path));
        }
        return contents;
    }

    private record TableSpec(String tableName, String typeName) {
    }
}
