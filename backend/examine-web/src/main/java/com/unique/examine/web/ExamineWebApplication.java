package com.unique.examine.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web application entry.
 */
@MapperScan("com.unique.examine.**.base.mapper")
@SpringBootApplication(scanBasePackages = "com.unique.examine")
public class ExamineWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamineWebApplication.class, args);
    }
}
