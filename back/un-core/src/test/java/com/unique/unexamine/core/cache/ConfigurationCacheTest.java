package com.unique.unexamine.core.cache;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 缓存提交边界和故障回源，使用轻量 Redis 替身而不连接共享环境。 */
class ConfigurationCacheTest {
    public record Value(String text) {}
    private final MemoryRedis redis=new MemoryRedis();
    private final ConfigurationCache cache=new ConfigurationCache(
            new StaticListableBeanFactory(Map.of("redis",redis)).getBeanProvider(StringRedisTemplate.class),new ObjectMapper());
    @AfterEach void clearTransaction() { TransactionSynchronizationManager.clear(); }
    @Test void cachesByPersistedRevisionAndInvalidatesOnlyAfterCommit() {
        AtomicInteger reads=new AtomicInteger();
        var first=cache.get("permission:1:2","1:9",Value.class,()->new Value("v"+reads.incrementAndGet()));
        assertEquals(first,cache.get("permission:1:2","1:9",Value.class,()->new Value("bad")));
        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAfterCommit("permission:1:2");
        assertEquals(first,cache.get("permission:1:2","1:9",Value.class,()->new Value("bad")));
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
        TransactionSynchronizationManager.clearSynchronization();
        assertEquals("new",cache.get("permission:1:2","2:9",Value.class,()->new Value("new")).text());
        assertEquals("isolated",cache.get("permission:1:3","2:9",Value.class,()->new Value("isolated")).text());
    }
    @Test void failedInvalidationBypassesOldValuesAndRestartUsesDatabaseRevision() {
        cache.get("permission:1:2","1:9",Value.class,()->new Value("old"));
        redis.failed=true; cache.invalidateAfterCommit("permission:1:2"); redis.failed=false;
        assertEquals("revoked",cache.get("permission:1:2","1:9",Value.class,()->new Value("revoked")).text());
        var restarted=new ConfigurationCache(new StaticListableBeanFactory(Map.of("redis",redis)).getBeanProvider(StringRedisTemplate.class),new ObjectMapper());
        assertEquals("current",restarted.get("permission:1:2","2:9",Value.class,()->new Value("current")).text());
    }
    @Test void rollbackDoesNotInvalidateAndTransactionsNeverPopulateCache() {
        cache.get("dict:1:2","1:1",Value.class,()->new Value("published"));
        TransactionSynchronizationManager.initSynchronization(); cache.invalidateAfterCommit("dict:1:2");
        TransactionSynchronizationManager.clearSynchronization();
        assertEquals("published",cache.get("dict:1:2","1:1",Value.class,()->new Value("bad")).text());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals("draft",cache.get("dict:1:2","1:1",Value.class,()->new Value("draft")).text());
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertEquals("published",cache.get("dict:1:2","1:1",Value.class,()->new Value("bad")).text());
    }
    @Test void invalidationDuringLoadCannotReturnOrRefillAnOldVersion() {
        AtomicInteger loads=new AtomicInteger();
        Value value=cache.get("permission:1:2","1:9",Value.class,()->{
            if (loads.incrementAndGet()==1) { cache.invalidateAfterCommit("permission:1:2"); return new Value("old"); }
            return new Value("current");
        });
        assertEquals("current",value.text()); assertEquals(2,loads.get());
        assertEquals(value,cache.get("permission:1:2","1:9",Value.class,()->new Value("bad")));
    }
    private static class MemoryRedis extends StringRedisTemplate {
        final Map<String,String> values=new HashMap<>(); boolean failed;
        @Override @SuppressWarnings("unchecked") public ValueOperations<String,String> opsForValue() {
            return (ValueOperations<String,String>)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ValueOperations.class},(proxy,method,args)->{
                if (failed) throw new DataAccessResourceFailureException("故障测试");
                if (method.getName().equals("get")) return values.get(args[0]);
                if (method.getName().equals("set")) { values.put((String)args[0],(String)args[1]); return null; }
                throw new UnsupportedOperationException(method.getName());
            });
        }
        @Override public Boolean delete(String key) { return values.remove(key)!=null; }
    }
}
