package com.unique.unexamine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 当前产品的单一后端入口。 */
@SpringBootApplication
@MapperScan("com.unique.unexamine.base.mapper")
public class UnexamineApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnexamineApplication.class, args);
    }
}
