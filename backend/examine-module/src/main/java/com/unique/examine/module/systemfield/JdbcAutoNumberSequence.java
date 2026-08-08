package com.unique.examine.module.systemfield;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
public final class JdbcAutoNumberSequence implements AutoNumberSequence {
    private final JdbcTemplate jdbc;

    public JdbcAutoNumberSequence(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public long next(long systemId, long tenantId, long moduleId, long fieldId) {
        var now = LocalDateTime.now();
        jdbc.update("INSERT IGNORE INTO un_module_auto_number_sequence "
                        + "(system_id,tenant_id,logical_module_id,logical_field_id,next_value,created_at,updated_at) "
                        + "VALUES (?,?,?,?,0,?,?)",
                systemId, tenantId, moduleId, fieldId, now, now);
        var current = jdbc.queryForObject("SELECT next_value FROM un_module_auto_number_sequence "
                        + "WHERE system_id=? AND tenant_id=? AND logical_module_id=? AND logical_field_id=? FOR UPDATE",
                Long.class, systemId, tenantId, moduleId, fieldId);
        if (current == null || current == Long.MAX_VALUE) {
            throw new IllegalStateException("Auto-number sequence is exhausted");
        }
        var allocated = current + 1;
        var updated = jdbc.update("UPDATE un_module_auto_number_sequence SET next_value=?,updated_at=? "
                        + "WHERE system_id=? AND tenant_id=? AND logical_module_id=? AND logical_field_id=? "
                        + "AND next_value=?",
                allocated, now, systemId, tenantId, moduleId, fieldId, current);
        if (updated != 1) {
            throw new IllegalStateException("Auto-number sequence allocation lost its row lock");
        }
        return allocated;
    }
}
