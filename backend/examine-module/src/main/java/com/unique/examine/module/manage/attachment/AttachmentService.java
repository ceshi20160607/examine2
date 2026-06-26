package com.unique.examine.module.manage.attachment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.module.base.entity.ModuleDynamicAttachment;
import com.unique.examine.module.base.service.ModuleDynamicAttachmentBaseService;
import com.unique.examine.module.manage.attachment.AttachmentModels.AttachmentBindRequest;
import com.unique.examine.module.manage.attachment.AttachmentModels.AttachmentBindResult;
import com.unique.examine.module.manage.attachment.AttachmentModels.AttachmentVO;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.upload.manage.UploadManageModels.UploadFileVO;
import com.unique.examine.upload.manage.UploadManageService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Business record attachment binding service.
 */
@Service
public class AttachmentService {

    private final ModuleSystemContextResolver contextResolver;
    private final ModuleDynamicAttachmentBaseService attachmentBaseService;
    private final UploadManageService uploadManageService;

    public AttachmentService(ModuleSystemContextResolver contextResolver,
                             ModuleDynamicAttachmentBaseService attachmentBaseService,
                             UploadManageService uploadManageService) {
        this.contextResolver = contextResolver;
        this.attachmentBaseService = attachmentBaseService;
        this.uploadManageService = uploadManageService;
    }

    /**
     * Bind uploaded files to one business record.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param recordId record id
     * @param request bind request
     * @return bind result
     */
    @Transactional(rollbackFor = Exception.class)
    public AttachmentBindResult bind(String systemId, String moduleId, String recordId,
                                     AttachmentBindRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        Long modulePk = contextResolver.parseRequiredId(moduleId, "模块ID格式不正确");
        Long recordPk = contextResolver.parseRequiredId(recordId, "记录ID格式不正确");
        if ("REPLACE".equalsIgnoreCase(Objects.isNull(request) ? null : request.bindMode())) {
            attachmentBaseService.remove(new LambdaQueryWrapper<ModuleDynamicAttachment>()
                    .eq(ModuleDynamicAttachment::getSystemId, context.systemId())
                    .eq(ModuleDynamicAttachment::getTenantId, context.tenantId())
                    .eq(ModuleDynamicAttachment::getModuleId, modulePk)
                    .eq(ModuleDynamicAttachment::getRecordId, recordPk));
        }
        List<AttachmentVO> attachments = fileIds(request).stream()
                .map(fileId -> bindOne(context, modulePk, recordPk, fileId))
                .toList();
        RequestContext requestContext = RequestContext.current();
        return new AttachmentBindResult(recordId, moduleId, attachments, context.permissionVersion(),
                requestContext.traceId(), auditLogId(requestContext), LocalDateTime.now());
    }

    private AttachmentVO bindOne(ModuleSystemContext context, Long moduleId, Long recordId, String fileId) {
        UploadFileVO file = uploadManageService.getFile(fileId);
        ModuleDynamicAttachment existing = attachmentBaseService.getOne(
                new LambdaQueryWrapper<ModuleDynamicAttachment>()
                        .eq(ModuleDynamicAttachment::getSystemId, context.systemId())
                        .eq(ModuleDynamicAttachment::getTenantId, context.tenantId())
                        .eq(ModuleDynamicAttachment::getModuleId, moduleId)
                        .eq(ModuleDynamicAttachment::getRecordId, recordId)
                        .eq(ModuleDynamicAttachment::getFileId, fileId)
                        .last("LIMIT 1"), false);
        ModuleDynamicAttachment attachment = Objects.isNull(existing) ? new ModuleDynamicAttachment() : existing;
        attachment.setSystemId(context.systemId());
        attachment.setTenantId(context.tenantId());
        attachment.setModuleId(moduleId);
        attachment.setRecordId(recordId);
        attachment.setFileId(file.fileId());
        attachment.setFileName(file.fileName());
        attachment.setUploadStatus(file.status());
        attachment.setPermissionSnapshotId(context.permissionSnapshotId());
        attachment.setTraceId(RequestContext.current().traceId());
        attachment.setCreatedBy(context.systemMemberId());
        if (Objects.isNull(attachment.getCreatedAt())) {
            attachment.setCreatedAt(LocalDateTime.now());
        }
        if (Objects.isNull(attachment.getId())) {
            attachmentBaseService.saveEntity(attachment);
        } else {
            attachmentBaseService.updateById(attachment);
        }
        return toVO(attachment, file);
    }

    private AttachmentVO toVO(ModuleDynamicAttachment attachment, UploadFileVO file) {
        return new AttachmentVO(String.valueOf(attachment.getId()), attachment.getFileId(), attachment.getFileName(),
                file.fileType(), attachment.getUploadStatus(), file.previewUrl(), file.downloadUrl(),
                "READABLE", "FAILED".equals(attachment.getUploadStatus()), String.valueOf(attachment.getCreatedBy()),
                attachment.getCreatedAt());
    }

    private List<String> fileIds(AttachmentBindRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.fileIds()) || request.fileIds().isEmpty()) {
            return List.of();
        }
        return request.fileIds();
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }
}
