package com.unique.examine.generator.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Mapping from database table prefix to target Maven module and base package.
 *
 * @param tablePrefixes table prefixes owned by the target module
 * @param moduleName Maven module name
 * @param basePackage Java base package for generated base code
 * @param sourceRoot relative Java source root under backend project root
 * @param mapperXmlRoot relative mapper XML root under backend project root
 */
public record GeneratorModuleMapping(
        List<String> tablePrefixes,
        String moduleName,
        String basePackage,
        String sourceRoot,
        String mapperXmlRoot
) {

    /**
     * Creates a mapping and normalizes immutable prefix values.
     *
     * @param tablePrefixes table prefixes owned by the target module
     * @param moduleName Maven module name
     * @param basePackage Java base package for generated base code
     * @param sourceRoot relative Java source root under backend project root
     * @param mapperXmlRoot relative mapper XML root under backend project root
     * @return module mapping
     */
    public static GeneratorModuleMapping of(
            List<String> tablePrefixes,
            String moduleName,
            String basePackage,
            String sourceRoot,
            String mapperXmlRoot
    ) {
        return new GeneratorModuleMapping(
                List.copyOf(tablePrefixes),
                Objects.requireNonNull(moduleName, "moduleName"),
                Objects.requireNonNull(basePackage, "basePackage"),
                Objects.requireNonNull(sourceRoot, "sourceRoot"),
                Objects.requireNonNull(mapperXmlRoot, "mapperXmlRoot")
        );
    }

    /**
     * Returns whether this mapping owns the table.
     *
     * @param tableName database table name
     * @return true when the table starts with one of the mapping prefixes
     */
    public boolean matches(String tableName) {
        return tablePrefixes.stream().anyMatch(tableName::startsWith);
    }

    /**
     * Resolves the output root for this mapping.
     *
     * @param backendRoot backend project root
     * @return generated Java output root
     */
    public Path resolveSourceRoot(Path backendRoot) {
        return backendRoot.resolve(sourceRoot).normalize();
    }

    /**
     * Resolves the mapper XML output root for this mapping.
     *
     * @param backendRoot backend project root
     * @return generated mapper XML output root
     */
    public Path resolveMapperXmlRoot(Path backendRoot) {
        return backendRoot.resolve(mapperXmlRoot).normalize();
    }
}
