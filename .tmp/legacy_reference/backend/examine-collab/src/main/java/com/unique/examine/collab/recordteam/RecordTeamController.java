package com.unique.examine.collab.recordteam;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}"
                + "/records/{recordId}")
public class RecordTeamController {
    private final RecordTeamService service;

    public RecordTeamController(RecordTeamService service) {
        this.service = service;
    }

    @GetMapping("/team")
    public ApiResponse<RecordTeamApi.TeamResponse> team(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = RecordTeamHttpSession.require(sessionValue, systemId);
        session.requireView(moduleCode);
        return ok(RecordTeamApi.TeamResponse.from(service.team(session.key(recordId))), request);
    }

    @PostMapping("/team:initialize")
    public ResponseEntity<ApiResponse<RecordTeamApi.InitializeTeamResponse>> initialize(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = RecordTeamHttpSession.require(sessionValue, systemId);
        var initialization = service.initialize(
                session.actor(moduleCode),
                session.key(recordId));
        var status = initialization.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ok(RecordTeamApi.InitializeTeamResponse.from(initialization), request));
    }

    @PostMapping("/team/members")
    public ResponseEntity<ApiResponse<RecordTeamApi.TeamResponse>> addMember(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordTeamApi.AddMemberRequest body,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = RecordTeamHttpSession.require(sessionValue, systemId);
        var changed = service.addMember(
                session.actor(moduleCode),
                session.key(recordId),
                body.memberId(),
                body.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ok(RecordTeamApi.TeamResponse.from(changed), request));
    }

    @PutMapping("/team/members/{memberId}/role")
    public ApiResponse<RecordTeamApi.TeamResponse> changeRole(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String memberId,
            @RequestBody RecordTeamApi.ChangeRoleRequest body,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = RecordTeamHttpSession.require(sessionValue, systemId);
        var changed = service.changeRole(
                session.actor(moduleCode),
                session.key(recordId),
                memberId,
                body.role());
        return ok(RecordTeamApi.TeamResponse.from(changed), request);
    }

    @DeleteMapping("/team/members/{memberId}")
    public ApiResponse<RecordTeamApi.TeamResponse> removeMember(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @PathVariable String memberId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = RecordTeamHttpSession.require(sessionValue, systemId);
        var changed = service.removeMember(
                session.actor(moduleCode),
                session.key(recordId),
                memberId);
        return ok(RecordTeamApi.TeamResponse.from(changed), request);
    }

    @PostMapping("/team/transfer")
    public ApiResponse<RecordTeamApi.TeamResponse> transferOwnership(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestBody RecordTeamApi.TransferOwnershipRequest body,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = RecordTeamHttpSession.require(sessionValue, systemId);
        var changed = service.transferOwnership(
                session.actor(moduleCode),
                session.key(recordId),
                body.targetMemberId());
        return ok(RecordTeamApi.TeamResponse.from(changed), request);
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
