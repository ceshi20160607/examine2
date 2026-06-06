package com.unique.examine.manage.controller;

import com.unique.examine.base.entity.OpenapiClient;
import com.unique.examine.base.entity.OpenapiIdempotency;
import com.unique.examine.base.service.IOpenapiIdempotencyService;
import com.unique.examine.manage.bo.BusinessRecordSaveBO;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.service.OpenApiAuthService;
import com.unique.examine.manage.service.RuntimeRecordManageService;
import com.unique.examine.manage.vo.ApiResponse;
import com.unique.examine.manage.vo.RecordVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/open/records")
public class OpenApiRecordController {
    private final OpenApiAuthService openApiAuthService;
    private final RuntimeRecordManageService runtimeRecordManageService;
    private final IOpenapiIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{moduleId}/{recordId}")
    public ApiResponse<RecordVO> detail(@PathVariable Long moduleId, @PathVariable Long recordId, HttpServletRequest request) {
        OpenapiClient client = openApiAuthService.authenticateClient(request, "");
        RecordVO record = runtimeRecordManageService.detailByOpenApi(recordId);
        if (!moduleId.equals(record.getModuleId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "OpenAPI路径模块与记录归属不一致");
        }
        openApiAuthService.requireRequestScope(client, record.getSystemId(), record.getTenantId(), record.getAppId(), record.getModuleId(), "record:view");
        return ApiResponse.ok(record);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<RecordVO> create(@RequestBody String body, HttpServletRequest request) throws Exception {
        OpenapiClient client = openApiAuthService.authenticateClient(request, body);
        BusinessRecordSaveBO bo = objectMapper.readValue(body, BusinessRecordSaveBO.class);
        openApiAuthService.requireRequestScope(client, bo.getSystemId(), bo.getTenantId(), bo.getAppId(), bo.getModuleId(), "record:create");
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String requestHash = sha256(body);
            OpenapiIdempotency exists = idempotencyService.getOne(Wrappers.<OpenapiIdempotency>lambdaQuery().eq(OpenapiIdempotency::getClientPk, client.getId()).eq(OpenapiIdempotency::getIdempotencyKey, idempotencyKey), false);
            if (exists != null) {
                if (!requestHash.equals(exists.getRequestHash())) {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                }
                if (StatusEnums.SUCCESS.equals(exists.getStatus()) && exists.getResponseSnapshot() != null) {
                    return objectMapper.readValue(exists.getResponseSnapshot(), objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, RecordVO.class));
                }
            } else {
                exists = new OpenapiIdempotency();
                exists.setClientPk(client.getId()); exists.setIdempotencyKey(idempotencyKey); exists.setRequestHash(requestHash); exists.setStatus("PROCESSING"); idempotencyService.save(exists);
            }
            ApiResponse<RecordVO> response = ApiResponse.ok(runtimeRecordManageService.createByOpenApi(bo));
            exists.setResponseSnapshot(objectMapper.writeValueAsString(response)); exists.setStatus(StatusEnums.SUCCESS); idempotencyService.updateById(exists);
            return response;
        }
        return ApiResponse.ok(runtimeRecordManageService.createByOpenApi(bo));
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) { builder.append(String.format("%02x", b)); }
        return builder.toString();
    }
}
