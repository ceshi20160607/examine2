package com.unique.examine.plat.directory;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemberDirectoryRepositoryTest {
    @Test
    void scopesBothQueriesToActiveTenantMembershipAndActiveMember() {
        var calls = new ArrayList<JdbcCall>();
        var jdbc = (NamedParameterJdbcOperations) Proxy.newProxyInstance(
                NamedParameterJdbcOperations.class.getClassLoader(),
                new Class<?>[]{NamedParameterJdbcOperations.class},
                (proxy, method, arguments) -> {
                    if ("queryForObject".equals(method.getName())) {
                        calls.add(call(arguments));
                        return 2L;
                    }
                    if ("query".equals(method.getName())) {
                        calls.add(call(arguments));
                        return List.of();
                    }
                    throw new AssertionError("Unexpected JDBC method: " + method.getName());
                }
        );
        var repository = new JdbcMemberDirectoryRepository(jdbc);

        assertThat(repository.count(10, 20, "a_%!")).isEqualTo(2);
        assertThat(repository.find(10, 20, "a_%!", 50, 25)).isEmpty();

        assertThat(calls).hasSize(2);
        for (var call : calls) {
            assertThat(call.sql)
                    .contains("mt.system_id = :systemId")
                    .contains("mt.tenant_id = :tenantId")
                    .contains("mt.status = 'ACTIVE'")
                    .contains("mt.deleted_at IS NULL")
                    .contains("mt.expires_at IS NULL OR mt.expires_at > CURRENT_TIMESTAMP(3)")
                    .contains("m.status = 'ACTIVE'")
                    .contains("m.deleted_at IS NULL")
                    .contains("LOWER(m.member_code) LIKE :keywordPattern")
                    .contains("LOWER(m.display_name) LIKE :keywordPattern");
            assertThat(call.parameters.getValue("systemId")).isEqualTo(10L);
            assertThat(call.parameters.getValue("tenantId")).isEqualTo(20L);
            assertThat(call.parameters.getValue("keywordPattern")).isEqualTo("%a!_!%!!%");
        }

        var find = calls.get(1);
        assertThat(find.sql)
                .contains("ORDER BY m.display_name ASC, m.id ASC")
                .contains("LIMIT :limit OFFSET :offset");
        assertThat(find.parameters.getValue("offset")).isEqualTo(50L);
        assertThat(find.parameters.getValue("limit")).isEqualTo(25);
    }

    @Test
    void escapesSqlLikeWildcardsAsLiteralKeywordCharacters() {
        assertThat(JdbcMemberDirectoryRepository.escapeLike("100%_done!"))
                .isEqualTo("100!%!_done!!");
    }

    private static JdbcCall call(Object[] arguments) {
        return new JdbcCall(
                (String) arguments[0],
                (SqlParameterSource) arguments[1]
        );
    }

    private record JdbcCall(String sql, SqlParameterSource parameters) {
    }
}
