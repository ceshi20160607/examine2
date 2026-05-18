package com.unique.examine.core.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

/**
 * MySQL 库表 {@code create_time}/{@code update_time} 常为 NOT NULL 且无默认值；
 * 实体上 {@code updateTime} 多为 {@code FieldFill.UPDATE}，插入时 MetaObjectHandler 不会写入 SQL。
 * 在 INSERT 执行前直接给实体字段赋值，确保出现在 INSERT 列中。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AuditInsertInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        if (ms.getSqlCommandType() == SqlCommandType.INSERT) {
            stampAuditTimes(invocation.getArgs()[1]);
        }
        return invocation.proceed();
    }

    private void stampAuditTimes(Object parameter) {
        if (parameter == null) {
            return;
        }
        if (parameter instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                stampAuditTimes(value);
            }
            return;
        }
        if (parameter instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                stampAuditTimes(item);
            }
            return;
        }
        MetaObject meta = SystemMetaObject.forObject(parameter);
        if (!meta.hasSetter("updateTime")) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (meta.hasSetter("createTime") && meta.getValue("createTime") == null) {
            meta.setValue("createTime", now);
        }
        if (meta.getValue("updateTime") == null) {
            meta.setValue("updateTime", now);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
