package com.unique.examine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Backend web startup entry.
 */
@SpringBootApplication(
        scanBasePackages = "com.unique.examine",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
@MapperScan(value = {
        "com.unique.examine.core.base.mapper",
        "com.unique.examine.plat.base.mapper",
        "com.unique.examine.module.base.mapper",
        "com.unique.examine.flow.base.mapper",
        "com.unique.examine.upload.base.mapper",
        "com.unique.examine.app.base.mapper"
}, nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class ExamineWebApplication {

    /**
     * Start the backend web application.
     *
     * @param args startup arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ExamineWebApplication.class, args);
    }
}
