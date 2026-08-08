package com.unique.examine.generator.cli;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GeneratorCli {
    private GeneratorCli() {
    }

    public static void main(String[] args) throws Exception {
        var options = Options.parse(args);
        var url = requiredEnvironment("EXAMINE_DB_URL");
        var username = requiredEnvironment("EXAMINE_DB_USERNAME");
        var password = requiredEnvironment("EXAMINE_DB_PASSWORD");
        var backendRoot = options.backendRoot().toAbsolutePath().normalize();
        var moduleRoot = backendRoot.resolve(options.moduleName()).normalize();
        requireChild(backendRoot, moduleRoot, "module root");
        if (!Files.isDirectory(moduleRoot)) {
            throw new IllegalArgumentException("Module directory does not exist: " + moduleRoot);
        }

        var sourceRoot = moduleRoot.resolve("src/main/java");
        var mapperRoot = moduleRoot.resolve("src/main/resources/mapper/base");
        var reportPath = backendRoot.resolve("generator-reports")
                .resolve(options.moduleName() + "-" + sanitize(options.tablePrefix()) + ".json");

        try (var connection = DriverManager.getConnection(url, username, password)) {
            var metadata = connection.getMetaData();
            var schema = connection.getCatalog();
            var tables = matchingTables(metadata, schema, options.tablePrefix());
            if (tables.isEmpty()) {
                throw new IllegalStateException("No tables found for prefix: " + options.tablePrefix());
            }
            var generatedColumns = generatedColumnsByTable(connection, schema, tables);

            if (options.execute()) {
                var existingSinceLines = existingSinceLines(sourceRoot, mapperRoot, options, tables);
                Files.createDirectories(sourceRoot);
                Files.createDirectories(mapperRoot);
                deleteLegacyDuplicateFiles(sourceRoot, mapperRoot, options, tables);
                for (var batch : generationBatches(tables, generatedColumns)) {
                    generate(url, username, password, sourceRoot, mapperRoot, options,
                            batch.tables(), batch.generatedColumns());
                }
                normalizeGeneratedFiles(sourceRoot, mapperRoot, options, tables, existingSinceLines);
            }

            writeReport(reportPath, metadata, schema, backendRoot, sourceRoot, mapperRoot,
                    options, tables, generatedColumns);
            System.out.printf(
                    Locale.ROOT,
                    "Generator %s: module=%s prefix=%s tables=%d report=%s%n",
                    options.execute() ? "executed" : "dry-run",
                    options.moduleName(),
                    options.tablePrefix(),
                    tables.size(),
                    reportPath
            );
        } finally {
            AbandonedConnectionCleanupThread.checkedShutdown();
        }
    }

    private static void generate(
            String url,
            String username,
            String password,
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables,
            List<String> generatedColumns
    ) {
        var pathInfo = new EnumMap<OutputFile, String>(OutputFile.class);
        pathInfo.put(OutputFile.xml, mapperRoot.toString());

        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> builder
                        .author("examine-generator")
                        .disableOpenDir()
                        .commentDate("yyyy-MM-dd")
                        .outputDir(sourceRoot.toString()))
                .packageConfig(builder -> builder
                        .parent(options.basePackage())
                        .entity("base.entity")
                        .mapper("base.mapper")
                        .service("base.service")
                        .serviceImpl("base.service.impl")
                        .pathInfo(pathInfo))
                .strategyConfig(builder -> builder
                        .addInclude(tables)
                        .addTablePrefix(options.tablePrefix())
                        .entityBuilder()
                        .enableFileOverride()
                        .enableTableFieldAnnotation()
                        .toString(false)
                        .addIgnoreColumns(generatedColumns)
                        .logicDeleteColumnName("deleted_at")
                        .versionColumnName("version")
                        .mapperBuilder()
                        .enableFileOverride()
                        .formatMapperFileName(serviceImplementationPrefix(options.moduleName()) + "%sMapper")
                        .formatXmlFileName(serviceImplementationPrefix(options.moduleName()) + "%sMapper")
                        .mapperAnnotation(org.apache.ibatis.annotations.Mapper.class)
                        .enableBaseResultMap()
                        .enableBaseColumnList()
                        .serviceBuilder()
                        .enableFileOverride()
                        .formatServiceImplFileName(serviceImplementationPrefix(options.moduleName()) + "%sServiceImpl")
                        .controllerBuilder()
                        .disable())
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }

    private static List<String> matchingTables(DatabaseMetaData metadata, String schema, String prefix)
            throws SQLException {
        var tables = new ArrayList<String>();
        try (ResultSet resultSet = metadata.getTables(schema, null, prefix + "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        tables.sort(String::compareTo);
        return List.copyOf(tables);
    }

    private static Map<String, List<String>> generatedColumnsByTable(
            Connection connection,
            String schema,
            List<String> tables
    ) throws SQLException {
        var result = new LinkedHashMap<String, List<String>>();
        try (var statement = connection.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=? AND TABLE_NAME=? "
                        + "AND GENERATION_EXPRESSION IS NOT NULL AND GENERATION_EXPRESSION<>'' "
                        + "ORDER BY ORDINAL_POSITION")) {
            for (var table : tables) {
                statement.setString(1, schema);
                statement.setString(2, table);
                var columns = new ArrayList<String>();
                try (var resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME"));
                    }
                }
                columns.sort(String::compareTo);
                result.put(table, List.copyOf(columns));
            }
        }
        return result;
    }

    private static List<GenerationBatch> generationBatches(
            List<String> tables,
            Map<String, List<String>> generatedColumns
    ) {
        var grouped = new LinkedHashMap<List<String>, List<String>>();
        for (var table : tables) {
            var columns = generatedColumns.getOrDefault(table, List.of());
            grouped.computeIfAbsent(columns, ignored -> new ArrayList<>()).add(table);
        }
        return grouped.entrySet().stream()
                .map(entry -> new GenerationBatch(List.copyOf(entry.getValue()), entry.getKey()))
                .toList();
    }

    private static void writeReport(
            Path reportPath,
            DatabaseMetaData metadata,
            String schema,
            Path backendRoot,
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables,
            Map<String, List<String>> generatedColumns
    ) throws SQLException, IOException {
        Files.createDirectories(reportPath.getParent());
        var content = new LinkedHashMap<String, Object>();
        content.put("execute", options.execute());
        content.put("databaseProduct", metadata.getDatabaseProductName());
        content.put("databaseVersion", metadata.getDatabaseProductVersion());
        content.put("schema", schema);
        content.put("moduleName", options.moduleName());
        content.put("tablePrefix", options.tablePrefix());
        content.put("basePackage", options.basePackage());
        content.put("sourceRoot", relativePath(backendRoot, sourceRoot));
        content.put("mapperXmlRoot", relativePath(backendRoot, mapperRoot));
        content.put("tables", tables);
        content.put("databaseGeneratedColumns", generatedColumns);
        content.put(
                "generatedFiles",
                options.execute()
                        ? generatedFiles(backendRoot, sourceRoot, mapperRoot,
                                options, tables)
                        : List.of()
        );

        var objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var report = new LinkedHashMap<String, Object>();
        report.put("generatedAt", stableGeneratedAt(reportPath, objectMapper, content));
        report.putAll(content);
        objectMapper.writeValue(reportPath.toFile(), report);
    }

    private static String stableGeneratedAt(
            Path reportPath,
            ObjectMapper objectMapper,
            Map<String, Object> content
    ) {
        if (Files.isRegularFile(reportPath)) {
            try {
                var existing = objectMapper.readTree(reportPath.toFile());
                if (existing instanceof ObjectNode existingObject) {
                    var generatedAt = existingObject.path("generatedAt").asText("");
                    existingObject.remove("generatedAt");
                    if (!generatedAt.isBlank() && existingObject.equals(objectMapper.valueToTree(content))) {
                        return generatedAt;
                    }
                }
            } catch (IOException ignored) {
                // A malformed previous report is replaced by the current successful generation.
            }
        }
        return OffsetDateTime.now().toString();
    }

    private static List<String> generatedFiles(
            Path backendRoot,
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables
    ) throws IOException {
        return expectedGeneratedFiles(sourceRoot, mapperRoot, options, tables).stream()
                .map(path -> relativePath(backendRoot, path))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static void normalizeGeneratedFiles(
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables,
            Map<Path, String> existingSinceLines
    ) throws IOException {
        for (var path : expectedGeneratedFiles(sourceRoot, mapperRoot, options, tables)) {
            var preservedSinceLine = existingSinceLines.get(path);
            var lines = Files.readString(path).lines()
                    .map(line -> preservedSinceLine != null && line.contains("* @since ")
                            ? preservedSinceLine : line)
                    .map(String::stripTrailing)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            Files.writeString(path, String.join("\n", lines) + "\n");
        }
    }

    private static Map<Path, String> existingSinceLines(
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables
    ) throws IOException {
        var result = new LinkedHashMap<Path, String>();
        for (var path : expectedGeneratedPaths(sourceRoot, mapperRoot, options, tables)) {
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
                continue;
            }
            try (var lines = Files.lines(path)) {
                lines.filter(line -> line.contains("* @since "))
                        .findFirst()
                        .ifPresent(line -> result.put(path, line.stripTrailing()));
            }
        }
        return Map.copyOf(result);
    }

    private static List<Path> expectedGeneratedFiles(
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables
    ) throws IOException {
        var files = expectedGeneratedPaths(sourceRoot, mapperRoot, options, tables);
        var missing = files.stream().filter(path -> !Files.isRegularFile(path)).toList();
        if (!missing.isEmpty()) {
            throw new IOException("Generator did not create expected files: " + missing);
        }
        return files;
    }

    private static List<Path> expectedGeneratedPaths(
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables
    ) {
        var baseRoot = sourceRoot.resolve(options.basePackage().replace('.', '/')).resolve("base");
        var modulePrefix = serviceImplementationPrefix(options.moduleName());
        var files = new ArrayList<Path>();
        for (var table : tables) {
            var typeName = snakeToPascalCase(table.substring(options.tablePrefix().length()));
            files.add(baseRoot.resolve("entity").resolve(typeName + ".java"));
            files.add(baseRoot.resolve("mapper").resolve(modulePrefix + typeName + "Mapper.java"));
            files.add(baseRoot.resolve("service").resolve("I" + typeName + "Service.java"));
            files.add(baseRoot.resolve("service/impl").resolve(modulePrefix + typeName + "ServiceImpl.java"));
            files.add(mapperRoot.resolve(modulePrefix + typeName + "Mapper.xml"));
        }
        return List.copyOf(files);
    }

    private static void deleteLegacyDuplicateFiles(
            Path sourceRoot,
            Path mapperRoot,
            Options options,
            List<String> tables
    ) throws IOException {
        var baseRoot = sourceRoot.resolve(options.basePackage().replace('.', '/')).resolve("base");
        for (var table : tables) {
            var typeName = snakeToPascalCase(table.substring(options.tablePrefix().length()));
            Files.deleteIfExists(baseRoot.resolve("mapper").resolve(typeName + "Mapper.java"));
            Files.deleteIfExists(baseRoot.resolve("service/impl").resolve(typeName + "ServiceImpl.java"));
            Files.deleteIfExists(mapperRoot.resolve(typeName + "Mapper.xml"));
        }
    }

    private static String relativePath(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String serviceImplementationPrefix(String moduleName) {
        var value = moduleName.startsWith("examine-")
                ? moduleName.substring("examine-".length())
                : moduleName;
        var result = new StringBuilder();
        for (var part : value.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Module name cannot produce a service bean prefix: " + moduleName);
        }
        return result.toString();
    }

    private static String snakeToPascalCase(String value) {
        var result = new StringBuilder();
        for (var part : value.split("_")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return result.toString();
    }

    private static String requiredEnvironment(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required environment variable is missing: " + name);
        }
        return value;
    }

    private static void requireChild(Path parent, Path child, String label) {
        if (!child.startsWith(parent)) {
            throw new IllegalArgumentException(label + " escapes backend root: " + child);
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private record GenerationBatch(List<String> tables, List<String> generatedColumns) {
    }

    private record Options(
            Path backendRoot,
            String moduleName,
            String tablePrefix,
            String basePackage,
            boolean execute
    ) {
        private static Options parse(String[] args) {
            var values = new LinkedHashMap<String, String>();
            var execute = false;
            for (var argument : args) {
                if ("--execute".equals(argument)) {
                    execute = true;
                    continue;
                }
                if (!argument.startsWith("--") || !argument.contains("=")) {
                    throw new IllegalArgumentException("Unsupported argument: " + argument);
                }
                var separator = argument.indexOf('=');
                values.put(argument.substring(2, separator), argument.substring(separator + 1));
            }
            return new Options(
                    Path.of(required(values, "backend-root")),
                    required(values, "module-name"),
                    required(values, "table-prefix"),
                    required(values, "base-package"),
                    execute
            );
        }

        private static String required(Map<String, String> values, String key) {
            var value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Required argument is missing: --" + key + "=<value>");
            }
            return value;
        }
    }
}
