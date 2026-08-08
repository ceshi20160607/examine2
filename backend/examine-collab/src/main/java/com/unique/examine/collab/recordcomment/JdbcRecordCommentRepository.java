package com.unique.examine.collab.recordcomment;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

public final class JdbcRecordCommentRepository implements RecordCommentRepository {
    static final String FIND_PAGE_SQL = """
            SELECT comment_id, system_id, tenant_id, record_id, parent_comment_id,
                   author_member_id, body, deleted, version, created_at, updated_at
              FROM un_collab_record_comment
             WHERE system_id = ? AND tenant_id = ? AND record_id = ?
             ORDER BY created_at ASC, comment_id ASC
             LIMIT ? OFFSET ?
            """;
    static final String COUNT_SQL = """
            SELECT COUNT(*)
              FROM un_collab_record_comment
             WHERE system_id = ? AND tenant_id = ? AND record_id = ?
            """;
    static final String FIND_SQL = """
            SELECT comment_id, system_id, tenant_id, record_id, parent_comment_id,
                   author_member_id, body, deleted, version, created_at, updated_at
              FROM un_collab_record_comment
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND comment_id = ?
            """;
    static final String FIND_PARENT_SQL = """
            SELECT parent_comment_id
              FROM un_collab_record_comment
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND comment_id = ?
            """;
    static final String FIND_IDEMPOTENT_SQL = """
            SELECT comment_id, system_id, tenant_id, record_id, parent_comment_id,
                   author_member_id, body, deleted, version, created_at, updated_at,
                   request_hash
              FROM un_collab_record_comment
             WHERE system_id = ? AND tenant_id = ? AND record_id = ?
               AND author_member_id = ? AND idempotency_key = ?
            """;
    static final String INSERT_SQL = """
            INSERT INTO un_collab_record_comment (
                system_id, tenant_id, record_id, parent_comment_id,
                author_member_id, body, deleted, version,
                idempotency_key, request_hash,
                created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, 0, 1, ?, ?,
                      CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3), ?)
            """;
    static final String FIND_MENTIONS_SQL = """
            SELECT mentioned_member_id
              FROM un_collab_record_comment_mention
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND comment_id = ?
             ORDER BY mentioned_member_id
            """;
    static final String INSERT_MENTION_SQL = """
            INSERT INTO un_collab_record_comment_mention (
                system_id, tenant_id, record_id, comment_id,
                mentioned_member_id, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), ?)
            """;
    static final String UPDATE_SQL = """
            UPDATE un_collab_record_comment
               SET body = ?, version = version + 1,
                   updated_at = CURRENT_TIMESTAMP(3), updated_by = ?
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND comment_id = ?
               AND deleted = 0 AND version = ?
            """;
    static final String TOMBSTONE_SQL = """
            UPDATE un_collab_record_comment
               SET body = NULL, deleted = 1, version = version + 1,
                   deleted_at = CURRENT_TIMESTAMP(3), deleted_by = ?,
                   updated_at = CURRENT_TIMESTAMP(3), updated_by = ?
             WHERE system_id = ? AND tenant_id = ? AND record_id = ? AND comment_id = ?
               AND deleted = 0 AND version = ?
            """;

