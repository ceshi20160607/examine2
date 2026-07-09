package com.unique.unexamine.core.auth;

public record LoginRequest(
        String username,
        String password
) {
}