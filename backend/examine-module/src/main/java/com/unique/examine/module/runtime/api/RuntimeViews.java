package com.unique.examine.module.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class RuntimeViews {
    private RuntimeViews() { }

    public record Navigation(String activeVersionId, String versionNo, List<Group> groups) { }

    public record Group(String id, String code, String name, String iconKey, int sortOrder,
                        List<Module> modules) { }

    public record Module(String id, String code, String name, String iconKey, int sortOrder,
                         String permissionCode, String defaultPageCode) { }

    public record Definition(String activeVersionId, String versionNo, JsonNode module, JsonNode fields,
                             JsonNode pages, JsonNode components, JsonNode actions, JsonNode rules,
                             JsonNode dictionaries, JsonNode dictionaryItems, boolean recordsAvailable) { }
}
