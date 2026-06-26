package com.unique.examine.generator.sql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight parser for the deterministic `sql/init.sql` format.
 */
public final class SqlInitParser {

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "CREATE TABLE IF NOT EXISTS\\s+([a-zA-Z0-9_]+)\\s*\\((.*?)\\)\\s*COMMENT='table';",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "^\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s+([a-zA-Z]+)(?:\\([^)]*\\))?.*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMMENT_PATTERN = Pattern.compile("COMMENT\\s+'([^']*)'", Pattern.CASE_INSENSITIVE);

    /**
     * Parse all table blocks from a SQL init file.
     *
     * @param sqlPath SQL baseline path
     * @return table metadata
     * @throws IOException when the SQL file cannot be read
     */
    public List<SqlTable> parse(Path sqlPath) throws IOException {
        String sql = Files.readString(sqlPath);
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        List<SqlTable> tables = new ArrayList<>();
        while (matcher.find()) {
            tables.add(new SqlTable(matcher.group(1), parseColumns(matcher.group(2))));
        }
        return tables;
    }

    private List<SqlColumn> parseColumns(String tableBody) {
        List<SqlColumn> columns = new ArrayList<>();
        for (String rawLine : tableBody.split("\\R")) {
            String line = trimTrailingComma(rawLine.trim());
            if (line.isBlank() || isIndexLine(line)) {
                continue;
            }
            Matcher matcher = COLUMN_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String columnName = matcher.group(1);
            String sqlType = matcher.group(2).toUpperCase(Locale.ROOT);
            boolean primaryKey = line.toUpperCase(Locale.ROOT).contains("PRIMARY KEY");
            boolean nullable = !line.toUpperCase(Locale.ROOT).contains("NOT NULL") && !primaryKey;
            columns.add(new SqlColumn(columnName, sqlType, nullable, primaryKey, parseComment(line)));
        }
        return columns;
    }

    private boolean isIndexLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("PRIMARY KEY")
                || upper.startsWith("UNIQUE KEY")
                || upper.startsWith("KEY ")
                || upper.startsWith("CONSTRAINT ");
    }

    private String parseComment(String line) {
        Matcher matcher = COMMENT_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String trimTrailingComma(String line) {
        return line.endsWith(",") ? line.substring(0, line.length() - 1) : line;
    }
}
