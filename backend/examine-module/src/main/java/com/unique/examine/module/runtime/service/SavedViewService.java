package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.SavedViewViews;
import com.unique.examine.module.runtime.query.SavedViewRequestParser;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SavedViewService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final SavedViewRepository repository;
    private final SavedViewRequestParser parser;
    private final RecordRuntimeService records;
    private final SavedViewMutationSupport mutations;
    private final RuntimeAuthorizationFacade authorization;
    private final ObjectMapper objectMapper;

    public SavedViewService(
            SavedViewRepository repository,
            SavedViewRequestParser parser,
            RecordRuntimeService records,
            SavedViewMutationSupport mutations,
            RuntimeAuthorizationFacade authorization,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.parser = parser;
        this.records = records;
        this.mutations = mutations;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
    }

    public SavedViewViews.SavedViewListResponse list(
            RuntimeSession session,
            String moduleCode,
            String correlationId
    ) {
        authorize(session);
        var schema = records.schema(session, moduleCode);
        var items = repository.list(session, Long.parseLong(schema.logicalModuleId())).stream()
                .map(view -> response(view, correlationId)).toList();
        return new SavedViewViews.SavedViewListResponse(items, correlationId);
    }

    @Transactional
    public SavedViewViews.SavedViewResponse create(
            RuntimeSession session,
            String body,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        authorize(session);
        var request = parser.create(body);
        var schema = records.schema(session, request.moduleCode());
        records.validateQuery(session, request.moduleCode(), request.query().canonicalJson());
        var path = "/runtime/saved-views";
        return mutations.idempotent(session, "POST", path, idempotencyKey, request,
                SavedViewViews.SavedViewResponse.class, 201, () -> {
                    try {
                        var stored = repository.create(session, Long.parseLong(schema.logicalModuleId()),
                                Long.parseLong(schema.schemaVersionId()), request.name(),
                                request.query().canonicalJson(), json(request.columns()));
                        var response = response(stored, requestId);
                        mutations.changed(session, stored.id(), stored.version(), "SAVED_VIEW_CREATED",
                                null, response, requestId, traceId);
                        return response;
                    } catch (DataIntegrityViolationException exception) {
                        throw nameConflict();
                    }
                });
    }

    @Transactional
    public SavedViewViews.SavedViewResponse update(
            RuntimeSession session,
            long viewId,
            String body,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        authorize(session);
        var before = repository.requireOwned(session, viewId);
        var request = parser.update(body);
        var schema = records.schema(session, before.moduleCode());
        records.validateQuery(session, before.moduleCode(), request.query().canonicalJson());
        var path = "/runtime/saved-views/" + viewId;
        return mutations.idempotent(session, "PUT", path, idempotencyKey, request,
                SavedViewViews.SavedViewResponse.class, 200, () -> {
                    try {
                        var stored = repository.update(session, before.moduleId(), viewId,
                                Long.parseLong(schema.schemaVersionId()), request.expectedVersion(), request.name(),
                                request.query().canonicalJson(), json(request.columns()));
                        var response = response(stored, requestId);
                        mutations.changed(session, stored.id(), stored.version(), "SAVED_VIEW_UPDATED",
                                response(before, requestId), response, requestId, traceId);
                        return response;
                    } catch (DataIntegrityViolationException exception) {
                        throw nameConflict();
                    }
                });
    }

    @Transactional
    public SavedViewViews.DeleteResponse delete(
            RuntimeSession session,
            long viewId,
            String body,
            String idempotencyKey,
            String requestId,
        String traceId
    ) {
        authorize(session);
        var expectedVersion = parser.delete(body);
        var path = "/runtime/saved-views/" + viewId;
        return mutations.idempotent(session, "DELETE", path, idempotencyKey, expectedVersion,
                SavedViewViews.DeleteResponse.class, 200, () -> {
                    var before = repository.requireOwned(session, viewId);
                    records.schema(session, before.moduleCode());
                    var version = repository.delete(session, before.moduleId(), viewId, expectedVersion);
                    var response = new SavedViewViews.DeleteResponse(Long.toString(viewId), version, true, requestId);
                    mutations.changed(session, viewId, version, "SAVED_VIEW_DELETED",
                            response(before, requestId), response, requestId, traceId);
                    return response;
                });
    }

    private void authorize(RuntimeSession session) {
        var permission = "runtime.saved_view.manage";
        if (!session.permissions().contains(permission)
                || authorization.resolve(new RuntimeAuthorizationFacade.RuntimeAuthorizationRequest(
                session.systemId(), tenant(session), session.memberId(), permission)).denied()) {
            throw new BusinessException("PERMISSION_DENIED",
                    "Current member cannot manage saved views", HttpStatus.FORBIDDEN);
        }
    }

    private SavedViewViews.SavedViewResponse response(SavedViewRepository.StoredView view, String correlationId) {
        try {
            return new SavedViewViews.SavedViewResponse(Long.toString(view.id()), view.version(), view.moduleCode(),
                    view.name(), objectMapper.readTree(view.queryJson()),
                    objectMapper.readValue(view.columnsJson(), STRING_LIST), correlationId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored saved view JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Saved view payload must be JSON serializable", exception);
        }
    }

    private static long tenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("CONTEXT_TENANT_REQUIRED", "Tenant context is required", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    private static BusinessException nameConflict() {
        return new BusinessException("SAVED_VIEW_NAME_CONFLICT",
                "An active saved view already uses this name", HttpStatus.CONFLICT);
    }
}
