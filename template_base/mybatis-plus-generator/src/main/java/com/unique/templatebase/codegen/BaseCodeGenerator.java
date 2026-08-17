package com.unique.templatebase.codegen;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.INameConvert;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.TemplateType;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BaseCodeGenerator {
    private static final ObjectMapper JSON = new ObjectMapper();

    private BaseCodeGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: BaseCodeGenerator <project-codegen.json>");
        }

        Path configPath = Path.of(args[0]).toAbsolutePath().normalize();
        GeneratorConfig config = JSON.readValue(configPath.toFile(), GeneratorConfig.class);
        Path projectRoot = resolveProjectRoot(configPath, config.projectRoot());
        Path sourceRoot = resolveProjectPath(projectRoot, config.sourceRoot(), "sourceRoot");
        Path mapperXmlRoot = resolveProjectPath(projectRoot, config.mapperXmlRoot(), "mapperXmlRoot");
        Path migrationRoot = resolveProjectPath(projectRoot, config.migrationRoot(), "migrationRoot");
        Path manifestPath = resolveProjectPath(projectRoot, config.manifestPath(), "manifestPath");
        String url = environmentOrDefault("APP_DB_URL", config.database().url());
        String username = environmentOrDefault("APP_DB_USERNAME", config.database().username());
        String password = environmentOrDefault("APP_DB_PASSWORD", config.database().password());

        validateConfig(config, sourceRoot, mapperXmlRoot, migrationRoot);
        migrate(url, username, password, migrationRoot);
        validateDatabaseTables(url, username, password, config.modules());
        cleanGeneratedBase(config, sourceRoot, mapperXmlRoot);

        List<String> generatedModules = new ArrayList<>();
        for (ModuleConfig module : config.modules()) {
            generateModule(config.basePackage(), module, sourceRoot, mapperXmlRoot, url, username, password);
            generatedModules.add(module.name());
        }
        Map<String, Object> manifest = writeManifest(
                projectRoot, manifestPath, config, sourceRoot, mapperXmlRoot, url, username, password
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("generator", "MyBatis-Plus Generator 3.5.17");
        result.put("database", sanitizedDatabase(url));
        result.put("moduleCount", generatedModules.size());
        result.put("modules", generatedModules);
        result.put("tableCount", config.modules().stream().mapToInt(module -> module.tables().size()).sum());
        result.put("schemaSha256", manifest.get("schemaSha256"));
        result.put("generatedFileCount", ((List<?>) manifest.get("generatedFiles")).size());
        result.put("manifest", projectRoot.relativize(manifestPath).toString().replace('\\', '/'));
        System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    private static void migrate(String url, String username, String password, Path migrationRoot) {
        Flyway.configure()
                .dataSource(url, username, password)
                .locations("filesystem:" + migrationRoot.toString().replace('\\', '/'))
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static void generateModule(
            String basePackage,
            ModuleConfig module,
            Path sourceRoot,
            Path mapperXmlRoot,
            String url,
            String username,
            String password
    ) {
        Map<String, TableConfig> tables = new LinkedHashMap<>();
        for (TableConfig table : module.tables()) {
            tables.put(table.name(), table);
        }
        INameConvert nameConvert = new ConfiguredNameConvert(tables);
        Set<String> immutableTables = new HashSet<>();
        for (TableConfig table : module.tables()) {
            if (table.immutable()) {
                immutableTables.add(table.name());
            }
        }

        String parentPackage = basePackage + "." + module.javaPackage() + ".base";
        Path moduleXmlRoot = mapperXmlRoot.resolve(module.name()).resolve("base").normalize();
        FastAutoGenerator.create(url, username, password)
                .dataSourceConfig(builder -> builder.typeConvertHandler((global, registry, metaInfo) -> {
                    if ("tinyint".equalsIgnoreCase(metaInfo.getTypeName())) {
                        return DbColumnType.BOOLEAN;
                    }
                    return registry.getColumnType(metaInfo);
                }))
                .globalConfig(builder -> builder
                        .author("Template Base")
                        .disableOpenDir()
                        .outputDir(sourceRoot.toString())
                        .dateType(DateType.TIME_PACK)
                        .commentDate(() -> "generated"))
                .packageConfig(builder -> builder
                        .parent(parentPackage)
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .pathInfo(Map.of(OutputFile.xml, moduleXmlRoot.toString())))
                .strategyConfig(builder -> {
                    builder.disableSqlFilter().addInclude(new ArrayList<>(tables.keySet()));
                    builder.entityBuilder()
                            .nameConvert(nameConvert)
                            .naming(NamingStrategy.underline_to_camel)
                            .columnNaming(NamingStrategy.underline_to_camel)
                            .idType(IdType.AUTO)
                            .disableSerialVersionUID()
                            .enableTableFieldAnnotation()
                            .versionColumnName("version")
                            .enableFileOverride();
                    builder.mapperBuilder()
                            .enableMapperAnnotation()
                            .enableBaseResultMap()
                            .enableBaseColumnList()
                            .enableFileOverride();
                    builder.serviceBuilder()
                            .formatServiceFileName("%sBaseService")
                            .serviceTemplate("/templates/base-service.java")
                            .disableServiceImpl()
                            .enableFileOverride();
                    builder.controllerBuilder().disable();
                })
                .injectionConfig(builder -> builder.beforeOutputFile((table, values) ->
                        values.put("immutable", immutableTables.contains(table.getName()))))
                .templateConfig(builder -> builder.disable(TemplateType.CONTROLLER))
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }

    private static void validateConfig(GeneratorConfig config, Path sourceRoot, Path mapperXmlRoot, Path migrationRoot) {
        if (config.basePackage() == null || !config.basePackage().matches("[a-z][a-z0-9_.]+")) {
            throw new IllegalArgumentException("basePackage is invalid");
        }
        if (config.database() == null || config.modules() == null || config.modules().isEmpty()) {
            throw new IllegalArgumentException("database and at least one module are required");
        }
        if (!Files.isDirectory(sourceRoot) || !Files.isDirectory(migrationRoot)) {
            throw new IllegalArgumentException("sourceRoot and migrationRoot must exist");
        }
        if (!mapperXmlRoot.startsWith(sourceRoot.getParent().getParent())) {
            throw new IllegalArgumentException("mapperXmlRoot must stay inside the backend project");
        }

        Set<String> moduleNames = new HashSet<>();
        Set<String> tableNames = new HashSet<>();
        for (ModuleConfig module : config.modules()) {
            if (!moduleNames.add(module.name())) {
                throw new IllegalArgumentException("duplicate module: " + module.name());
            }
            if (module.tables() == null || module.tables().isEmpty()) {
                throw new IllegalArgumentException("module has no tables: " + module.name());
            }
            for (TableConfig table : module.tables()) {
                if (!tableNames.add(table.name())) {
                    throw new IllegalArgumentException("table is configured more than once: " + table.name());
                }
                if (table.entity() == null || !table.entity().matches("[A-Z][A-Za-z0-9]+")) {
                    throw new IllegalArgumentException("invalid entity for table " + table.name());
                }
            }
        }
    }

    private static void validateDatabaseTables(
            String url,
            String username,
            String password,
            List<ModuleConfig> modules
    ) throws Exception {
        Set<String> expected = new HashSet<>();
        for (ModuleConfig module : modules) {
            for (TableConfig table : module.tables()) {
                expected.add(table.name());
            }
        }
        Set<String> actual = new HashSet<>();
        try (var connection = DriverManager.getConnection(url, username, password);
             var result = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                actual.add(result.getString("TABLE_NAME"));
            }
        }
        actual.remove("flyway_schema_history");
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("configured tables are missing in MySQL: " + missing);
        }
        Set<String> unassigned = new HashSet<>(actual);
        unassigned.removeAll(expected);
        if (!unassigned.isEmpty()) {
            throw new IllegalStateException("MySQL tables are not assigned to a generated module: " + unassigned);
        }
    }

    private static Map<String, Object> writeManifest(
            Path projectRoot,
            Path manifestPath,
            GeneratorConfig config,
            Path sourceRoot,
            Path mapperXmlRoot,
            String url,
            String username,
            String password
    ) throws Exception {
        List<Map<String, Object>> schema = new ArrayList<>();
        try (var connection = DriverManager.getConnection(url, username, password)) {
            for (ModuleConfig module : config.modules()) {
                for (TableConfig table : module.tables()) {
                    try (var columns = connection.getMetaData().getColumns(
                            connection.getCatalog(), null, table.name(), "%"
                    )) {
                        while (columns.next()) {
                            Map<String, Object> column = new LinkedHashMap<>();
                            column.put("module", module.name());
                            column.put("table", table.name());
                            column.put("ordinal", columns.getInt("ORDINAL_POSITION"));
                            column.put("name", columns.getString("COLUMN_NAME"));
                            column.put("type", columns.getString("TYPE_NAME"));
                            column.put("size", columns.getInt("COLUMN_SIZE"));
                            column.put("nullable", columns.getInt("NULLABLE"));
                            column.put("default", columns.getString("COLUMN_DEF"));
                            column.put("autoIncrement", columns.getString("IS_AUTOINCREMENT"));
                            schema.add(column);
                        }
                    }
                }
            }
        }
        schema.sort(Comparator
                .comparing((Map<String, Object> value) -> (String) value.get("module"))
                .thenComparing(value -> (String) value.get("table"))
                .thenComparingInt(value -> (Integer) value.get("ordinal")));

        List<Map<String, String>> generatedFiles = new ArrayList<>();
        Path packageRoot = sourceRoot.resolve(config.basePackage().replace('.', '/')).normalize();
        for (ModuleConfig module : config.modules()) {
            addGeneratedFiles(
                    projectRoot,
                    packageRoot.resolve(module.javaPackage().replace('.', '/')).resolve("base"),
                    generatedFiles
            );
            addGeneratedFiles(projectRoot, mapperXmlRoot.resolve(module.name()).resolve("base"), generatedFiles);
        }
        generatedFiles.sort(Comparator.comparing(value -> value.get("path")));
        int expectedFileCount = config.modules().stream().mapToInt(value -> value.tables().size()).sum() * 4;
        if (generatedFiles.size() != expectedFileCount) {
            throw new IllegalStateException(
                    "generated file count mismatch: expected " + expectedFileCount + ", got " + generatedFiles.size()
            );
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("generator", "MyBatis-Plus Generator 3.5.17");
        manifest.put("database", sanitizedDatabase(url));
        manifest.put("schemaSha256", sha256(JSON.writeValueAsBytes(schema)));
        manifest.put("moduleCount", config.modules().size());
        manifest.put("tableCount", config.modules().stream().mapToInt(value -> value.tables().size()).sum());
        manifest.put("generatedFiles", generatedFiles);
        Files.createDirectories(manifestPath.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        return manifest;
    }

    private static void addGeneratedFiles(
            Path projectRoot,
            Path generatedRoot,
            List<Map<String, String>> generatedFiles
    ) throws Exception {
        if (!Files.isDirectory(generatedRoot)) {
            throw new IllegalStateException("generated directory is missing: " + generatedRoot);
        }
        try (var paths = Files.walk(generatedRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("path", projectRoot.relativize(file).toString().replace('\\', '/'));
                entry.put("sha256", sha256(Files.readAllBytes(file)));
                generatedFiles.add(entry);
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static void cleanGeneratedBase(GeneratorConfig config, Path sourceRoot, Path mapperXmlRoot) throws IOException {
        Path packageRoot = sourceRoot.resolve(config.basePackage().replace('.', '/')).normalize();
        for (ModuleConfig module : config.modules()) {
            Path javaBase = packageRoot.resolve(module.javaPackage().replace('.', '/')).resolve("base").normalize();
            Path xmlBase = mapperXmlRoot.resolve(module.name()).resolve("base").normalize();
            deleteGeneratedDirectory(sourceRoot, javaBase);
            deleteGeneratedDirectory(mapperXmlRoot, xmlBase);
        }
    }

    private static void deleteGeneratedDirectory(Path allowedRoot, Path target) throws IOException {
        if (!target.startsWith(allowedRoot) || target.equals(allowedRoot) || !target.getFileName().toString().equals("base")) {
            throw new IllegalArgumentException("refusing to clean unsafe generated path: " + target);
        }
        if (!Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static Path resolveProjectPath(Path projectRoot, String configured, String name) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        Path result = projectRoot.resolve(configured).normalize().toAbsolutePath();
        if (!result.startsWith(projectRoot)) {
            throw new IllegalArgumentException(name + " must stay inside the project root");
        }
        return result;
    }

    private static Path resolveProjectRoot(Path configPath, String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("projectRoot is required");
        }
        Path result = configPath.getParent().resolve(configured).normalize().toAbsolutePath();
        if (!configPath.startsWith(result) || result.getParent() == null) {
            throw new IllegalArgumentException("projectRoot must contain the generator configuration");
        }
        return result;
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sanitizedDatabase(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    private static String snakeToCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                upper = true;
            } else if (upper) {
                result.append(Character.toUpperCase(character));
                upper = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static final class ConfiguredNameConvert implements INameConvert {
        private final Map<String, TableConfig> tables;
        private final Map<String, String> properties;

        private ConfiguredNameConvert(Map<String, TableConfig> tables) {
            this.tables = tables;
            this.properties = new HashMap<>();
            for (TableConfig table : tables.values()) {
                for (Map.Entry<String, String> property : table.properties().entrySet()) {
                    String previous = properties.putIfAbsent(property.getKey(), property.getValue());
                    if (previous != null && !previous.equals(property.getValue())) {
                        throw new IllegalArgumentException(
                                "one module cannot map column " + property.getKey() + " to multiple properties"
                        );
                    }
                }
            }
        }

        @Override
        public String entityNameConvert(TableInfo tableInfo) {
            TableConfig table = tables.get(tableInfo.getName());
            if (table == null) {
                throw new IllegalStateException("table is not configured: " + tableInfo.getName());
            }
            return table.entity();
        }

        @Override
        public String propertyNameConvert(TableField tableField) {
            String configured = properties.get(tableField.getName());
            return configured == null ? snakeToCamel(tableField.getName()) : configured;
        }
    }

    public record GeneratorConfig(
            String projectRoot,
            String basePackage,
            String sourceRoot,
            String mapperXmlRoot,
            String migrationRoot,
            String manifestPath,
            DatabaseConfig database,
            List<ModuleConfig> modules
    ) {
    }

    public record DatabaseConfig(String url, String username, String password) {
    }

    public record ModuleConfig(String name, String javaPackage, List<TableConfig> tables) {
    }

    public record TableConfig(
            String name,
            String entity,
            boolean immutable,
            Map<String, String> properties
    ) {
        public TableConfig {
            properties = properties == null ? Map.of() : Map.copyOf(properties);
        }
    }
}
