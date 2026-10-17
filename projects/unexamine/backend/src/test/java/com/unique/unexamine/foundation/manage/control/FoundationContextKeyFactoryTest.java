package com.unique.unexamine.foundation.manage.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 验证共用 Redis 时不同业务数据库不会复用彼此的权限快照。 */
class FoundationContextKeyFactoryTest {
    @Test
    void permissionCacheSeparatesDatabasesWithIdenticalBusinessIds() {
        FoundationContextKeyFactory first = new FoundationContextKeyFactory(
                "jdbc:mysql://127.0.0.1:3306/unexamine_first?serverTimezone=Asia/Shanghai");
        FoundationContextKeyFactory second = new FoundationContextKeyFactory(
                "jdbc:mysql://127.0.0.1:3306/unexamine_second?serverTimezone=Asia/Shanghai");
        FoundationContextKeyFactory sameDatabase = new FoundationContextKeyFactory(
                "jdbc:mysql://127.0.0.1:3306/unexamine_first?serverTimezone=Asia/Shanghai");

        String firstKey = first.permissionCacheKey(1L, 1L, 3L, 8L);
        assertNotEquals(firstKey, second.permissionCacheKey(1L, 1L, 3L, 8L));
        assertEquals(firstKey, sameDatabase.permissionCacheKey(1L, 1L, 3L, 8L));
        assertNotEquals(first.backgroundJobKeyPrefix(), second.backgroundJobKeyPrefix());
        assertEquals(first.backgroundJobKeyPrefix(), sameDatabase.backgroundJobKeyPrefix());
    }
}
