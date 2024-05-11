package com.unique.examine;

import cn.dev33.satoken.SaManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan(basePackages = "com.unique.*.mapper")
@ComponentScan({"com.unique.*"})
public class ExamineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamineApplication.class, args);

        System.out.println("启动成功：Sa-Token配置如下：" + SaManager.getConfig());
    }
//    public static void main(String[] args) throws JsonProcessingException {
//        SpringApplication.run(ApproveApplication.class, args);
//        System.out.println("启动成功：Sa-Token配置如下：" + SaManager.getConfig());
//    }

}
