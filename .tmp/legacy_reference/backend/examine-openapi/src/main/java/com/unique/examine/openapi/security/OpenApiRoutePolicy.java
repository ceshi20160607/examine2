package com.unique.examine.openapi.security;

import java.util.Optional;
import java.util.regex.Pattern;

public record OpenApiRoutePolicy(
        String method,
        String path,
        String routeTemplate,
        String requiredScope,
        String requiredMemberPermission
) {
    private static final Pattern FLOW_INSTANCE_START = Pattern.compile(
            "^/openapi/v1/flow/definitions/([1-9][0-9]*)/instances$");
    private static final Pattern FLOW_INSTANCE_DETAIL = Pattern.compile(
            "^/openapi/v1/flow/instances/([1-9][0-9]*)$");
    private static final String FLOW_INSTANCE_START_TEMPLATE =
            "/openapi/v1/flow/definitions/{definitionId}/instances";
    private static final String FLOW_INSTANCE_DETAIL_TEMPLATE =
            "/openapi/v1/flow/instances/{instanceId}";
    private static final String FLOW_INSTANCE_START_PERMISSION = "flow.instance.start";
    private static final String FLOW_INSTANCE_READ_SCOPE = "flow.read";
    private static final String FLOW_INSTANCE_READ_PERMISSION = "flow.instance.read";
    private static final Pattern RECORD_COLLECTION = Pattern.compile(
            "^/openapi/v1/modules/([A-Za-z][A-Za-z0-9_]{0,63})/records$");
    private static final Pattern RECORD_DETAIL = Pattern.compile(
            "^/openapi/v1/modules/([A-Za-z][A-Za-z0-9_]{0,63})/records/([1-9][0-9]*)$");
    private static final Pattern RECORD_ACTION = Pattern.compile(
            "^/openapi/v1/modules/([A-Za-z][A-Za-z0-9_]{0,63})/records/"
                    + "([1-9][0-9]*):(activate|archive|unarchive|trash|restore-from-trash)$");
    private static final Pattern RECORD_FILE_COLLECTION = Pattern.compile(
            "^/openapi/v1/modules/([A-Za-z][A-Za-z0-9_]{0,63})/records/"
                    + "([1-9][0-9]*)/files$");
    private static final Pattern RECORD_FILE_CONTENT = Pattern.compile(
            "^/openapi/v1/modules/([A-Za-z][A-Za-z0-9_]{0,63})/records/"
                    + "([1-9][0-9]*)/files/([1-9][0-9]*)/content$");
    private static final Pattern RECORD_COMPOSITION = Pattern.compile(
            "^/openapi/v1/modules/([A-Za-z][A-Za-z0-9_]{0,63})/records/"
                    + "([1-9][0-9]*)/(relations|subtables)/"
                    + "([A-Za-z][A-Za-z0-9_]{0,63})(:mutate)?$");
    private static final String RECORD_COLLECTION_TEMPLATE =
            "/openapi/v1/modules/{moduleCode}/records";
    private static final String RECORD_DETAIL_TEMPLATE =
            "/openapi/v1/modules/{moduleCode}/records/{recordId}";
    private static final String RECORD_FILE_COLLECTION_TEMPLATE =
            RECORD_DETAIL_TEMPLATE + "/files";
    private static final String RECORD_FILE_CONTENT_TEMPLATE =
            RECORD_FILE_COLLECTION_TEMPLATE + "/{fileId}/content";
    private static final String RECORD_WRITE_SCOPE = "record.write";
    private static final String RECORD_READ_SCOPE = "record.read";
    private static final String FILE_WRITE_SCOPE = "file.write";
    private static final String FILE_READ_SCOPE = "file.read";
    private static final String RUNTIME_PERMISSION = "system.runtime.access";

    public static Optional<OpenApiRoutePolicy> resolve(String method, String path) {
        if ("GET".equalsIgnoreCase(method) && "/openapi/v1/ping".equals(path)) {
            return Optional.of(new OpenApiRoutePolicy(
                    "GET",
                    "/openapi/v1/ping",
                    "/openapi/v1/ping",
                    "openapi.ping",
                    "system.runtime.access"
            ));
        }
        if (path == null) {
            return Optional.empty();
        }
        var collection = RECORD_COLLECTION.matcher(path);
        if (collection.matches()) {
            if ("POST".equalsIgnoreCase(method)) {
                return Optional.of(new OpenApiRoutePolicy(
                        "POST", path, RECORD_COLLECTION_TEMPLATE,
                        RECORD_WRITE_SCOPE, RUNTIME_PERMISSION));
            }
            if ("GET".equalsIgnoreCase(method)) {
                return Optional.of(new OpenApiRoutePolicy(
                        "GET", path, RECORD_COLLECTION_TEMPLATE,
                        RECORD_READ_SCOPE, RUNTIME_PERMISSION));
            }
            return Optional.empty();
        }
        var detail = RECORD_DETAIL.matcher(path);
        if (detail.matches()) {
            if (!positiveLong(detail.group(2))) {
                return Optional.empty();
            }
            if ("GET".equalsIgnoreCase(method)) {
                return Optional.of(new OpenApiRoutePolicy(
                        "GET", path, RECORD_DETAIL_TEMPLATE,
                        RECORD_READ_SCOPE, RUNTIME_PERMISSION));
            }
            if ("PUT".equalsIgnoreCase(method)) {
                return Optional.of(new OpenApiRoutePolicy(
                        "PUT", path, RECORD_DETAIL_TEMPLATE,
                        RECORD_WRITE_SCOPE, RUNTIME_PERMISSION));
            }
            return Optional.empty();
        }
        var action = RECORD_ACTION.matcher(path);
        if (action.matches()) {
            if (!"POST".equalsIgnoreCase(method)
                    || !positiveLong(action.group(2))) {
                return Optional.empty();
            }
            return Optional.of(new OpenApiRoutePolicy(
                    "POST", path,
                    RECORD_DETAIL_TEMPLATE + ":" + action.group(3),
                    RECORD_WRITE_SCOPE, RUNTIME_PERMISSION));
        }
        var fileCollection = RECORD_FILE_COLLECTION.matcher(path);
        if (fileCollection.matches()) {
            if (!positiveLong(fileCollection.group(2))) {
                return Optional.empty();
            }
            if ("POST".equalsIgnoreCase(method)) {
                return Optional.of(new OpenApiRoutePolicy(
                        "POST", path, RECORD_FILE_COLLECTION_TEMPLATE,
                        FILE_WRITE_SCOPE, RUNTIME_PERMISSION));
            }
            if ("GET".equalsIgnoreCase(method)) {
                return Optional.of(new OpenApiRoutePolicy(
                        "GET", path, RECORD_FILE_COLLECTION_TEMPLATE,
                        FILE_READ_SCOPE, RUNTIME_PERMISSION));
            }
            return Optional.empty();
        }
        var fileContent = RECORD_FILE_CONTENT.matcher(path);
        if (fileContent.matches()) {
            if (!"GET".equalsIgnoreCase(method)
                    || !positiveLong(fileContent.group(2))
                    || !positiveLong(fileContent.group(3))) {
                return Optional.empty();
            }
            return Optional.of(new OpenApiRoutePolicy(
                    "GET", path, RECORD_FILE_CONTENT_TEMPLATE,
                    FILE_READ_SCOPE, RUNTIME_PERMISSION));
        }
        var composition = RECORD_COMPOSITION.matcher(path);
        if (composition.matches()) {
            if (!positiveLong(composition.group(2))) {
                return Optional.empty();
            }
            var template = RECORD_DETAIL_TEMPLATE + "/"
                    + composition.group(3) + "/{fieldCode}";
            if ("GET".equalsIgnoreCase(method)
                    && composition.group(5) == null) {
                return Optional.of(new OpenApiRoutePolicy(
                        "GET", path, template,
                        RECORD_READ_SCOPE, RUNTIME_PERMISSION));
            }
            if ("POST".equalsIgnoreCase(method)
                    && composition.group(5) != null) {
                return Optional.of(new OpenApiRoutePolicy(
                        "POST", path, template + ":mutate",
                        RECORD_WRITE_SCOPE, RUNTIME_PERMISSION));
            }
            return Optional.empty();
        }
        var flowDetail = FLOW_INSTANCE_DETAIL.matcher(path);
        if (flowDetail.matches()) {
            if (!"GET".equalsIgnoreCase(method)
                    || !positiveLong(flowDetail.group(1))) {
                return Optional.empty();
            }
            return Optional.of(new OpenApiRoutePolicy(
                    "GET", path, FLOW_INSTANCE_DETAIL_TEMPLATE,
                    FLOW_INSTANCE_READ_SCOPE, FLOW_INSTANCE_READ_PERMISSION));
        }
        if (!"POST".equalsIgnoreCase(method)) {
            return Optional.empty();
        }
        var flowStart = FLOW_INSTANCE_START.matcher(path);
        if (!flowStart.matches() || !positiveLong(flowStart.group(1))) {
            return Optional.empty();
        }
        return Optional.of(new OpenApiRoutePolicy(
                "POST",
                path,
                FLOW_INSTANCE_START_TEMPLATE,
                FLOW_INSTANCE_START_PERMISSION,
                FLOW_INSTANCE_START_PERMISSION
        ));
    }

    private static boolean positiveLong(String value) {
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
