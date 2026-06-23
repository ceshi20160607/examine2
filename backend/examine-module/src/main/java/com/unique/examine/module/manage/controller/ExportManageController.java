package com.unique.examine.module.manage.controller;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.ExportJobActionBO;
import com.unique.examine.module.manage.bo.ExportJobCreateBO;
import com.unique.examine.module.manage.bo.ExportJobQueryBO;
import com.unique.examine.module.manage.bo.ExportTemplateSaveBO;
import com.unique.examine.module.manage.service.ExportManageService;
import com.unique.examine.module.manage.vo.ExportJobDetailVO;
import com.unique.examine.module.manage.vo.ExportJobListItemVO;
import com.unique.examine.module.manage.vo.ExportTemplateVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出模板和任务接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/exports")
public class ExportManageController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ExportManageService exportManageService;

    private final AuthSessionService authSessionService;

    @Operation(summary = "查询导出模板")
    @GetMapping("/templates")
    public List<ExportTemplateVO> listTemplates(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestParam(required = false) Long moduleId) {
        validateLogin(authorization);
        return exportManageService.listTemplates(systemId, moduleId);
    }

    @Operation(summary = "创建导出模板")
    @PostMapping("/templates")
    public ExportTemplateVO createTemplate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody ExportTemplateSaveBO saveBO) {
        validateLogin(authorization);
        return exportManageService.createTemplate(systemId, saveBO);
    }

    @Operation(summary = "更新导出模板")
    @PutMapping("/templates/{templateId}")
    public ExportTemplateVO updateTemplate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long templateId,
            @Valid @RequestBody ExportTemplateSaveBO saveBO) {
        validateLogin(authorization);
        return exportManageService.updateTemplate(systemId, templateId, saveBO);
    }

    @Operation(summary = "创建导出任务")
    @PostMapping("/jobs")
    public ExportJobDetailVO createJob(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody ExportJobCreateBO createBO) {
        validateLogin(authorization);
        return exportManageService.createJob(systemId, createBO);
    }

    @Operation(summary = "查询导出任务")
    @GetMapping("/jobs")
    public PageResult<ExportJobListItemVO> listJobs(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, ExportJobQueryBO queryBO) {
        validateLogin(authorization);
        return exportManageService.listJobs(systemId, queryBO);
    }

    @Operation(summary = "查询导出任务详情")
    @GetMapping("/jobs/{jobId}")
    public ExportJobDetailVO jobDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long jobId) {
        validateLogin(authorization);
        return exportManageService.jobDetail(systemId, jobId);
    }

    @Operation(summary = "重试导出任务")
    @PostMapping("/jobs/{jobId}/retry")
    public ExportJobDetailVO retryJob(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long jobId,
            @RequestBody(required = false) ExportJobActionBO actionBO) {
        validateLogin(authorization);
        return exportManageService.retryJob(systemId, jobId, actionBO);
    }

    @Operation(summary = "取消导出任务")
    @PostMapping("/jobs/{jobId}/cancel")
    public ExportJobDetailVO cancelJob(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long jobId,
            @RequestBody(required = false) ExportJobActionBO actionBO) {
        validateLogin(authorization);
        return exportManageService.cancelJob(systemId, jobId, actionBO);
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
