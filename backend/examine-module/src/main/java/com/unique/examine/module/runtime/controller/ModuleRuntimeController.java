package com.unique.examine.module.runtime.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.ModuleRuntimeService;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime")
public class ModuleRuntimeController {
    private final ModuleRuntimeService service;
    private final RecordRuntimeService recordService;

    public ModuleRuntimeController(ModuleRuntimeService service, RecordRuntimeService recordService) {
        this.service = service;
        this.recordService = recordService;
    }

    @GetMapping("/navigation")
    public ApiResponse<RuntimeViews.Navigation> navigation(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.navigation(RuntimeSession.require(value, systemId)), request);
    }

    @GetMapping("/modules/{moduleCode}/definition")
    public ApiResponse<RuntimeViews.Definition> definition(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.definition(RuntimeSession.require(value, systemId), moduleCode), request);
    }

    @GetMapping("/modules/{moduleCode}/record-schema")
    public ApiResponse<RecordRuntimeViews.RecordSchema> recordSchema(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.schema(RuntimeSession.require(value, systemId), moduleCode), request);
    }

    @GetMapping("/modules/{moduleCode}/records")
    public ApiResponse<RecordRuntimeViews.RecordPage> records(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String filter,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.list(RuntimeSession.require(value, systemId), moduleCode,
                page, size, status, sort, filter), request);
    }

    @PostMapping("/modules/{moduleCode}/records:query")
    public ApiResponse<RecordRuntimeViews.RecordPage> queryRecords(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestBody String body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.query(RuntimeSession.require(value, systemId), moduleCode, body), request);
    }

    @PostMapping("/modules/{moduleCode}/records:my-drafts-query")
    public ApiResponse<RecordRuntimeViews.RecordPage> queryMyDrafts(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestBody RecordRuntimeViews.MyDraftsQueryRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.myDraftsQuery(
                RuntimeSession.require(value, systemId), moduleCode, body), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:neighbors")
    public ApiResponse<RecordRuntimeViews.NeighborResponse> recordNeighbor(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.NeighborRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.neighbor(
                RuntimeSession.require(value, systemId),
                moduleCode,
                recordId,
                body,
                traceId(request)), request);
    }

    @GetMapping("/modules/{moduleCode}/records/{recordId}")
    public ApiResponse<RecordRuntimeViews.RecordDetail> recordDetail(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.detail(RuntimeSession.require(value, systemId), moduleCode, recordId), request);
    }

    @GetMapping("/modules/{moduleCode}/relations/{fieldCode}/candidates")
    public ApiResponse<RecordRuntimeViews.RelationCandidatePage> relationCandidates(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable String fieldCode,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.relationCandidates(RuntimeSession.require(value, systemId), moduleCode,
                fieldCode, q, page, size, traceId(request)), request);
    }

    @GetMapping("/modules/{moduleCode}/records/{recordId}/relations/{fieldCode}")
    public ApiResponse<RecordRuntimeViews.RelationPage> relations(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String fieldCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.relations(RuntimeSession.require(value, systemId), moduleCode, recordId,
                fieldCode, page, size, traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}/relations/{fieldCode}:mutate")
    public ApiResponse<RecordRuntimeViews.RecordMutationResponse> mutateRelation(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String fieldCode,
            @RequestBody RecordRuntimeViews.RelationMutationRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.mutateRelation(RuntimeSession.require(value, systemId), moduleCode, recordId,
                fieldCode, body, idempotencyKey, requestId(request), traceId(request)), request);
    }

    @GetMapping("/modules/{moduleCode}/records/{recordId}/subtables/{fieldCode}")
    public ApiResponse<RecordRuntimeViews.SubtablePage> subtable(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String fieldCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.subtable(RuntimeSession.require(value, systemId), moduleCode, recordId,
                fieldCode, page, size, traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}/subtables/{fieldCode}:mutate")
    public ApiResponse<RecordRuntimeViews.RecordMutationResponse> mutateSubtable(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String fieldCode,
            @RequestBody RecordRuntimeViews.SubtableMutationRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.mutateSubtable(RuntimeSession.require(value, systemId), moduleCode, recordId,
                fieldCode, body, idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}/references/{fieldCode}:retry")
    public ApiResponse<RecordRuntimeViews.ReferenceRetryResponse> retryReference(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String fieldCode,
            @RequestBody RecordRuntimeViews.ReferenceRetryRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.retryReference(RuntimeSession.require(value, systemId), moduleCode, recordId,
                fieldCode, body, idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records")
    public ResponseEntity<ApiResponse<RecordRuntimeViews.RecordDetail>> createRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestBody RecordRuntimeViews.CreateRecordRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        var response = recordService.create(RuntimeSession.require(value, systemId), moduleCode, body,
                idempotencyKey, requestId(request), traceId(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ok(response, request));
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:activate")
    public ApiResponse<RecordRuntimeViews.RecordDetail> activateRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.VersionCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.activate(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:archive")
    public ApiResponse<RecordRuntimeViews.RecordDetail> archiveRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.VersionCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.archive(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records:batch-archive")
    public ApiResponse<RecordRuntimeViews.BatchMutationResponse> batchArchiveRecords(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestBody RecordRuntimeViews.BatchCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.batchArchive(
                RuntimeSession.require(value, systemId),
                moduleCode,
                body,
                idempotencyKey,
                requestId(request),
                traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records:batch-trash")
    public ApiResponse<RecordRuntimeViews.BatchMutationResponse> batchTrashRecords(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestBody RecordRuntimeViews.BatchCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.batchTrash(
                RuntimeSession.require(value, systemId),
                moduleCode,
                body,
                idempotencyKey,
                requestId(request),
                traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records:batch-transfer")
    public ApiResponse<RecordRuntimeViews.BatchMutationResponse> batchTransferRecords(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestBody RecordRuntimeViews.BatchTransferRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.batchTransfer(
                RuntimeSession.require(value, systemId),
                moduleCode,
                body,
                idempotencyKey,
                requestId(request),
                traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records:batch-edit")
    public ApiResponse<RecordRuntimeViews.BatchMutationResponse> batchEditRecords(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @RequestBody RecordRuntimeViews.BatchEditRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.batchEdit(
                RuntimeSession.require(value, systemId),
                moduleCode,
                body,
                idempotencyKey,
                requestId(request),
                traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:unarchive")
    public ApiResponse<RecordRuntimeViews.RecordDetail> unarchiveRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.VersionCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.unarchive(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:trash")
    public ApiResponse<RecordRuntimeViews.RecordDetail> trashRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.VersionCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.trash(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:restore-from-trash")
    public ApiResponse<RecordRuntimeViews.RecordDetail> restoreRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.VersionCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.restoreFromTrash(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:discard")
    public ApiResponse<RecordRuntimeViews.RecordDetail> discardRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.VersionCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.discard(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:recover")
    public ApiResponse<RecordRuntimeViews.RecordDetail> recoverRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.VersionCommandRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.recover(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PutMapping("/modules/{moduleCode}/records/{recordId}")
    public ApiResponse<RecordRuntimeViews.RecordDetail> updateRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.UpdateRecordRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.update(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    @PostMapping("/modules/{moduleCode}/records/{recordId}:autosave")
    public ApiResponse<RecordRuntimeViews.AutosaveRecordResponse> autosaveRecord(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordRuntimeViews.AutosaveRecordRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(recordService.autosave(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request)), request);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestId(request), traceId(request));
    }

    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }

    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }
}
