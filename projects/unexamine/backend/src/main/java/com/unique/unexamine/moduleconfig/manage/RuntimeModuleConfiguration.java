package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeModuleConfiguration(
        Long moduleId,
        Long versionId,
        Integer versionNumber,
        Integer publicationVersion,
        JsonNode configuration) {
}
