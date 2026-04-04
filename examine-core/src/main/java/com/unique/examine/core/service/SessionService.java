package com.unique.examine.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.security.SessionPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private static final String PREFIX = "plat:session:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SessionService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String createSession(SessionPayload payload) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            String json = objectMapper.writeValueAsString(payload);
            redis.opsForValue().set(PREFIX + token, json, TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return token;
    }

    public Optional<SessionPayload> getSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String json = redis.opsForValue().get(PREFIX + token);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SessionPayload.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void deleteSession(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(PREFIX + token);
        }
    }
}
