package com.unique.examine.generator.cli;

import com.unique.examine.generator.engine.GenerationExecutor;
import com.unique.examine.generator.config.GeneratorModuleMapping;
import com.unique.examine.generator.config.GeneratorProperties;
import com.unique.examine.generator.config.GeneratorPropertiesLoader;
import com.unique.examine.generator.plan.GenerationPlan;
import com.unique.examine.generator.plan.GenerationPlanner;
import com.unique.examine.generator.sql.SqlScriptExecutor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成器命令行入口，负责解析参数、生成计划、执行生成并写出报告。
 */
public final class GeneratorCli {

    private GeneratorCli() {
    }

    /**
     * 解析命令行参数，并根据 `--execute` 决定只输出预览报告还是执行真实代码生成。
     *
     * @param args 命令行参数
     * @throws Exception 配置读取、SQL 表解析、代码生成或报告写入失败时抛出
     */
    public static void main(String[] args) throws Exception {
        try {
            CliOptions options = CliOptions.parse(args);
            if (options.help) {
                printUsage();
                return;
            }

            GeneratorProperties properties = GeneratorPropertiesLoader.load(options.backendRoot, options.configFile);
            SqlScriptExecutor.ExecutionResult sqlExecutionResult = null;
            if (options.initSqlFile != null) {
                sqlExecutionResult = new SqlScriptExecutor().execute(properties.dataSource(), options.initSqlFile);
            }
            List<GeneratorModuleMapping> mappings = options.buildMappings();
            GenerationPlan plan = GenerationPlanner.plan(mappings, options.tables, options.prefixes);
            GenerationExecutor.ExecutionResult executionResult = null;
            if (options.execute) {
                if (mappings.isEmpty()) {
                    if (sqlExecutionResult != null) {
                        System.out.println("SQL initialized: " + sqlExecutionResult.executedStatements() + " statements.");
                        return;
                    }
                    throw new IllegalArgumentException("Generation requires --module-name, --base-package, "
                            + "--source-root, --mapper-xml-root and --table-prefix.");
                }
                executionResult = new GenerationExecutor().execute(properties, plan);
            }

            if (sqlExecutionResult != null) {
                System.out.println("SQL initialized: " + sqlExecutionResult.executedStatements() + " statements.");
            }
            System.out.println(options.execute ? "MyBatis-Plus base CRUD generation executed." : "Dry-run only.");
        } finally {
            shutdownMysqlCleanupThread();
        }
    }

    private static void shutdownMysqlCleanupThread() {
        try {
            Class<?> cleanupThread = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            cleanupThread.getMethod("checkedShutdown").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // MySQL 驱动是运行时依赖；dry-run 模式可能不会加载该类。
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -cp examine-generator.jar com.unique.examine.generator.cli.GeneratorCli [options]

                Options:
                  --backend-root <path>  Backend Maven parent root. Defaults to current directory.
                  --config <path>        Optional UTF-8 .properties config file.
                  --module-name <name>   Target Maven module name, for example examine-plat.
                  --table-prefix <pre>   Table prefix for this module, repeatable.
                  --base-package <pkg>   Generated base package, for example com.unique.examine.plat.base.
                  --source-root <path>   Java source root relative to backend root.
                  --mapper-xml-root <p>  Mapper XML root relative to backend root.
                  --table <name[,name]>  Table names to plan, repeatable.
                  --prefix <prefix>      Alias of --table-prefix.
                  --init-sql <path>      Execute SQL before generation through JDBC.
                  --execute              Connect to MySQL and generate base CRUD files.
                  --help                 Print this help.

                Without --execute this command only writes a dry-run plan.
                """);
    }

    private static final class CliOptions {

        private Path backendRoot = Path.of("").toAbsolutePath().normalize();
        private Path configFile;
        private Path initSqlFile;
        private String moduleName;
        private String basePackage;
        private String sourceRoot;
        private String mapperXmlRoot;
        private final List<String> tables = new ArrayList<>();
        private final List<String> prefixes = new ArrayList<>();
        private boolean help;
        private boolean execute;

        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--backend-root" -> options.backendRoot = Path.of(options.nextValue(args, ++i, arg))
                            .toAbsolutePath()
                            .normalize();
                    case "--config" -> options.configFile = Path.of(options.nextValue(args, ++i, arg))
                            .toAbsolutePath()
                            .normalize();
                    case "--module-name" -> options.moduleName = options.nextValue(args, ++i, arg);
                    case "--base-package" -> options.basePackage = options.nextValue(args, ++i, arg);
                    case "--source-root" -> options.sourceRoot = options.nextValue(args, ++i, arg);
                    case "--mapper-xml-root" -> options.mapperXmlRoot = options.nextValue(args, ++i, arg);
                    case "--table-prefix" -> addCsv(options.prefixes, options.nextValue(args, ++i, arg));
                    case "--table" -> addCsv(options.tables, options.nextValue(args, ++i, arg));
                    case "--prefix" -> addCsv(options.prefixes, options.nextValue(args, ++i, arg));
                    case "--init-sql" -> options.initSqlFile = Path.of(options.nextValue(args, ++i, arg))
                            .toAbsolutePath()
                            .normalize();
                    case "--execute" -> options.execute = true;
                    case "--help", "-h" -> options.help = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            return options;
        }

        private String nextValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            return args[index];
        }

        private static void addCsv(List<String> values, String csv) {
            for (String value : csv.split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isBlank()) {
                    values.add(trimmed);
                }
            }
        }

        private List<GeneratorModuleMapping> buildMappings() {
            boolean hasModuleConfig = moduleName != null || basePackage != null || sourceRoot != null
                    || mapperXmlRoot != null || !prefixes.isEmpty();
            if (!hasModuleConfig) {
                return List.of();
            }
            if (moduleName == null || basePackage == null || sourceRoot == null || mapperXmlRoot == null
                    || prefixes.isEmpty()) {
                throw new IllegalArgumentException("Module generation requires --module-name, --base-package, "
                        + "--source-root, --mapper-xml-root and at least one --table-prefix.");
            }
            return List.of(GeneratorModuleMapping.of(prefixes, moduleName, basePackage, sourceRoot, mapperXmlRoot));
        }
    }
}
