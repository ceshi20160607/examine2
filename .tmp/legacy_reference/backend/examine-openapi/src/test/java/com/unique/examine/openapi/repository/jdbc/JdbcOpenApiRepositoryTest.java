package com.unique.examine.openapi.repository.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcOpenApiRepositoryTest {
    @Test
    void encodesIpv4AsFourDatabaseBytes() {
        assertThat(JdbcOpenApiRepository.ipBytes("127.0.0.1"))
                .containsExactly(127, 0, 0, 1)
                .hasSize(4);
    }

    @Test
    void encodesIpv6AsSixteenDatabaseBytes() {
        assertThat(JdbcOpenApiRepository.ipBytes("2001:db8::1"))
                .hasSize(16)
                .containsExactly(
                        (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                        (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                        (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                        (byte) 0, (byte) 0, (byte) 0, (byte) 1
                );
    }

    @Test
    void rejectsUnparseableObservedIpBeforeSqlBinding() {
        assertThatThrownBy(() -> JdbcOpenApiRepository.ipBytes("not an ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Observed OpenAPI IP is invalid");
    }

    @Test
    void decodesDatabaseBytesAsCanonicalIpv4AndIpv6Text() {
        assertThat(JdbcOpenApiRepository.ipText(new byte[]{
                (byte) 192, 0, 2, 10})).isEqualTo("192.0.2.10");
        assertThat(JdbcOpenApiRepository.ipText(new byte[]{
                0x20, 0x01, 0x0d, (byte) 0xb8,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        })).isEqualTo("2001:db8::1");
        assertThat(JdbcOpenApiRepository.ipText(new byte[16]))
                .isEqualTo("::");
        assertThatThrownBy(() -> JdbcOpenApiRepository.ipText(new byte[8]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stored OpenAPI IP is invalid");
    }

    @Test
    void callLogPageSqlIsSafeBoundedApplicationScopedAndNewestFirst() {
        var page = normalize(OpenApiSql.SELECT_APPLICATION_CALL_LOGS);
        var count = normalize(OpenApiSql.COUNT_APPLICATION_CALL_LOGS);

        assertThat(page)
                .startsWith("select id,credential_version,route_template,request_method,")
                .contains(
                        "result_category,http_status,latency_ms,request_id,trace_id, observed_ip,created_at",
                        "where application_id=?",
                        "(?='all' or result_category=?)",
                        "(?='all' or request_method=?)",
                        "order by created_at desc,id desc",
                        "limit ? offset ?")
                .doesNotContain(
                        "app_key_hash", "app_key", "secret_ref", "signature",
                        "nonce", "request_body", "query_string", "headers",
                        "response_body", "system_id", "tenant_id", "member_id")
                .doesNotContain("insert ", "update ", "delete ");
        assertThat(count)
                .contains(
                        "from un_openapi_call_log where application_id=?",
                        "(?='all' or result_category=?)",
                        "(?='all' or request_method=?)")
                .doesNotContain("app_key_hash", "system_id", "tenant_id")
                .doesNotContain("insert ", "update ", "delete ");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
