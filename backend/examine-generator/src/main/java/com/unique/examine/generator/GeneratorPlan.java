package com.unique.examine.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight generation plan used before the real database-backed generator is added.
 */
public final class GeneratorPlan {

    private final List<String> unresolvedTables = new ArrayList<>();
    private final List<GeneratorModule> resolvedModules = new ArrayList<>();

    /**
     * Add one table to the generation plan.
     *
     * @param tableName database table name
     */
    public void addTable(String tableName) {
        GeneratorModuleRegistry.resolve(tableName)
                .ifPresentOrElse(resolvedModules::add, () -> unresolvedTables.add(tableName));
    }

    /**
     * Return module mappings resolved from the input tables.
     *
     * @return resolved module mappings
     */
    public List<GeneratorModule> resolvedModules() {
        return List.copyOf(resolvedModules);
    }

    /**
     * Return tables that do not match any registered prefix.
     *
     * @return unresolved table names
     */
    public List<String> unresolvedTables() {
        return List.copyOf(unresolvedTables);
    }
}
