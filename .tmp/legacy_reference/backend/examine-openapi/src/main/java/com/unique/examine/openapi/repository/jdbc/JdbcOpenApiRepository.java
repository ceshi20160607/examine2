package com.unique.examine.openapi.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.OpenApiRateBucket;
import com.unique.examine.openapi.repository.OpenApiRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class JdbcOpenApiRepository implements OpenApiRepository {
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<ApplicationBundle> bundleMapper = this::bundle;

    public JdbcOpenApiRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public Optional<ApplicationBundle> findByAppKey(String appKey) {
        return one(OpenApiSql.SELECT_BY_APP_KEY, appKey);
    }

    @Override
    public Optional<ApplicationBundle> find(long systemId, long tenantId, long applicationId) {
        return one(OpenApiSql.SELECT_APPLICATION, systemId, tenantId, applicationId);
    }

    @Override
    public List<ApplicationBundle> list(long systemId, long tenantId, int offset, int limit) {
        return List.copyOf(jdbc.query(
                OpenApiSql.SELECT_APPLICATIONS,
                bundleMapper,
                systemId,
                tenantId,
                limit,
                offset
        ));
    }

    @Override
    public long count(long systemId, long tenantId) {
        var value = jdbc.queryForObject(
                OpenApiSql.COUNT_APPLICATIONS,
                Long.class,
                systemId,
                tenantId
        );
        return Objects.requireNonNullElse(value, 0L);
    }

    @Override
    public void insertApplication(OpenApiApplication value) {
        jdbc.update(
                OpenApiSql.INSERT_APPLICATION,
                value.id(),
                value.systemId(),
                value.tenantId(),
                value.serviceMemberId(),
                value.appKey(),
                value.name(),
                value.status().name(),
                write(new TreeSet<>(value.scopes())),
                write(value.ipAllowlist()),
                value.rateLimitPerMinute(),
                value.currentCredentialVersion(),
                timestamp(value.createdAt()),
                value.createdBy(),
                timestamp(value.updatedAt()),
                value.updatedBy(),
                value.version()
        );
    }

    @Override
    public void insertCredential(OpenApiCredential value) {
        jdbc.update(
                OpenApiSql.INSERT_CREDENTIAL,
                value.id(),
                value.applicationId(),
                value.credentialVersion(),
                value.secretRef(),
                value.status().name(),
                timestamp(value.activatedAt()),
                timestamp(value.revokedAt()),
                timestamp(value.createdAt()),
                value.createdBy()
        );
    }

    @Override
    public boolean updatePolicy(OpenApiApplication value, long expectedVersion) {
        return jdbc.update(
                OpenApiSql.UPDATE_POLICY,
                value.serviceMemberId(),
                value.name(),
                write(new TreeSet<>(value.scopes())),
                write(value.ipAllowlist()),
                value.rateLimitPerMinute(),
                timestamp(value.updatedAt()),
                value.updatedBy(),
                value.systemId(),
                value.tenantId(),
                value.id(),
                expectedVersion
        ) == 1;
    }

    @Override
    public boolean updateStatus(
            long systemId,
            long tenantId,
            long applicationId,
            OpenApiApplication.Status status,
            long actorId,
            Instant updatedAt,
            long expectedVersion
    ) {
        return jdbc.update(
                OpenApiSql.UPDATE_STATUS,
                status.name(),
                timestamp(updatedAt),
                actorId,
                systemId,
                tenantId,
                applicationId,
                expectedVersion
        ) == 1;
    }

    @Override
    @Transactional
    public boolean rotateCredential(
            OpenApiApplication application,
            OpenApiCredential previous,
            OpenApiCredential replacement,
            long expectedVersion
    ) {
        var applicationUpdated = jdbc.update(
                OpenApiSql.UPDATE_CURRENT_CREDENTIAL,
                replacement.credentialVersion(),
                timestamp(application.updatedAt()),
                application.updatedBy(),
                application.systemId(),
                application.tenantId(),
                application.id(),
                expectedVersion
        );
        if (applicationUpdated != 1) {
            return false;
        }
        var revoked = jdbc.update(
                OpenApiSql.REVOKE_CREDENTIAL,
                timestamp(previous.revokedAt()),
                previous.applicationId(),
                previous.credentialVersion()
        );
        if (revoked != 1) {
            throw new IllegalStateException("Current OpenAPI credential changed concurrently");
        }
        insertCredential(replacement);
        return true;
    }

    @Override
    public boolean consumeNonce(
            long applicationId,
            int credentialVersion,
            String nonce,
            Instant expiresAt,
            Instant createdAt
    ) {
        jdbc.update(OpenApiSql.DELETE_EXPIRED_NONCES, applicationId, timestamp(createdAt));
        try {
            jdbc.update(
                    OpenApiSql.INSERT_NONCE,
                    applicationId,
                    credentialVersion,
                    nonce,
                    timestamp(expiresAt),
                    timestamp(createdAt)
            );
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public OpenApiRateBucket lockRateBucket(long applicationId, Instant windowStart) {
        jdbc.update(OpenApiSql.INSERT_RATE_BUCKET, applicationId, timestamp(windowStart));
        return jdbc.queryForObject(
                OpenApiSql.SELECT_RATE_BUCKET_FOR_UPDATE,
                (result, row) -> new OpenApiRateBucket(
                        result.getLong("application_id"),
                        result.getTimestamp("window_start").toInstant(),
                        result.getInt("request_count"),
                        result.getLong("version")
                ),
                applicationId,
                timestamp(windowStart)
        );
    }

    @Override
    public boolean incrementRateBucket(
            long applicationId,
            Instant windowStart,
            int expectedCount,
            long expectedVersion
    ) {
        return jdbc.update(
                OpenApiSql.INCREMENT_RATE_BUCKET,
                applicationId,
                timestamp(windowStart),
                expectedCount,
                expectedVersion
        ) == 1;
    }

    @Override
    public void insertCallLog(OpenApiCallLog value) {
        jdbc.update(
                OpenApiSql.INSERT_CALL_LOG,
                value.id(),
                value.applicationId(),
                value.appKeyHash(),
                value.credentialVersion(),
                value.routeTemplate(),
                value.requestMethod(),
                value.resultCategory().name(),
                value.httpStatus(),
                value.latencyMs(),
                value.requestId(),
                value.traceId(),
                ipBytes(value.observedIp()),
                timestamp(value.createdAt())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countApplicationCallLogs(
            long applicationId,
            CallLogResultFilter resultCategory,
            CallLogMethodFilter requestMethod) {
        requireCallLogQuery(applicationId, resultCategory, requestMethod);
        var total = jdbc.queryForObject(
                OpenApiSql.COUNT_APPLICATION_CALL_LOGS,
                Long.class,
                applicationId,
                resultCategory.name(),
                resultCategory.name(),
                requestMethod.name(),
                requestMethod.name());
        if (total == null || total < 0) {
            throw new IllegalStateException("OpenAPI call-log count is invalid");
        }
        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationCallLog> listApplicationCallLogs(
            long applicationId,
            CallLogResultFilter resultCategory,
            CallLogMethodFilter requestMethod,
            int offset,
            int limit) {
        requireCallLogQuery(applicationId, resultCategory, requestMethod);
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("OpenAPI call-log page is invalid");
        }
        return List.copyOf(jdbc.query(
                OpenApiSql.SELECT_APPLICATION_CALL_LOGS,
                this::callLog,
                applicationId,
                resultCategory.name(),
                resultCategory.name(),
                requestMethod.name(),
                requestMethod.name(),
                limit,
                offset));
    }

    private Optional<ApplicationBundle> one(String sql, Object... arguments) {
        var values = jdbc.query(sql, bundleMapper, arguments);
        if (values.size() > 1) {
            throw new IllegalStateException("Scoped OpenAPI query returned multiple applications");
        }
        return values.stream().findFirst();
    }

    private ApplicationBundle bundle(ResultSet result, int row) throws SQLException {
        var application = new OpenApiApplication(
                result.getLong("id"),
                result.getLong("system_id"),
                result.getLong("tenant_id"),
                result.getLong("service_member_id"),
                result.getString("app_key"),
                result.getString("name"),
                OpenApiApplication.Status.valueOf(result.getString("status")),
                readSet(result.getString("scopes_json")),
                readList(result.getString("ip_allowlist_json")),
                result.getInt("rate_limit_per_minute"),
                result.getInt("current_credential_version"),
                result.getTimestamp("created_at").toInstant(),
                result.getLong("created_by"),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("updated_by"),
                result.getLong("version")
        );
        var revokedAt = result.getTimestamp("revoked_at");
        var credential = new OpenApiCredential(
                result.getLong("credential_id"),
                application.id(),
                result.getInt("credential_version"),
                result.getString("secret_ref"),
                OpenApiCredential.Status.valueOf(result.getString("credential_status")),
                result.getTimestamp("activated_at").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                result.getTimestamp("credential_created_at").toInstant(),
                result.getLong("credential_created_by")
        );
        return new ApplicationBundle(application, credential);
    }

    private ApplicationCallLog callLog(ResultSet result, int row)
            throws SQLException {
        var credentialVersion = result.getObject("credential_version", Integer.class);
        return new ApplicationCallLog(
                result.getLong("id"),
                credentialVersion,
                result.getString("route_template"),
                result.getString("request_method"),
                OpenApiCallLog.ResultCategory.valueOf(
                        result.getString("result_category")),
                result.getInt("http_status"),
                result.getLong("latency_ms"),
                result.getString("request_id"),
                result.getString("trace_id"),
                ipText(result.getBytes("observed_ip")),
                result.getTimestamp("created_at").toInstant());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("OpenAPI policy must be JSON serializable", exception);
        }
    }

    private Set<String> readSet(String value) {
        try {
            return json.readValue(value, STRING_SET);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored OpenAPI scopes are invalid", exception);
        }
    }

    private List<String> readList(String value) {
        try {
            return json.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored OpenAPI IP allowlist is invalid", exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    static byte[] ipBytes(String value) {
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Observed OpenAPI IP is invalid", exception);
        }
    }

    static String ipText(byte[] value) {
        if (value == null) {
            throw new IllegalStateException("Stored OpenAPI IP is missing");
        }
        if (value.length == 4) {
            return (value[0] & 0xff) + "." + (value[1] & 0xff) + "."
                    + (value[2] & 0xff) + "." + (value[3] & 0xff);
        }
        if (value.length != 16) {
            throw new IllegalStateException("Stored OpenAPI IP is invalid");
        }
        var words = new int[8];
        for (var index = 0; index < words.length; index++) {
            words[index] = (value[index * 2] & 0xff) << 8
                    | value[index * 2 + 1] & 0xff;
        }
        var bestStart = -1;
        var bestLength = 0;
        for (var index = 0; index < words.length;) {
            if (words[index] != 0) {
                index++;
                continue;
            }
            var start = index;
            while (index < words.length && words[index] == 0) index++;
            var length = index - start;
            if (length >= 2 && length > bestLength) {
                bestStart = start;
                bestLength = length;
            }
        }
        var result = new StringBuilder(39);
        for (var index = 0; index < words.length;) {
            if (index == bestStart) {
                result.append("::");
                index += bestLength;
                continue;
            }
            if (!result.isEmpty() && result.charAt(result.length() - 1) != ':') {
                result.append(':');
            }
            result.append(Integer.toHexString(words[index]));
            index++;
        }
        return result.toString();
    }

    private static void requireCallLogQuery(
            long applicationId,
            CallLogResultFilter resultCategory,
            CallLogMethodFilter requestMethod) {
        if (applicationId <= 0 || resultCategory == null || requestMethod == null) {
            throw new IllegalArgumentException("OpenAPI call-log query is invalid");
        }
    }
}
