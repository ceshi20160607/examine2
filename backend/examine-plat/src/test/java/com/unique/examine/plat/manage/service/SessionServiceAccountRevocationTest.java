package com.unique.examine.plat.manage.service;

import com.unique.examine.plat.base.entity.ContextSession;
import com.unique.examine.plat.base.entity.RefreshToken;
import com.unique.examine.plat.base.mapper.PlatContextSessionMapper;
import com.unique.examine.plat.base.mapper.PlatRefreshTokenMapper;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceAccountRevocationTest {

    @Test
    void revokesEveryActiveContextRefreshTokenAndRedisAccessKey() {
        var first = context(101, 17, "hash-101");
        var second = context(102, 17, "hash-102");
        var contexts = new LinkedHashMap<Long, ContextSession>();
        contexts.put(first.getId(), first);
        contexts.put(second.getId(), second);
        var contextUpdates = new ArrayList<ContextSession>();
        var contextMapper = proxy(PlatContextSessionMapper.class, (method, arguments) -> switch (method) {
            case "selectList" -> List.copyOf(contexts.values());
            case "selectById" -> contexts.get(arguments[0]);
            case "updateById" -> {
                contextUpdates.add((ContextSession) arguments[0]);
                yield 1;
            }
            default -> unexpected(method);
        });

        var firstRefresh = refresh(201, 101);
        var secondRefresh = refresh(202, 102);
        var refreshReads = new ArrayDeque<>(List.of(
                List.of(firstRefresh), List.of(secondRefresh)));
        var refreshUpdates = new ArrayList<RefreshToken>();
        var refreshMapper = proxy(PlatRefreshTokenMapper.class, (method, arguments) -> switch (method) {
            case "selectList" -> refreshReads.removeFirst();
            case "updateById" -> {
                refreshUpdates.add((RefreshToken) arguments[0]);
                yield 1;
            }
            default -> unexpected(method);
        });
        var redis = new RecordingRedisTemplate();
        var service = service(contextMapper, refreshMapper, redis);

        service.revokeAllForAccount(17L);

        assertThat(contextUpdates).containsExactly(first, second);
        assertThat(contextUpdates).allSatisfy(context -> {
            assertThat(context.getStatus()).isEqualTo("REVOKED");
            assertThat(context.getRevokedAt()).isNotNull();
            assertThat(context.getUpdatedAt()).isEqualTo(context.getRevokedAt());
        });
        assertThat(refreshUpdates).containsExactly(firstRefresh, secondRefresh);
        assertThat(refreshUpdates).allSatisfy(token -> {
            assertThat(token.getStatus()).isEqualTo("REVOKED");
            assertThat(token.getRevokedAt()).isNotNull();
        });
        assertThat(redis.deletedKeys).containsExactly(
                "examine:session:hash-101",
                "examine:session:hash-102"
        );
        assertThat(refreshReads).isEmpty();
    }

    @Test
    void contextConflictFailsBeforeRefreshOrRedisRevocation() {
        var context = context(101, 17, "hash-101");
        var contextMapper = proxy(PlatContextSessionMapper.class, (method, arguments) -> switch (method) {
            case "selectList" -> List.of(context);
            case "selectById" -> context;
            case "updateById" -> 0;
            default -> unexpected(method);
        });
        var refreshReads = new ArrayList<Object>();
        var refreshMapper = proxy(PlatRefreshTokenMapper.class, (method, arguments) -> {
            refreshReads.add(method);
            return unexpected(method);
        });
        var redis = new RecordingRedisTemplate();
        var service = service(contextMapper, refreshMapper, redis);

        assertThatThrownBy(() -> service.revokeAllForAccount(17L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("SESSION_REVOCATION_CONFLICT"));

        assertThat(refreshReads).isEmpty();
        assertThat(redis.deletedKeys).isEmpty();
    }

    private static SessionService service(
            PlatContextSessionMapper contextMapper,
            PlatRefreshTokenMapper refreshMapper,
            StringRedisTemplate redis
    ) {
        return new SessionService(
                null,
                contextMapper,
                refreshMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                redis,
                null,
                null
        );
    }

    private static ContextSession context(long id, long accountId, String hash) {
        var value = new ContextSession();
        value.setId(id);
        value.setAccountId(accountId);
        value.setTokenHash(hash);
        value.setStatus("ACTIVE");
        value.setVersion(0L);
        return value;
    }

    private static RefreshToken refresh(long id, long contextId) {
        var value = new RefreshToken();
        value.setId(id);
        value.setContextSessionId(contextId);
        value.setStatus("ACTIVE");
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments)
        );
    }

    private static Object unexpected(String method) {
        throw new AssertionError("Unexpected mapper method: " + method);
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {
        private final List<String> deletedKeys = new ArrayList<>();

        @Override
        public Boolean delete(String key) {
            deletedKeys.add(key);
            return true;
        }
    }
}
