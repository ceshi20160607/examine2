package com.unique.examine.generator.config;

import java.util.Objects;

/**
 * Database connection parameters for the generator.
 *
 * @param url JDBC URL
 * @param username database username
 * @param password database password
 * @param source configuration source description
 */
public record GeneratorDataSourceProperties(String url, String username, String password, String source) {

    /**
     * Creates datasource properties with null-safe text values.
     *
     * @param url JDBC URL
     * @param username database username
     * @param password database password
     * @param source configuration source description
     * @return datasource properties
     */
    public static GeneratorDataSourceProperties of(String url, String username, String password, String source) {
        return new GeneratorDataSourceProperties(
                Objects.toString(url, ""),
                Objects.toString(username, ""),
                Objects.toString(password, ""),
                Objects.toString(source, "not configured")
        );
    }

    /**
     * Returns whether JDBC credentials are complete enough for a future run.
     *
     * @return true when URL and username are present
     */
    public boolean isConfigured() {
        return !url.isBlank() && !username.isBlank();
    }
}
