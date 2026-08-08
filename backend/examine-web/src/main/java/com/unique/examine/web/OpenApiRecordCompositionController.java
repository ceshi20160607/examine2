package com.unique.examine.web;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.openapi.OpenApiRecordFacade;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import com.unique.examine.openapi.security.OpenApiSecurityErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/openapi/v1/modules/{moduleCode}/records/{recordId}")
public class OpenApiRecordCompositionController {
    private final CompositionOperations compositions;

    @Autowired
    public OpenApiRecordCompositionController(OpenApiRecordFacade compositions) {
        this(new FacadeCompositionOperations(compositions));
    }

    OpenApiRecordCompositionController(CompositionOperations compositions) {
        this.compositions = Objects.requireNonNull(compositions, "compositions");
    }

    @GetMapping("/relations/{fieldCode}")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.RelationPageView>> relations(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var result = compositions.relations(
                session(authenticationValue, sessionValue, request),
                moduleCode, recordId, fieldCode, page, size);
        return ResponseEntity.ok(success(result, request));
    }

    @GetMapping("/subtables/{fieldCode}")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.SubtablePageView>> subtable(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var result = compositions.subtable(
                session(authenticationValue, sessionValue, request),
                moduleCode, recordId, fieldCode, page, size);
        return ResponseEntity.ok(success(result, request));
    }

    @PostMapping("/relations/{fieldCode}:mutate")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.MutationReceipt>> mutateRelation(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @RequestBody OpenApiRecordFacade.RelationMutationCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var result = compositions.mutateRelation(
                session(authenticationValue, sessionValue, request),
                moduleCode, recordId, fieldCode, body, idempotencyKey);
        return ResponseEntity.ok(success(result, request));
    }

    @PostMapping("/subtables/{fieldCode}:mutate")
    public ResponseEntity<ApiResponse<OpenApiRecordFacade.MutationReceipt>> mutateSubtable(
            @PathVariable String moduleCode,
            @PathVariable String recordId,
            @PathVariable String fieldCode,
            @RequestBody OpenApiRecordFacade.SubtableMutationCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = OpenApiAuthentication.REQUEST_ATTRIBUTE, required = false)
            Object authenticationValue,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
            Object sessionValue,
            HttpServletRequest request
    ) {
        var result = compositions.mutateSubtable(
                session(authenticationValue, sessionValue, request),
                moduleCode, recordId, fieldCode, body, idempotencyKey);
        return ResponseEntity.ok(success(result, request));
    }

    private static OpenApiRecordFacade.Session session(
            Object authenticationValue,
            Object sessionValue,
            HttpServletRequest request
    ) {
        if (!(authenticationValue instanceof OpenApiAuthentication authentication)
                || !(sessionValue instanceof OpenApiMachineSession machineSession)
                || !machineSession.equals(authentication.session())
                || !Objects.equals(machineSession.systemId(), authentication.application().systemId())
                || !Objects.equals(machineSession.tenantId(), authentication.application().tenantId())
                || !Objects.equals(
                        machineSession.memberId(), authentication.application().serviceMemberId())) {
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

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(
                value,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    interface CompositionOperations {
        OpenApiRecordFacade.RelationPageView relations(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                int page,
                int size);

        OpenApiRecordFacade.SubtablePageView subtable(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                int page,
                int size);

        OpenApiRecordFacade.MutationReceipt mutateRelation(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                OpenApiRecordFacade.RelationMutationCommand command,
                String idempotencyKey);

        OpenApiRecordFacade.MutationReceipt mutateSubtable(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                OpenApiRecordFacade.SubtableMutationCommand command,
                String idempotencyKey);
    }

    private record FacadeCompositionOperations(OpenApiRecordFacade delegate)
            implements CompositionOperations {
        private FacadeCompositionOperations {
            Objects.requireNonNull(delegate, "compositions");
        }

        @Override
        public OpenApiRecordFacade.RelationPageView relations(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                int page,
                int size) {
            return delegate.relations(session, moduleCode, recordId, fieldCode, page, size);
        }

        @Override
        public OpenApiRecordFacade.SubtablePageView subtable(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                int page,
                int size) {
            return delegate.subtable(session, moduleCode, recordId, fieldCode, page, size);
        }

        @Override
        public OpenApiRecordFacade.MutationReceipt mutateRelation(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                OpenApiRecordFacade.RelationMutationCommand command,
                String idempotencyKey) {
            return delegate.mutateRelation(
                    session, moduleCode, recordId, fieldCode, command, idempotencyKey);
        }

        @Override
        public OpenApiRecordFacade.MutationReceipt mutateSubtable(
                OpenApiRecordFacade.Session session,
                String moduleCode,
                String recordId,
                String fieldCode,
                OpenApiRecordFacade.SubtableMutationCommand command,
                String idempotencyKey) {
            return delegate.mutateSubtable(
                    session, moduleCode, recordId, fieldCode, command, idempotencyKey);
        }
    }
}
