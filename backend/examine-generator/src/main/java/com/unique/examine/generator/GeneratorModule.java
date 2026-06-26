package com.unique.examine.generator;

import java.nio.file.Path;

/**
 * Base CRUD generator target for one table-prefix ownership slice.
 *
 * @param tablePrefix database table prefix owned by the module
 * @param moduleName Maven module name
 * @param basePackage generated base package
 * @param javaSourceRoot module Java source root
 * @param mapperXmlRoot mapper XML output root
 */
public record GeneratorModule(
        String tablePrefix,
        String moduleName,
        String basePackage,
        Path javaSourceRoot,
        Path mapperXmlRoot
) {
}
