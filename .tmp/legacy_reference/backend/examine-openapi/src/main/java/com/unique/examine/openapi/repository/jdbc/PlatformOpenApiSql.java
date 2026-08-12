package com.unique.examine.openapi.repository.jdbc;

final class PlatformOpenApiSql {
    private PlatformOpenApiSql() {}
    static final String COLUMNS = """
        a.id,a.service_account_id,a.app_key,a.name,a.status,a.scopes_json,a.ip_allowlist_json,
        a.rate_limit_per_minute,a.current_credential_version,a.created_at,a.created_by,
        a.updated_at,a.updated_by,a.version,c.id credential_id,c.credential_version,c.secret_ref,
        c.status credential_status,c.activated_at,c.revoked_at,c.created_at credential_created_at,
        c.created_by credential_created_by
        """;
    static final String BY_KEY = "SELECT "+COLUMNS+" FROM un_platform_openapi_application a JOIN un_platform_openapi_credential c ON c.application_id=a.id AND c.credential_version=a.current_credential_version WHERE a.app_key=?";
    static final String BY_ID = "SELECT "+COLUMNS+" FROM un_platform_openapi_application a JOIN un_platform_openapi_credential c ON c.application_id=a.id AND c.credential_version=a.current_credential_version WHERE a.id=?";
    static final String LIST = "SELECT "+COLUMNS+" FROM un_platform_openapi_application a JOIN un_platform_openapi_credential c ON c.application_id=a.id AND c.credential_version=a.current_credential_version ORDER BY a.updated_at DESC,a.id DESC LIMIT ? OFFSET ?";
}
