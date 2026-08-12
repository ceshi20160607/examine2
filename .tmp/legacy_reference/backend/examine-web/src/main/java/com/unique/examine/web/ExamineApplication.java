package com.unique.examine.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.unique.examine")
@MapperScan({
        "com.unique.examine.core.base.mapper",
        "com.unique.examine.plat.base.mapper",
        "com.unique.examine.plat.vnext.base.mapper",
        "com.unique.examine.module.base.mapper",
        "com.unique.examine.openapi.base.mapper"
})
public class ExamineApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(ExamineApplication.class);
        application.addInitializers(context ->
                FoundationStartupDiagnostics.validateConfiguration(context.getEnvironment()));
        application.run(args);
    }
}
