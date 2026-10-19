package com.unique.unexamine.sys.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysLogLogin;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysSystem;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysLogLoginMapper;
import com.unique.unexamine.sys.base.mapper.UnSysMemberMapper;
import com.unique.unexamine.sys.base.mapper.UnSysSystemMapper;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMemberMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** 系统成员登录、租户切换所需的会话和可进入租户查询。 */
@Tag(name = "系统会话", description = "系统成员登录、当前会话和租户切换。")
@RestController
@RequestMapping("/api/system/session")
public class SystemSessionController {
    private final UnSysSystemMapper systemMapper;
    private final UnSysMemberMapper memberMapper;
    private final UnSysTenantMapper tenantMapper;
    private final UnSysTenantMemberMapper tenantMemberMapper;
    private final UnSysLogLoginMapper loginLogMapper;
    private final SystemSessionAccess access;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SystemSessionController(UnSysSystemMapper systemMapper, UnSysMemberMapper memberMapper,
                                   UnSysTenantMapper tenantMapper, UnSysTenantMemberMapper tenantMemberMapper,
                                   UnSysLogLoginMapper loginLogMapper, SystemSessionAccess access) {
        this.systemMapper = systemMapper;
        this.memberMapper = memberMapper;
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.loginLogMapper = loginLogMapper;
        this.access = access;
    }

    /** 登录时同时校验系统及成员状态；无论成败均保留登录日志。 */
    @Operation(summary = "登录（系统会话）")
    @PostMapping
    public SessionView login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        UnSysSystem system = systemMapper.selectOne(new LambdaQueryWrapper<UnSysSystem>()
                .eq(UnSysSystem::getSystemCode, body.systemCode().trim()));
        UnSysMember member = system == null ? null : memberMapper.selectOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, system.getId()).eq(UnSysMember::getAccount, body.account().trim()));
        boolean valid = system != null && Byte.valueOf((byte) 1).equals(system.getStatus())
                && member != null && Byte.valueOf((byte) 1).equals(member.getStatus())
                && encoder.matches(body.password(), member.getPasswordHash());
        if (system != null) {
            UnSysLogLogin log = new UnSysLogLogin();
            log.setSystemId(system.getId());
            log.setMemberId(member == null ? null : member.getId());
            log.setLoginAccount(body.account().trim());
            log.setSuccess((byte) (valid ? 1 : 0));
            log.setFailureReason(valid ? null : "账号不存在、停用或密码错误");
            log.setIpAddress(request.getRemoteAddr());
            loginLogMapper.insert(log);
        }
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "系统、账号或密码错误");
        }
        member.setLastLoginTime(LocalDateTime.now());
        memberMapper.updateById(member);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("systemMemberId", member.getId());
        session.setMaxInactiveInterval(8 * 60 * 60);
        return view(system, member);
    }

    /** 会话读取重新检查成员和系统状态，停用后立即失效。 */
    @Operation(summary = "查询当前信息（系统会话）")
    @GetMapping
    public SessionView current(HttpServletRequest request) {
        UnSysMember member = access.systemMember(request);
        UnSysSystem system = systemMapper.selectById(member.getSystemId());
        if (system == null || !Byte.valueOf((byte) 1).equals(system.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "系统已停用");
        }
        return view(system, member);
    }

    /** 退出时销毁整个服务端会话。 */
    @Operation(summary = "退出登录（系统会话）")
    @DeleteMapping
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    /** 只返回当前成员实际加入且未停用的租户，供用户选择工作范围。 */
    @Operation(summary = "查询租户（系统会话）")
    @GetMapping("/tenants")
    public List<TenantView> tenants(HttpServletRequest request) {
        UnSysMember member = access.systemMember(request);
        return tenantMemberMapper.selectList(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getMemberId, member.getId()).eq(UnSysTenantMember::getStatus, 1))
                .stream().map(link -> {
                    UnSysTenant tenant = tenantMapper.selectById(link.getTenantId());
                    return tenant != null && Byte.valueOf((byte) 1).equals(tenant.getStatus())
                            && member.getSystemId().equals(tenant.getSystemId())
                            ? new TenantView(tenant.getId(), tenant.getTenantCode(), tenant.getTenantName(), link.getId()) : null;
                }).filter(item -> item != null).toList();
    }

    private SessionView view(UnSysSystem system, UnSysMember member) {
        return new SessionView(system.getId(), system.getSystemCode(), member.getId(),
                member.getAccount(), member.getDisplayName());
    }

    /** 客户端提交系统编码与凭据，不提交权限或租户身份。 */
    public record LoginRequest(@NotBlank @Size(max = 64) String systemCode,
                               @NotBlank @Size(max = 64) String account,
                               @NotBlank String password) {}

    /** 系统会话只公开工作入口需要的身份信息。 */
    public record SessionView(Long systemId, String systemCode, Long memberId, String account, String displayName) {}

    /** 当前系统成员可进入的租户。 */
    public record TenantView(Long id, String code, String name, Long tenantMemberId) {}
}
