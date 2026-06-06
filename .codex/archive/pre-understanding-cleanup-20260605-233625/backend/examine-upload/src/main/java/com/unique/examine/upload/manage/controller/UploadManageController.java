package com.unique.examine.upload.manage.controller;

import com.unique.examine.core.common.ApiResult;
import com.unique.examine.upload.manage.bo.AttachmentCreateBO;
import com.unique.examine.upload.manage.bo.UploadFileCreateBO;
import com.unique.examine.upload.manage.bo.UploadImportExportJobBO;
import com.unique.examine.upload.manage.dto.UploadQueryDTO;
import com.unique.examine.upload.manage.service.UploadManageService;
import com.unique.examine.upload.manage.vo.UploadManageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 上传中心接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/uploads")
@Tag(name = "上传中心")
public class UploadManageController {

    private final UploadManageService uploadManageService;

    /**
     * 查询文件列表。
     *
     * @param tenantId 租户 ID
     * @param status 文件状态
     * @return 文件列表
     */
    @GetMapping("/files")
    @Operation(summary = "查询文件列表")
    public ApiResult<List<UploadManageVO>> listFiles(@RequestParam(required = false) Long tenantId,
                                                     @RequestParam(required = false) String status) {
        UploadQueryDTO dto = new UploadQueryDTO();
        dto.setTenantId(tenantId);
        dto.setStatus(status);
        return ApiResult.success(uploadManageService.listFiles(dto));
    }

    /**
     * 登记文件元数据。
     *
     * @param bo 文件元数据入参
     * @return 文件信息
     */
    @PostMapping("/files")
    @Operation(summary = "登记文件元数据")
    public ApiResult<UploadManageVO> createFile(@RequestBody UploadFileCreateBO bo) {
        return ApiResult.success(uploadManageService.createFile(bo));
    }

    /**
     * 删除文件。
     *
     * @param id 文件 ID
     * @return 文件信息
     */
    @DeleteMapping("/files")
    @Operation(summary = "删除文件")
    public ApiResult<UploadManageVO> deleteFile(@RequestParam Long id) {
        return ApiResult.success(uploadManageService.deleteFile(id));
    }

    /**
     * 创建附件引用。
     *
     * @param bo 附件入参
     * @return 附件信息
     */
    @PostMapping("/attachments")
    @Operation(summary = "创建附件引用")
    public ApiResult<UploadManageVO> createAttachment(@RequestBody AttachmentCreateBO bo) {
        return ApiResult.success(uploadManageService.createAttachment(bo));
    }

    /**
     * 查询业务附件。
     *
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @return 附件列表
     */
    @GetMapping("/attachments")
    @Operation(summary = "查询业务附件")
    public ApiResult<List<UploadManageVO>> listAttachments(@RequestParam String bizType, @RequestParam Long bizId) {
        return ApiResult.success(uploadManageService.listAttachments(bizType, bizId));
    }

    /**
     * 创建导入导出任务。
     *
     * @param bo 任务入参
     * @return 任务信息
     */
    @PostMapping("/jobs")
    @Operation(summary = "创建导入导出任务")
    public ApiResult<UploadManageVO> createJob(@RequestBody UploadImportExportJobBO bo) {
        return ApiResult.success(uploadManageService.createJob(bo));
    }
}
