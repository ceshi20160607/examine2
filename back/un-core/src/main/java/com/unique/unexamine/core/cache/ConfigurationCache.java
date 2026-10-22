package com.unique.unexamine.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 配置和授权规则的公共缓存；命名空间包含作用域，业务数据及敏感明文不进入本缓存。 */
@Service
public class ConfigurationCache {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationCache.class);
    private static final Duration LIFE = Duration.ofMinutes(5);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    /** 未装配 Redis 时直接回源，保持基础库可独立使用。 */
    public ConfigurationCache(ObjectProvider<StringRedisTemplate> redis, ObjectMapper json) {
        this.redis = redis.getIfAvailable();
        this.json = json;
    }

    /** 版本化缓存避免旧查询在失效后重新填回旧授权；事务内读取始终走数据库。 */
    public <T> T get(String namespace, String key, Class<T> type, Supplier<T> loader) {
        validate(namespace, key);
        if (redis == null || disabled || TransactionSynchronizationManager.isActualTransactionActive()) return loader.get();
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                String version = version(namespace);
                String cacheKey = "un:config:" + namespace + ":" + version + ":" + key;
                String cached = redis.opsForValue().get(cacheKey);
                T result = null;
                if (cached != null) {
                    try { result = json.readValue(cached, type); }
                    catch (JsonProcessingException ex) { redis.delete(cacheKey); }
                }
                if (result == null) {
                    result = loader.get();
                    if (result != null) redis.opsForValue().set(cacheKey, json.writeValueAsString(result), LIFE);
                }
                if (version.equals(version(namespace))) return result;
            }
        } catch (DataAccessException | JsonProcessingException ex) {
            disabled = true;
            LOG.warn("配置缓存不可用，按作用域回源读取: {}", namespace);
        }
        return loader.get();
    }

    /** 写入提交后切换命名空间版本；回滚不影响已提交配置的缓存。 */
    public void invalidateAfterCommit(String namespace) {
        validate(namespace, "invalidate");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate(namespace); }
            });
        } else invalidate(namespace);
    }

    private String version(String namespace) {
        String value = redis.opsForValue().get("un:config-version:" + namespace);
        return value == null ? "0" : value;
    }

    private void invalidate(String namespace) {
        if (redis == null) return;
        try { redis.opsForValue().set("un:config-version:" + namespace, UUID.randomUUID().toString()); }
        catch (DataAccessException ex) {
            // 失效失败必须让本实例后续读取绕过缓存，否则可能继续使用撤销前的授权。
            disabled = true;
            LOG.warn("配置缓存失效失败，当前实例停止使用缓存: {}", namespace);
        }
    }

    private volatile boolean disabled;

    /** 命名空间由业务作用域和固定资源名组成，禁止用户输入任意 Redis 模式。 */
    private void validate(String namespace, String key) {
        if (namespace == null || !namespace.matches("[a-zA-Z0-9:_-]{1,200}")
                || key == null || !key.matches("[a-zA-Z0-9:_-]{1,200}")) {
            throw new IllegalArgumentException("缓存作用域无效");
        }
    }
}
