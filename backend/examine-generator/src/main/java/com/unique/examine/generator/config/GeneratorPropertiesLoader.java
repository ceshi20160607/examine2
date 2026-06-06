package com.unique.examine.generator.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads generator properties from command-line supplied property files and environment variables.
 */
public final class GeneratorPropertiesLoader {

    private static final String DEFAULT_AUTHOR = "examine-generator";
    private static final String DEFAULT_REPORT_DIR = "examine-generator/reports";

    private GeneratorPropertiesLoader() {
    }

    /**
     * Loads generator properties.
     *
     * @param backendRoot backend Maven parent root
     * @param configFile optional property file
     * @return generator properties
     * @throws IOException when the config file cannot be read
     */
    public static GeneratorProperties load(Path backendRoot, Path configFile) throws IOException {
        Properties properties = new Properties();
        String source = "defaults";
        if (configFile != null) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            source = configFile.toString();
        }

        String author = get(properties, "generator.author", DEFAULT_AUTHOR);
        String reportDir = get(properties, "generator.report-dir", DEFAULT_REPORT_DIR);
        String configuredUrl = get(properties, "generator.datasource.url", "");
        String envUrl = System.getenv("EXAMINE_GENERATOR_DATASOURCE_URL");
        String url = firstPresent(configuredUrl, envUrl);
        String username = firstPresent(
                get(properties, "generator.datasource.username", ""),
                System.getenv("EXAMINE_GENERATOR_DATASOURCE_USERNAME")
        );
        String password = firstPresent(
                get(properties, "generator.datasource.password", ""),
                System.getenv("EXAMINE_GENERATOR_DATASOURCE_PASSWORD")
        );
        String dataSourceSource = resolveDataSourceSource(configuredUrl, envUrl, source);

        return GeneratorProperties.of(
                backendRoot,
                author,
                Path.of(reportDir),
                GeneratorDataSourceProperties.of(url, username, password, dataSourceSource),
                GeneratorModuleMappings.defaults()
        );
    }

    private static String get(Properties properties, String key, String defaultValue) {
        return Objects.toString(properties.getProperty(key), defaultValue).trim();
    }

    private static String firstPresent(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return Objects.toString(fallback, "").trim();
    }

    private static String resolveDataSourceSource(String configuredUrl, String envUrl, String configuredSource) {
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredSource;
        }
        if (envUrl != null && !envUrl.isBlank()) {
            return "environment variables";
        }
        return "not configured";
    }
}
