package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.security.PlatPermCodes;
import com.unique.examine.core.security.SessionPayload;
import com.unique.examine.core.service.SessionService;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.plat.entity.po.PlatSystem;
import com.unique.examine.plat.entity.po.PlatTenant;
import com.unique.examine.plat.service.IPlatSystemService;
import com.unique.examine.plat.service.IPlatTenantService;
import com.unique.examine.plat.manage.PlatRbacManageService;
import com.unique.examine.plat.entity.dto.PlatMenuTreeNode;
import com.unique.examine.plat.manage.PlatPermissionManageService;
import com.unique.examine.module.manage.SystemModuleBootstrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "平台态-系统与租户")
@RestController
@RequestMapping("/v1/platform")
public class PlatformContextController {

    @Autowired
    private IPlatSystemService platSystemService;
    @Autowired
    private IPlatTenantService platTenantService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private PlatPermissionManageService platPermissionService;
    @Autowired
    private PlatRbacManageService platRbacManageService;
    @Autowired
    private SystemModuleBootstrapService systemModuleBootstrapService;

    @Operation(summary = "当前账号的平台级权限（RBAC：角色+菜单树+权限码；无绑定角色时回退账号列 plat_perm_codes；不含系统内 module 权限）")
    @GetMapping("/permissions/me")
    public ApiResult<Map<String, Object>> myPlatformPermissions() {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requireAccount(platId);
        Map<String, Object> m = new LinkedHashMap<>();
        boolean rbac = platRbacManageService.hasAccountRoleBinding(platId);
        m.put("rbacEnabled", rbac);
        m.put("roleCodes", rbac ? platRbacManageService.listRoleCodes(platId) : new ArrayList<String>());
        List<PlatMenuTreeNode> menus = rbac ? platRbacManageService.buildMenuTree(platId) : List.of();
        m.put("menus", menus);
        var effective = platRbacManageService.resolveEffectivePermCodes(platId, null);
        m.put("platPermCodes", effective.stream().sorted().collect(Collectors.toList()));
        m.put("canCreateSystem", effective.contains(PlatPermCodes.SYSTEM_CREATE) ? 1 : 0);
        return ApiResult.ok(m);
    }

    @Operation(summary = "我创建的系统列表")
    @GetMapping("/systems")
    public ApiResult<List<PlatSystem>> mySystems() {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(platSystemService.lambdaQuery()
                .eq(PlatSystem::getOwnerPlatAccountId, platId)
                .orderByDesc(PlatSystem::getCreateTime)
                .list());
    }

    public record CreateSystemBody(String name, Integer multiTenantEnabled) {}

