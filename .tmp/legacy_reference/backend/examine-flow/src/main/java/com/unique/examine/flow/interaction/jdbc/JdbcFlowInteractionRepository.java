package com.unique.examine.flow.interaction.jdbc;

import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.interaction.FlowComment;
import com.unique.examine.flow.interaction.FlowCopy;
import com.unique.examine.flow.interaction.FlowInteractionRepository;
import com.unique.examine.flow.interaction.FlowUrge;
import com.unique.examine.flow.repository.jdbc.FlowTenantScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.PERSISTENCE_CONFLICT;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COPY_ALREADY_EXISTS;

public final class JdbcFlowInteractionRepository implements FlowInteractionRepository {
    private final JdbcTemplate jdbc;
    private final FlowTenantScope scope;

    public JdbcFlowInteractionRepository(JdbcTemplate jdbc, FlowTenantScope scope) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    @Override
    public FlowUrge saveUrge(FlowUrge urge) {
        try {
            var inserted = jdbc.update(
                    JdbcFlowInteractionSql.INSERT_URGE,
                    scope.systemId(), scope.tenantId(), urge.id(),
                    urge.actorId(), urge.recipientId(), urge.message(), Timestamp.from(urge.createdAt()),
                    scope.systemId(), scope.tenantId(), urge.instanceId(),
                    urge.actorId(), urge.recipientId()
            );
            if (inserted != 1) {
                throw new ApprovalDomainException(
                        INSTANCE_STATE_INVALID,
                        "The Flow instance is no longer pending for the expected requester and approver"
                );
            }
            return urge;
        } catch (DuplicateKeyException exception) {
            throw conflict("Flow urge ids are immutable");
        }
    }

    @Override
    public List<FlowUrge> findUrges(long instanceId, int offset, int limit) {
        return List.copyOf(jdbc.query(
                JdbcFlowInteractionSql.SELECT_URGES,
                (result, row) -> new FlowUrge(
                        result.getLong("urge_id"),
                        result.getLong("instance_id"),
                        result.getLong("actor_id"),
                        result.getLong("recipient_id"),
                        result.getString("message"),
                        result.getTimestamp("created_at").toInstant()
                ),
                scope.systemId(), scope.tenantId(), instanceId, limit, offset
        ));
    }

    @Override
    public long countUrges(long instanceId) {
        return count(JdbcFlowInteractionSql.COUNT_URGES, instanceId);
    }

    @Override
    public FlowComment saveComment(FlowComment comment) {
        try {
            jdbc.update(
                    JdbcFlowInteractionSql.INSERT_COMMENT,
                    scope.systemId(), scope.tenantId(), comment.id(), comment.instanceId(),
                    comment.authorId(), comment.body(), Timestamp.from(comment.createdAt())
            );
            return comment;
        } catch (DuplicateKeyException exception) {
            throw conflict("Flow comment ids are immutable");
        }
    }

    @Override
    public List<FlowComment> findComments(long instanceId, int offset, int limit) {
        return List.copyOf(jdbc.query(
                JdbcFlowInteractionSql.SELECT_COMMENTS,
                (result, row) -> new FlowComment(
                        result.getLong("comment_id"),
                        result.getLong("instance_id"),
                        result.getLong("author_id"),
                        result.getString("body"),
                        result.getTimestamp("created_at").toInstant()
                ),
                scope.systemId(), scope.tenantId(), instanceId, limit, offset
        ));
    }

    @Override
    public long countComments(long instanceId) {
        return count(JdbcFlowInteractionSql.COUNT_COMMENTS, instanceId);
    }

    @Override
    public FlowCopy saveCopy(FlowCopy copy) {
        try {
            jdbc.update(
                    JdbcFlowInteractionSql.INSERT_COPY,
                    scope.systemId(), scope.tenantId(), copy.id(), copy.instanceId(),
                    copy.actorId(), copy.recipientId(), copy.message(),
                    Timestamp.from(copy.createdAt())
            );
            return copy;
        } catch (DuplicateKeyException exception) {
            throw new ApprovalDomainException(
                    COPY_ALREADY_EXISTS,
                    "The member is already copied on this Flow instance"
            );
        }
    }

    @Override
    public List<FlowCopy> findCopies(long instanceId, int offset, int limit) {
        return List.copyOf(jdbc.query(
                JdbcFlowInteractionSql.SELECT_COPIES,
                (result, row) -> new FlowCopy(
                        result.getLong("copy_id"),
                        result.getLong("instance_id"),
                        result.getLong("actor_id"),
                        result.getLong("recipient_id"),
                        result.getString("message"),
                        result.getTimestamp("created_at").toInstant()
                ),
                scope.systemId(), scope.tenantId(), instanceId, limit, offset
        ));
    }

    @Override
    public long countCopies(long instanceId) {
        return count(JdbcFlowInteractionSql.COUNT_COPIES, instanceId);
    }

    private long count(String sql, long instanceId) {
        var value = jdbc.queryForObject(
                sql,
                Long.class,
                scope.systemId(), scope.tenantId(), instanceId
        );
        if (value == null || value < 0) {
            throw conflict("Scoped Flow interaction count is invalid");
        }
        return value;
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(PERSISTENCE_CONFLICT, message);
    }
}
