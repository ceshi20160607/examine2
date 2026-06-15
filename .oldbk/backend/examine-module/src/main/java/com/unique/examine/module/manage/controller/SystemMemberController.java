package com.unique.examine.module.manage.controller;

import java.util.List;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.MemberInviteBO;
import com.unique.examine.module.manage.bo.MemberRoleAssignBO;
import com.unique.examine.module.manage.bo.MemberUpdateBO;
import com.unique.examine.module.manage.bo.StatusChangeBO;
import com.unique.examine.module.manage.service.SystemRbacService;
import com.unique.examine.module.manage.vo.MemberVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统成员接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/members")
public class SystemMemberController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SystemRbacService systemRbacService;

    private final AuthSessionService authSessionService;

    /**
     * 查询成员列表。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param keyword 关键字
     * @param status 状态
     * @return 成员列表
     */
    @Operation(summary = "查询成员列表")
    @GetMapping
    public List<MemberVO> listMembers(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        validateLogin(authorization);
        return systemRbacService.listMembers(systemId, keyword, status);
    }

    /**
     * 邀请成员。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param inviteBO 邀请入参
     * @return 成员
     */
    @Operation(summary = "邀请成员")
    @PostMapping("/invitations")
    public MemberVO inviteMember(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody MemberInviteBO inviteBO) {
        validateLogin(authorization);
        return systemRbacService.inviteMember(systemId, inviteBO);
    }

    /**
     * 查询当前成员。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 成员
     */
    @Operation(summary = "查询当前成员")
    @GetMapping("/current")
    public MemberVO currentMember(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        return systemRbacService.currentMember(currentAccountId(authorization), systemId);
    }

    /**
     * 查询成员详情。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @return 成员
     */
    @Operation(summary = "查询成员详情")
    @GetMapping("/{memberId}")
    public MemberVO getMember(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long memberId) {
        validateLogin(authorization);
        return systemRbacService.getMember(systemId, memberId);
    }

    /**
     * 更新成员。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param updateBO 更新入参
     * @return 成员
     */
    @Operation(summary = "更新成员")
    @PutMapping("/{memberId}")
    public MemberVO updateMember(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long memberId, @Valid @RequestBody MemberUpdateBO updateBO) {
        validateLogin(authorization);
        return systemRbacService.updateMember(systemId, memberId, updateBO);
    }

    /**
     * 变更成员状态。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param statusBO 状态入参
     * @return 成员
     */
    @Operation(summary = "变更成员状态")
    @PatchMapping("/{memberId}/status")
    public MemberVO changeMemberStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long memberId, @Valid @RequestBody StatusChangeBO statusBO) {
        validateLogin(authorization);
        return systemRbacService.changeMemberStatus(systemId, memberId, statusBO);
    }

    /**
     * 分配成员角色。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param assignBO 角色分配入参
     * @return 成员
     */
    @Operation(summary = "分配成员角色")
    @PutMapping("/{memberId}/roles")
    public MemberVO assignMemberRoles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long memberId,
            @Valid @RequestBody MemberRoleAssignBO assignBO) {
        validateLogin(authorization);
        return systemRbacService.assignMemberRoles(systemId, memberId, assignBO);
    }

    private void validateLogin(String authorization) {
        currentAccountId(authorization);
    }

    private Long currentAccountId(String authorization) {
        CurrentUserVO currentUser = authSessionService.me(resolveBearer(authorization));
        return Long.valueOf(currentUser.getAccount().getAccountId());
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
