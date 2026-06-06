package com.unique.examine.upload.manage.converter;

import com.unique.examine.upload.base.entity.Attachment;
import com.unique.examine.upload.base.entity.ImportExportJob;
import com.unique.examine.upload.base.entity.File;
import com.unique.examine.upload.manage.vo.UploadManageVO;

/**
 * 上传中心实体转换器。
 */
public final class UploadManageConverter {

    private UploadManageConverter() {
    }

    /**
     * 转换文件信息。
     *
     * @param entity 文件实体
     * @return 上传中心出参
     */
    public static UploadManageVO fromFile(File entity) {
        UploadManageVO vo = new UploadManageVO();
        vo.setId(entity.getId());
        vo.setFileId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setName(entity.getOriginalName());
        vo.setType(entity.getMimeType());
        vo.setSize(entity.getFileSize());
        vo.setStoragePath(entity.getStoragePath());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换附件引用。
     *
     * @param entity 附件引用实体
     * @return 上传中心出参
     */
    public static UploadManageVO fromAttachment(Attachment entity) {
        UploadManageVO vo = new UploadManageVO();
        vo.setId(entity.getId());
        vo.setFileId(entity.getFileId());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setName(entity.getFieldCode());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 转换导入导出任务。
     *
     * @param entity 任务实体
     * @return 上传中心出参
     */
    public static UploadManageVO fromJob(ImportExportJob entity) {
        UploadManageVO vo = new UploadManageVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setModuleId(entity.getModuleId());
        vo.setType(entity.getJobType());
        vo.setStatus(entity.getStatus());
        vo.setFileId(entity.getSourceFileId());
        vo.setFailureReason(entity.getFailureReason());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
