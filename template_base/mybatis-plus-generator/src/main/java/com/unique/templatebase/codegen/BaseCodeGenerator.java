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
import java.util.Arrays;

/** 从已建业务表生成各模块的基础 CRUD，业务逻辑只能写在 manage。 */
public final class BaseCodeGenerator {
    private BaseCodeGenerator() {
    }

    /** 先验证表归属及实际数据库，再调用模板生成 Entity、Mapper 和五方法 Service。 */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("用法: BaseCodeGenerator <back/base-codegen.json> [本批表名,以逗号分隔]");
        }
        Path configFile = Path.of(args[0]).toAbsolutePath().normalize();
        Path projectRoot = configFile.getParent();
        Config config = new ObjectMapper().readValue(configFile.toFile(), Config.class);
        if (config.databaseUrl() == null || config.databaseUsername() == null
                || config.targets() == null || config.targets().isEmpty()) {
            throw new IllegalArgumentException("生成配置缺少数据库或业务模块");
        }
        Set<String> tableNames = new HashSet<>();
        for (Target target : config.targets()) {
            if (target.sourceRoot() == null || target.basePackage() == null
                    || !target.basePackage().matches("com\\.unique\\.unexamine\\.[a-z]+\\.base")
                    || target.tables() == null || target.tables().isEmpty()) {
                throw new IllegalArgumentException("业务模块缺少源码目录、base 包名或表名");
            }
            Path sourceRoot = projectRoot.resolve(target.sourceRoot()).normalize();
            if (!sourceRoot.startsWith(projectRoot) || !Files.isDirectory(sourceRoot)) {
                throw new IllegalArgumentException("源码目录必须已存在且位于 back 内: " + sourceRoot);
            }
            for (String table : target.tables()) {
                if (table == null || !table.matches("un_[a-z0-9_]+") || !tableNames.add(table)) {
                    throw new IllegalArgumentException("表名无效或跨模块重复: " + table);
                }
            }
        }
        Set<String> selected = args.length == 1 ? tableNames
                : new HashSet<>(Arrays.asList(args[1].split(",", -1)));
        if (selected.isEmpty() || selected.stream().anyMatch(name -> !tableNames.contains(name))) {
            throw new IllegalArgumentException("本批表名必须全部存在于表归属配置中");
        }
        String password = System.getenv("APP_DB_PASSWORD");
        if (password == null) {
            throw new IllegalArgumentException("请设置 APP_DB_PASSWORD 环境变量");
        }
        String url = envOrDefault("APP_DB_URL", config.databaseUrl());
        String username = envOrDefault("APP_DB_USERNAME", config.databaseUsername());
        // 不从旧源码猜表结构，生成目标必须在当前独立数据库中真实存在。
        try (var connection = DriverManager.getConnection(url, username, password)) {
            Set<String> actual = new HashSet<>();
            try (var tables = connection.getMetaData().getTables(
                    connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    actual.add(tables.getString("TABLE_NAME"));
                }
            }
            if (!actual.containsAll(selected)) {
                Set<String> missing = new HashSet<>(selected);
                missing.removeAll(actual);
                throw new IllegalStateException("数据库缺少本批业务表: " + missing);
            }
        }
        for (Target target : config.targets()) {
            List<String> batchTables = target.tables().stream().filter(selected::contains).toList();
            if (batchTables.isEmpty()) {
                continue;
            }
            Path sourceRoot = projectRoot.resolve(target.sourceRoot()).normalize();
            FastAutoGenerator.create(url, username, password)
                    .globalConfig(builder -> builder.author("Template Base")
                            .disableOpenDir().outputDir(sourceRoot.toString()).dateType(DateType.TIME_PACK))
                    .packageConfig(builder -> builder.parent(target.basePackage())
                            .entity("entity").mapper("mapper")
                            .service("service").serviceImpl("service.impl"))
                    .strategyConfig(builder -> {
                        builder.disableSqlFilter().addInclude(batchTables);
                        builder.entityBuilder().enableTableFieldAnnotation().toString(false).enableFileOverride();
                        builder.mapperBuilder().enableMapperAnnotation().disableMapperXml().enableFileOverride();
                        builder.serviceBuilder().formatServiceFileName("I%sService")
                                .formatServiceImplFileName("%sServiceImpl").enableFileOverride();
                        // 通用无权限 HTTP 路由会绕过租户授权；具体 Controller 在 manage 中保持薄层。
                        builder.controllerBuilder().disable();
                    })
                    .templateConfig(builder -> builder.service("/templates/service.java")
                            .serviceImpl("/templates/serviceImpl.java"))
                    .templateEngine(new FreemarkerTemplateEngine())
                    .execute();
            System.out.printf("%s：已生成 %d 张表的基础 CRUD%n",
                    target.basePackage(), batchTables.size());
        }
    }

    /** 本机环境变量可覆盖项目配置中的地址和用户名。 */
    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 密码不进入配置文件。 */
    public record Config(String databaseUrl, String databaseUsername, List<Target> targets) {
    }

    /** 一张表只归属于一个业务模块。 */
    public record Target(String sourceRoot, String basePackage, List<String> tables) {
    }
}
