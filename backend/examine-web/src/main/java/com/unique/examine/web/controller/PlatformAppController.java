package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.security.PlatPermCodes;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.app.entity.po.AppClient;
import com.unique.examine.web.service.PlatPermissionService;
import com.unique.examine.web.service.PlatformAppManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "平台态-对外应用（appId/secret）")
@RestController
@RequestMapping("/v1/platform/apps")
public class PlatformAppController {

    @Autowired
    private PlatPermissionService platPermissionService;
    @Autowired
    private PlatformAppManageService platformAppManageService;

    @Operation(summary = "创建对外应用（生成 accessKey/secret；secret 仅本次返回）")
    @PostMapping
    public ApiResult<PlatformAppManageService.CreateClientResult> create(@RequestBody PlatformAppManageService.CreateClientBody body) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.APP_CREATE);
        return ApiResult.ok(platformAppManageService.createClient(platId, body));
    }

    @Operation(summary = "对外应用列表（不返回 secret）")
    @GetMapping
    public ApiResult<List<AppClient>> list() {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requireAccount(platId);
        return ApiResult.ok(platformAppManageService.listClients());
    }

    @Operation(summary = "对外应用详情（不返回 secret；返回当前 active accessKey）")
    @GetMapping("/{id}")
    public ApiResult<PlatformAppManageService.ClientDetail> detail(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requireAccount(platId);
        return ApiResult.ok(platformAppManageService.getClientDetail(id));
    }

    @Operation(summary = "更新对外应用资料（不含 clientCode/密钥；secret 仍仅创建与轮换可见）")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable("id") Long id, @RequestBody PlatformAppManageService.UpdateClientBody body) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.APP_STATUS);
        platformAppManageService.updateClient(platId, id, body);
        return ApiResult.ok();
    }

    public record StatusBody(Integer status) {
    }

    @Operation(summary = "启用/停用对外应用")
    @PostMapping("/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable("id") Long id, @RequestBody StatusBody body) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.APP_STATUS);
        platformAppManageService.updateClientStatus(platId, id, body == null ? null : body.status());
        return ApiResult.ok();
    }

    @Operation(summary = "删除对外应用（连带停用凭证）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.APP_DELETE);
        platformAppManageService.deleteClient(platId, id);
        return ApiResult.ok();
    }

    @Operation(summary = "轮换 secret（生成新的 accessKey/secret；secret 仅本次返回；旧凭证置为停用）")
    @PostMapping("/{id}/rotate-secret")
    public ApiResult<PlatformAppManageService.RotateSecretResult> rotateSecret(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        platPermissionService.requirePermission(platId, PlatPermCodes.APP_STATUS);
        return ApiResult.ok(platformAppManageService.rotateSecret(platId, id));
    }
}

