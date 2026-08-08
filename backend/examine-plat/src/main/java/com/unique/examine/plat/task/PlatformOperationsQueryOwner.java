package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Live authorization owner for bounded platform operations projections. */
@Component
public class PlatformOperationsQueryOwner
        implements PlatformOperationsQueryFacade {
    private final PlatformTaskAccess access;
    private final PlatformTaskQueryStore tasks;
    private final AiQuotaProvider quotas;
    private final ServiceHealthProvider health;
    private final AgentActivityProvider activity;

    @Autowired
    public PlatformOperationsQueryOwner(
            LivePlatformTaskAccess access,
            JdbcPlatformTaskStore tasks,
            ObjectProvider<AiQuotaProvider> quotas,
            ObjectProvider<ServiceHealthProvider> health,
            ObjectProvider<AgentActivityProvider> activity) {
        this(access, tasks, quotas.getIfUnique(), health.getIfUnique(),
                activity.getIfUnique());
    }

    PlatformOperationsQueryOwner(
            PlatformTaskAccess access,
            PlatformTaskQueryStore tasks,
            AiQuotaProvider quotas,
            ServiceHealthProvider health,
            AgentActivityProvider activity) {
        this.access = Objects.requireNonNull(access, "access");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.quotas = quotas;
        this.health = health;
        this.activity = activity;
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        var live = access.current(request.accountId());
        if (live.epoch() != request.authorizationEpoch()) {
            throw new BusinessException(
                    "PLATFORM_OPERATIONS_AUTHORIZATION_STALE",
                    "Platform authorization changed before the query",
                    HttpStatus.CONFLICT);
        }
        require(live, permission(request.queryKind()));
        return switch (request.queryKind()) {
            case PERSONAL_TASKS -> personalTasks(request);
            case AI_QUOTA -> quota(request);
            case SERVICE_HEALTH -> health();
            case AGENT_ACTIVITY -> activity(request);
        };
    }

    private PersonalTasksResult personalTasks(Request request) {
        var result = new PersonalTasksResult(tasks.findOwnTasks(
                request.accountId(), request.limit()));
        bounded(result.tasks().size(), request.limit());
        return result;
    }

    private AiQuotaResult quota(Request request) {
        if (quotas == null) throw unavailable(QueryKind.AI_QUOTA);
        return Objects.requireNonNull(
                quotas.current(request.accountId()), "AI quota projection");
    }

    private ServiceHealthResult health() {
        if (health == null) throw unavailable(QueryKind.SERVICE_HEALTH);
        return Objects.requireNonNull(health.current(), "service health projection");
    }

    private AgentActivityResult activity(Request request) {
        if (activity == null) throw unavailable(QueryKind.AGENT_ACTIVITY);
        var result = Objects.requireNonNull(
                activity.recent(request.accountId(), request.limit()),
                "Agent activity projection");
        bounded(result.activities().size(), request.limit());
        return result;
    }

    private static String permission(QueryKind value) {
        return switch (value) {
            case PERSONAL_TASKS -> "platform.task.read";
            case AI_QUOTA -> "platform.ai.agent.use";
            case SERVICE_HEALTH, AGENT_ACTIVITY -> "platform.audit.view";
        };
    }

    private static void require(
            PlatformTaskAccess.LiveAuthorization live, String permission) {
        if (!live.permissions().contains(permission)) {
            throw new BusinessException(
                    "PLATFORM_OPERATIONS_PERMISSION_DENIED",
                    "The platform operations query permission is required",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static void bounded(int size, int limit) {
        if (size > limit) {
            throw new IllegalStateException(
                    "Platform operations provider exceeded the requested limit");
        }
    }

    private static BusinessException unavailable(QueryKind value) {
        return new BusinessException(
                "PLATFORM_OPERATIONS_SOURCE_UNAVAILABLE",
                value + " projection is unavailable",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
