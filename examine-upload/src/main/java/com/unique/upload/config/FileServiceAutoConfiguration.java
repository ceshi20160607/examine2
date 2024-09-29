package com.unique.upload.config;

import com.unique.upload.FileServiceFactory;
import com.unique.upload.entity.UploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


/**
 * spring自动注入fileService
 * @author UNIQUE
 * @date 2024/09/20
 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
@Import({FileServiceFactory.class})
public class FileServiceAutoConfiguration {
}
