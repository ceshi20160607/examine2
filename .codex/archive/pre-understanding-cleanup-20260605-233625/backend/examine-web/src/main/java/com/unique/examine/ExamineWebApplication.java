package com.unique.examine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后端启动入口。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.unique.examine")
@MapperScan({
        "com.unique.examine.core.base.mapper",
        "com.unique.examine.plat.base.mapper",
        "com.unique.examine.module.base.mapper",
        "com.unique.examine.flow.base.mapper",
        "com.unique.examine.upload.base.mapper",
        "com.unique.examine.app.base.mapper"
})
public class ExamineWebApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ExamineWebApplication.class, args);
    }
}
