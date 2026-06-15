package com.unique.examine.generator.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 生成器运行配置。
 *
 * @param backendRoot 后端 Maven 父工程根目录
 * @param author 生成代码作者
 * @param dataSource 数据源配置
 */
public record GeneratorProperties(
        Path backendRoot,
        String author,
        GeneratorDataSourceProperties dataSource
) {

    /**
     * 创建生成器配置。
     *
     * @param backendRoot 后端 Maven 父工程根目录
     * @param author 生成代码作者
     * @param dataSource 数据源配置
     * @return 生成器配置
     */
    public static GeneratorProperties of(
            Path backendRoot,
            String author,
            GeneratorDataSourceProperties dataSource
    ) {
        return new GeneratorProperties(
                Objects.requireNonNull(backendRoot, "backendRoot").normalize(),
                Objects.toString(author, "examine-generator"),
                Objects.requireNonNull(dataSource, "dataSource")
        );
    }
}
