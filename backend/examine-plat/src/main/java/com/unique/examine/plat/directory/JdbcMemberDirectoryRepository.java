package com.unique.examine.plat.directory;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class JdbcMemberDirectoryRepository implements MemberDirectoryRepository {
    private static final String FILTER = """
            FROM un_plat_member_tenant mt
            INNER JOIN un_plat_member m
                    ON m.system_id = mt.system_id
                   AND m.id = mt.member_id
            WHERE mt.system_id = :systemId
              AND mt.tenant_id = :tenantId
              AND mt.status = 'ACTIVE'
              AND mt.deleted_at IS NULL
              AND (mt.expires_at IS NULL OR mt.expires_at > CURRENT_TIMESTAMP(3))
              AND m.status = 'ACTIVE'
              AND m.deleted_at IS NULL
              AND (
                    :keywordPattern = ''
                    OR LOWER(m.member_code) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(m.display_name) LIKE :keywordPattern ESCAPE '!'
              )
            """;
    private static final String COUNT_SQL = "SELECT COUNT(*) " + FILTER;
    private static final String FIND_SQL = """
            SELECT m.id, m.member_code, m.display_name
            """ + FILTER + """
            ORDER BY m.display_name ASC, m.id ASC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcOperations jdbc;

    JdbcMemberDirectoryRepository(NamedParameterJdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long count(long systemId, long tenantId, String keyword) {
        var count = jdbc.queryForObject(COUNT_SQL, parameters(systemId, tenantId, keyword), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<MemberDirectoryApi.Member> find(
            long systemId,
            long tenantId,
            String keyword,
            long offset,
            int limit
    ) {
        var parameters = parameters(systemId, tenantId, keyword)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbc.query(FIND_SQL, parameters, (resultSet, rowNumber) ->
                new MemberDirectoryApi.Member(
                        Long.toString(resultSet.getLong("id")),
                        resultSet.getString("member_code"),
                        resultSet.getString("display_name")
                ));
    }

    private static MapSqlParameterSource parameters(long systemId, long tenantId, String keyword) {
        return new MapSqlParameterSource()
                .addValue("systemId", systemId)
                .addValue("tenantId", tenantId)
                .addValue("keywordPattern", keyword.isEmpty() ? "" : "%" + escapeLike(keyword) + "%");
    }

    static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
