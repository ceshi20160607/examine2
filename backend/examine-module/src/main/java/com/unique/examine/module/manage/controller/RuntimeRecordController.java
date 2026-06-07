package com.unique.examine.module.manage.controller;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.RecordQueryBO;
import com.unique.examine.module.manage.bo.RecordSaveBO;
import com.unique.examine.module.manage.bo.RecordSubmitBO;
import com.unique.examine.module.manage.bo.RecordUpdateBO;
import com.unique.examine.module.manage.service.RuntimeRecordService;
import com.unique.examine.module.manage.vo.RecordDetailVO;
import com.unique.examine.module.manage.vo.RecordHistoryVO;
import com.unique.examine.module.manage.vo.RecordListItemVO;
import com.unique.examine.module.manage.vo.RecordMutationResultVO;
import com.unique.examine.module.manage.vo.RecordRelationVO;
import com.unique.examine.module.manage.vo.RuntimeMenuVO;
import com.unique.examine.module.manage.vo.RuntimeModuleSchemaVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用运行台记录接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/runtime")
public class RuntimeRecordController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RuntimeRecordService runtimeRecordService;

    private final AuthSessionService authSessionService;

    /**
     * 查询运行台菜单。
     */
    @Operation(summary = "查询运行台菜单")
    @GetMapping("/menus")
    public List<RuntimeMenuVO> runtimeMenus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return runtimeRecordService.runtimeMenus(systemId);
    }

    /**
     * 查询模块运行态 schema。
     */
    @Operation(summary = "查询模块运行态 schema")
    @GetMapping("/modules/{moduleId}/schema")
    public RuntimeModuleSchemaVO moduleSchema(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId) {
        validateLogin(authorization);
        return runtimeRecordService.moduleSchema(systemId, moduleId);
    }

    /**
     * 查询运行记录。
     */
    @Operation(summary = "查询运行记录")
    @PostMapping("/modules/{moduleId}/records/query")
    public PageResult<RecordListItemVO> queryRecords(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody RecordQueryBO queryBO) {
        validateLogin(authorization);
        return runtimeRecordService.queryRecords(systemId, moduleId, queryBO);
    }

    /**
     * 创建运行记录。
     */
    @Operation(summary = "创建运行记录")
    @PostMapping("/modules/{moduleId}/records")
    public RecordMutationResultVO createRecord(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @Valid @RequestBody RecordSaveBO saveBO) {
        validateLogin(authorization);
        return runtimeRecordService.createRecord(systemId, moduleId, saveBO);
    }

    /**
     * 查询运行记录详情。
     */
    @Operation(summary = "查询运行记录详情")
    @GetMapping("/modules/{moduleId}/records/{recordId}")
    public RecordDetailVO recordDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        validateLogin(authorization);
        return runtimeRecordService.recordDetail(systemId, moduleId, recordId);
    }

    /**
     * 更新运行记录。
     */
    @Operation(summary = "更新运行记录")
    @PutMapping("/modules/{moduleId}/records/{recordId}")
    public RecordMutationResultVO updateRecord(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId,
            @Valid @RequestBody RecordUpdateBO updateBO) {
        validateLogin(authorization);
        return runtimeRecordService.updateRecord(systemId, moduleId, recordId, updateBO);
    }

    /**
     * 软删除运行记录。
     */
    @Operation(summary = "软删除运行记录")
    @DeleteMapping("/modules/{moduleId}/records/{recordId}")
    public RecordMutationResultVO deleteRecord(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        validateLogin(authorization);
        return runtimeRecordService.deleteRecord(systemId, moduleId, recordId);
    }

    /**
     * 提交运行记录。
     */
    @Operation(summary = "提交运行记录")
    @PostMapping("/modules/{moduleId}/records/{recordId}/submit")
    public RecordMutationResultVO submitRecord(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId,
            @Valid @RequestBody RecordSubmitBO submitBO) {
        validateLogin(authorization);
        return runtimeRecordService.submitRecord(systemId, moduleId, recordId, submitBO);
    }

    /**
     * 查询运行记录历史。
     */
    @Operation(summary = "查询运行记录历史")
    @GetMapping("/modules/{moduleId}/records/{recordId}/history")
    public List<RecordHistoryVO> recordHistory(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        validateLogin(authorization);
        return runtimeRecordService.recordHistory(systemId, moduleId, recordId);
    }

    /**
     * 查询运行记录关联关系。
     */
    @Operation(summary = "查询运行记录关联关系")
    @GetMapping("/modules/{moduleId}/records/{recordId}/relations")
    public List<RecordRelationVO> recordRelations(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        validateLogin(authorization);
        return runtimeRecordService.recordRelations(systemId, moduleId, recordId);
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
