package com.unique.examine.app.manage.service.impl;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.app.manage.bo.OpenApiRecordQueryBO;
import com.unique.examine.app.manage.bo.OpenApiRecordSaveBO;
import com.unique.examine.app.manage.bo.OpenApiRecordSubmitBO;
import com.unique.examine.app.manage.bo.OpenApiRecordUpdateBO;
import com.unique.examine.app.manage.service.OpenApiExternalService;
import com.unique.examine.app.manage.service.OpenApiSecurityService;
import com.unique.examine.app.manage.vo.OpenApiRequestContext;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.flow.manage.bo.FlowActionBO;
import com.unique.examine.flow.manage.service.FlowManageService;
import com.unique.examine.flow.manage.vo.FlowActionResultVO;
import com.unique.examine.module.manage.bo.RecordQueryBO;
import com.unique.examine.module.manage.bo.RecordSaveBO;
import com.unique.examine.module.manage.bo.RecordSubmitBO;
import com.unique.examine.module.manage.bo.RecordUpdateBO;
import com.unique.examine.module.manage.service.RuntimeRecordService;
import com.unique.examine.module.manage.vo.RecordDetailVO;
import com.unique.examine.module.manage.vo.RecordListItemVO;
import com.unique.examine.module.manage.vo.RecordMutationResultVO;
import com.unique.examine.upload.manage.service.UploadFileService;
import com.unique.examine.upload.manage.vo.FileAccessVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * OpenAPI 外部业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class OpenApiExternalServiceImpl implements OpenApiExternalService {

    private final OpenApiSecurityService securityService;

    private final RuntimeRecordService runtimeRecordService;

    private final FlowManageService flowManageService;

    private final UploadFileService uploadFileService;

    private final ObjectMapper objectMapper;

    @Override
    public PageResult<RecordListItemVO> queryRecords(HttpServletRequest request, String rawBody) {
        OpenApiRecordQueryBO queryBO = read(rawBody, OpenApiRecordQueryBO.class);
        OpenApiRequestContext context = null;
        try {
            context = securityService.verify(request, rawBody, "OPN-001", "record:read", queryBO.getModuleCode(),
                    false);
            RecordQueryBO internal = new RecordQueryBO();
            internal.setPageNo(queryBO.getPageNo());
            internal.setPageSize(queryBO.getPageSize());
            internal.setKeyword(queryBO.getKeyword());
            internal.setFilters(queryBO.getFilters());
            internal.setSorter(queryBO.getSorter());
            PageResult<RecordListItemVO> result = runtimeRecordService.queryRecords(context.getClient().getSystemId(),
                    context.getModuleId(), internal);
            securityService.markSuccess(context, "RECORD_QUERY", queryBO.getModuleCode());
            return result;
        } catch (BusinessException e) {
            markFailure(context, e);
            throw e;
        }
    }

    @Override
    public RecordDetailVO recordDetail(HttpServletRequest request, Long recordId, String moduleCode) {
        OpenApiRequestContext context = null;
        try {
            context = securityService.verify(request, "", "OPN-002", "record:read", moduleCode, false);
            RecordDetailVO result = runtimeRecordService.recordDetail(context.getClient().getSystemId(),
                    context.getModuleId(), recordId);
            securityService.markSuccess(context, "RECORD", String.valueOf(recordId));
            return result;
        } catch (BusinessException e) {
            markFailure(context, e);
            throw e;
        }
    }

    @Override
    public RecordMutationResultVO createRecord(HttpServletRequest request, String rawBody) {
        OpenApiRecordSaveBO saveBO = read(rawBody, OpenApiRecordSaveBO.class);
        OpenApiRequestContext context = null;
        try {
            context = securityService.verify(request, rawBody, "OPN-003", "record:create", saveBO.getModuleCode(),
                    true);
            RecordSaveBO internal = new RecordSaveBO();
            internal.setValues(saveBO.getValues());
            internal.setRemark(saveBO.getRemark());
            RecordMutationResultVO result = runtimeRecordService.createRecord(context.getClient().getSystemId(),
                    context.getModuleId(), internal);
            securityService.markSuccess(context, "RECORD", resolveRecordId(result));
            return result;
        } catch (BusinessException e) {
            markFailure(context, e);
            throw e;
        }
    }

    @Override
    public RecordMutationResultVO updateRecord(HttpServletRequest request, Long recordId, String rawBody) {
        OpenApiRecordUpdateBO updateBO = read(rawBody, OpenApiRecordUpdateBO.class);
        OpenApiRequestContext context = null;
        try {
            context = securityService.verify(request, rawBody, "OPN-004", "record:update", updateBO.getModuleCode(),
                    true);
            RecordUpdateBO internal = new RecordUpdateBO();
            internal.setValues(updateBO.getValues());
            internal.setRemark(updateBO.getRemark());
            internal.setRecordVersion(updateBO.getRecordVersion());
            RecordMutationResultVO result = runtimeRecordService.updateRecord(context.getClient().getSystemId(),
                    context.getModuleId(), recordId, internal);
            securityService.markSuccess(context, "RECORD", String.valueOf(recordId));
            return result;
        } catch (BusinessException e) {
            markFailure(context, e);
            throw e;
        }
    }

    @Override
    public RecordMutationResultVO submitRecord(HttpServletRequest request, Long recordId, String rawBody) {
        OpenApiRecordSubmitBO submitBO = read(rawBody, OpenApiRecordSubmitBO.class);
        OpenApiRequestContext context = null;
        try {
            context = securityService.verify(request, rawBody, "OPN-005", "record:submit", submitBO.getModuleCode(),
                    true);
            RecordSubmitBO internal = new RecordSubmitBO();
            internal.setRecordVersion(submitBO.getRecordVersion());
            internal.setReason(submitBO.getReason());
            RecordMutationResultVO result = runtimeRecordService.submitRecord(context.getClient().getSystemId(),
                    context.getModuleId(), recordId, internal);
            securityService.markSuccess(context, "RECORD", String.valueOf(recordId));
            return result;
        } catch (BusinessException e) {
            markFailure(context, e);
            throw e;
        }
    }

    @Override
    public FlowActionResultVO handleTask(HttpServletRequest request, Long taskId, String rawBody) {
        FlowActionBO actionBO = read(rawBody, FlowActionBO.class);
        OpenApiRequestContext context = null;
        try {
            context = securityService.verify(request, rawBody, "OPN-006", "flow:task:handle", null, true);
            FlowActionResultVO result = flowManageService.handleTask(context.getClient().getSystemId(), taskId,
                    actionBO);
            securityService.markSuccess(context, "FLOW_TASK", String.valueOf(taskId));
            return result;
        } catch (BusinessException e) {
            markFailure(context, e);
            throw e;
        }
    }

    @Override
    public FileAccessVO downloadFile(HttpServletRequest request, Long fileId) {
        OpenApiRequestContext context = null;
        try {
            context = securityService.verify(request, "", "OPN-007", "file:download", null, false);
            FileAccessVO result = uploadFileService.download(context.getClient().getSystemId(), fileId);
            securityService.markSuccess(context, "FILE", String.valueOf(fileId));
            return result;
        } catch (BusinessException e) {
            markFailure(context, e);
            throw e;
        }
    }

    private <T> T read(String rawBody, Class<T> type) {
        try {
            return objectMapper.readValue(Objects.requireNonNullElse(rawBody, ""), type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.REQUEST_BODY_INVALID);
        }
    }

    private void markFailure(OpenApiRequestContext context, BusinessException e) {
        if (Objects.nonNull(context)) {
            securityService.markFailure(context, e.getErrorCode().getCode());
        }
    }

    private String resolveRecordId(RecordMutationResultVO result) {
        return Objects.nonNull(result) && Objects.nonNull(result.getRecordId()) ? String.valueOf(result.getRecordId())
                : null;
    }
}
