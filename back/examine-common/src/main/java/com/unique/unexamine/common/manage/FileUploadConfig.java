package com.unique.unexamine.common.manage;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** 使容器上传上限与文件接口的 25 MB 业务限制一致。 */
@Configuration
public class FileUploadConfig {
    /** 表单还有少量文本字段，请求总量在文件上限之上留 1 MB。 */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(25));
        factory.setMaxRequestSize(DataSize.ofMegabytes(26));
        return factory.createMultipartConfig();
    }
}
