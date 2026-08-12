package com.unique.examine.collab.recordcomment;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcRecordCommentContractTest {
    private static final String MIGRATION = "V8_2_0__collab_record_comment.sql";
    private static final String MENTION_MIGRATION = "V8_27_0__collab_record_comment_mention.sql";
    private static final RecordCommentKey KEY = new RecordCommentKey("1", "2", "3");

    private JdbcTemplate jdbc;
    private JdbcRecordCommentRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:record_comment_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE un_collab_record_comment (
                    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    record_id BIGINT NOT NULL,
                    parent_comment_id BIGINT,
                    author_member_id BIGINT NOT NULL,
                    body VARCHAR(8000),
                    deleted TINYINT NOT NULL DEFAULT 0,
                    version BIGINT NOT NULL DEFAULT 1,
                    idempotency_key VARCHAR(128) NOT NULL,
                    request_hash CHAR(64) NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL,
                    created_by BIGINT NOT NULL,
                    updated_at TIMESTAMP(3) NOT NULL,
                    updated_by BIGINT NOT NULL,
                    deleted_at TIMESTAMP(3),
                    deleted_by BIGINT,
                    CONSTRAINT uk_comment_idempotency UNIQUE (
                        system_id, tenant_id, record_id, author_member_id, idempotency_key
                    )
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_collab_record_comment_mention (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    record_id BIGINT NOT NULL,
                    comment_id BIGINT NOT NULL,
                    mentioned_member_id BIGINT NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL,
                    created_by BIGINT NOT NULL,
                    PRIMARY KEY (system_id, tenant_id, record_id, comment_id, mentioned_member_id)
                )
                """);
        repository = new JdbcRecordCommentRepository(
                jdbc,
                new DataSourceTransactionManager(dataSource));
    }

    @Test
    void createIsTrimmedIdempotentAndScopedByTenantRecordAndAuthor() {
        var first = repository.create(create(KEY, "10", "  first comment  ", null, "request-1"));
        var replay = repository.create(create(KEY, "10", "first comment", null, "request-1"));
        var otherTenant = repository.create(create(
                new RecordCommentKey("1", "4", "3"),
                "10",
                "tenant four",
                null,
                "request-1"));

        assertThat(first.created()).isTrue();
        assertThat(first.comment().body()).isEqualTo("first comment");
        assertThat(replay.created()).isFalse();
        assertThat(replay.comment().commentId()).isEqualTo(first.comment().commentId());
        assertThat(otherTenant.created()).isTrue();
        assertThat(otherTenant.comment().commentId()).isNotEqualTo(first.comment().commentId());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment",
                Long.class)).isEqualTo(2);

        assertCode(
                () -> repository.create(create(
                        KEY, "10", "different body", null, "request-1")),
                "RECORD_COMMENT_IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void repliesAreOneLevelAndCannotCrossTheScopedRecord() {
        var root = repository.create(create(KEY, "10", "root", null, "root")).comment();
        var reply = repository.create(create(
                KEY, "11", "reply", root.commentId(), "reply")).comment();
        var otherRecord = new RecordCommentKey("1", "2", "9");

        assertThat(reply.parentCommentId()).isEqualTo(root.commentId());
        assertCode(
                () -> repository.create(create(
                        KEY, "12", "nested", reply.commentId(), "nested")),
                "RECORD_COMMENT_REPLY_DEPTH_EXCEEDED");
        assertCode(
                () -> repository.create(create(
                        otherRecord, "12", "cross record", root.commentId(), "cross")),
                "RECORD_COMMENT_PARENT_NOT_FOUND");
    }

    @Test
    void mentionRowsAreAtomicImmutableAndPartOfTheIdempotencyFingerprint() {
        var command = new RecordCommentCreate(KEY, null, "10", "mentioned", "mentions",
                List.of("11", "12"));
        var first = repository.create(command);
        var replay = repository.create(new RecordCommentCreate(KEY, null, "10", "mentioned", "mentions",
                List.of("12", "11")));

        assertThat(first.comment().mentionedMemberIds()).containsExactly("11", "12");
        assertThat(replay.created()).isFalse();
        assertThat(replay.comment().mentionedMemberIds()).containsExactly("11", "12");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment_mention WHERE comment_id=?",
                Long.class, first.comment().commentId())).isEqualTo(2);
        assertCode(() -> repository.create(new RecordCommentCreate(KEY, null, "10", "mentioned", "mentions",
                List.of("11"))), "RECORD_COMMENT_IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void updateAndTombstoneUseCasWhilePageRetainsDeletedOrdering() {
        var first = repository.create(create(KEY, "10", "first", null, "one")).comment();
        var second = repository.create(create(KEY, "11", "second", null, "two")).comment();

        var changed = repository.update(KEY, first.commentId(), 1, "  changed  ", "10");
        assertThat(changed.body()).isEqualTo("changed");
        assertThat(changed.version()).isEqualTo(2);
        assertCode(
                () -> repository.update(KEY, first.commentId(), 1, "stale", "10"),
                "RECORD_COMMENT_VERSION_CONFLICT");

        var deleted = repository.tombstone(KEY, first.commentId(), 2, "10");
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.body()).isNull();
        assertThat(deleted.version()).isEqualTo(3);
        assertCode(
                () -> repository.tombstone(KEY, first.commentId(), 2, "10"),
                "RECORD_COMMENT_DELETED");

        var page = repository.findPage(KEY, 1, 20);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(RecordComment::commentId)
                .containsExactly(first.commentId(), second.commentId());
        assertThat(page.items().getFirst().body()).isNull();
    }

    @Test
    void everyLookupAndMutationIsFullyScopedAndPageOrderIsStable() {
        for (var sql : List.of(
                JdbcRecordCommentRepository.FIND_PAGE_SQL,
                JdbcRecordCommentRepository.COUNT_SQL,
                JdbcRecordCommentRepository.FIND_SQL,
                JdbcRecordCommentRepository.FIND_PARENT_SQL,
                JdbcRecordCommentRepository.FIND_IDEMPOTENT_SQL,
                JdbcRecordCommentRepository.UPDATE_SQL,
                JdbcRecordCommentRepository.TOMBSTONE_SQL)) {
            assertThat(sql)
                    .contains("system_id = ?")
                    .contains("tenant_id = ?")
                    .contains("record_id = ?");
        }
        assertThat(JdbcRecordCommentRepository.FIND_PAGE_SQL)
                .contains("ORDER BY created_at ASC, comment_id ASC");
        assertThat(JdbcRecordCommentRepository.UPDATE_SQL)
                .contains("deleted = 0")
                .contains("version = ?");
        assertThat(JdbcRecordCommentRepository.TOMBSTONE_SQL)
                .contains("body = NULL")
                .contains("deleted = 1")
                .contains("version = ?");
    }

    @Test
    void validatesPageAndUnicodeBodyCharacterLimits() {
        assertCode(() -> repository.findPage(KEY, 0, 20), "RECORD_COMMENT_PAGE_INVALID");
        assertCode(() -> repository.findPage(KEY, 1, 101), "RECORD_COMMENT_SIZE_INVALID");

        var fourThousandCodePoints = "\uD83D\uDE00".repeat(4000);
        assertThat(repository.create(create(
                KEY, "10", fourThousandCodePoints, null, "unicode"))
                .comment().body().codePointCount(0, fourThousandCodePoints.length()))
                .isEqualTo(4000);
        assertCode(
                () -> repository.create(create(
                        KEY, "10", fourThousandCodePoints + "x", null, "too-long")),
                "RECORD_COMMENT_BODY_INVALID");
    }

    @Test
    void migrationDeclaresRecordMemberIdempotencyTombstoneAndOrderingContracts()
            throws IOException {
        var sql = Files.readString(locateMigration());

        assertThat(sql)
                .contains("CREATE TABLE un_collab_record_comment")
                .contains("FOREIGN KEY (system_id, tenant_id, record_id)")
                .contains("FOREIGN KEY (system_id, author_member_id, tenant_id)")
                .contains("uk_collab_comment_idempotency")
                .contains("idx_collab_comment_page")
                .contains("created_at, comment_id")
                .contains("parent_comment_id")
                .contains("request_hash CHAR(64)")
                .contains("ck_collab_comment_body")
                .contains("ck_collab_comment_tombstone")
                .contains("version BIGINT NOT NULL DEFAULT 1");

        var mentionSql = Files.readString(locateMigration(MENTION_MIGRATION));
        assertThat(mentionSql)
                .contains("CREATE TABLE un_collab_record_comment_mention")
                .contains("fk_collab_comment_mention_comment")
                .contains("fk_collab_comment_mention_team_member")
                .contains("mentioned_member_id <> created_by");
    }

    private static RecordCommentCreate create(
            RecordCommentKey key,
            String author,
            String body,
            String parent,
            String idempotencyKey
    ) {
        return new RecordCommentCreate(key, parent, author, body, idempotencyKey);
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static Path locateMigration() {
        return locateMigration(MIGRATION);
    }

    private static Path locateMigration(String migration) {
        var cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            var candidate = cursor.resolve("sql").resolve("migration").resolve(migration);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new AssertionError("Cannot locate migration " + migration);
    }
}
