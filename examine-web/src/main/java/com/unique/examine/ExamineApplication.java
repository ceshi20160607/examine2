package com.unique.examine;

import cn.dev33.satoken.SaManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

//@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@SpringBootApplication
@MapperScan(basePackages = "com.unique.*.mapper")
@ComponentScan({"com.unique.*"})
public class ExamineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamineApplication.class, args);

        System.out.println("启动成功：Sa-Token配置如下：" + SaManager.getConfig());
    }
}
