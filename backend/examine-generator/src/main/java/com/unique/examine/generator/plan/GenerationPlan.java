package com.unique.examine.generator.plan;

import com.unique.examine.generator.config.GeneratorModuleMapping;

import java.nio.file.Path;
import java.util.List;

/**
 * Dry-run generation plan.
 *
 * @param tablePlans table plans
 * @param skippedTables tables with unsupported prefixes
 */
public record GenerationPlan(List<TableGenerationPlan> tablePlans, List<String> skippedTables) {

    /**
     * Per-table generation target.
     *
     * @param tableName database table name
     * @param mapping target module mapping
     * @param outputRoot generated Java output root
     */
    public record TableGenerationPlan(String tableName, GeneratorModuleMapping mapping, Path outputRoot) {
    }
}
