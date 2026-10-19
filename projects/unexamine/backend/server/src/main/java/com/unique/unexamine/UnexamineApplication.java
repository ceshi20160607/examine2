package com.unique.unexamine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 当前产品的单一后端入口。 */
@SpringBootApplication
@MapperScan({
        "com.unique.unexamine.identity.base.mapper",
        "com.unique.unexamine.commonresource.base.mapper",
        "com.unique.unexamine.module.base.mapper",
        "com.unique.unexamine.flow.base.mapper",
        "com.unique.unexamine.examine.base.mapper",
        "com.unique.unexamine.work.base.mapper",
        "com.unique.unexamine.data.base.mapper",
        "com.unique.unexamine.print.base.mapper",
        "com.unique.unexamine.app.base.mapper"
})
public class UnexamineApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnexamineApplication.class, args);
    }
}
