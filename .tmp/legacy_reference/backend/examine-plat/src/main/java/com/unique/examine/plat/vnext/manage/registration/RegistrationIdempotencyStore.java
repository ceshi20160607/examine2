package com.unique.examine.plat.vnext.manage.registration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.base.entity.Idempotency;
import com.unique.examine.core.base.service.IIdempotencyService;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
final class RegistrationIdempotencyStore {
    private final IIdempotencyService service;
    private final IdService idService;
    private final ObjectMapper objectMapper;

    RegistrationIdempotencyStore(
            IIdempotencyService service, IdService idService, ObjectMapper objectMapper
    ) {
        this.service = service;
        this.idService = idService;
        this.objectMapper = objectMapper;
    }

    Idempotency begin(RegistrationCommand command, LocalDateTime now) {
        var value = new Idempotency();
        value.setId(idService.nextId());
        value.setScopeType("REGISTER_FIRST_SYSTEM");
        value.setScopeKey("GLOBAL");
        value.setIdempotencyKey(command.idempotencyKey());
        value.setRequestHash(command.requestHash());
        value.setStatus("PROCESSING");
        value.setLockedUntil(now.plusMinutes(2));
        value.setExpiresAt(now.plusHours(24));
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        require(service.save(value), "Idempotency begin");
        return value;
    }

    void complete(Idempotency value, StoredRegistration stored, LocalDateTime now) {
        value.setResponseBody(write(stored));
        value.setStatus("COMPLETED");
        value.setResponseHttpStatus(200);
        value.setResponseCode("OK");
        value.setLockedUntil(null);
        value.setUpdatedAt(now);
        require(service.updateById(value), "Idempotency completion");
    }

    Idempotency find(String key) {
        return service.getOne(Wrappers.<Idempotency>lambdaQuery()
                .eq(Idempotency::getScopeType, "REGISTER_FIRST_SYSTEM")
                .eq(Idempotency::getScopeKey, "GLOBAL")
                .eq(Idempotency::getIdempotencyKey, key), false);
    }

    StoredRegistration read(Idempotency value) {
        try {
            return objectMapper.readValue(value.getResponseBody(), StoredRegistration.class);
        } catch (JsonProcessingException exception) {
            throw RegistrationErrors.failed();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize registration receipt", exception);
        }
    }

    private static void require(boolean result, String operation) {
        if (!result) {
            throw new IllegalStateException(operation + " was rejected");
        }
    }
}
