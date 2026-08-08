package com.unique.examine.web;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.openapi.OpenApiRecordFacade;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import com.unique.examine.openapi.security.OpenApiSecurityErrors;
import com.unique.examine.openapi.service.OpenApiCallbackPublisher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Map;

@RestController
@RequestMapping("/openapi/v1/modules/{moduleCode}/records")
public class OpenApiRecordController {
    private static final int MAXIMUM_PAGE = 1_000_000;
    private static final int MAXIMUM_PAGE_SIZE = 200;

    private final RecordOperations records;
    private final OpenApiCallbackPublisher callbacks;

    @Autowired
    public OpenApiRecordController(OpenApiRecordFacade records, OpenApiCallbackPublisher callbacks) {
        this(new FacadeRecordOperations(records), callbacks);
    }

    public OpenApiRecordController(OpenApiRecordFacade records) {
        this(new FacadeRecordOperations(records), OpenApiCallbackPublisher.noop());
    }

    OpenApiRecordController(RecordOperations records) {
        this(records, OpenApiCallbackPublisher.noop());
    }

    OpenApiRecordController(RecordOperations records, OpenApiCallbackPublisher callbacks) {
        this.records = Objects.requireNonNull(records, "records");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> create(
            @PathVariable String moduleCode,
            @RequestBody OpenApiRecordFacade.CreateCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestAttribute(
                    value = OpenApiAuthentication.REQUEST_ATTRIBUTE,
                    required = false
            ) Object authenticationValue,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = session(authenticationValue, sessionValue, request);
        var response = records.create(
                session, moduleCode, body, idempotencyKey);
        publish(session, "RECORD_CREATED", moduleCode, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(success(
                response, request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.PageView>> list(
            @PathVariable String moduleCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = OpenApiAuthentication.REQUEST_ATTRIBUTE,
                    required = false
            ) Object authenticationValue,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        page(page, size);
        var session = session(authenticationValue, sessionValue, request);
        return ResponseEntity.ok(success(
                records.list(session, moduleCode, page, size), request));
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> detail(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestAttribute(
                    value = OpenApiAuthentication.REQUEST_ATTRIBUTE,
                    required = false
            ) Object authenticationValue,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = session(authenticationValue, sessionValue, request);
        return ResponseEntity.ok(success(
                records.detail(session, moduleCode, recordId), request));
    }

    @PutMapping("/{recordId}")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> update(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestBody OpenApiRecordFacade.UpdateCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestAttribute(
                    value = OpenApiAuthentication.REQUEST_ATTRIBUTE,
                    required = false
            ) Object authenticationValue,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = session(authenticationValue, sessionValue, request);
        var response = records.update(session, moduleCode, recordId, body, idempotencyKey);
        publish(session, "RECORD_UPDATED", moduleCode, response);
        return ResponseEntity.ok(success(response, request));
    }

    @PostMapping("/{recordId}:activate")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> activate(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestBody OpenApiRecordFacade.VersionCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return lifecycle(authenticationValue, sessionValue, request, moduleCode, recordId,
                body, idempotencyKey, LifecycleAction.ACTIVATE);
    }

    @PostMapping("/{recordId}:archive")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> archive(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestBody OpenApiRecordFacade.VersionCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return lifecycle(authenticationValue, sessionValue, request, moduleCode, recordId,
                body, idempotencyKey, LifecycleAction.ARCHIVE);
    }

    @PostMapping("/{recordId}:unarchive")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> unarchive(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestBody OpenApiRecordFacade.VersionCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return lifecycle(authenticationValue, sessionValue, request, moduleCode, recordId,
                body, idempotencyKey, LifecycleAction.UNARCHIVE);
    }

    @PostMapping("/{recordId}:trash")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> trash(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestBody OpenApiRecordFacade.VersionCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return lifecycle(authenticationValue, sessionValue, request, moduleCode, recordId,
                body, idempotencyKey, LifecycleAction.TRASH);
    }

    @PostMapping("/{recordId}:restore-from-trash")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> restoreFromTrash(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @RequestBody OpenApiRecordFacade.VersionCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        return lifecycle(authenticationValue, sessionValue, request, moduleCode, recordId,
                body, idempotencyKey, LifecycleAction.RESTORE_FROM_TRASH);
    }

    private ResponseEntity<ApiResponse<OpenApiRecordFacade.RecordView>> lifecycle(
            Object authenticationValue,
            Object sessionValue,
            HttpServletRequest request,
            String moduleCode,
            String recordId,
            OpenApiRecordFacade.VersionCommand body,
            String idempotencyKey,
            LifecycleAction action
    ) {
        var session = session(authenticationValue, sessionValue, request);
        var response = switch (action) {
            case ACTIVATE -> records.activate(session, moduleCode, recordId, body, idempotencyKey);
            case ARCHIVE -> records.archive(session, moduleCode, recordId, body, idempotencyKey);
            case UNARCHIVE -> records.unarchive(session, moduleCode, recordId, body, idempotencyKey);
            case TRASH -> records.trash(session, moduleCode, recordId, body, idempotencyKey);
            case RESTORE_FROM_TRASH -> records.restoreFromTrash(
                    session, moduleCode, recordId, body, idempotencyKey);
        };
        publish(session, "RECORD_" + action.name(), moduleCode, response);
        return ResponseEntity.ok(success(response, request));
    }

    private void publish(OpenApiRecordFacade.Session session, String eventType,
                         String moduleCode, OpenApiRecordFacade.RecordView response) {
        callbacks.publish(new OpenApiCallbackPublisher.Event(session.systemId(), session.tenantId(),
                session.applicationId(), session.serviceMemberId(),
                OpenApiCallbackPublisher.deterministicEventId(eventType,
                        Long.toString(session.applicationId()), moduleCode,
                        response.recordId(), Long.toString(response.version())),
                eventType, "RECORD", response.recordId(),
                Map.of("moduleCode", moduleCode, "status", response.status(),
                        "version", Long.toString(response.version())),
                session.requestId(), session.traceId()));
    }

    private static OpenApiRecordFacade.Session session(
            Object authenticationValue,
            Object sessionValue,
            HttpServletRequest request
    ) {
        if (!(authenticationValue instanceof OpenApiAuthentication authentication)
                || !(sessionValue instanceof OpenApiMachineSession machineSession)
                || !machineSession.equals(authentication.session())
                || !Objects.equals(
                        machineSession.systemId(),
                        authentication.application().systemId())
                || !Objects.equals(
                        machineSession.tenantId(),
                        authentication.application().tenantId())
                || !Objects.equals(
                        machineSession.memberId(),
                        authentication.application().serviceMemberId())) {
            throw OpenApiSecurityErrors.authenticationRequired();
        }
        return new OpenApiRecordFacade.Session(
                authentication.application().id(),
                machineSession.accountId(),
                machineSession.systemId(),
                machineSession.tenantId(),
                machineSession.memberId(),
                machineSession.permissions(),
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static void page(int page, int size) {
        if (page < 1 || page > MAXIMUM_PAGE
                || size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new BusinessException(
                    "QUERY_INVALID",
                    "page must be between 1 and 1000000 and size between 1 and 200",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static <T> ApiResponse<T> success(
            T value, HttpServletRequest request) {
        return ApiResponse.success(
                value,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    interface RecordOperations {
        OpenApiRecordFacade.RecordView create(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                OpenApiRecordFacade.CreateCommand command,
                String idempotencyKey);

        OpenApiRecordFacade.RecordView detail(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId);

        OpenApiRecordFacade.PageView list(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                int page,
                int size);

        OpenApiRecordFacade.RecordView update(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                OpenApiRecordFacade.UpdateCommand command,
                String idempotencyKey);

        OpenApiRecordFacade.RecordView activate(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey);

        OpenApiRecordFacade.RecordView archive(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey);

        OpenApiRecordFacade.RecordView unarchive(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey);

        OpenApiRecordFacade.RecordView trash(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey);

        OpenApiRecordFacade.RecordView restoreFromTrash(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey);
    }

    private record FacadeRecordOperations(OpenApiRecordFacade delegate)
            implements RecordOperations {
        private FacadeRecordOperations {
            Objects.requireNonNull(delegate, "records");
        }

        @Override
        public OpenApiRecordFacade.RecordView create(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                OpenApiRecordFacade.CreateCommand command,
                String idempotencyKey) {
            return delegate.create(session, moduleCode, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView detail(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId) {
            return delegate.detail(session, moduleCode, recordId);
        }

        @Override
        public OpenApiRecordFacade.PageView list(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                int page,
                int size) {
            return delegate.list(session, moduleCode, page, size);
        }

        @Override
        public OpenApiRecordFacade.RecordView update(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.UpdateCommand command, String idempotencyKey) {
            return delegate.update(session, moduleCode, recordId, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView activate(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey) {
            return delegate.activate(session, moduleCode, recordId, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView archive(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey) {
            return delegate.archive(session, moduleCode, recordId, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView unarchive(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey) {
            return delegate.unarchive(session, moduleCode, recordId, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView trash(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey) {
            return delegate.trash(session, moduleCode, recordId, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.RecordView restoreFromTrash(
                OpenApiRecordFacade.Session session, String moduleCode, String recordId,
                OpenApiRecordFacade.VersionCommand command, String idempotencyKey) {
            return delegate.restoreFromTrash(session, moduleCode, recordId, command, idempotencyKey);
        }
    }

    private enum LifecycleAction {
        ACTIVATE,
        ARCHIVE,
        UNARCHIVE,
        TRASH,
        RESTORE_FROM_TRASH
    }
}
