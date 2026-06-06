package com.unique.examine.generator.cli;

import com.unique.examine.generator.engine.GenerationExecutor;
import com.unique.examine.generator.config.GeneratorModuleMapping;
import com.unique.examine.generator.config.GeneratorProperties;
import com.unique.examine.generator.config.GeneratorPropertiesLoader;
import com.unique.examine.generator.plan.GenerationPlan;
import com.unique.examine.generator.plan.GenerationPlanner;
import com.unique.examine.generator.plan.SqlInitTableReader;
import com.unique.examine.generator.report.GenerationReportWriter;

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
            // `--all-from-sql` 用于后续表结构变动后的批量再生成，表清单来自当前 init.sql。
            if (options.allFromSql) {
                if (options.sqlFile == null) {
                    throw new IllegalArgumentException("--all-from-sql requires --sql <path>.");
                }
                options.tables.addAll(SqlInitTableReader.readTableNames(options.sqlFile));
            }
            List<GeneratorModuleMapping> mappings = GenerationPlanner.configuredMappings(properties);
            GenerationPlan plan = GenerationPlanner.plan(properties, options.tables, options.prefixes);
            Path reportPath = options.reportPath == null
                    ? properties.backendRoot().resolve("docs/mybatis-plus-generation.md").normalize()
                    : options.reportPath;

            GenerationExecutor.ExecutionResult executionResult = null;
            if (options.execute) {
                executionResult = new GenerationExecutor().execute(properties, plan);
            }

            new GenerationReportWriter().write(properties, mappings, plan, executionResult, options.renderCommand(), reportPath);

            System.out.println("Generator report: " + reportPath);
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
                  --table <name[,name]>  Table names to plan, repeatable.
                  --prefix <prefix>      Table prefix to plan, repeatable. Example: un_plat_
                  --sql <path>           Initialization SQL used to load all CREATE TABLE names.
                  --all-from-sql         Add every CREATE TABLE table from --sql.
                  --execute              Connect to MySQL and generate base CRUD files.
                  --report <path>        Report path. Defaults to backend/docs/mybatis-plus-generation.md.
                  --help                 Print this help.

                Without --execute this command only writes a dry-run plan.
                """);
    }

    private static final class CliOptions {

        private Path backendRoot = Path.of("").toAbsolutePath().normalize();
        private Path configFile;
        private Path reportPath;
        private Path sqlFile;
        private final List<String> tables = new ArrayList<>();
        private final List<String> prefixes = new ArrayList<>();
        private final List<String> rawArgs = new ArrayList<>();
        private boolean help;
        private boolean execute;
        private boolean allFromSql;

        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                options.rawArgs.add(arg);
                switch (arg) {
                    case "--backend-root" -> options.backendRoot = Path.of(options.nextAndRecord(args, ++i, arg))
                            .toAbsolutePath()
                            .normalize();
                    case "--config" -> options.configFile = Path.of(options.nextAndRecord(args, ++i, arg))
                            .toAbsolutePath()
                            .normalize();
                    case "--table" -> addCsv(options.tables, options.nextAndRecord(args, ++i, arg));
                    case "--prefix" -> addCsv(options.prefixes, options.nextAndRecord(args, ++i, arg));
                    case "--sql" -> options.sqlFile = Path.of(options.nextAndRecord(args, ++i, arg))
                            .toAbsolutePath()
                            .normalize();
                    case "--all-from-sql" -> options.allFromSql = true;
                    case "--execute" -> options.execute = true;
                    case "--report" -> options.reportPath = Path.of(options.nextAndRecord(args, ++i, arg))
                            .toAbsolutePath()
                            .normalize();
                    case "--help", "-h" -> options.help = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            return options;
        }

        private String nextAndRecord(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            rawArgs.add(args[index]);
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

        private String renderCommand() {
            return "GeneratorCli " + String.join(" ", rawArgs);
        }
    }
}
