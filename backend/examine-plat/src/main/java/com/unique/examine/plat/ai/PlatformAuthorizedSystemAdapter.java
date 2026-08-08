package com.unique.examine.plat.ai;

import com.unique.examine.core.ai.PlatformAuthorizedSystemFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.manage.service.AuthorizationService;
import com.unique.examine.plat.manage.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Live platform authorization and system-switch projection owner. */
@Component
public class PlatformAuthorizedSystemAdapter implements PlatformAuthorizedSystemFacade {
    static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            "platform.runtime.access", "platform.ai.agent.use");

    private final AuthorizationReader authorization;
    private final DirectoryReader directory;

    @Autowired
    public PlatformAuthorizedSystemAdapter(
            AuthorizationService authorization,
            SessionService sessions
    ) {
        this(
                accountId -> {
                    var snapshot = authorization.platform(accountId);
                    return new LiveAuthorization(snapshot.epoch(), snapshot.permissions());
                },
                accountId -> sessions.listAuthorizedSystems(accountId).stream()
                        .map(system -> new Projection(
                                system.systemId(), system.systemCode(), system.systemName(),
                                system.status(), system.membershipState(), system.accessState(),
                                system.switchTarget()))
                        .toList());
    }

    PlatformAuthorizedSystemAdapter(
            AuthorizationReader authorization,
            DirectoryReader directory
    ) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    @Override
    @Transactional(readOnly = true)
    public Result authorizedSystems(Request request) {
        Objects.requireNonNull(request, "request");
        var live = authorization.read(request.accountId());
        if (live.epoch() != request.authorizationEpoch()
                || !live.permissions().equals(request.effectivePermissions())) {
            throw new BusinessException(
                    "AI_PLATFORM_AUTHORIZATION_STALE",
                    "Platform authorization changed",
                    HttpStatus.CONFLICT);
        }
        if (!live.permissions().containsAll(REQUIRED_PERMISSIONS)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "Platform AI Agent permission is required",
                    HttpStatus.FORBIDDEN);
        }
        var systems = directory.read(request.accountId()).stream()
                .map(system -> new SystemAccess(
                        Long.toString(system.systemId()), system.systemCode(), system.systemName(),
                        system.status(), system.membershipState(), system.accessState(),
                        system.switchTarget()))
                .toList();
        return new Result(systems);
    }

    @FunctionalInterface
    interface AuthorizationReader {
        LiveAuthorization read(long accountId);
    }

    @FunctionalInterface
    interface DirectoryReader {
        List<Projection> read(long accountId);
    }

    record LiveAuthorization(long epoch, Set<String> permissions) {
        LiveAuthorization {
            permissions = Set.copyOf(permissions);
        }
    }

    record Projection(
            long systemId,
            String systemCode,
            String systemName,
            String status,
            String membershipState,
            String accessState,
            String switchTarget
    ) {
    }
}
