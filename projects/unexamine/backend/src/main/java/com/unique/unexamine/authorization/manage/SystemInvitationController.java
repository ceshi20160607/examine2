package com.unique.unexamine.authorization.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台账号在接受邀请前只能读取邀请内容，不能因此取得系统成员权限。 */
@RestController
@RequestMapping("/api/invitations")
public class SystemInvitationController {
    private final SystemInvitationService service;

    public SystemInvitationController(SystemInvitationService service) { this.service = service; }

    @PostMapping("/preview")
    public ApiResult<SystemInvitationModels.InvitationView> preview(
            @Valid @RequestBody SystemInvitationModels.AcceptRequest input) {
        return ApiResult.ok(service.preview(AuthenticationContextHolder.require(), input.token()));
    }

    @PostMapping("/accept")
    public ApiResult<SystemInvitationModels.AcceptedInvitation> accept(
            @Valid @RequestBody SystemInvitationModels.AcceptRequest input, HttpServletRequest request) {
        return ApiResult.ok(service.accept(AuthenticationContextHolder.require(), input.token(),
                TraceIdFilter.current(request)));
    }
}
