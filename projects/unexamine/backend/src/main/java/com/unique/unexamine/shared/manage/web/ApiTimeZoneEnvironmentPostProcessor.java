package com.unique.unexamine.shared.manage.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.TimeZone;

/** 在业务 Bean 和数据库连接创建前统一时区，避免 UTC 容器写出早于数据库开始时间的完成时间。 */
public final class ApiTimeZoneEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /** 与已有 API 序列化及数据库 +08:00 会话保持一致，不改写历史 DATETIME 值。 */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TimeZone.setDefault(TimeZone.getTimeZone(ApiContractConfiguration.API_ZONE));
    }

    /** 必须先于配置和连接池初始化执行，生成的应用入口无需手工维护。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
