package com.unique.examine.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.security.SessionPayload;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private static final String PREFIX = "plat:session:";
    private static final Duration TTL = Duration.ofDays(7);

    @Value("${examine.session.store:redis}")
    private String sessionStore;

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, String> memorySessions = new ConcurrentHashMap<>();

    private boolean useMemory() {
        return "memory".equalsIgnoreCase(sessionStore);
    }

    public String createSession(SessionPayload payload) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = PREFIX + token;
            if (useMemory()) {
                memorySessions.put(key, json);
            } else {
                redis.opsForValue().set(key, json, TTL);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return token;
    }

    public Optional<SessionPayload> getSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String json = useMemory()
                ? memorySessions.get(PREFIX + token)
                : redis.opsForValue().get(PREFIX + token);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SessionPayload.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void updateSession(String token, SessionPayload payload) {
        if (token == null || token.isBlank() || payload == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = PREFIX + token;
            if (useMemory()) {
                memorySessions.put(key, json);
            } else {
                redis.opsForValue().set(key, json, TTL);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deleteSession(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (useMemory()) {
            memorySessions.remove(PREFIX + token);
        } else {
            redis.delete(PREFIX + token);
        }
    }
}
