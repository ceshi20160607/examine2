package com.unique.examine.generator;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Central mapping between database table prefixes and Maven module output paths.
 */
public final class GeneratorModuleRegistry {

    public static final String PLATFORM_PREFIX = "un_plat_";
    public static final String MODULE_PREFIX = "un_module_";
    public static final String FLOW_PREFIX = "un_flow_";
    public static final String MESSAGE_PREFIX = "un_message_";
    public static final String AUDIT_PREFIX = "un_audit_";
    public static final String UPLOAD_PREFIX = "un_upload_";
    public static final String OPENAPI_PREFIX = "un_openapi_";
    public static final String AGENT_PREFIX = "un_agent_";
    public static final String WORK_PREFIX = "un_work_";
    public static final String SYSTEM_PREFIX = "un_sys_";
    public static final String OPS_PREFIX = "un_ops_";

    private static final List<GeneratorModule> MODULES = List.of(
            module(PLATFORM_PREFIX, "examine-plat", "com.unique.examine.plat.base"),
            module(MODULE_PREFIX, "examine-module", "com.unique.examine.module.base"),
            module(FLOW_PREFIX, "examine-flow", "com.unique.examine.flow.base"),
            module(MESSAGE_PREFIX, "examine-message-log", "com.unique.examine.messagelog.base"),
            module(AUDIT_PREFIX, "examine-message-log", "com.unique.examine.messagelog.base"),
            module(UPLOAD_PREFIX, "examine-upload", "com.unique.examine.upload.base"),
            module(OPENAPI_PREFIX, "examine-app", "com.unique.examine.app.base"),
            module(AGENT_PREFIX, "examine-ai-work", "com.unique.examine.aiwork.base"),
            module(WORK_PREFIX, "examine-ai-work", "com.unique.examine.aiwork.base"),
            module(SYSTEM_PREFIX, "examine-core", "com.unique.examine.core.base"),
            module(OPS_PREFIX, "examine-core", "com.unique.examine.core.base")
    );

    private GeneratorModuleRegistry() {
    }

    /**
     * Return all configured generator targets.
     *
     * @return immutable module mappings
     */
    public static List<GeneratorModule> all() {
        return MODULES;
    }

    /**
     * Resolve the generator target for a physical table name.
     *
     * @param tableName database table name
     * @return matched generator target
     */
    public static Optional<GeneratorModule> resolve(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return Optional.empty();
        }
        return MODULES.stream()
                .filter(module -> tableName.startsWith(module.tablePrefix()))
                .findFirst();
    }

    private static GeneratorModule module(String prefix, String moduleName, String basePackage) {
        return new GeneratorModule(
                prefix,
                moduleName,
                basePackage,
                Path.of(moduleName, "src", "main", "java"),
                Path.of(moduleName, "src", "main", "resources", "mapper", "base")
        );
    }
}
