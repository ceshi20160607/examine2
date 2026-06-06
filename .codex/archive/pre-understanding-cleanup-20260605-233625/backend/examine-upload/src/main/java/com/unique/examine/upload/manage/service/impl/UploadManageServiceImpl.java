package com.unique.examine.upload.manage.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContext;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.upload.base.entity.Attachment;
import com.unique.examine.upload.base.entity.File;
import com.unique.examine.upload.base.entity.ImportExportJob;
import com.unique.examine.upload.base.service.IAttachmentService;
import com.unique.examine.upload.base.service.IFileService;
import com.unique.examine.upload.base.service.IImportExportJobService;
import com.unique.examine.upload.manage.bo.AttachmentCreateBO;
import com.unique.examine.upload.manage.bo.UploadFileCreateBO;
import com.unique.examine.upload.manage.bo.UploadImportExportJobBO;
import com.unique.examine.upload.manage.converter.UploadManageConverter;
import com.unique.examine.upload.manage.dto.UploadQueryDTO;
import com.unique.examine.upload.manage.enums.UploadManageErrorCode;
import com.unique.examine.upload.manage.service.UploadManageService;
import com.unique.examine.upload.manage.vo.UploadManageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 上传中心业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class UploadManageServiceImpl implements UploadManageService {

    private static final String TEMP = "TEMP";
    private static final String REFERENCED = "REFERENCED";
    private static final String DELETED = "DELETED";
    private static final String PENDING = "PENDING";

    private final IFileService fileService;
    private final IAttachmentService attachmentService;
    private final IImportExportJobService importExportJobService;

    @Override
    public List<UploadManageVO> listFiles(UploadQueryDTO dto) {
        return fileService.list(Wrappers.<File>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(dto.getTenantId()), File::getTenantId, dto.getTenantId())
                        .eq(StrUtil.isNotBlank(dto.getStatus()), File::getStatus, dto.getStatus()))
                .stream().map(UploadManageConverter::fromFile).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadManageVO createFile(UploadFileCreateBO bo) {
        requireId(bo.getStorageConfigId(), "storageConfigId");
        requireText(bo.getOriginalName(), "originalName");
        requireText(bo.getStoragePath(), "storagePath");
        File file = new File();
        file.setTenantId(bo.getTenantId());
        file.setStorageConfigId(bo.getStorageConfigId());
        file.setOriginalName(bo.getOriginalName());
        file.setFileExt(bo.getFileExt());
        file.setMimeType(bo.getMimeType());
        file.setFileSize(ObjectUtil.defaultIfNull(bo.getFileSize(), 0L));
        file.setStoragePath(bo.getStoragePath());
        file.setSha256(bo.getSha256());
        file.setStatus(TEMP);
        file.setUploadedBy(currentAccountId());
        fillAudit(file::setCreatedBy, file::setUpdatedBy);
        fileService.save(file);
        return UploadManageConverter.fromFile(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadManageVO deleteFile(Long id) {
        File file = requireFile(id);
        file.setStatus(DELETED);
        fillUpdatedBy(file::setUpdatedBy);
        fileService.updateById(file);
        return UploadManageConverter.fromFile(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadManageVO createAttachment(AttachmentCreateBO bo) {
        requireId(bo.getFileId(), "fileId");
        requireText(bo.getBizType(), "bizType");
        requireId(bo.getBizId(), "bizId");
        File file = requireFile(bo.getFileId());
        if (DELETED.equals(file.getStatus())) {
            throwError(UploadManageErrorCode.STATUS_INVALID);
        }
        Attachment attachment = new Attachment();
        attachment.setFileId(bo.getFileId());
        attachment.setBizType(bo.getBizType());
        attachment.setBizId(bo.getBizId());
        attachment.setFieldCode(bo.getFieldCode());
        fillAudit(attachment::setCreatedBy, attachment::setUpdatedBy);
        attachmentService.save(attachment);
        file.setStatus(REFERENCED);
        fillUpdatedBy(file::setUpdatedBy);
        fileService.updateById(file);
        return UploadManageConverter.fromAttachment(attachment);
    }

    @Override
    public List<UploadManageVO> listAttachments(String bizType, Long bizId) {
        requireText(bizType, "bizType");
        requireId(bizId, "bizId");
        return attachmentService.list(Wrappers.<Attachment>lambdaQuery()
                        .eq(Attachment::getBizType, bizType)
                        .eq(Attachment::getBizId, bizId))
                .stream().map(UploadManageConverter::fromAttachment).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadManageVO createJob(UploadImportExportJobBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        ImportExportJob job = new ImportExportJob();
        job.setTenantId(bo.getTenantId());
        job.setModuleId(bo.getModuleId());
        job.setJobType(StrUtil.blankToDefault(bo.getJobType(), "EXPORT"));
        job.setStatus(PENDING);
        job.setSourceFileId(bo.getSourceFileId());
        job.setRequestJson(bo.getRequestJson());
        fillAudit(job::setCreatedBy, job::setUpdatedBy);
        importExportJobService.save(job);
        return UploadManageConverter.fromJob(job);
    }

    /**
     * 查询并校验文件存在。
     *
     * @param id 文件 ID
     * @return 文件实体
     */
    private File requireFile(Long id) {
        File file = fileService.getById(id);
        if (ObjectUtil.isNull(file)) {
            throwError(UploadManageErrorCode.DATA_NOT_FOUND);
        }
        return file;
    }

    /**
     * 校验文本必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireText(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(UploadManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 校验 ID 必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireId(Long value, String field) {
        if (ObjectUtil.isNull(value)) {
            throw new BusinessException(UploadManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 写入创建与更新人。
     *
     * @param createdSetter 创建人写入器
     * @param updatedSetter 更新人写入器
     */
    private void fillAudit(java.util.function.Consumer<Long> createdSetter, java.util.function.Consumer<Long> updatedSetter) {
        Long accountId = currentAccountId();
        createdSetter.accept(accountId);
        updatedSetter.accept(accountId);
    }

    /**
     * 写入更新人。
     *
     * @param updatedSetter 更新人写入器
     */
    private void fillUpdatedBy(java.util.function.Consumer<Long> updatedSetter) {
        updatedSetter.accept(currentAccountId());
    }

    /**
     * 获取当前账号 ID。
     *
     * @return 当前账号 ID
     */
    private Long currentAccountId() {
        AuthContext context = AuthContextHolder.get();
        return ObjectUtil.isNull(context) ? null : context.getAccountId();
    }

    /**
     * 抛出上传异常。
     *
     * @param errorCode 错误码
     */
    private void throwError(UploadManageErrorCode errorCode) {
        throw new BusinessException(errorCode.getCode(), errorCode.getMessage());
    }
}
