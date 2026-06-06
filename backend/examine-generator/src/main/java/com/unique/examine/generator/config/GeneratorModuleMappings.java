package com.unique.examine.generator.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fixed MVP table-prefix to Maven-module mapping.
 */
public final class GeneratorModuleMappings {

    private GeneratorModuleMappings() {
    }

    /**
     * Returns the default module mappings required by the frozen task plan.
     *
     * @return module mappings
     */
    public static List<GeneratorModuleMapping> defaults() {
        List<GeneratorModuleMapping> mappings = new ArrayList<>();
        mappings.add(GeneratorModuleMapping.of(
                List.of("un_plat_"),
                "examine-plat",
                "com.unique.examine.plat.base",
                "examine-plat/src/main/java",
                "examine-plat/src/main/resources/mapper/base"
        ));
        mappings.add(GeneratorModuleMapping.of(
                List.of("un_module_"),
                "examine-module",
                "com.unique.examine.module.base",
                "examine-module/src/main/java",
                "examine-module/src/main/resources/mapper/base"
        ));
        mappings.add(GeneratorModuleMapping.of(
                List.of("un_flow_"),
                "examine-flow",
                "com.unique.examine.flow.base",
                "examine-flow/src/main/java",
                "examine-flow/src/main/resources/mapper/base"
        ));
        mappings.add(GeneratorModuleMapping.of(
                List.of("un_upload_"),
                "examine-upload",
                "com.unique.examine.upload.base",
                "examine-upload/src/main/java",
                "examine-upload/src/main/resources/mapper/base"
        ));
        mappings.add(GeneratorModuleMapping.of(
                List.of("un_openapi_"),
                "examine-app",
                "com.unique.examine.app.base",
                "examine-app/src/main/java",
                "examine-app/src/main/resources/mapper/base"
        ));
        mappings.add(GeneratorModuleMapping.of(
                List.of("un_sys_", "un_audit_"),
                "examine-core",
                "com.unique.examine.core.base",
                "examine-core/src/main/java",
                "examine-core/src/main/resources/mapper/base"
        ));
        return List.copyOf(mappings);
    }

    /**
     * Finds the module mapping that owns the table.
     *
     * @param mappings available mappings
     * @param tableName database table name
     * @return matching mapping
     */
    public static Optional<GeneratorModuleMapping> findByTableName(
            List<GeneratorModuleMapping> mappings,
            String tableName
    ) {
        return mappings.stream().filter(mapping -> mapping.matches(tableName)).findFirst();
    }
}
