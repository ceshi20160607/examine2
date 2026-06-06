package com.unique.examine.generator;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.TemplateType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MyBatis-Plus 基础 CRUD 代码生成器。
 */
class MybatisPlusCodeGeneratorTest {

    private static final String JDBC_URL = env("EXAMINE_DB_URL",
            "jdbc:mysql://192.168.0.211:3306/examine1?characterEncoding=utf8&useSSL=false"
                    + "&zeroDateTimeBehavior=convertToNull&tinyInt1isBit=false&serverTimezone=Asia/Shanghai"
                    + "&useAffectedRows=true&allowPublicKeyRetrieval=true");

    private static final String JDBC_USERNAME = env("EXAMINE_DB_USERNAME", "root");

    private static final String JDBC_PASSWORD = env("EXAMINE_DB_PASSWORD", "Admin001m");

    private static final List<String> TABLE_PREFIXES = List.of(
            "un_platt_", "un_module_", "un_flow_", "un_upload_", "un_app_", "un_openapi_", "un_sys_", "un_audit_");

    private static final List<TableModuleMapping> TABLE_MODULE_MAPPINGS = List.of(
            new TableModuleMapping("un_platt_", "examine-plat", "com.unique.examine.plat.base", List.of(
                    "un_platt_system",
                    "un_platt_tenant",
                    "un_platt_account",
                    "un_platt_account_tenant",
                    "un_platt_department",
                    "un_platt_role",
                    "un_platt_permission",
                    "un_platt_role_permission",
                    "un_platt_account_role",
                    "un_platt_dict",
                    "un_platt_dict_item")),
            new TableModuleMapping("un_module_", "examine-module", "com.unique.examine.module.base", List.of(
                    "un_module_model",
                    "un_module_field",
                    "un_module_field_option",
                    "un_module_page",
                    "un_module_menu",
                    "un_module_record",
                    "un_module_record_value",
                    "un_module_data_scope",
                    "un_module_export_job")),
            new TableModuleMapping("un_flow_", "examine-flow", "com.unique.examine.flow.base", List.of(
                    "un_flow_template",
                    "un_flow_template_version",
                    "un_flow_instance",
                    "un_flow_task",
                    "un_flow_approval_log")),
            new TableModuleMapping("un_upload_", "examine-upload", "com.unique.examine.upload.base", List.of(
                    "un_upload_storage_config",
                    "un_upload_file",
                    "un_upload_attachment",
                    "un_upload_import_export_job")),
            new TableModuleMapping("un_app_/un_openapi_", "examine-app", "com.unique.examine.app.base", List.of(
                    "un_app_application",
                    "un_app_version",
                    "un_openapi_client",
                    "un_openapi_credential",
                    "un_openapi_scope",
                    "un_openapi_ip_whitelist",
                    "un_openapi_idempotent",
                    "un_openapi_access_log")),
            new TableModuleMapping("un_sys_/un_audit_", "examine-core", "com.unique.examine.core.base", List.of(
                    "un_sys_config",
                    "un_sys_login_log",
                    "un_audit_operation_log"))
    );

    /**
     * 执行初始化 SQL 并生成所有表的基础 CRUD。
     */
    @Test
    void importDatabaseAndGenerateBaseCrud() throws Exception {
        Path backendRoot = findBackendRoot();
        Path workspaceRoot = backendRoot.getParent();
        Path initSql = workspaceRoot.resolve("sql").resolve("init.sql");

        int executedStatements = executeInitSql(initSql);
        for (TableModuleMapping mapping : TABLE_MODULE_MAPPINGS) {
            generateForModule(backendRoot, mapping);
        }
        writeGenerationReport(backendRoot, initSql, executedStatements);
    }

    private static void generateForModule(Path backendRoot, TableModuleMapping mapping) {
        Path moduleRoot = backendRoot.resolve(mapping.moduleName());
        Path javaOutputDir = moduleRoot.resolve("src/main/java");
        Path xmlOutputDir = moduleRoot.resolve("src/main/resources")
                .resolve(mapping.packageName().replace('.', '/'))
                .resolve("mapper/xml");

        FastAutoGenerator.create(JDBC_URL, JDBC_USERNAME, JDBC_PASSWORD)
                .globalConfig(builder -> builder
                        .author("codex")
                        .commentDate("yyyy-MM-dd")
                        .outputDir(javaOutputDir.toString())
                        .disableOpenDir())
                .packageConfig(builder -> builder
                        .parent(mapping.packageName())
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .pathInfo(Map.of(OutputFile.xml, xmlOutputDir.toString())))
                .strategyConfig(builder -> {
                    builder.addInclude(mapping.tables()).addTablePrefix(TABLE_PREFIXES);
                    builder.entityBuilder()
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .idType(IdType.AUTO)
                            .logicDeleteColumnName("deleted");
                    builder.mapperBuilder()
                            .enableBaseResultMap()
                            .enableBaseColumnList();
                    builder.serviceBuilder()
                            .formatServiceFileName("I%sService");
                    builder.controllerBuilder().disable();
                })
                .templateConfig(builder -> builder.disable(TemplateType.CONTROLLER))
                .execute();
    }

