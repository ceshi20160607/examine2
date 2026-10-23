package com.unique.unexamine.file;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 文件 starter 默认接入本地存储；业务可自行提供 FileStorage 替换实现。 */
@AutoConfiguration
@EnableConfigurationProperties(FileStorageAutoConfiguration.LocalProperties.class)
public class FileStorageAutoConfiguration {
    /** 文件目录配置，仅属于存储接入，不携带租户或业务规则。 */
    @ConfigurationProperties(prefix = "un.file.local")
    public static class LocalProperties {
        private Path root = Path.of("data", "files");
        public Path getRoot() { return root; }
        public void setRoot(Path root) { this.root = root; }
    }

    /** 未接入对象存储时启用本地实现。 */
    @Bean
    @ConditionalOnMissingBean(FileStorage.class)
    public FileStorage fileStorage(LocalProperties properties) {
        return new LocalFileStorage(properties.getRoot());
    }
}
