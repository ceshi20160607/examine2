package com.unique.examine.generator.report;

import com.unique.examine.generator.config.GeneratorModuleMapping;
import com.unique.examine.generator.config.GeneratorProperties;
import com.unique.examine.generator.engine.GenerationExecutor;
import com.unique.examine.generator.plan.GenerationPlan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 写出生成器执行报告。
 */
public final class GenerationReportWriter {

    /**
     * 写出生成报告。
     *
     * @param properties 生成器配置
     * @param configuredMappings 已配置的模块映射
     * @param plan 生成计划
     * @param executionResult 生成执行结果，dry-run 时为空
     * @param command 渲染后的命令
     * @param reportPath 报告输出路径
     * @throws IOException 报告无法写入时抛出
     */
    public void write(
            GeneratorProperties properties,
            List<GeneratorModuleMapping> configuredMappings,
            GenerationPlan plan,
            GenerationExecutor.ExecutionResult executionResult,
            String command,
            Path reportPath
    ) throws IOException {
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, render(properties, configuredMappings, plan, executionResult, command), StandardCharsets.UTF_8);
    }

    private String render(
            GeneratorProperties properties,
            List<GeneratorModuleMapping> configuredMappings,
            GenerationPlan plan,
            GenerationExecutor.ExecutionResult executionResult,
            String command
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# MyBatis-Plus Generation Report\n\n");
        builder.append("- generatedAt: ").append(OffsetDateTime.now()).append('\n');
        builder.append("- mode: ").append(executionResult == null ? "dry-run" : "execute").append('\n');
        builder.append("- backendRoot: ").append(properties.backendRoot()).append('\n');
        builder.append("- datasourceSource: ").append(properties.dataSource().source()).append('\n');
        builder.append("- datasourceConfigured: ").append(properties.dataSource().isConfigured()).append("\n\n");

        builder.append("## Generation Command\n\n");
        builder.append("```text\n").append(command).append("\n```\n\n");

        builder.append("## SQL Execution\n\n");
        builder.append("`sql/init.sql` was imported by DBA-006 before this generation phase. ");
        builder.append("The generator reads table metadata from the configured MySQL database and does not rewrite SQL.\n\n");

        builder.append("## Module Mappings\n\n");
        builder.append("| prefixes | module | basePackage | sourceRoot | mapperXmlRoot |\n");
        builder.append("| --- | --- | --- | --- | --- |\n");
        for (GeneratorModuleMapping mapping : configuredMappings) {
            builder.append("| ")
                    .append(String.join(", ", mapping.tablePrefixes()))
                    .append(" | ")
                    .append(mapping.moduleName())
                    .append(" | ")
                    .append(mapping.basePackage())
                    .append(" | ")
                    .append(mapping.sourceRoot())
                    .append(" | ")
                    .append(mapping.mapperXmlRoot())
                    .append(" |\n");
        }

        builder.append("\n## Planned Tables\n\n");
        if (plan.tablePlans().isEmpty()) {
            builder.append("No explicit table was selected. Use --table or --prefix for a dry-run plan.\n");
        } else {
            builder.append("| table | module | javaOutputRoot | mapperXmlRoot |\n");
            builder.append("| --- | --- | --- | --- |\n");
            for (GenerationPlan.TableGenerationPlan tablePlan : plan.tablePlans()) {
                builder.append("| ")
                        .append(tablePlan.tableName())
                        .append(" | ")
                        .append(tablePlan.mapping().moduleName())
                        .append(" | ")
                        .append(tablePlan.outputRoot())
                        .append(" | ")
                        .append(tablePlan.mapping().mapperXmlRoot())
                        .append(" |\n");
            }
        }

        if (!plan.skippedTables().isEmpty()) {
            builder.append("\n## Skipped Tables\n\n");
            for (String skippedTable : plan.skippedTables()) {
                builder.append("- ").append(skippedTable).append(" (unsupported prefix)\n");
            }
        }

        builder.append("\n## Execution\n\n");
        if (executionResult == null) {
            builder.append("This report is produced without connecting to the database and without generating CRUD files.\n");
        } else {
            builder.append("MyBatis-Plus generation executed by module. Generated artifacts are limited to ");
            builder.append("entity, mapper, mapper.xml, service and serviceImpl under each module base layer.\n\n");
            builder.append("Base CRUD methods generated in every base service: ");
            builder.append("`queryById`、`queryAll`、`queryPage`、`addOrUpdate`、`deleteByIds`. ");
            builder.append("Mapper XML includes `BaseResultMap` and `Base_Column_List`. ");
            builder.append("No base Controller is generated; public APIs must stay in manage controllers.\n\n");
            builder.append("| module | tableCount | tables |\n");
            builder.append("| --- | ---: | --- |\n");
            for (GenerationExecutor.ExecutedModule executedModule : executionResult.modules()) {
                builder.append("| ")
                        .append(executedModule.mapping().moduleName())
                        .append(" | ")
                        .append(executedModule.tableNames().size())
                        .append(" | ")
                        .append(String.join(", ", executedModule.tableNames()))
                        .append(" |\n");
            }
            builder.append("\n### Controller Cleanup\n\n");
            if (executionResult.removedControllerDirectories().isEmpty()) {
                builder.append("No default controller directory was found after generation.\n");
            } else {
                builder.append("MyBatis-Plus default controller output was removed to keep base generation within the frozen contract:\n\n");
                for (var path : executionResult.removedControllerDirectories()) {
                    builder.append("- ").append(path).append('\n');
                }
            }
        }

        builder.append("\n## Old Generator Reference\n\n");
        builder.append("Referenced `.codex/oldgenerator` concepts: `GeneratorOwner`, `DefaultTemplateEngine` and ");
        builder.append("`template_owner` base templates. Removed legacy datasource, interactive-only entry, ");
        builder.append("legacy package naming and Controller generation.\n");
        return builder.toString();
    }
}