    private static final RowMapper<RecordComment> COMMENT_MAPPER =
            JdbcRecordCommentRepository::mapComment;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcRecordCommentRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public RecordCommentPage findPage(RecordCommentKey key, int page, int size) {
        Objects.requireNonNull(key, "key");
        RecordCommentPage.validate(page, size);
        var total = jdbc.queryForObject(
                COUNT_SQL,
                Long.class,
                key.systemId(),
                key.tenantId(),
                key.recordId());
        var offset = Math.multiplyExact((long) page - 1, size);
        var items = jdbc.query(
                FIND_PAGE_SQL,
                COMMENT_MAPPER,
                key.systemId(),
                key.tenantId(),
                key.recordId(),
                size,
                offset).stream().map(this::withMentions).toList();
        return new RecordCommentPage(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<RecordComment> find(RecordCommentKey key, String commentId) {
        Objects.requireNonNull(key, "key");
        var normalizedId = RecordComment.requireId(commentId, "commentId");
        return jdbc.query(
                        FIND_SQL,
                        COMMENT_MAPPER,
                        key.systemId(),
                        key.tenantId(),
                        key.recordId(),
                        normalizedId)
                .stream()
                .findFirst()
                .map(this::withMentions);
    }

    @Override
    public RecordCommentCreation create(RecordCommentCreate command) {
        Objects.requireNonNull(command, "command");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return insertIdempotently(command);
        }
        try {
            return Objects.requireNonNull(transaction.execute(status -> insert(command)));
        } catch (DuplicateKeyException exception) {
            return existing(command);
        }
    }

    private RecordCommentCreation insertIdempotently(RecordCommentCreate command) {
        var replay = findIdempotent(command);
        if (replay.isPresent()) return existing(command, replay.get());
        try {
            return insert(command);
        } catch (DuplicateKeyException exception) {
            return existing(command);
        }
    }

    @Override
    public RecordComment update(
            RecordCommentKey key,
            String commentId,
            long expectedVersion,
            String body,
            String actorMemberId
    ) {
        Objects.requireNonNull(key, "key");
        var normalizedId = RecordComment.requireId(commentId, "commentId");
        var normalizedBody = RecordComment.normalizeBody(body);
        var actor = RecordComment.requireId(actorMemberId, "actorMemberId");
        requireVersion(expectedVersion);
        var updated = jdbc.update(
                UPDATE_SQL,
                normalizedBody,
                actor,
                key.systemId(),
                key.tenantId(),
                key.recordId(),
                normalizedId,
                expectedVersion);
        if (updated != 1) {
            throw mutationFailure(key, normalizedId);
        }
        return find(key, normalizedId).orElseThrow(() -> RecordCommentException.conflict(
                "RECORD_COMMENT_PERSISTENCE_INVALID",
                "updated comment is no longer visible"));
    }

    @Override
    public RecordComment tombstone(
            RecordCommentKey key,
            String commentId,
            long expectedVersion,
            String actorMemberId
    ) {
        Objects.requireNonNull(key, "key");
        var normalizedId = RecordComment.requireId(commentId, "commentId");
        var actor = RecordComment.requireId(actorMemberId, "actorMemberId");
        requireVersion(expectedVersion);
        var updated = jdbc.update(
                TOMBSTONE_SQL,
                actor,
                actor,
                key.systemId(),
                key.tenantId(),
                key.recordId(),
                normalizedId,
                expectedVersion);
        if (updated != 1) {
            throw mutationFailure(key, normalizedId);
        }
        return find(key, normalizedId).orElseThrow(() -> RecordCommentException.conflict(
                "RECORD_COMMENT_PERSISTENCE_INVALID",
                "deleted comment is no longer visible"));
    }

    private RecordCommentCreation insert(RecordCommentCreate command) {
        validateParent(command);
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    INSERT_SQL,
                    Statement.RETURN_GENERATED_KEYS);
            var key = command.key();
            statement.setString(1, key.systemId());
            statement.setString(2, key.tenantId());
            statement.setString(3, key.recordId());
            if (command.parentCommentId() == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setString(4, command.parentCommentId());
            }
            statement.setString(5, command.authorMemberId());
            statement.setString(6, command.body());
            statement.setString(7, command.idempotencyKey());
            statement.setString(8, command.requestHash());
            statement.setString(9, command.authorMemberId());
            statement.setString(10, command.authorMemberId());
            return statement;
        }, keyHolder);
        var generated = keyHolder.getKey();
        if (generated == null) {
            throw RecordCommentException.conflict(
                    "RECORD_COMMENT_PERSISTENCE_INVALID",
                    "comment ID was not generated");
        }
        insertMentions(command, generated.toString());
        var comment = find(command.key(), generated.toString()).orElseThrow(
                () -> RecordCommentException.conflict(
                        "RECORD_COMMENT_PERSISTENCE_INVALID",
                        "created comment is not visible"));
        return RecordCommentCreation.created(comment);
    }

    private void insertMentions(RecordCommentCreate command, String commentId) {
        var key = command.key();
        for (var memberId : command.mentionedMemberIds()) {
            jdbc.update(INSERT_MENTION_SQL, key.systemId(), key.tenantId(), key.recordId(),
                    commentId, memberId, command.authorMemberId());
        }
    }

    private void validateParent(RecordCommentCreate command) {
        if (command.parentCommentId() == null) {
            return;
        }
        var key = command.key();
        var parents = jdbc.query(
                FIND_PARENT_SQL,
                (resultSet, rowNumber) -> resultSet.getString("parent_comment_id"),
                key.systemId(),
                key.tenantId(),
                key.recordId(),
                command.parentCommentId());
        if (parents.isEmpty()) {
            throw RecordCommentException.notFound(
                    "RECORD_COMMENT_PARENT_NOT_FOUND",
                    "parent comment does not exist in this record");
        }
        if (parents.getFirst() != null) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_REPLY_DEPTH_EXCEEDED",
                    "replies can only target a top-level comment");
        }
    }

    private Optional<IdempotentRow> findIdempotent(RecordCommentCreate command) {
        var key = command.key();
        return jdbc.query(
                        FIND_IDEMPOTENT_SQL,
                        (resultSet, rowNumber) -> new IdempotentRow(
                                mapComment(resultSet, rowNumber),
                                resultSet.getString("request_hash")),
                        key.systemId(),
                        key.tenantId(),
                        key.recordId(),
                        command.authorMemberId(),
                        command.idempotencyKey())
                .stream()
                .findFirst();
    }

    private RecordCommentCreation existing(RecordCommentCreate command) {
        var existing = findIdempotent(command);
        if (existing.isEmpty()) {
            throw RecordCommentException.conflict(
                    "RECORD_COMMENT_CREATE_CONFLICT",
                    "comment creation conflicted with another persisted row");
        }
        return existing(command, existing.get());
    }

    private RecordCommentCreation existing(RecordCommentCreate command, IdempotentRow existing) {
        if (!existing.requestHash().equals(command.requestHash())) {
            throw RecordCommentException.conflict(
                    "RECORD_COMMENT_IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for a different comment request");
        }
        return RecordCommentCreation.existing(withMentions(existing.comment()));
    }

    private RecordComment withMentions(RecordComment comment) {
        var key = comment.key();
        var memberIds = jdbc.query(FIND_MENTIONS_SQL,
                (result, row) -> result.getString("mentioned_member_id"),
                key.systemId(), key.tenantId(), key.recordId(), comment.commentId());
        return comment.withMentionedMemberIds(memberIds);
    }

    private RecordCommentException mutationFailure(RecordCommentKey key, String commentId) {
        var current = find(key, commentId);
        if (current.isEmpty()) {
            return RecordCommentException.notFound(
                    "RECORD_COMMENT_NOT_FOUND",
                    "comment does not exist in this record");
        }
        if (current.get().deleted()) {
            return RecordCommentException.conflict(
                    "RECORD_COMMENT_DELETED",
                    "deleted comments cannot be changed");
        }
        return RecordCommentException.conflict(
                "RECORD_COMMENT_VERSION_CONFLICT",
                "comment changed concurrently");
    }

    private static void requireVersion(long version) {
        if (version < 1) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_VERSION_INVALID",
                    "version must be positive");
        }
    }

    private static RecordComment mapComment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RecordComment(
                resultSet.getString("comment_id"),
                new RecordCommentKey(
                        resultSet.getString("system_id"),
                        resultSet.getString("tenant_id"),
                        resultSet.getString("record_id")),
                resultSet.getString("parent_comment_id"),
                resultSet.getString("author_member_id"),
                resultSet.getString("body"),
                resultSet.getBoolean("deleted"),
                resultSet.getLong("version"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        var timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record IdempotentRow(RecordComment comment, String requestHash) {
    }
}
