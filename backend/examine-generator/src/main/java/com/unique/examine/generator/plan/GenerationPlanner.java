package com.unique.examine.generator.plan;

import com.unique.examine.generator.config.GeneratorModuleMapping;
import com.unique.examine.generator.config.GeneratorModuleMappings;
import com.unique.examine.generator.config.GeneratorProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates dry-run generation plans without connecting to the database.
 */
public final class GenerationPlanner {

    private GenerationPlanner() {
    }

    /**
     * Builds a plan for explicit tables and prefix selections.
     *
     * @param properties generator properties
     * @param tableNames explicit table names
     * @param prefixes selected table prefixes
     * @return dry-run generation plan
     */
    public static GenerationPlan plan(
            GeneratorProperties properties,
            List<String> tableNames,
            List<String> prefixes
    ) {
        Set<String> selectedTables = new LinkedHashSet<>(tableNames);
        for (String prefix : prefixes) {
            selectedTables.add(prefix + "*");
        }

        List<GenerationPlan.TableGenerationPlan> tablePlans = new ArrayList<>();
        List<String> skippedTables = new ArrayList<>();
        for (String tableName : selectedTables) {
            String lookupName = tableName.endsWith("*") ? tableName.substring(0, tableName.length() - 1) : tableName;
            GeneratorModuleMappings.findByTableName(properties.moduleMappings(), lookupName)
                    .ifPresentOrElse(
                            mapping -> tablePlans.add(new GenerationPlan.TableGenerationPlan(
                                    tableName,
                                    mapping,
                                    mapping.resolveSourceRoot(properties.backendRoot())
                            )),
                            () -> skippedTables.add(tableName)
                    );
        }

        return new GenerationPlan(List.copyOf(tablePlans), List.copyOf(skippedTables));
    }

    /**
     * Returns the configured module mappings as a plan-like view.
     *
     * @param properties generator properties
     * @return all configured mappings
     */
    public static List<GeneratorModuleMapping> configuredMappings(GeneratorProperties properties) {
        return properties.moduleMappings();
    }
}
