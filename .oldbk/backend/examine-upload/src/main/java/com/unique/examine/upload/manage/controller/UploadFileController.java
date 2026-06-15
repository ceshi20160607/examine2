package com.unique.examine.upload.manage.controller;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.upload.manage.bo.FileQueryBO;
import com.unique.examine.upload.manage.service.UploadFileService;
import com.unique.examine.upload.manage.vo.FileAccessVO;
import com.unique.examine.upload.manage.vo.FileInfoVO;
import com.unique.examine.upload.manage.vo.FileListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传文件接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/files")
public class UploadFileController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UploadFileService uploadFileService;

    private final AuthSessionService authSessionService;

    @Operation(summary = "上传文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileInfoVO upload(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String bizType, @RequestParam(required = false) Long moduleId,
            @RequestParam(required = false) Long recordId, @RequestParam(required = false) String fieldCode) {
        validateLogin(authorization);
        return uploadFileService.upload(systemId, file, bizType, moduleId, recordId, fieldCode);
    }

    @Operation(summary = "查询文件列表")
    @GetMapping
    public PageResult<FileListItemVO> queryFiles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, FileQueryBO queryBO) {
        validateLogin(authorization);
        return uploadFileService.queryFiles(systemId, queryBO);
    }

    @Operation(summary = "查询文件详情")
    @GetMapping("/{fileId}")
    public FileInfoVO fileDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long fileId) {
        validateLogin(authorization);
        return uploadFileService.fileDetail(systemId, fileId);
    }

    @Operation(summary = "预览文件")
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<Resource> preview(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long fileId) {
        validateLogin(authorization);
        return fileResponse(uploadFileService.preview(systemId, fileId));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long fileId) {
        validateLogin(authorization);
        return fileResponse(uploadFileService.download(systemId, fileId));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public FileInfoVO deleteFile(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long fileId) {
        validateLogin(authorization);
        return uploadFileService.deleteFile(systemId, fileId);
    }

    private ResponseEntity<Resource> fileResponse(FileAccessVO accessVO) {
        MediaType mediaType = StringUtils.hasText(accessVO.getContentType())
                ? MediaType.parseMediaType(accessVO.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition disposition = (Boolean.TRUE.equals(accessVO.getInline()) ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(accessVO.getFileName())
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(accessVO.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(accessVO.getResource());
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
