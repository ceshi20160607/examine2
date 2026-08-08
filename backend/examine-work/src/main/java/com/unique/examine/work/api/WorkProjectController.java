package com.unique.examine.work.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.work.domain.WorkProjectQuery;
import com.unique.examine.work.service.WorkProjectService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/work/projects")
public class WorkProjectController {
    private final WorkProjectService service;

    public WorkProjectController(WorkProjectService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<WorkProjectApiModels.ProjectPage> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") WorkProjectQuery.StatusFilter status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var result = service.page(
                WorkRequestSession.require(session, systemId),
                new WorkProjectQuery(keyword, status, page, size));
        return success(WorkProjectApiModels.ProjectPage.from(result), request);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkProjectApiModels.ProjectView>> create(
            @PathVariable long systemId,
            @RequestBody WorkProjectApiModels.CreateProject body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var project = service.create(
                WorkRequestSession.require(session, systemId),
                body.title(), body.description());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(
                        WorkProjectApiModels.ProjectView.from(project),
                        request));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<WorkProjectApiModels.ProjectView> get(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var project = service.get(
                WorkRequestSession.require(session, systemId), projectId);
        return success(WorkProjectApiModels.ProjectView.from(project), request);
    }

    @PutMapping("/{projectId}")
    public ApiResponse<WorkProjectApiModels.ProjectView> update(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @RequestBody WorkProjectApiModels.UpdateProject body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var project = service.update(
                WorkRequestSession.require(session, systemId), projectId,
                body.title(), body.description(), body.version());
        return success(WorkProjectApiModels.ProjectView.from(project), request);
    }

    @PostMapping("/{projectId}:archive")
    public ApiResponse<WorkProjectApiModels.ProjectView> archive(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @RequestBody WorkProjectApiModels.ProjectVersion body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var project = service.archive(
                WorkRequestSession.require(session, systemId),
                projectId, body.version());
        return success(WorkProjectApiModels.ProjectView.from(project), request);
    }

    @PostMapping("/{projectId}:reopen")
    public ApiResponse<WorkProjectApiModels.ProjectView> reopen(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @RequestBody WorkProjectApiModels.ProjectVersion body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var project = service.reopen(
                WorkRequestSession.require(session, systemId),
                projectId, body.version());
        return success(WorkProjectApiModels.ProjectView.from(project), request);
    }

    @GetMapping("/{projectId}/members")
    public ApiResponse<List<WorkProjectApiModels.MemberView>> members(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var result = service.members(
                        WorkRequestSession.require(session, systemId), projectId)
                .stream().map(WorkProjectApiModels.MemberView::from).toList();
        return success(result, request);
    }

    @PostMapping("/{projectId}/members")
    public ResponseEntity<ApiResponse<WorkProjectApiModels.MemberView>> addMember(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @RequestBody WorkProjectApiModels.AddMember body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var member = service.addMember(
                WorkRequestSession.require(session, systemId), projectId,
                body.memberId(), body.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(
                        WorkProjectApiModels.MemberView.from(member), request));
    }

    @PutMapping("/{projectId}/members/{memberId}")
    public ApiResponse<WorkProjectApiModels.MemberView> updateMember(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @PathVariable long memberId,
            @RequestBody WorkProjectApiModels.UpdateMember body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var member = service.updateMember(
                WorkRequestSession.require(session, systemId), projectId,
                memberId, body.role(), body.version());
        return success(WorkProjectApiModels.MemberView.from(member), request);
    }

    @PostMapping("/{projectId}/members/{memberId}:remove")
    public ApiResponse<WorkProjectApiModels.MemberView> removeMember(
            @PathVariable long systemId,
            @PathVariable long projectId,
            @PathVariable long memberId,
            @RequestBody WorkProjectApiModels.MemberVersion body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var member = service.removeMember(
                WorkRequestSession.require(session, systemId), projectId,
                memberId, body.version());
        return success(WorkProjectApiModels.MemberView.from(member), request);
    }

    private static <T> ApiResponse<T> success(
            T data, HttpServletRequest request
    ) {
        return ApiResponse.success(
                data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(
            HttpServletRequest request, String name
    ) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
