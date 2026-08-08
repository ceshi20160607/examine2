package com.unique.examine.core.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcJobStore implements JobStore {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcJobStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void insert(DurableJobFacade.JobRecord job) {
        jdbc.update("INSERT INTO un_sys_job (id,job_type,owner_type,owner_id,system_id,tenant_id,requested_by,"
                        + "status,progress_percent,input_json,result_json,result_file_id,attempt_count,max_attempts,"
                        + "available_at,lease_until,last_error,started_at,finished_at,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,?,?,'QUEUED',0,?,NULL,NULL,0,?,?,NULL,NULL,NULL,NULL,?,?,0)",
                job.id(), job.jobType(), job.ownerType(), job.ownerId(), job.systemId(), job.tenantId(),
                job.requestedBy(), write(job.input()), job.maxAttempts(), job.availableAt(),
                job.createdAt(), job.updatedAt());
    }

    @Override
    public List<DurableJobFacade.JobRecord> claimCandidates(String jobType, LocalDateTime now, int limit) {
        return jdbc.query("SELECT * FROM un_sys_job WHERE job_type=? AND ((status='QUEUED' AND available_at<=?) "
                        + "OR (status='RUNNING' AND lease_until<?)) ORDER BY available_at,id LIMIT ?",
                (result, row) -> map(result), jobType, now, now, limit);
    }

    @Override
    public Optional<DurableJobFacade.JobRecord> claim(
            long jobId, long expectedVersion, LocalDateTime now, LocalDateTime leaseUntil) {
        var changed = jdbc.update("UPDATE un_sys_job SET status='RUNNING',progress_percent=GREATEST(progress_percent,1),"
                        + "attempt_count=attempt_count+1,lease_until=?,started_at=COALESCE(started_at,?),"
                        + "last_error=NULL,updated_at=?,version=version+1 WHERE id=? AND version=? "
                        + "AND attempt_count<max_attempts AND ((status='QUEUED' AND available_at<=?) "
                        + "OR (status='RUNNING' AND lease_until<?))",
                leaseUntil, now, now, jobId, expectedVersion, now, now);
        return changed == 1 ? find(jobId) : Optional.empty();
    }

    @Override
    public Optional<DurableJobFacade.JobRecord> finish(
            long jobId, long claimVersion, String status, int progressPercent,
            String resultJson, String error, LocalDateTime availableAt,
            LocalDateTime finishedAt, LocalDateTime now) {
        var changed = jdbc.update("UPDATE un_sys_job SET status=?,progress_percent=?,result_json=?,last_error=?,"
                        + "available_at=?,lease_until=NULL,finished_at=?,updated_at=?,version=version+1 "
                        + "WHERE id=? AND status='RUNNING' AND version=?",
                status, progressPercent, resultJson, error, availableAt, finishedAt, now, jobId, claimVersion);
        return changed == 1 ? find(jobId) : Optional.empty();
    }

    @Override
    public Optional<DurableJobFacade.JobRecord> find(long jobId) {
        var rows = jdbc.query("SELECT * FROM un_sys_job WHERE id=?", (result, row) -> map(result), jobId);
        return rows.stream().findFirst();
    }

    private DurableJobFacade.JobRecord map(ResultSet result) throws SQLException {
        return new DurableJobFacade.JobRecord(
                result.getLong("id"), result.getString("job_type"), result.getString("owner_type"),
                result.getString("owner_id"), nullableLong(result, "system_id"), nullableLong(result, "tenant_id"),
                nullableLong(result, "requested_by"), result.getString("status"),
                result.getInt("progress_percent"), read(result.getString("input_json")),
                read(result.getString("result_json")), result.getInt("attempt_count"),
                result.getInt("max_attempts"), result.getObject("available_at", LocalDateTime.class),
                result.getObject("lease_until", LocalDateTime.class), result.getString("last_error"),
                result.getObject("started_at", LocalDateTime.class),
                result.getObject("finished_at", LocalDateTime.class),
                result.getObject("created_at", LocalDateTime.class),
                result.getObject("updated_at", LocalDateTime.class), result.getLong("version"));
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        var value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return Map.copyOf(mapper.readValue(json, MAP));
        } catch (Exception exception) {
            throw new IllegalStateException("Durable job JSON is invalid", exception);
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Durable job input must be JSON serializable", exception);
        }
    }
}
