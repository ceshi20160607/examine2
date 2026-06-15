package com.unique.examine.core.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                if (metaObject.hasSetter("updateTime")) {
                    strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
                }
                if (metaObject.hasSetter("updateUserId") && metaObject.hasSetter("createUserId")) {
                    Object updateUserId = getFieldValByName("updateUserId", metaObject);
                    Object createUserId = getFieldValByName("createUserId", metaObject);
                    if (updateUserId == null && createUserId != null) {
                        strictInsertFill(metaObject, "updateUserId", Long.class, (Long) createUserId);
                    }
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
