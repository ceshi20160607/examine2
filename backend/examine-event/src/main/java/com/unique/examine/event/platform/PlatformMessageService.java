package com.unique.examine.event.platform;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PlatformMessageService {
    public static final String READ = "platform.message.read";
    public static final String MANAGE = "platform.message.manage";
    private static final Set<String> TARGET_TYPES = Set.of(
            "PLATFORM_AUTHORIZATION", "PLATFORM_TASK", "PLATFORM_PROJECT",
            "PLATFORM_LOG", "SYSTEM_SWITCH", "PLATFORM_AGENT");

    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final Clock clock;

    @Autowired
    public PlatformMessageService(JdbcTemplate jdbc, IdService ids) {
        this(jdbc, ids, Clock.systemUTC());
    }

    PlatformMessageService(JdbcTemplate jdbc, IdService ids, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public PlatformMessageApiModels.Page list(RequestSession session, String statusValue,
                                              String typeValue, String templateCode,
                                              String keyword, LocalDate from, LocalDate to,
                                              int page, int size) {
        var caller = require(session, READ);
        var status = value(PlatformMessageApiModels.Status.class, statusValue, PlatformMessageApiModels.Status.ALL);
        var type = value(PlatformMessageApiModels.Type.class, typeValue, PlatformMessageApiModels.Type.ALL);
        if (page < 1 || page > 10_000 || size < 1 || size > 100 || from != null && to != null && to.isBefore(from)) throw invalid("message query is invalid");
        var sql = new StringBuilder(" WHERE recipient_account_id=?");
        var args = new java.util.ArrayList<Object>(); args.add(caller.accountId());
        if (status != PlatformMessageApiModels.Status.ALL) {
            sql.append(switch (status) { case UNREAD -> " AND read_at IS NULL AND archived_at IS NULL"; case READ -> " AND read_at IS NOT NULL AND archived_at IS NULL"; case ARCHIVED -> " AND archived_at IS NOT NULL"; default -> ""; });
        } else sql.append(" AND archived_at IS NULL");
        if (type != PlatformMessageApiModels.Type.ALL) { sql.append(" AND message_type=?"); args.add(type.name()); }
        if (templateCode != null && !templateCode.isBlank()) { sql.append(" AND template_code=?"); args.add(token(templateCode, "templateCode", 100)); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND (title LIKE ? OR body LIKE ?)"); var pattern="%"+keyword.strip().replace("%", "\\%").replace("_", "\\_")+"%"; args.add(pattern); args.add(pattern); }
        if (from != null) { sql.append(" AND created_at>=?"); args.add(Timestamp.valueOf(from.atStartOfDay())); }
        if (to != null) { sql.append(" AND created_at<?"); args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay())); }
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM un_platform_inbox_message" + sql, Long.class, args.toArray());
        var pageArgs = new java.util.ArrayList<>(args); pageArgs.add(size); pageArgs.add(Math.multiplyExact(page - 1, size));
        var items = jdbc.query("""
                SELECT id,template_code,message_type,title,body,target_type,target_id,target_path,
                       created_at,read_at,archived_at,version
                  FROM un_platform_inbox_message
                """ + sql + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", this::view, pageArgs.toArray());
        return new PlatformMessageApiModels.Page(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public PlatformMessageApiModels.UnreadCount unread(RequestSession session) {
        var caller = require(session, READ);
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_platform_inbox_message WHERE recipient_account_id=? AND read_at IS NULL AND archived_at IS NULL", Long.class, caller.accountId());
        return new PlatformMessageApiModels.UnreadCount(count == null ? 0 : count);
    }

    @Transactional
    public PlatformMessageApiModels.MessageView read(RequestSession session, String messageId) {
        var caller = require(session, MANAGE);
        var id = id(messageId);
        jdbc.update("UPDATE un_platform_inbox_message SET read_at=COALESCE(read_at,?),version=version+IF(read_at IS NULL,1,0) WHERE id=? AND recipient_account_id=?", Timestamp.from(clock.instant()), id, caller.accountId());
        return find(caller.accountId(), id);
    }

    @Transactional
    public PlatformMessageApiModels.ChangedCount readAll(RequestSession session) {
        var caller = require(session, MANAGE);
        var changed = jdbc.update("UPDATE un_platform_inbox_message SET read_at=?,version=version+1 WHERE recipient_account_id=? AND read_at IS NULL AND archived_at IS NULL", Timestamp.from(clock.instant()), caller.accountId());
        return new PlatformMessageApiModels.ChangedCount(changed);
    }

    @Transactional
    public PlatformMessageApiModels.MessageView archive(RequestSession session, String messageId) {
        var caller = require(session, MANAGE);
        var id = id(messageId);
        var now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE un_platform_inbox_message SET read_at=COALESCE(read_at,?),archived_at=COALESCE(archived_at,?),version=version+IF(archived_at IS NULL,1,0) WHERE id=? AND recipient_account_id=?", now, now, id, caller.accountId());
        return find(caller.accountId(), id);
    }

    /** Owner entry point for platform domains; target validation prevents system-business deep links. */
    @Transactional
    public PlatformMessageApiModels.MessageView send(long recipientAccountId,
                                                      String templateCode, String type,
                                                      String title, String body,
                                                      PlatformMessageApiModels.Target target) {
        if (recipientAccountId <= 0) throw invalid("recipient is invalid");
        templateCode = token(templateCode, "templateCode", 100);
        var messageType = value(PlatformMessageApiModels.Type.class, type, null);
        if (messageType == null || messageType == PlatformMessageApiModels.Type.ALL) throw invalid("message type is invalid");
        title = text(title, "title", 200); body = text(body, "body", 4000);
        validateTarget(target);
        var id = ids.nextId(); var now = clock.instant();
        jdbc.update("""
                INSERT INTO un_platform_inbox_message(
                  id,recipient_account_id,template_code,message_type,title,body,
                  target_type,target_id,target_path,created_at,read_at,archived_at,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,NULL,NULL,0)
                """, id, recipientAccountId, templateCode, messageType.name(), title, body,
                target == null ? null : target.type(), target == null ? null : target.id(),
                target == null ? null : target.path(), Timestamp.from(now));
        return find(recipientAccountId, id);
    }

    private PlatformMessageApiModels.MessageView find(long accountId, long id) {
        return jdbc.query("""
                SELECT id,template_code,message_type,title,body,target_type,target_id,target_path,
                       created_at,read_at,archived_at,version
                  FROM un_platform_inbox_message WHERE recipient_account_id=? AND id=?
                """, this::view, accountId, id).stream().findFirst().orElseThrow(() -> new BusinessException("PLATFORM_MESSAGE_NOT_FOUND", "Platform message was not found", HttpStatus.NOT_FOUND));
    }

    private PlatformMessageApiModels.MessageView view(ResultSet row, int ignored) throws SQLException {
        var read = row.getTimestamp("read_at"); var archived = row.getTimestamp("archived_at");
        var targetType = row.getString("target_type");
        var target = targetType == null ? null : new PlatformMessageApiModels.Target(targetType, row.getString("target_id"), row.getString("target_path"));
        return new PlatformMessageApiModels.MessageView(Long.toString(row.getLong("id")), row.getString("template_code"), row.getString("message_type"), row.getString("title"), row.getString("body"), target,
                archived != null ? "ARCHIVED" : read == null ? "UNREAD" : "READ",
                row.getTimestamp("created_at").toInstant(), read == null ? null : read.toInstant(), archived == null ? null : archived.toInstant(), row.getLong("version"));
    }

    private static RequestSession require(RequestSession session, String permission) {
        if (session == null) throw new BusinessException("AUTH_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        if (session.contextType() != ContextType.PLATFORM || session.systemId() != null || session.tenantId() != null || session.memberId() != null) throw new BusinessException("CONTEXT_PLATFORM_REQUIRED", "请先返回平台上下文", HttpStatus.FORBIDDEN);
        if (!session.permissions().contains(permission)) throw new BusinessException("PERMISSION_DENIED", "当前身份没有执行此操作的权限", HttpStatus.FORBIDDEN);
        return session;
    }
    private static void validateTarget(PlatformMessageApiModels.Target target) {
        if (target == null) return;
        if (!TARGET_TYPES.contains(target.type()) || target.id() == null || target.id().isBlank() || target.id().length() > 128 || target.path() == null || !target.path().startsWith("/platform/") || target.path().startsWith("/systems/") || target.path().length() > 500) throw invalid("platform message target is unsafe");
    }
    private static long id(String value) { try { var id=Long.parseLong(value); if(id<=0)throw new NumberFormatException(); return id; } catch(RuntimeException failure){throw invalid("messageId is invalid");} }
    private static String token(String value,String name,int max){if(value==null||value.isBlank()||value.length()>max||!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"+(max-1)+"}$"))throw invalid(name+" is invalid");return value;}
    private static String text(String value,String name,int max){if(value==null||value.isBlank())throw invalid(name+" is required");value=value.strip();if(value.codePointCount(0,value.length())>max)throw invalid(name+" is too long");return value;}
    private static <E extends Enum<E>> E value(Class<E> type,String value,E fallback){if(value==null||value.isBlank())return fallback;try{return Enum.valueOf(type,value);}catch(RuntimeException failure){throw invalid("filter is invalid");}}
    private static BusinessException invalid(String message){return new BusinessException("PLATFORM_MESSAGE_REQUEST_INVALID",message,HttpStatus.BAD_REQUEST);}
}
