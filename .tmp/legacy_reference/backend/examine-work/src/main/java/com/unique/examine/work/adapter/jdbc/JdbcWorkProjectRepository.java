package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.core.id.IdService;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.domain.WorkProjectPage;
import com.unique.examine.work.domain.WorkProjectQuery;
import com.unique.examine.work.port.WorkProjectRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcWorkProjectRepository
        implements WorkProjectRepository {
    private static final String PROJECT_COLUMNS = """
            id,system_id,tenant_id,creator_member_id,title,description,status,
            created_at,updated_at,version
            """;
    private static final String MEMBER_COLUMNS = """
            system_id,tenant_id,project_id,member_id,role,status,
            joined_at,updated_at,version
            """;

    static final String INSERT_PROJECT = """
            INSERT INTO un_work_project
              (id,system_id,tenant_id,creator_member_id,title,description,
               status,created_at,updated_at,version)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;
    static final String INSERT_MEMBER = """
            INSERT INTO un_work_project_member
              (system_id,tenant_id,project_id,member_id,role,status,
               joined_at,updated_at,version)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;
    static final String FIND_PROJECT = """
            SELECT %s FROM un_work_project
            WHERE system_id=? AND tenant_id=? AND id=?
            """.formatted(PROJECT_COLUMNS);
    static final String FIND_PROJECT_FOR_UPDATE = FIND_PROJECT + " FOR UPDATE";
    static final String FIND_VISIBLE_PROJECT = """
            SELECT %s FROM un_work_project project_row
            WHERE project_row.system_id=? AND project_row.tenant_id=?
              AND project_row.id=?
              AND (?=TRUE OR EXISTS (
                SELECT 1 FROM un_work_project_member member_row
                WHERE member_row.system_id=project_row.system_id
                  AND member_row.tenant_id=project_row.tenant_id
                  AND member_row.project_id=project_row.id
                  AND member_row.member_id=? AND member_row.status='ACTIVE'))
            """.formatted(PROJECT_COLUMNS);
    static final String UPDATE_PROJECT = """
            UPDATE un_work_project
            SET title=?,description=?,status=?,updated_at=?,version=?
            WHERE system_id=? AND tenant_id=? AND id=? AND version=?
            """;
    static final String FIND_MEMBER = """
            SELECT %s FROM un_work_project_member
            WHERE system_id=? AND tenant_id=? AND project_id=? AND member_id=?
            """.formatted(MEMBER_COLUMNS);
    static final String FIND_MEMBER_FOR_UPDATE = FIND_MEMBER + " FOR UPDATE";
    static final String FIND_MEMBERS = """
            SELECT %s FROM un_work_project_member
            WHERE system_id=? AND tenant_id=? AND project_id=?
              AND status='ACTIVE'
            ORDER BY CASE WHEN role='OWNER' THEN 0 ELSE 1 END,
                     joined_at,member_id
            """.formatted(MEMBER_COLUMNS);
    static final String LOCK_ACTIVE_OWNERS = """
            SELECT member_id FROM un_work_project_member
            WHERE system_id=? AND tenant_id=? AND project_id=?
              AND status='ACTIVE' AND role='OWNER'
            ORDER BY member_id FOR UPDATE
            """;
    static final String UPDATE_MEMBER = """
            UPDATE un_work_project_member
            SET role=?,status=?,updated_at=?,version=?
            WHERE system_id=? AND tenant_id=? AND project_id=? AND member_id=?
              AND version=?
            """;
    private static final String PAGE_SELECT =
            "SELECT " + PROJECT_COLUMNS + " FROM un_work_project project_row\n";
    private static final String PAGE_COUNT =
            "SELECT COUNT(*) FROM un_work_project project_row\n";
    private static final String PAGE_ORDER = """
            ORDER BY project_row.updated_at DESC,project_row.id DESC
            LIMIT ? OFFSET ?
            """;

    static final RowMapper<WorkProject> PROJECT_MAPPER = (result, row) ->
            new WorkProject(
                    result.getLong("id"), result.getLong("system_id"),
                    result.getLong("tenant_id"),
                    result.getLong("creator_member_id"),
                    result.getString("title"), result.getString("description"),
                    WorkProject.Status.valueOf(result.getString("status")),
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("updated_at").toInstant(),
                    result.getLong("version"));

    static final RowMapper<WorkProjectMember> MEMBER_MAPPER = (result, row) ->
            new WorkProjectMember(
                    result.getLong("system_id"), result.getLong("tenant_id"),
                    result.getLong("project_id"), result.getLong("member_id"),
                    WorkProjectMember.Role.valueOf(result.getString("role")),
                    WorkProjectMember.Status.valueOf(result.getString("status")),
                    result.getTimestamp("joined_at").toInstant(),
                    result.getTimestamp("updated_at").toInstant(),
                    result.getLong("version"));

    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final TransactionTemplate transactions;

    public JdbcWorkProjectRepository(
            JdbcTemplate jdbc,
            IdService ids,
            PlatformTransactionManager transactionManager
    ) {
        if (jdbc == null || ids == null || transactionManager == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate, IdService and transaction manager are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public long nextId() {
        return ids.nextId();
    }

    @Override
    public WorkProject create(
            WorkProject project, WorkProjectMember creatorOwner
    ) {
        validateCreation(project, creatorOwner);
        try {
            return transactions.execute(status -> {
                insertProject(project);
                insertMember(creatorOwner);
                return project;
            });
        } catch (DuplicateKeyException exception) {
            throw conflict("WORK_PROJECT_VERSION_CONFLICT",
                    "Project already exists", exception);
        }
    }

    @Override
    public Optional<WorkProject> findById(
            long systemId, long tenantId, long projectId
    ) {
        return jdbc.query(FIND_PROJECT, PROJECT_MAPPER,
                systemId, tenantId, projectId).stream().findFirst();
    }

    @Override
    public Optional<WorkProject> findVisibleById(
            long systemId,
            long tenantId,
            long projectId,
            long memberId,
            boolean manager
    ) {
        return jdbc.query(FIND_VISIBLE_PROJECT, PROJECT_MAPPER,
                systemId, tenantId, projectId, manager, memberId)
                .stream().findFirst();
    }

    @Override
    public WorkProjectPage findPage(
            long systemId,
            long tenantId,
            long memberId,
            boolean manager,
            WorkProjectQuery query
    ) {
        var statements = pageStatements(
                systemId, tenantId, memberId, manager, query);
        var total = jdbc.queryForObject(
                statements.countSql(), Long.class,
                statements.countArguments().toArray());
        var items = jdbc.query(
                statements.pageSql(), PROJECT_MAPPER,
                statements.pageArguments().toArray());
        return new WorkProjectPage(
                items, query.page(), query.size(), total == null ? 0 : total);
    }

    @Override
    public WorkProject save(WorkProject project) {
        var updated = jdbc.update(
                UPDATE_PROJECT, project.title(), project.description(),
                project.status().name(), Timestamp.from(project.updatedAt()),
                project.version(), project.systemId(), project.tenantId(),
                project.id(), project.version() - 1);
        if (updated != 1) {
            throw conflict("WORK_PROJECT_VERSION_CONFLICT",
                    "Project version is stale", null);
        }
        return project;
    }

    @Override
    public Optional<WorkProjectMember> findMember(
            long systemId,
            long tenantId,
            long projectId,
            long memberId
    ) {
        return jdbc.query(FIND_MEMBER, MEMBER_MAPPER,
                systemId, tenantId, projectId, memberId)
                .stream().findFirst();
    }

    @Override
    public Optional<WorkProjectMember> findMemberForUpdate(
            long systemId,
            long tenantId,
            long projectId,
            long memberId
    ) {
        return jdbc.query(FIND_MEMBER_FOR_UPDATE, MEMBER_MAPPER,
                systemId, tenantId, projectId, memberId)
                .stream().findFirst();
    }

    @Override
    public List<WorkProjectMember> findMembers(
            long systemId, long tenantId, long projectId
    ) {
        return List.copyOf(jdbc.query(
                FIND_MEMBERS, MEMBER_MAPPER,
                systemId, tenantId, projectId));
    }

    @Override
    public WorkProjectMember saveMember(WorkProjectMember member) {
        try {
            return transactions.execute(status -> {
                var project = jdbc.query(
                        FIND_PROJECT_FOR_UPDATE, PROJECT_MAPPER,
                        member.systemId(), member.tenantId(), member.projectId())
                        .stream().findFirst();
                if (project.isEmpty()) {
                    throw conflict("WORK_PROJECT_NOT_FOUND",
                            "Project was not found", null);
                }
                var current = findMemberForUpdate(
                        member.systemId(), member.tenantId(),
                        member.projectId(), member.memberId());
                if (current.isEmpty()) {
                    if (member.version() != 1
                            || member.status()
                            != WorkProjectMember.Status.ACTIVE) {
                        throw conflict("WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                                "New project member snapshot is invalid", null);
                    }
                    insertMember(member);
                    return member;
                }
                var stored = current.get();
                if (member.version() != stored.version() + 1
                        || !member.joinedAt().equals(stored.joinedAt())) {
                    throw conflict("WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                            "Project member version is stale", null);
                }
                if (stored.activeOwner() && !member.activeOwner()) {
                    var owners = jdbc.queryForList(
                            LOCK_ACTIVE_OWNERS, Long.class,
                            member.systemId(), member.tenantId(),
                            member.projectId());
                    if (owners.size() <= 1) {
                        throw conflict("WORK_PROJECT_LAST_OWNER_INVALID",
                                "A project must retain at least one active owner",
                                null);
                    }
                }
                var updated = jdbc.update(
                        UPDATE_MEMBER, member.role().name(),
                        member.status().name(), Timestamp.from(member.updatedAt()),
                        member.version(), member.systemId(), member.tenantId(),
                        member.projectId(), member.memberId(),
                        member.version() - 1);
                if (updated != 1) {
                    throw conflict("WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                            "Project member version is stale", null);
                }
                return member;
            });
        } catch (DuplicateKeyException exception) {
            throw conflict("WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                    "Project member already exists", exception);
        }
    }

    static PageStatements pageStatements(
            long systemId,
            long tenantId,
            long memberId,
            boolean manager,
            WorkProjectQuery query
    ) {
        var where = new StringBuilder("""
                WHERE project_row.system_id=? AND project_row.tenant_id=?
                """);
        var arguments = new ArrayList<Object>();
        arguments.add(systemId);
        arguments.add(tenantId);
        if (!manager) {
            where.append("""
                     AND EXISTS (
                       SELECT 1 FROM un_work_project_member member_row
                       WHERE member_row.system_id=project_row.system_id
                         AND member_row.tenant_id=project_row.tenant_id
                         AND member_row.project_id=project_row.id
                         AND member_row.member_id=?
                         AND member_row.status='ACTIVE')
                    """);
            arguments.add(memberId);
        }
        if (query.status() != WorkProjectQuery.StatusFilter.ALL) {
            where.append(" AND project_row.status=?\n");
            arguments.add(query.status().name());
        }
        if (!query.keyword().isEmpty()) {
            where.append("""
                     AND (project_row.title LIKE ? ESCAPE '!'
                       OR project_row.description LIKE ? ESCAPE '!')
                    """);
            var pattern = JdbcWorkTaskRepository.likePattern(query.keyword());
            arguments.add(pattern);
            arguments.add(pattern);
        }
        var pageArguments = new ArrayList<>(arguments);
        pageArguments.add(query.size());
        pageArguments.add(query.offset());
        return new PageStatements(
                PAGE_SELECT + where + PAGE_ORDER, pageArguments,
                PAGE_COUNT + where, arguments);
    }

    private void insertProject(WorkProject value) {
        jdbc.update(INSERT_PROJECT,
                value.id(), value.systemId(), value.tenantId(),
                value.creatorMemberId(), value.title(), value.description(),
                value.status().name(), Timestamp.from(value.createdAt()),
                Timestamp.from(value.updatedAt()), value.version());
    }

    private void insertMember(WorkProjectMember value) {
        jdbc.update(INSERT_MEMBER,
                value.systemId(), value.tenantId(), value.projectId(),
                value.memberId(), value.role().name(), value.status().name(),
                Timestamp.from(value.joinedAt()),
                Timestamp.from(value.updatedAt()), value.version());
    }

    private static void validateCreation(
            WorkProject project, WorkProjectMember owner
    ) {
        if (project.version() != 1
                || project.status() != WorkProject.Status.ACTIVE
                || owner.systemId() != project.systemId()
                || owner.tenantId() != project.tenantId()
                || owner.projectId() != project.id()
                || owner.memberId() != project.creatorMemberId()
                || owner.version() != 1 || !owner.activeOwner()) {
            throw conflict("WORK_PROJECT_STATE_INVALID",
                    "Project creation snapshot is invalid", null);
        }
    }

    private static WorkDomainException conflict(
            String code, String message, Throwable cause
    ) {
        var error = new WorkDomainException(code, message);
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    record PageStatements(
            String pageSql,
            List<Object> pageArguments,
            String countSql,
            List<Object> countArguments
    ) {
        PageStatements {
            pageArguments = List.copyOf(pageArguments);
            countArguments = List.copyOf(countArguments);
        }
    }
}
