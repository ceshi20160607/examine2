package com.unique.examine.generator.sql;

import com.unique.examine.generator.config.GeneratorDataSourceProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL 初始化脚本执行器。
 *
 * <p>用于在生成代码前把 `sql/init.sql` 导入到配置数据库，避免依赖本机 mysql 命令行客户端。</p>
 */
public final class SqlScriptExecutor {

    /**
     * 执行 SQL 文件。
     *
     * @param dataSource 数据源配置
     * @param sqlFile SQL 文件路径
     * @return 执行结果
     * @throws IOException SQL 文件读取失败
     * @throws SQLException SQL 执行失败
     */
    public ExecutionResult execute(GeneratorDataSourceProperties dataSource, Path sqlFile)
            throws IOException, SQLException {
        if (!dataSource.isConfigured()) {
            throw new IllegalArgumentException("Generator datasource is not configured.");
        }
        String sql = removeLineComments(removeBom(Files.readString(sqlFile, StandardCharsets.UTF_8)));
        List<String> statements = splitStatements(sql);
        int executedCount = 0;
        try (Connection connection = DriverManager.getConnection(dataSource.url(), dataSource.username(),
                dataSource.password());
             Statement statement = connection.createStatement()) {
            for (String sqlStatement : statements) {
                if (sqlStatement.isBlank()) {
                    continue;
                }
                statement.execute(sqlStatement);
                executedCount++;
            }
        }
        return new ExecutionResult(sqlFile.toAbsolutePath().normalize(), executedCount);
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        for (int index = 0; index < sql.length(); index++) {
            char value = sql.charAt(index);
            char previous = index == 0 ? '\0' : sql.charAt(index - 1);
            if (value == '\'' && !doubleQuote && !backtick && previous != '\\') {
                singleQuote = !singleQuote;
            } else if (value == '"' && !singleQuote && !backtick && previous != '\\') {
                doubleQuote = !doubleQuote;
            } else if (value == '`' && !singleQuote && !doubleQuote) {
                backtick = !backtick;
            }
            if (value == ';' && !singleQuote && !doubleQuote && !backtick) {
                statements.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        if (!current.isEmpty()) {
            statements.add(current.toString().strip());
        }
        return statements;
    }

    private static String removeBom(String sql) {
        if (sql != null && !sql.isEmpty() && sql.charAt(0) == '\uFEFF') {
            return sql.substring(1);
        }
        return sql;
    }

    private static String removeLineComments(String sql) {
        StringBuilder builder = new StringBuilder();
        for (String line : sql.lines().toList()) {
            if (!line.stripLeading().startsWith("--")) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    /**
     * SQL 执行结果。
     *
     * @param sqlFile SQL 文件
     * @param executedStatements 执行语句数量
     */
    public record ExecutionResult(Path sqlFile, int executedStatements) {
    }
}
