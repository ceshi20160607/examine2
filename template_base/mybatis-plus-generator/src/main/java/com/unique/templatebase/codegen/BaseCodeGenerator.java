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

/** 从已经建好的业务表生成 base 层基础 CRUD，不推断或修改业务表。 */
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
        if (config.databaseUrl() == null || config.databaseUsername() == null
                || config.targets() == null || config.targets().isEmpty()) {
            throw new IllegalArgumentException("生成配置缺少数据库或业务模块");
        }
        Set<String> tableNames = new HashSet<>();
        for (Target target : config.targets()) {
            if (target.sourceRoot() == null || target.basePackage() == null
                    || !target.basePackage().matches("[a-z][a-z0-9_.]+")
                    || target.tables() == null || target.tables().isEmpty()) {
                throw new IllegalArgumentException("业务模块缺少源码目录、包名或表名");
            }
            Path sourceRoot = projectRoot.resolve(target.sourceRoot()).normalize();
            if (!sourceRoot.startsWith(projectRoot) || !Files.isDirectory(sourceRoot)) {
                throw new IllegalArgumentException("源码目录必须已存在且位于项目内");
            }
            for (String table : target.tables()) {
                if (table == null || !table.matches("un_[a-z0-9_]+") || !tableNames.add(table)) {
                    throw new IllegalArgumentException("表名无效或跨模块重复: " + table);
                }
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
                Set<String> missing = new HashSet<>(tableNames);
                missing.removeAll(actual);
                throw new IllegalStateException("数据库中缺少原名表: " + missing);
            }
        }

        // 一张表只归一个业务模块；生成器不把全产品的持久化代码合并到公共模块。
        for (Target target : config.targets()) {
            Path sourceRoot = projectRoot.resolve(target.sourceRoot()).normalize();
            FastAutoGenerator.create(url, username, password)
                    .globalConfig(builder -> builder
                            .author("Template Base")
                            .disableOpenDir()
                            .outputDir(sourceRoot.toString())
                            .dateType(DateType.TIME_PACK))
                    // base 包承载生成器产出的完整基础 CRUD；manage 包只写业务编排和特殊规则。
                    .packageConfig(builder -> builder
                            .parent(target.basePackage())
                            .entity("entity")
                            .mapper("mapper")
                            .service("service")
                            .serviceImpl("service.impl"))
                    .strategyConfig(builder -> {
                        builder.disableSqlFilter().addInclude(target.tables());
                        builder.entityBuilder()
                                .enableTableFieldAnnotation()
                                .toString(false)
                                .enableFileOverride();
                        builder.mapperBuilder()
                                .enableMapperAnnotation()
                                .disableMapperXml()
                                .enableFileOverride();
                        // IService 与 ServiceImpl 是每张表都需要的基础服务，不在 manage 中重复包装。
                        builder.serviceBuilder()
                                .formatServiceFileName("I%sService")
                                .formatServiceImplFileName("%sServiceImpl")
                                .enableFileOverride();
                        builder.controllerBuilder().disable();
                    })
                    .templateConfig(builder -> builder
                            .service("/templates/service.java")
                            .serviceImpl("/templates/serviceImpl.java"))
                    .templateEngine(new FreemarkerTemplateEngine())
                    .execute();
            System.out.printf("%s：生成 %d 张表的 Entity、Mapper、IService 和 ServiceImpl%n",
                    target.basePackage(), target.tables().size());
        }
    }

    /** 本地环境变量可覆盖项目里的数据库地址和账号。 */
    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 项目本地配置按业务模块声明表归属，数据库密码由环境变量提供。 */
    public record Config(
            String databaseUrl,
            String databaseUsername,
            List<Target> targets
    ) {
    }

    /** 每个目标目录只承载本业务模块拥有的表。 */
    public record Target(
            String sourceRoot,
            String basePackage,
            List<String> tables
    ) {
    }
}
