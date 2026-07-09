package com.unique.unexamine.core.auth;

public record SwitchContextRequest(
        String target,
        String systemId
) {
}