    @Operation(summary = "创建自建系统（需平台权限 SYSTEM_CREATE；RBAC 由菜单绑定该权限码）")
    @PostMapping("/systems")
    public ApiResult<PlatSystem> createSystem(@RequestBody CreateSystemBody body) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.SYSTEM_CREATE);
        if (body == null || !StringUtils.hasText(body.name())) {
            throw new BusinessException("系统名称不能为空");
        }
        PlatSystem s = new PlatSystem();
        s.setName(body.name().trim());
        s.setMultiTenantEnabled(body.multiTenantEnabled() == null ? 0 : body.multiTenantEnabled());
        s.setStatus(1);
        s.setOwnerPlatAccountId(platId);
        s.setCreateUserId(platId);
        s.setUpdateUserId(platId);
        platSystemService.save(s);
        systemModuleBootstrapService.afterSystemCreated(s, platId);
        return ApiResult.ok(s);
    }

    public record SystemStatusBody(Integer status) {}

    @Operation(summary = "启用/停用自建系统（需 SYSTEM_STATUS；仅所有者；platform 占位 id=0 不可改）")
    @PostMapping("/systems/{id}/status")
    public ApiResult<Void> updateSystemStatus(@PathVariable("id") Long id, @RequestBody SystemStatusBody body) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.SYSTEM_STATUS);
        if (id == null || id == 0L) {
            throw new BusinessException(403, "不能修改平台占位系统");
        }
        if (body == null || body.status() == null || (body.status() != 1 && body.status() != 2)) {
            throw new BusinessException("status 须为 1=启用 或 2=停用");
        }
        PlatSystem s = platSystemService.getById(id);
        requireOwner(platId, s);
        s.setStatus(body.status());
        s.setUpdateUserId(platId);
        platSystemService.updateById(s);
        return ApiResult.ok();
    }

    @Operation(summary = "删除自建系统（软删 status=2；需 SYSTEM_DELETE；仅所有者；id=0 不可删）")
    @DeleteMapping("/systems/{id}")
    public ApiResult<Void> deleteSystem(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.SYSTEM_DELETE);
        if (id == null || id == 0L) {
            throw new BusinessException(403, "不能删除平台占位系统");
        }
        PlatSystem s = platSystemService.getById(id);
        requireOwner(platId, s);
        s.setStatus(2);
        s.setUpdateUserId(platId);
        platSystemService.updateById(s);
        return ApiResult.ok();
    }

    public record EnterSystemBody(Long systemId) {}

    @Operation(summary = "进入系统（更新 token 对应会话中的 systemId/tenantId）")
    @PostMapping("/context/enter-system")
    public ApiResult<SessionPayload> enterSystem(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestBody EnterSystemBody body) {
        String token = resolveBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("token 缺失");
        }
        if (body == null || body.systemId() == null) {
            throw new BusinessException("systemId 不能为空");
        }
        PlatSystem s = platSystemService.getById(body.systemId());
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BusinessException("系统不存在或已停用");
        }
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null || !platId.equals(s.getOwnerPlatAccountId())) {
            throw new BusinessException("无权限进入该系统");
        }

        SessionPayload cur = sessionService.getSession(token)
                .orElseThrow(() -> new BusinessException("登录已过期"));

        long tenantId;
        if (s.getMultiTenantEnabled() != null && s.getMultiTenantEnabled() == 1) {
            tenantId = s.getDefaultTenantId() == null ? 0L : s.getDefaultTenantId();
        } else {
            tenantId = 0L;
        }
        SessionPayload next = new SessionPayload(cur.platId(), cur.username(), s.getId(), tenantId);
        sessionService.updateSession(token, next);
        if (s.getMultiTenantEnabled() != null && s.getMultiTenantEnabled() == 1 && tenantId > 0) {
            systemModuleBootstrapService.ensureTenantModuleSeed(s, tenantId, platId);
        }
        return ApiResult.ok(next);
    }

    @Operation(summary = "租户列表（按 systemId）")
    @GetMapping("/tenants")
    public ApiResult<List<PlatTenant>> listTenants(@RequestParam("systemId") Long systemId) {
        if (systemId == null) {
            throw new BusinessException("systemId 不能为空");
        }
        Long platId = AuthContextHolder.getPlatId();
        PlatSystem s = platSystemService.getById(systemId);
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BusinessException("系统不存在或已停用");
        }
        if (platId == null || !platId.equals(s.getOwnerPlatAccountId())) {
            throw new BusinessException("无权限查看该系统租户");
        }
        return ApiResult.ok(platTenantService.lambdaQuery()
                .eq(PlatTenant::getSystemId, systemId)
                .eq(PlatTenant::getStatus, 1)
                .orderByDesc(PlatTenant::getCreateTime)
                .list());
    }

    public record SelectTenantBody(Long tenantId) {}

    @Operation(summary = "选择租户（更新 token 对应会话中的 tenantId）")
    @PostMapping("/context/select-tenant")
    public ApiResult<SessionPayload> selectTenant(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestBody SelectTenantBody body) {
        String token = resolveBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("token 缺失");
        }
        if (body == null || body.tenantId() == null) {
            throw new BusinessException("tenantId 不能为空");
        }
        SessionPayload cur = sessionService.getSession(token)
                .orElseThrow(() -> new BusinessException("登录已过期"));

        if (cur.systemId() == null || cur.systemId() == 0L) {
            throw new BusinessException("请先进入系统");
        }
        PlatSystem s = platSystemService.getById(cur.systemId());
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BusinessException("系统不存在或已停用");
        }
        if (s.getMultiTenantEnabled() == null || s.getMultiTenantEnabled() != 1) {
            throw new BusinessException("该系统未开启多租户");
        }
        PlatTenant t = platTenantService.getById(body.tenantId());
        if (t == null || t.getStatus() == null || t.getStatus() != 1 || t.getSystemId() == null || !t.getSystemId().equals(cur.systemId())) {
            throw new BusinessException("租户不存在或不属于当前系统");
        }

        SessionPayload next = new SessionPayload(cur.platId(), cur.username(), cur.systemId(), t.getId());
        sessionService.updateSession(token, next);
        systemModuleBootstrapService.ensureTenantModuleSeed(s, t.getId(), cur.platId());
        return ApiResult.ok(next);
    }

    private static void requireOwner(Long platId, PlatSystem s) {
        if (s == null) {
            throw new BusinessException("系统不存在");
        }
        if (platId == null || !platId.equals(s.getOwnerPlatAccountId())) {
            throw new BusinessException(403, "仅系统所有者可操作");
        }
    }

    private static String resolveBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}
