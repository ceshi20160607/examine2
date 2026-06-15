package com.unique.examine.generator.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * 从命令行配置文件和环境变量读取生成器配置。
 */
public final class GeneratorPropertiesLoader {

    private static final String DEFAULT_AUTHOR = "examine-generator";

    private GeneratorPropertiesLoader() {
    }

    /**
     * 读取生成器配置。
     *
     * @param backendRoot 后端 Maven 父工程根目录
     * @param configFile 可选 properties 配置文件
     * @return 生成器配置
     * @throws IOException 配置文件读取失败时抛出
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
                GeneratorDataSourceProperties.of(url, username, password, dataSourceSource)
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
