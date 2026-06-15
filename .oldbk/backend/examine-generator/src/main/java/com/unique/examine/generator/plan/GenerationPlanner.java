package com.unique.examine.generator.plan;

import com.unique.examine.generator.config.GeneratorModuleMapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 创建生成计划。
 */
public final class GenerationPlanner {

    private GenerationPlanner() {
    }

    /**
     * 根据显式表名或表前缀构建生成计划。
     *
     * <p>如果没有传入表名和前缀，默认使用所有已配置模块前缀，直接从数据库按前缀生成。</p>
     *
     * @param mappings 本次命令提供的模块映射
     * @param tableNames 显式表名
     * @param prefixes 指定表前缀
     * @return 生成计划
     */
    public static GenerationPlan plan(
            List<GeneratorModuleMapping> mappings,
            List<String> tableNames,
            List<String> prefixes
    ) {
        Set<String> selectedTables = new LinkedHashSet<>(tableNames);
        for (String prefix : prefixes) {
            selectedTables.add(prefix + "*");
        }
        if (selectedTables.isEmpty()) {
            for (GeneratorModuleMapping mapping : mappings) {
                for (String prefix : mapping.tablePrefixes()) {
                    selectedTables.add(prefix + "*");
                }
            }
        }

        List<GenerationPlan.TableGenerationPlan> tablePlans = new ArrayList<>();
        List<String> skippedTables = new ArrayList<>();
        for (String tableName : selectedTables) {
            String lookupName = tableName.endsWith("*") ? tableName.substring(0, tableName.length() - 1) : tableName;
            findByTableName(mappings, lookupName)
                    .ifPresentOrElse(
                            mapping -> tablePlans.add(new GenerationPlan.TableGenerationPlan(
                                    tableName,
                                    mapping,
                                    java.nio.file.Path.of(mapping.sourceRoot())
                            )),
                            () -> skippedTables.add(tableName)
                    );
        }

        return new GenerationPlan(List.copyOf(tablePlans), List.copyOf(skippedTables));
    }

    private static java.util.Optional<GeneratorModuleMapping> findByTableName(
            List<GeneratorModuleMapping> mappings,
            String tableName
    ) {
        return mappings.stream().filter(mapping -> mapping.matches(tableName)).findFirst();
    }
}
