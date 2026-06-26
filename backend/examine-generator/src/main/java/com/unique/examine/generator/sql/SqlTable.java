package com.unique.examine.generator.sql;

import java.util.List;

/**
 * Table metadata parsed from the initial SQL baseline.
 *
 * @param name physical table name
 * @param columns physical columns in declaration order
 */
public record SqlTable(String name, List<SqlColumn> columns) {
}
