package com.unique.examine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.unique.examine")
@EnableScheduling
@MapperScan({
        "com.unique.examine.plat.mapper",
        "com.unique.examine.app.mapper",
        "com.unique.examine.module.mapper",
        "com.unique.examine.upload.mapper",
        "com.unique.examine.flow.mapper"
})
public class ExamineWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamineWebApplication.class, args);
    }
}
