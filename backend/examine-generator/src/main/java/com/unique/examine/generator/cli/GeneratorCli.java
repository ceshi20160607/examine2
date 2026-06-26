package com.unique.examine.generator.cli;

import com.unique.examine.generator.GeneratorModule;
import com.unique.examine.generator.GeneratorModuleRegistry;
import com.unique.examine.generator.output.GeneratedBaseWriter;
import com.unique.examine.generator.sql.SqlInitParser;
import com.unique.examine.generator.sql.SqlTable;
import java.nio.file.Path;
import java.util.List;

/**
 * Minimal CLI entry that prints generator module conventions.
 */
public final class GeneratorCli {

    private GeneratorCli() {
    }

    /**
     * Print table-prefix to module mappings for build evidence.
     *
     * @param args use `--execute --sql sql/init.sql --backend-root backend` to generate base files
     */
    public static void main(String[] args) throws Exception {
        if (hasArg(args, "--execute")) {
            generate(args);
            return;
        }
        for (GeneratorModule module : GeneratorModuleRegistry.all()) {
            System.out.printf(
                    "%s -> %s -> %s -> %s -> %s%n",
                    module.tablePrefix(),
                    module.moduleName(),
                    module.basePackage(),
                    module.javaSourceRoot(),
                    module.mapperXmlRoot()
            );
        }
    }

    private static void generate(String[] args) throws Exception {
        Path sqlPath = Path.of(argValue(args, "--sql", "sql/init.sql"));
        Path backendRoot = Path.of(argValue(args, "--backend-root", "backend"));
        List<SqlTable> tables = new SqlInitParser().parse(sqlPath);
        GeneratedBaseWriter.GenerationSummary summary = new GeneratedBaseWriter(backendRoot).write(tables);
        System.out.println("tables=" + tables.size());
        for (var entry : summary.generatedByModule().entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
        if (!summary.unresolvedTables().isEmpty()) {
            throw new IllegalStateException("Unresolved tables: " + summary.unresolvedTables());
        }
    }

    private static boolean hasArg(String[] args, String name) {
        for (String arg : args) {
            if (name.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String argValue(String[] args, String name, String defaultValue) {
        for (int index = 0; index < args.length - 1; index++) {
            if (name.equals(args[index])) {
                return args[index + 1];
            }
        }
        return defaultValue;
    }
}
