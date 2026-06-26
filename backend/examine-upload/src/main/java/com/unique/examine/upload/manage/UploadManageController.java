package com.unique.examine.upload.manage;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.upload.manage.UploadManageModels.FileAccessRequest;
import com.unique.examine.upload.manage.UploadManageModels.FileAccessResultVO;
import com.unique.examine.upload.manage.UploadManageModels.UploadFileVO;
import com.unique.examine.upload.manage.UploadManageModels.UploadResultRequest;
import com.unique.examine.upload.manage.UploadManageModels.UploadResultVO;
import com.unique.examine.upload.manage.UploadManageModels.UploadStoragePolicyVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传管理接口。
 */
@RestController
public class UploadManageController {

    private final UploadManageService uploadManageService;

    public UploadManageController(UploadManageService uploadManageService) {
        this.uploadManageService = uploadManageService;
    }

    /**
     * 登记上传结果。
     *
     * @param request 上传结果请求
     * @return 上传结果
     */
    @PostMapping("/api/v1/uploads/results")
    public ApiResponse<UploadResultVO> saveResult(@RequestBody(required = false) UploadResultRequest request) {
        return ApiResponse.success(uploadManageService.saveResult(request));
    }

    /**
     * 上传真实文件到当前本地存储策略。
     *
     * @param file 文件内容
     * @param policyCode 存储策略编码
     * @param sourceType 来源类型
     * @return 上传结果
     */
    @PostMapping(value = "/api/v1/uploads/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResultVO> upload(@RequestPart("file") MultipartFile file,
                                              @RequestParam(required = false) String policyCode,
                                              @RequestParam(required = false) String sourceType) {
        return ApiResponse.success(uploadManageService.upload(file, policyCode, sourceType));
    }

    /**
     * 获取文件详情。
     *
     * @param fileId 文件编号
     * @return 文件详情
     */
    @GetMapping("/api/v1/uploads/files/{fileId}")
    public ApiResponse<UploadFileVO> getFile(@PathVariable String fileId) {
        return ApiResponse.success(uploadManageService.getFile(fileId));
    }

    /**
     * 下载文件流。
     *
     * @param fileId 文件编号
     * @return 文件流
     */
    @GetMapping("/api/v1/uploads/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        return uploadManageService.fileResponse(fileId, false);
    }

    /**
     * 预览文件流。
     *
     * @param fileId 文件编号
     * @return 文件流
     */
    @GetMapping("/api/v1/uploads/files/{fileId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable String fileId) {
        return uploadManageService.fileResponse(fileId, true);
    }

    /**
     * 查询上传策略。
     *
     * @return 上传策略列表
     */
    @GetMapping("/api/v1/uploads/storage-policies")
    public ApiResponse<List<UploadStoragePolicyVO>> policies() {
        return ApiResponse.success(uploadManageService.policies());
    }

    /**
     * 获取文件访问结果。
     *
     * @param fileId 文件编号
     * @param request 访问请求
     * @return 访问结果
     */
    @PostMapping("/api/v1/uploads/files/{fileId}/access-result")
    public ApiResponse<FileAccessResultVO> accessResult(@PathVariable String fileId,
                                                        @RequestBody(required = false)
                                                        FileAccessRequest request) {
        return ApiResponse.success(uploadManageService.accessResult(fileId, request));
    }
}