    private static int executeInitSql(Path initSql) throws IOException, SQLException {
        String sql = Files.readString(initSql, StandardCharsets.UTF_8);
        List<String> statements = splitSqlStatements(sql);
        String serverUrl = JDBC_URL.replaceFirst("//([^/:?]+):(\\d+)/[^?]+\\?", "//$1:$2/?");
        try (Connection connection = DriverManager.getConnection(serverUrl, JDBC_USERNAME, JDBC_PASSWORD);
             Statement statement = connection.createStatement()) {
            int executed = 0;
            for (String sqlStatement : statements) {
                if (!sqlStatement.isBlank()) {
                    statement.execute(sqlStatement);
                    executed++;
                }
            }
            return executed;
        }
    }

    private static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char previous = i > 0 ? sql.charAt(i - 1) : '\0';
            if (ch == '\'' && !inDoubleQuote && !inBacktick && previous != '\\') {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote && !inBacktick && previous != '\\') {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
            }
            if (ch == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                statements.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }

    private static void writeGenerationReport(Path backendRoot, Path initSql, int executedStatements) throws IOException {
        Path reportPath = backendRoot.resolve("docs").resolve("mybatis-plus-generation.md");
        Files.createDirectories(reportPath.getParent());

        StringBuilder report = new StringBuilder();
        report.append("# MyBatis-Plus Generation Report\n\n");
        report.append("## Database Connection Source\n\n");
        report.append("- Source: `docs/service_info.md`\n");
        report.append("- JDBC URL: `").append(maskJdbcUrl(JDBC_URL)).append("`\n");
        report.append("- Username: `").append(JDBC_USERNAME).append("`\n\n");
        report.append("## SQL Import\n\n");
        report.append("- SQL file: `").append(initSql).append("`\n");
        report.append("- Command: `mvn -pl examine-web -Dtest=MybatisPlusCodeGeneratorTest test`\n");
        report.append("- Result: success\n");
        report.append("- Executed statements: ").append(executedStatements).append("\n\n");
        report.append("## Generator Command\n\n");
        report.append("`mvn -pl examine-web -Dtest=MybatisPlusCodeGeneratorTest test`\n\n");
        report.append("## Table And Module Mapping\n\n");
        for (TableModuleMapping mapping : TABLE_MODULE_MAPPINGS) {
            report.append("- `").append(mapping.prefix()).append("` -> `").append(mapping.moduleName())
                    .append("` -> `").append(mapping.packageName()).append("`\n");
            for (String table : mapping.tables()) {
                report.append("  - `").append(table).append("`\n");
            }
        }
        report.append("\n## Output Paths\n\n");
        for (TableModuleMapping mapping : TABLE_MODULE_MAPPINGS) {
            report.append("- `backend/").append(mapping.moduleName())
                    .append("/src/main/java/").append(mapping.packageName().replace('.', '/')).append("/`\n");
            report.append("- `backend/").append(mapping.moduleName())
                    .append("/src/main/resources/").append(mapping.packageName().replace('.', '/'))
                    .append("/mapper/xml/`\n");
        }
        report.append("\n## Execution Status\n\n");
        report.append("- Actual generator execution: yes\n");
        report.append("- Generated table count: ").append(TABLE_MODULE_MAPPINGS.stream()
                .mapToInt(mapping -> mapping.tables().size()).sum()).append("\n");
        report.append("- OpenAPI tables: mapped to `examine-app` because `docs/db_design.md` assigns OpenAPI management to that business module.\n");
        report.append("- `un_sys_` and `un_audit_` tables: mapped to `examine-core` because they provide system configuration, login log, and audit foundations.\n");
        report.append("- Updated at: ").append(LocalDateTime.now()).append("\n");

        Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
    }

    private static Path findBackendRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("examine-web"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate backend root");
    }

    private static String maskJdbcUrl(String jdbcUrl) {
        return jdbcUrl.replaceAll("password=[^&]+", "password=******");
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record TableModuleMapping(String prefix, String moduleName, String packageName, List<String> tables) {
    }
}
