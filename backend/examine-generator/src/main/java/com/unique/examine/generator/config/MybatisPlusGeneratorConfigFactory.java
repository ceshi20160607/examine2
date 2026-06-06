package com.unique.examine.generator.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.enums.SqlLike;
import com.baomidou.mybatisplus.generator.IFill;
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.GlobalConfig;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.PackageConfig;
import com.baomidou.mybatisplus.generator.config.StrategyConfig;
import com.baomidou.mybatisplus.generator.config.TemplateConfig;
import com.baomidou.mybatisplus.generator.config.TemplateType;
import com.baomidou.mybatisplus.generator.config.po.LikeTable;
import com.baomidou.mybatisplus.generator.config.querys.MySqlQuery;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.fill.Column;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * MyBatis-Plus 代码生成配置工厂。
 *
 * <p>这里集中维护数据源、包名、输出目录、生成策略和模板路径，避免生成规则散落在 CLI 或执行器中。</p>
 */
public final class MybatisPlusGeneratorConfigFactory {

    private MybatisPlusGeneratorConfigFactory() {
    }

    /**
     * 构建数据源配置。
     *
     * @param properties 生成器配置
     * @return 数据源配置构建器
     */
    public static DataSourceConfig.Builder dataSourceConfig(GeneratorProperties properties) {
        GeneratorDataSourceProperties dataSource = properties.dataSource();
        if (!dataSource.isConfigured()) {
            throw new IllegalArgumentException("Generator datasource is not configured.");
        }
        DataSourceConfig.Builder builder = new DataSourceConfig.Builder(
                dataSource.url(),
                dataSource.username(),
                dataSource.password()
        );
        builder.dbQuery(new MySqlQuery());
        return builder;
    }

    /**
     * 构建全局配置，决定作者、日期、Swagger 注解和 Java 输出根目录。
     *
     * @param properties 生成器配置
     * @param mapping 模块映射
     * @return 全局配置回调
     */
    public static Consumer<GlobalConfig.Builder> globalConfig(
            GeneratorProperties properties,
            GeneratorModuleMapping mapping
    ) {
        Path outputDir = mapping.resolveSourceRoot(properties.backendRoot());
        return builder -> builder
                .author(properties.author())
                .commentDate("yyyy-MM-dd")
                .disableOpenDir()
                .dateType(DateType.TIME_PACK)
                .outputDir(outputDir.toString())
                .enableSwagger();
    }

    /**
     * 构建包配置，并把 mapper XML 输出到对应子模块的 `resources/mapper/base`。
     *
     * @param properties 生成器配置
     * @param mapping 模块映射
     * @return 包配置回调
     */
    public static Consumer<PackageConfig.Builder> packageConfig(
            GeneratorProperties properties,
            GeneratorModuleMapping mapping
    ) {
        Map<OutputFile, String> pathInfo = new HashMap<>();
        pathInfo.put(OutputFile.xml, mapping.resolveMapperXmlRoot(properties.backendRoot()).toString());
        return builder -> builder
                .parent(mapping.basePackage())
                .entity("entity")
                .mapper("mapper")
                .service("service")
                .serviceImpl("service.impl")
                .pathInfo(pathInfo);
    }

    /**
     * 构建 base 层生成策略，只生成 entity、mapper、mapper.xml、service、serviceImpl。
     *
     * @param mapping 模块映射
     * @param tableNames 本次需要生成的具体表
     * @return 生成策略回调
     */
    public static Consumer<StrategyConfig.Builder> strategyConfig(
            GeneratorModuleMapping mapping,
            List<String> tableNames
    ) {
        List<IFill> fillList = List.of(
                new Column("create_time", FieldFill.INSERT),
                new Column("create_user_id", FieldFill.INSERT),
                new Column("update_time", FieldFill.UPDATE),
                new Column("update_user_id", FieldFill.UPDATE)
        );
        return builder -> {
            // base 层不生成 Controller，对外接口必须由 manage 层按冻结 API 显式编排。
            builder.addTablePrefix(mapping.tablePrefixes())
                    .enableSkipView()
                    .entityBuilder()
                    .idType(IdType.ASSIGN_ID)
                    .enableLombok()
                    .enableChainModel()
                    .enableFileOverride()
                    .javaTemplate("templates/base/entity.java")
                    .addTableFills(fillList)
                    .build()
                    .mapperBuilder()
                    .enableBaseResultMap()
                    .enableBaseColumnList()
                    .enableFileOverride()
                    .mapperTemplate("templates/base/mapper.java")
                    .mapperXmlTemplate("templates/base/mapper.xml")
                    .build()
                    .serviceBuilder()
                    .formatServiceFileName("I%sService")
                    .formatServiceImplFileName("%sServiceImpl")
                    .enableFileOverride()
                    .serviceTemplate("templates/base/service.java")
                    .serviceImplTemplate("templates/base/serviceImpl.java")
                    .build()
                    .controllerBuilder()
                    .disable()
                    .build();

            if (tableNames.isEmpty()) {
                builder.likeTable(new LikeTable(mapping.tablePrefixes().getFirst(), SqlLike.RIGHT));
            } else {
                builder.addInclude(tableNames);
            }
        };
    }

    /**
     * 构建模板配置，显式禁用 Controller 模板。
     *
     * @return 模板配置回调
     */
    public static Consumer<TemplateConfig.Builder> templateConfig() {
        return builder -> builder
                .disable(TemplateType.CONTROLLER)
                .controller("")
                .entity("templates/base/entity.java")
                .mapper("templates/base/mapper.java")
                .xml("templates/base/mapper.xml")
                .service("templates/base/service.java")
                .serviceImpl("templates/base/serviceImpl.java");
    }
}
