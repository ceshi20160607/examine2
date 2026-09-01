package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;

public record SsoStartResult(String authorizationUrl, LocalDateTime expiresAt) {
}
