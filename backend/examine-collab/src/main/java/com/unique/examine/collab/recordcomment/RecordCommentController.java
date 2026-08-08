package com.unique.examine.collab.recordcomment;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping(
        "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}"
                + "/records/{recordId}/comments")
public class RecordCommentController {
    private final RecordCommentService service;

    public RecordCommentController(RecordCommentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<RecordCommentApi.PageResponse> page(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId, moduleCode, recordId);
        return ok(RecordCommentApi.PageResponse.from(
                service.page(actor, page, size),
                actor), request);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecordCommentApi.CommentResponse>> create(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RecordCommentApi.CreateRequest body,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId, moduleCode, recordId);
        var creation = service.create(
                actor,
                body.body(),
                body.parentCommentId(),
                idempotencyKey,
                body.mentionedMemberIds());
        var response = ok(RecordCommentApi.CommentResponse.from(creation.comment(), actor), request);
        return ResponseEntity
                .status(creation.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(response);
    }

    @PutMapping("/{commentId}")
    public ApiResponse<RecordCommentApi.CommentResponse> update(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String commentId,
            @RequestBody RecordCommentApi.UpdateRequest body,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId, moduleCode, recordId);
        return ok(RecordCommentApi.CommentResponse.from(
                service.update(actor, commentId, body.body(), body.version()),
                actor), request);
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<RecordCommentApi.CommentResponse> delete(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String commentId,
            @RequestBody RecordCommentApi.DeleteRequest body,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var actor = actor(sessionValue, systemId, moduleCode, recordId);
        return ok(RecordCommentApi.CommentResponse.from(
                service.delete(actor, commentId, body.version()),
                actor), request);
    }

    private static RecordCommentActor actor(
            Object sessionValue,
            long systemId,
            String moduleCode,
            long recordId
    ) {
        return RecordCommentHttpSession.require(sessionValue, systemId)
                .actor(moduleCode, recordId);
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
