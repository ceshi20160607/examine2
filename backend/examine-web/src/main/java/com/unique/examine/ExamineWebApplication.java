package com.unique.examine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.unique.examine")
@MapperScan("com.unique.examine.core.mapper")
public class ExamineWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamineWebApplication.class, args);
    }
}
