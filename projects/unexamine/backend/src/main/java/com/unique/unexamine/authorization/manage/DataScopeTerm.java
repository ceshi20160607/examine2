package com.unique.unexamine.authorization.manage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record DataScopeTerm(
        String type,
        JsonNode condition,
        List<Long> roleIds) {
}
