package com.unique.examine.generator.engine;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.unique.examine.generator.config.GeneratorModuleMapping;
import com.unique.examine.generator.config.GeneratorProperties;
import com.unique.examine.generator.config.MybatisPlusGeneratorConfigFactory;
import com.unique.examine.generator.plan.GenerationPlan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按目标业务模块执行 MyBatis-Plus 代码生成。
 */
public final class GenerationExecutor {

    /**
     * 执行生成计划中的所有具体表。
     *
     * @param properties 生成器配置
     * @param plan 生成计划
     * @return 执行摘要
     */
    public ExecutionResult execute(GeneratorProperties properties, GenerationPlan plan) {
        if (!properties.dataSource().isConfigured()) {
            throw new IllegalArgumentException("Generator datasource is not configured.");
        }
        if (!plan.skippedTables().isEmpty()) {
            throw new IllegalArgumentException("Unsupported table prefixes: " + String.join(", ", plan.skippedTables()));
        }

        // MyBatis-Plus 每次生成只能设置一组输出目录，因此先按模块映射分组再逐组执行。
        Map<GeneratorModuleMapping, List<String>> groupedTables = new LinkedHashMap<>();
        for (GenerationPlan.TableGenerationPlan tablePlan : plan.tablePlans()) {
            if (tablePlan.tableName().endsWith("*")) {
                throw new IllegalArgumentException("Prefix wildcard is only supported for dry-run: " + tablePlan.tableName());
            }
            groupedTables.computeIfAbsent(tablePlan.mapping(), key -> new ArrayList<>()).add(tablePlan.tableName());
        }

        List<ExecutedModule> executedModules = new ArrayList<>();
        List<Path> removedControllerDirectories = new ArrayList<>();
        for (Map.Entry<GeneratorModuleMapping, List<String>> entry : groupedTables.entrySet()) {
            GeneratorModuleMapping mapping = entry.getKey();
            List<String> tableNames = List.copyOf(entry.getValue());
            FastAutoGenerator.create(MybatisPlusGeneratorConfigFactory.dataSourceConfig(properties))
                    .globalConfig(MybatisPlusGeneratorConfigFactory.globalConfig(properties, mapping))
                    .packageConfig(MybatisPlusGeneratorConfigFactory.packageConfig(properties, mapping))
                    .strategyConfig(MybatisPlusGeneratorConfigFactory.strategyConfig(mapping, tableNames))
                    .templateConfig(MybatisPlusGeneratorConfigFactory.templateConfig())
                    .templateEngine(new UniqueFreemarkerTemplateEngine())
                    .execute();
            cleanupDefaultControllerOutput(properties, mapping).ifPresent(removedControllerDirectories::add);
            executedModules.add(new ExecutedModule(mapping, tableNames));
        }

        return new ExecutionResult(List.copyOf(executedModules), List.copyOf(removedControllerDirectories));
    }

    private static java.util.Optional<Path> cleanupDefaultControllerOutput(
            GeneratorProperties properties,
            GeneratorModuleMapping mapping
    ) {
        Path controllerDir = mapping.resolveSourceRoot(properties.backendRoot())
                .resolve(mapping.basePackage().replace('.', '/'))
                .resolve("controller")
                .normalize();
        if (!Files.exists(controllerDir)) {
            return java.util.Optional.empty();
        }

        Path backendRoot = properties.backendRoot().toAbsolutePath().normalize();
        Path resolvedControllerDir = controllerDir.toAbsolutePath().normalize();
        if (!resolvedControllerDir.startsWith(backendRoot)) {
            throw new IllegalStateException("Controller cleanup path is outside backend root: " + resolvedControllerDir);
        }

        try (var paths = Files.walk(resolvedControllerDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return java.util.Optional.of(resolvedControllerDir);
    }

    /**
     * 生成执行摘要。
     *
     * @param modules 已生成模块
     * @param removedControllerDirectories 生成后清理掉的默认 Controller 目录
     */
    public record ExecutionResult(List<ExecutedModule> modules, List<Path> removedControllerDirectories) {
    }

    /**
     * 单个模块的生成摘要。
     *
     * @param mapping 目标模块映射
     * @param tableNames 已生成表名
     */
    public record ExecutedModule(GeneratorModuleMapping mapping, List<String> tableNames) {
    }
}
