package com.unique.examine.module.manage.controller;

import java.util.List;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.DictDeleteBO;
import com.unique.examine.module.manage.bo.DictItemSaveBO;
import com.unique.examine.module.manage.bo.DictItemUpdateBO;
import com.unique.examine.module.manage.bo.DictStatusBO;
import com.unique.examine.module.manage.bo.DictTypeSaveBO;
import com.unique.examine.module.manage.bo.DictTypeUpdateBO;
import com.unique.examine.module.manage.service.DictManageService;
import com.unique.examine.module.manage.vo.DictItemVO;
import com.unique.examine.module.manage.vo.DictTypeVO;
import com.unique.examine.module.manage.vo.DictUsageVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 系统字典管理接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/dict")
public class DictManageController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final DictManageService dictManageService;

    private final AuthSessionService authSessionService;

    /**
     * 查询字典类型列表。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param scopeType 作用域
     * @param tenantId 租户 ID
     * @param keyword 关键字
     * @param status 状态
     * @return 字典类型列表
     */
    @Operation(summary = "查询字典类型列表")
    @GetMapping("/types")
    public List<DictTypeVO> listTypes(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String tenantId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        validateLogin(authorization);
        return dictManageService.listTypes(systemId, scopeType, tenantId, keyword, status);
    }

    /**
     * 创建字典类型。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 字典类型
     */
    @Operation(summary = "创建字典类型")
    @PostMapping("/types")
    public DictTypeVO createType(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody DictTypeSaveBO saveBO) {
        validateLogin(authorization);
        return dictManageService.createType(systemId, saveBO);
    }

    /**
     * 更新字典类型。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param updateBO 更新入参
     * @return 字典类型
     */
    @Operation(summary = "更新字典类型")
    @PutMapping("/types/{dictTypeId}")
    public DictTypeVO updateType(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictTypeId,
            @Valid @RequestBody DictTypeUpdateBO updateBO) {
        validateLogin(authorization);
        return dictManageService.updateType(systemId, dictTypeId, updateBO);
    }

    /**
     * 变更字典类型状态。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param statusBO 状态入参
     * @return 字典类型
     */
    @Operation(summary = "变更字典类型状态")
    @PatchMapping("/types/{dictTypeId}/status")
    public DictTypeVO changeTypeStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictTypeId, @Valid @RequestBody DictStatusBO statusBO) {
        validateLogin(authorization);
        return dictManageService.changeTypeStatus(systemId, dictTypeId, statusBO);
    }

    /**
     * 查询字典项。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param parentId 父项 ID
     * @param keyword 关键字
     * @param status 状态
     * @param treeMode 是否树模式
     * @return 字典项列表
     */
    @Operation(summary = "查询字典项")
    @GetMapping("/types/{dictTypeId}/items")
    public List<DictItemVO> listItems(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictTypeId,
            @RequestParam(required = false) String parentId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean treeMode) {
        validateLogin(authorization);
        return dictManageService.listItems(systemId, dictTypeId, parentId, keyword, status, treeMode);
    }

    /**
     * 创建字典项。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param saveBO 保存入参
     * @return 字典项
     */
    @Operation(summary = "创建字典项")
    @PostMapping("/types/{dictTypeId}/items")
    public DictItemVO createItem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictTypeId,
            @Valid @RequestBody DictItemSaveBO saveBO) {
        validateLogin(authorization);
        return dictManageService.createItem(systemId, dictTypeId, saveBO);
    }

    /**
     * 更新字典项。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param updateBO 更新入参
     * @return 字典项
     */
    @Operation(summary = "更新字典项")
    @PutMapping("/items/{dictItemId}")
    public DictItemVO updateItem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictItemId,
            @Valid @RequestBody DictItemUpdateBO updateBO) {
        validateLogin(authorization);
        return dictManageService.updateItem(systemId, dictItemId, updateBO);
    }

    /**
     * 变更字典项状态。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param statusBO 状态入参
     * @return 字典项
     */
    @Operation(summary = "变更字典项状态")
    @PatchMapping("/items/{dictItemId}/status")
    public DictItemVO changeItemStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictItemId, @Valid @RequestBody DictStatusBO statusBO) {
        validateLogin(authorization);
        return dictManageService.changeItemStatus(systemId, dictItemId, statusBO);
    }

    /**
     * 查询字典使用情况。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @return 使用情况
     */
    @Operation(summary = "查询字典使用情况")
    @GetMapping("/types/{dictTypeId}/usages")
    public DictUsageVO usages(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictTypeId) {
        validateLogin(authorization);
        return dictManageService.usages(systemId, dictTypeId);
    }

    /**
     * 删除字典类型。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param deleteBO 删除入参
     * @return 字典类型
     */
    @Operation(summary = "删除字典类型")
    @DeleteMapping("/types/{dictTypeId}")
    public DictTypeVO deleteType(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictTypeId, @Valid @RequestBody DictDeleteBO deleteBO) {
        validateLogin(authorization);
        return dictManageService.deleteType(systemId, dictTypeId, deleteBO);
    }

    /**
     * 删除字典项。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param deleteBO 删除入参
     * @return 字典项
     */
    @Operation(summary = "删除字典项")
    @DeleteMapping("/items/{dictItemId}")
    public DictItemVO deleteItem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long dictItemId, @Valid @RequestBody DictDeleteBO deleteBO) {
        validateLogin(authorization);
        return dictManageService.deleteItem(systemId, dictItemId, deleteBO);
    }

    private void validateLogin(String authorization) {
        authSessionService.me(resolveBearer(authorization));
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
