package com.unique.examine.upload.manage.service;

import com.unique.examine.upload.manage.bo.AttachmentCreateBO;
import com.unique.examine.upload.manage.bo.UploadFileCreateBO;
import com.unique.examine.upload.manage.bo.UploadImportExportJobBO;
import com.unique.examine.upload.manage.dto.UploadQueryDTO;
import com.unique.examine.upload.manage.vo.UploadManageVO;

import java.util.List;

/**
 * 上传中心业务服务。
 */
public interface UploadManageService {

    /**
     * 查询文件列表。
     *
     * @param dto 查询 DTO
     * @return 文件列表
     */
    List<UploadManageVO> listFiles(UploadQueryDTO dto);

    /**
     * 登记文件元数据。
     *
     * @param bo 文件元数据入参
     * @return 文件信息
     */
    UploadManageVO createFile(UploadFileCreateBO bo);

    /**
     * 删除文件。
     *
     * @param id 文件 ID
     * @return 文件信息
     */
    UploadManageVO deleteFile(Long id);

    /**
     * 创建附件引用。
     *
     * @param bo 附件入参
     * @return 附件信息
     */
    UploadManageVO createAttachment(AttachmentCreateBO bo);

    /**
     * 查询业务附件。
     *
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @return 附件列表
     */
    List<UploadManageVO> listAttachments(String bizType, Long bizId);

    /**
     * 创建导入导出任务。
     *
     * @param bo 任务入参
     * @return 任务信息
     */
    UploadManageVO createJob(UploadImportExportJobBO bo);
}
