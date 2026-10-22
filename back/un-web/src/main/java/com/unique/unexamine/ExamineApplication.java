package com.unique.unexamine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 唯一后端服务入口，按业务 Maven 模块组合功能。 */
@SpringBootApplication
public class ExamineApplication {
    /** 启动后端服务。 */
    public static void main(String[] args) {
        SpringApplication.run(ExamineApplication.class, args);
    }
}
