package com.unique.examine.module.runtime.favorite;

import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;

@Service
public class FavoriteService {
    private final FavoriteRepository repository;
    private final FavoriteRequestParser parser;
    private final FavoriteTargetResolver targets;
    private final FavoriteMutationSupport mutations;
    private final RuntimeAuthorizationFacade authorization;

    public FavoriteService(
            FavoriteRepository repository,
            FavoriteRequestParser parser,
            FavoriteTargetResolver targets,
            FavoriteMutationSupport mutations,
            RuntimeAuthorizationFacade authorization
    ) {
        this.repository = repository;
        this.parser = parser;
        this.targets = targets;
        this.mutations = mutations;
        this.authorization = authorization;
    }

    public FavoriteViews.FavoritePage list(RuntimeSession session, int page, int size) {
        authorize(session);
        var request = parser.page(page, size);
        var visible = new ArrayList<FavoriteViews.FavoriteItem>();
        for (var stored : repository.listActive(session)) {
            resolveVisible(session, stored).ifPresent(visible::add);
        }
        var offset = (long) (request.page() - 1) * request.size();
        if (offset >= visible.size()) {
            return new FavoriteViews.FavoritePage(
                    java.util.List.of(), request.page(), request.size(), visible.size());
        }
        var from = (int) offset;
        var to = Math.min(from + request.size(), visible.size());
        return new FavoriteViews.FavoritePage(
                visible.subList(from, to), request.page(), request.size(), visible.size());
    }

    @Transactional
    public CreateResult create(
            RuntimeSession session,
            String body,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        authorize(session);
        var request = parser.create(body);
        var target = targets.resolve(session, request.type(), request.moduleCode(), request.recordId());
        var path = "/runtime/favorites";
        return mutations.idempotent(session, "POST", path, idempotencyKey, request,
                CreateResult.class, 201, () -> {
                    var result = repository.createOrFind(session, target);
                    var response = item(result.favorite(), target);
                    if (result.created()) {
                        mutations.changed(session, result.favorite().id(), result.favorite().version(),
                                "FAVORITE_CREATED", null, response, requestId, traceId);
                    }
                    return new CreateResult(response, result.created());
                });
    }

    @Transactional
    public FavoriteViews.DeleteResponse delete(
            RuntimeSession session,
            long favoriteId,
            String body,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        authorize(session);
        var request = parser.delete(body);
        var path = "/runtime/favorites/" + favoriteId;
        return mutations.idempotent(session, "DELETE", path, idempotencyKey, request,
                FavoriteViews.DeleteResponse.class, 200, () -> {
                    var before = repository.requireOwned(session, favoriteId);
                    var version = repository.delete(session, favoriteId, request.expectedVersion());
                    var response = new FavoriteViews.DeleteResponse(Long.toString(favoriteId), version, true);
                    mutations.changed(session, favoriteId, version, "FAVORITE_DELETED",
                            auditSnapshot(before), response, requestId, traceId);
                    return response;
                });
    }

    private Optional<FavoriteViews.FavoriteItem> resolveVisible(
            RuntimeSession session,
            FavoriteRepository.StoredFavorite favorite
    ) {
        try {
            var target = targets.resolve(
                    session, favorite.type(), favorite.moduleCode(), favorite.recordId());
            return Optional.of(item(favorite, target));
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    private static FavoriteViews.FavoriteItem item(
            FavoriteRepository.StoredFavorite favorite,
            FavoriteTargetResolver.ResolvedTarget target
    ) {
        return new FavoriteViews.FavoriteItem(
                Long.toString(favorite.id()),
                favorite.version(),
                favorite.type(),
                target.moduleCode(),
                favorite.recordId() == null ? null : Long.toString(favorite.recordId()),
                target.displayLabel(),
                target.status(),
                favorite.updatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private static Object auditSnapshot(FavoriteRepository.StoredFavorite favorite) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("favoriteId", Long.toString(favorite.id()));
        snapshot.put("version", favorite.version());
        snapshot.put("type", favorite.type());
        snapshot.put("logicalModuleId", Long.toString(favorite.logicalModuleId()));
        if (favorite.recordId() != null) {
            snapshot.put("recordId", Long.toString(favorite.recordId()));
        }
        return snapshot;
    }

    private void authorize(RuntimeSession session) {
        var permission = "runtime.favorite.manage";
        if (!session.permissions().contains(permission)
                || authorization.resolve(new RuntimeAuthorizationFacade.RuntimeAuthorizationRequest(
                session.systemId(), tenant(session), session.memberId(), permission)).denied()) {
            throw new BusinessException("PERMISSION_DENIED",
                    "Current member cannot manage favorites", HttpStatus.FORBIDDEN);
        }
    }

    private static long tenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("CONTEXT_TENANT_REQUIRED",
                    "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    public record CreateResult(FavoriteViews.FavoriteItem item, boolean created) {
    }
}
