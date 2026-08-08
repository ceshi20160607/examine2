package com.unique.examine.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.unique.examine")
@EnableScheduling
@MapperScan({
        "com.unique.examine.core.base.mapper",
        "com.unique.examine.plat.base.mapper",
        "com.unique.examine.module.base.mapper",
        "com.unique.examine.openapi.base.mapper"
})
public class ExamineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExamineApplication.class, args);
    }
}
