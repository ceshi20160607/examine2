package com.unique.examine.generator.plan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads table names from the project initialization SQL.
 */
public final class SqlInitTableReader {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+`([^`]+)`",
            Pattern.CASE_INSENSITIVE
    );

    private SqlInitTableReader() {
    }

    /**
     * Extracts table names in declaration order.
     *
     * @param sqlFile initialization SQL file
     * @return unique table names
     * @throws IOException when the SQL file cannot be read
     */
    public static List<String> readTableNames(Path sqlFile) throws IOException {
        String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
        Set<String> tableNames = new LinkedHashSet<>();
        while (matcher.find()) {
            tableNames.add(matcher.group(1));
        }
        return List.copyOf(tableNames);
    }
}
