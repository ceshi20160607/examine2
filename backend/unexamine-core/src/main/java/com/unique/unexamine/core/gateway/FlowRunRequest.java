package com.unique.unexamine.core.gateway;

import java.util.Map;

public record FlowRunRequest(
        String moduleCode,
        String recordId,
        Map<String, String> inputValues
) {
}