package com.unique.examine.generator.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Generator runtime configuration.
 *
 * @param backendRoot backend Maven parent root
 * @param author generated code author
 * @param reportDir report output directory
 * @param dataSource datasource properties
 * @param moduleMappings table-prefix to module mappings
 */
public record GeneratorProperties(
        Path backendRoot,
        String author,
        Path reportDir,
        GeneratorDataSourceProperties dataSource,
        List<GeneratorModuleMapping> moduleMappings
) {

    /**
     * Creates generator properties with immutable mappings.
     *
     * @param backendRoot backend Maven parent root
     * @param author generated code author
     * @param reportDir report output directory
     * @param dataSource datasource properties
     * @param moduleMappings table-prefix to module mappings
     * @return generator properties
     */
    public static GeneratorProperties of(
            Path backendRoot,
            String author,
            Path reportDir,
            GeneratorDataSourceProperties dataSource,
            List<GeneratorModuleMapping> moduleMappings
    ) {
        return new GeneratorProperties(
                Objects.requireNonNull(backendRoot, "backendRoot").normalize(),
                Objects.toString(author, "examine-generator"),
                Objects.requireNonNull(reportDir, "reportDir").normalize(),
                Objects.requireNonNull(dataSource, "dataSource"),
                List.copyOf(moduleMappings)
        );
    }

    /**
     * Resolves the report directory against the backend root when needed.
     *
     * @return absolute or backend-root-relative report directory
     */
    public Path resolvedReportDir() {
        return reportDir.isAbsolute() ? reportDir : backendRoot.resolve(reportDir).normalize();
    }
}
