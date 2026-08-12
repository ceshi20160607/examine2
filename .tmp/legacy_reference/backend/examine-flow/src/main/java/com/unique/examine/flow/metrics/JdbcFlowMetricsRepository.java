package com.unique.examine.flow.metrics;

import com.unique.examine.flow.domain.ApprovalInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public final class JdbcFlowMetricsRepository implements FlowMetricsRepository {
    static final String FIND_CURRENT_FACTS = """
            SELECT status,started_at,completed_at
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
              AND (status='PENDING'
                OR (started_at>=? AND started_at<?)
                OR (status IN ('APPROVED','REJECTED','WITHDRAWN','TERMINATED')
                  AND completed_at>=? AND completed_at<?))
            """;
    static final RowMapper<FlowMetricInstance> ROW_MAPPER = (result, row) ->
            new FlowMetricInstance(
                    ApprovalInstance.Status.valueOf(result.getString("status")),
                    result.getTimestamp("started_at").toInstant(),
                    result.getTimestamp("completed_at") == null ? null
                            : result.getTimestamp("completed_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcFlowMetricsRepository(JdbcTemplate jdbc) {
        if (jdbc == null) throw new IllegalArgumentException("JdbcTemplate is required");
        this.jdbc = jdbc;
    }

    @Override
    public List<FlowMetricInstance> findCurrentFacts(
            long systemId,
            long tenantId,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        if (systemId <= 0 || tenantId <= 0 || fromInclusive == null || toExclusive == null
                || !toExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("Flow metrics query scope is invalid");
        }
        return jdbc.query(FIND_CURRENT_FACTS, ROW_MAPPER,
                systemId, tenantId,
                Timestamp.from(fromInclusive), Timestamp.from(toExclusive),
                Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
    }
}
