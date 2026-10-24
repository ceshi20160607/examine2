package com.unique.unexamine.sys.module.manage.util;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldSlotRegistry;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * un_module_record 的槽位实体适配。
 *
 * <p>槽位数量、类型映射和扩展键规则由 core 的 {@link FieldSlotRegistry} 固化，
 * 本类只负责把公共协议接到 UnModuleRecord 的 Java 属性和 MyBatis 更新语句。</p>
 */
public final class ModuleRecordStorage {
    private ModuleRecordStorage() {
    }

    /** 按公共槽位协议分配主表键，槽位耗尽后返回 field_*。 */
    public static String allocate(FieldDefinition definition, Set<String> occupied) {
        return FieldSlotRegistry.allocate(definition, occupied);
    }

    /** 判断当前字段是否使用 un_module_record 的固定列。 */
    public static boolean isMain(FieldDefinition definition) {
        return FieldSlotRegistry.isMain(definition);
    }

    /** 从固定列读取原始业务值。 */
    public static Object read(UnModuleRecord record, FieldDefinition definition) {
        if (!isMain(definition)) return null;
        Object value = invoke(getter(definition.code()), record);
        if (value == null) return null;
        return switch (definition.type()) {
            case NUMBER -> value instanceof BigDecimal number ? number.stripTrailingZeros() : value;
            case DATE_TIME -> value instanceof LocalDateTime time ? time.toString() : value;
            case USER, DEPARTMENT, RELATION -> value instanceof Number number ? number.longValue() : value;
            default -> value;
        };
    }

    /** 把已经通过公共校验的值写入固定列。 */
    public static void write(UnModuleRecord record, FieldDefinition definition, Object value) {
        if (isMain(definition)) invoke(setter(definition.code()), record, FieldSlotRegistry.physicalValue(definition, value));
    }

    /** 把固定列加入乐观锁更新语句，和记录基础属性同一事务提交。 */
    public static void apply(UpdateWrapper<UnModuleRecord> update, FieldDefinition definition, Object value) {
        if (isMain(definition)) update.set(definition.code(), FieldSlotRegistry.physicalValue(definition, value));
    }

    /** 返回固定列的 JDBC 值，供唯一校验和条件筛选复用。 */
    public static Object sqlValue(FieldDefinition definition, Object value) {
        return FieldSlotRegistry.physicalValue(definition, value);
    }

    /** 动态 SQL 只允许使用公共槽位白名单中的列。 */
    public static String column(String fieldKey) {
        return FieldSlotRegistry.column(fieldKey);
    }

    /** 判断字段键是否属于固定槽位列。 */
    public static boolean isReservedKey(String fieldKey) {
        return FieldSlotRegistry.isReservedKey(fieldKey);
    }

    private static Method getter(String key) {
        return method(key, "get");
    }

    private static Method setter(String key) {
        return method(key, "set");
    }

    private static Method method(String key, String prefix) {
        StringBuilder property = new StringBuilder();
        for (String part : key.split("_")) {
            property.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String name = prefix + property;
        try {
            if ("get".equals(prefix)) return UnModuleRecord.class.getMethod(name);
            for (Method method : UnModuleRecord.class.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1) return method;
            }
            throw new IllegalArgumentException("主表槽位没有实体映射: " + key);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("主表槽位没有实体映射: " + key, ex);
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("主表预留字段读写失败", ex);
        }
    }
}