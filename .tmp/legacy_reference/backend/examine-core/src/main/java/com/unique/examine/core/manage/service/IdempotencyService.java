package com.unique.examine.core.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.base.entity.Idempotency;
import com.unique.examine.core.base.mapper.CoreIdempotencyMapper;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class IdempotencyService implements IdempotencyFacade {
    private final CoreIdempotencyMapper mapper;
    private final IdService idService;

    public IdempotencyService(CoreIdempotencyMapper mapper, IdService idService) {
        this.mapper = mapper;
        this.idService = idService;
    }

    @Override
    public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
        var now = LocalDateTime.now();
        var entity = mapper.selectOne(Wrappers.<Idempotency>lambdaQuery()
                .eq(Idempotency::getScopeType, scopeType)
                .eq(Idempotency::getScopeKey, scopeKey)
                .eq(Idempotency::getIdempotencyKey, key)
                .gt(Idempotency::getExpiresAt, now));
        return Optional.ofNullable(entity)
                .map(value -> new IdempotencyRecord(
                        value.getId(),
                        value.getRequestHash(),
                        value.getStatus(),
                        value.getResponseBody()
                ));
    }

    @Override
    public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Idempotency TTL must be positive");
        }
        var now = LocalDateTime.now();
        var expired = mapper.selectOne(Wrappers.<Idempotency>lambdaQuery()
                .eq(Idempotency::getScopeType, scopeType)
                .eq(Idempotency::getScopeKey, scopeKey)
                .eq(Idempotency::getIdempotencyKey, key));
        if (expired != null && !expired.getExpiresAt().isAfter(now)) {
            var updated = mapper.update(null, Wrappers.<Idempotency>lambdaUpdate()
                    .set(Idempotency::getRequestHash, requestHash)
                    .set(Idempotency::getStatus, "PROCESSING")
                    .set(Idempotency::getResponseHttpStatus, null)
                    .set(Idempotency::getResponseCode, null)
                    .set(Idempotency::getResponseBody, null)
                    .set(Idempotency::getLockedUntil, now.plusMinutes(2))
                    .set(Idempotency::getExpiresAt, now.plus(ttl))
                    .set(Idempotency::getUpdatedAt, now)
                    .eq(Idempotency::getId, expired.getId())
                    .le(Idempotency::getExpiresAt, now));
            if (updated == 1) {
                return expired.getId();
            }
        }
        var entity = new Idempotency();
        entity.setId(idService.nextId());
        entity.setScopeType(scopeType);
        entity.setScopeKey(scopeKey);
        entity.setIdempotencyKey(key);
        entity.setRequestHash(requestHash);
        entity.setStatus("PROCESSING");
        entity.setLockedUntil(now.plusMinutes(2));
        entity.setExpiresAt(now.plus(ttl));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void complete(long id, int httpStatus, String responseCode, String responseBody) {
        var update = new Idempotency();
        update.setId(id);
        update.setStatus("COMPLETED");
        update.setResponseHttpStatus(httpStatus);
        update.setResponseCode(responseCode);
        update.setResponseBody(responseBody);
        update.setLockedUntil(null);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(update);
    }
}
