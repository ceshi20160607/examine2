package com.unique.templatebase.codegen;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 从已经建好的业务表生成 Entity 和 Mapper，不推断或修改业务表。 */
public final class BaseCodeGenerator {
    private BaseCodeGenerator() {
    }

    /** 校验项目配置和实际表名后，只生成贴表代码。 */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("用法: BaseCodeGenerator <项目内的 base-codegen.json>");
        }

        Path configFile = Path.of(args[0]).toAbsolutePath().normalize();
        Path projectRoot = configFile.getParent();
        Config config = new ObjectMapper().readValue(configFile.toFile(), Config.class);
        if (config.sourceRoot() == null || config.basePackage() == null
                || !config.basePackage().matches("[a-z][a-z0-9_.]+")
                || config.databaseUrl() == null || config.databaseUsername() == null
                || config.tables() == null || config.tables().isEmpty()) {
            throw new IllegalArgumentException("生成配置缺少源码目录、包名、数据库或表名");
        }
        Path sourceRoot = projectRoot.resolve(config.sourceRoot()).normalize();
        if (!sourceRoot.startsWith(projectRoot) || !Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("源码目录必须已存在且位于项目内");
        }
        Set<String> tableNames = new HashSet<>();
        for (String table : config.tables()) {
            if (table == null || !table.matches("un_[a-z0-9_]+") || !tableNames.add(table)) {
                throw new IllegalArgumentException("表名无效或重复: " + table);
            }
        }
        String url = envOrDefault("APP_DB_URL", config.databaseUrl());
        String username = envOrDefault("APP_DB_USERNAME", config.databaseUsername());
        String password = System.getenv("APP_DB_PASSWORD");
        if (password == null) {
            throw new IllegalArgumentException("请设置 APP_DB_PASSWORD 环境变量");
        }
        // 只允许从数据库中已经存在的原名业务表生成代码。
        try (var connection = DriverManager.getConnection(url, username, password)) {
            Set<String> actual = new HashSet<>();
            try (var tables = connection.getMetaData().getTables(
                    connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    actual.add(tables.getString("TABLE_NAME"));
                }
            }
            if (!actual.containsAll(tableNames)) {
                tableNames.removeAll(actual);
                throw new IllegalStateException("数据库中缺少原名表: " + tableNames);
            }
        }

        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> builder
                        .author("Template Base")
                        .disableOpenDir()
                        .outputDir(sourceRoot.toString())
                        .dateType(DateType.TIME_PACK))
                .packageConfig(builder -> builder
                        .parent(config.basePackage())
                        .entity("entity")
                        .mapper("mapper"))
                .strategyConfig(builder -> {
                    builder.disableSqlFilter().addInclude(config.tables());
                    builder.entityBuilder()
                            .enableTableFieldAnnotation()
                            .toString(false)
                            .enableFileOverride();
                    builder.mapperBuilder()
                            .enableMapperAnnotation()
                            .disableMapperXml()
                            .enableFileOverride();
                    builder.serviceBuilder().disable();
                    builder.controllerBuilder().disable();
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
        System.out.printf("已从 %d 张原名表生成 Entity 和 Mapper%n", config.tables().size());
    }

    /** 本地环境变量可覆盖项目里的数据库地址和账号。 */
    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 项目本地配置仅列出已落地的原名业务表，数据库密码由环境变量提供。 */
    public record Config(
            String sourceRoot,
            String basePackage,
            String databaseUrl,
            String databaseUsername,
            List<String> tables
    ) {
    }
}
