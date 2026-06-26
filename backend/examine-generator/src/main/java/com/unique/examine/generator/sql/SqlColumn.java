package com.unique.examine.generator.sql;

/**
 * Column metadata parsed from the initial SQL baseline.
 *
 * @param name physical column name
 * @param sqlType normalized SQL type
 * @param nullable whether the column accepts null
 * @param primaryKey whether the column is the primary key
 * @param comment column comment
 */
public record SqlColumn(
        String name,
        String sqlType,
        boolean nullable,
        boolean primaryKey,
        String comment
) {
}
