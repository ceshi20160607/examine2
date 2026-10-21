package com.unique.unexamine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 当前产品的单一后端入口。 */
@SpringBootApplication
@MapperScan({
        "com.unique.unexamine.plat.base.mapper",
        "com.unique.unexamine.sys.base.mapper",
        "com.unique.unexamine.common.base.mapper",
        "com.unique.unexamine.module.base.mapper",
        "com.unique.unexamine.flow.base.mapper",
        "com.unique.unexamine.approval.base.mapper",
        "com.unique.unexamine.work.base.mapper",
        "com.unique.unexamine.dashboard.base.mapper",
        "com.unique.unexamine.print.base.mapper",
        "com.unique.unexamine.app.base.mapper"
})
public class ExamineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExamineApplication.class, args);
    }
}
