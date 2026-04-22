package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.upload.entity.po.UploadFile;
import com.unique.examine.upload.service.IUploadFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Tag(name = "自建系统态-附件上传")
@RestController
@RequestMapping("/v1/system/uploads")
public class SystemUploadController {

    // MVP：先做 local 存储；后续可接入 UploadStorageConfig（minio/oss）
    @Value("${examine.upload.local-root-path:data/uploads}")
    private String localRootPath;

    @Autowired
    private IUploadFileService uploadFileService;

    @Operation(summary = "上传文件（multipart；local 存储；返回 fileId）")
    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "file 不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            originalName = "file";
        }
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException(400, "空文件不允许上传");
        }
        if (size > 50L * 1024 * 1024) {
            throw new BusinessException(400, "文件过大（最大 50MB）");
        }

        String contentType = file.getContentType();
        String ext = extractExt(originalName);

        LocalDate d = LocalDate.now();
        String dir = d.getYear() + "/" + String.format("%02d", d.getMonthValue()) + "/" + String.format("%02d", d.getDayOfMonth());
        String safeName = UUID.randomUUID().toString().replace("-", "");
        String filename = ext == null ? safeName : (safeName + "." + ext);

        String rootStr = StringUtils.hasText(localRootPath) ? localRootPath.trim() : "data/uploads";
        Path root = Paths.get(rootStr).toAbsolutePath().normalize();
        Path targetDir = root.resolve(String.valueOf(systemId)).resolve(String.valueOf(tenantId)).resolve(dir).normalize();
        try {
            Files.createDirectories(targetDir);
        } catch (Exception e) {
            throw new BusinessException(500, "创建上传目录失败: " + e.getMessage());
        }
        Path absPath = targetDir.resolve(filename).normalize();

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, absPath);
        } catch (Exception e) {
            throw new BusinessException(500, "保存文件失败: " + e.getMessage());
        }

        UploadFile uf = new UploadFile();
        uf.setSystemId(systemId);
        uf.setTenantId(tenantId);
        uf.setUploaderPlatId(platId);
        uf.setOriginalName(originalName);
        uf.setFileExt(ext);
        uf.setContentType(contentType);
        uf.setFileSize(size);
        uf.setStorageType("local");
        uf.setLocalAbsPath(absPath.toString());
        uf.setStatus(1);
        uf.setCreateUserId(platId);
        uf.setUpdateUserId(platId);
        uploadFileService.save(uf);

        return ApiResult.ok(Map.of(
                "fileId", uf.getId(),
                "originalName", uf.getOriginalName(),
                "contentType", uf.getContentType(),
                "fileSize", uf.getFileSize()
        ));
    }

    @Operation(summary = "文件分页列表（按 system/tenant 隔离；可选 keyword；默认仅 status=1）")
    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeDeleted", required = false) Integer includeDeleted
    ) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;
        boolean incDeleted = includeDeleted != null && includeDeleted == 1;

        var q = uploadFileService.lambdaQuery()
                .eq(UploadFile::getSystemId, systemId)
                .eq(UploadFile::getTenantId, tenantId);
        if (!incDeleted) {
            q.eq(UploadFile::getStatus, 1);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(UploadFile::getOriginalName, kw)
                    .or()
                    .like(UploadFile::getContentType, kw));
        }

        long total = q.count();
        List<UploadFile> records = total == 0 ? List.of() : q.orderByDesc(UploadFile::getId)
                .last("limit " + s + " offset " + offset)
                .list();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", p);
        m.put("size", s);
        m.put("total", total);
        m.put("records", records);
        return ApiResult.ok(m);
    }

    @Operation(summary = "文件详情（按 fileId；返回元数据与 view/download URL）")
    @GetMapping("/{fileId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable("fileId") Long fileId) {
        UploadFile uf = requireFileInScope(fileId, true);
        return ApiResult.ok(Map.of(
                "file", uf,
                "viewUrl", "/v1/system/uploads/" + uf.getId() + "/view",
                "downloadUrl", "/v1/system/uploads/" + uf.getId() + "/download"
        ));
    }

    @Operation(summary = "下载文件（按 fileId；Content-Disposition=attachment）")
    @GetMapping("/{fileId}/download")
    public void download(@PathVariable("fileId") Long fileId, HttpServletResponse response) {
        UploadFile uf = requireFileInScope(fileId, false);
        if (!Objects.equals(uf.getStorageType(), "local") || !StringUtils.hasText(uf.getLocalAbsPath())) {
            throw new BusinessException(400, "仅支持 local 存储文件下载");
        }
        Path p = Paths.get(uf.getLocalAbsPath());
        if (!Files.exists(p)) {
            throw new BusinessException(404, "文件不存在");
        }
        String ct = StringUtils.hasText(uf.getContentType()) ? uf.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        response.setContentType(ct);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + sanitizeFilename(uf.getOriginalName()) + "\"");
        try (InputStream in = Files.newInputStream(p); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch (Exception e) {
            throw new BusinessException(500, "下载失败: " + e.getMessage());
        }
    }

    @Operation(summary = "预览文件（按 fileId；inline）")
    @GetMapping("/{fileId}/view")
    public void view(@PathVariable("fileId") Long fileId, HttpServletResponse response) {
        UploadFile uf = requireFileInScope(fileId, false);
        if (!Objects.equals(uf.getStorageType(), "local") || !StringUtils.hasText(uf.getLocalAbsPath())) {
            throw new BusinessException(400, "仅支持 local 存储文件预览");
        }
        Path p = Paths.get(uf.getLocalAbsPath());
        if (!Files.exists(p)) {
            throw new BusinessException(404, "文件不存在");
        }
        String ct = StringUtils.hasText(uf.getContentType()) ? uf.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        response.setContentType(ct);
        response.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeFilename(uf.getOriginalName()) + "\"");
        try (InputStream in = Files.newInputStream(p); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch (Exception e) {
            throw new BusinessException(500, "预览失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除文件（软删 status=2；不物理删除；需同 system/tenant）")
    @PostMapping("/{fileId}/delete")
    public ApiResult<Void> delete(@PathVariable("fileId") Long fileId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        UploadFile uf = requireFileInScope(fileId, true);
        if (uf.getStatus() != null && uf.getStatus() == 2) {
            return ApiResult.ok();
        }
        uf.setStatus(2);
        uf.setUpdateUserId(platId);
        uploadFileService.updateById(uf);
        return ApiResult.ok();
    }

    private UploadFile requireFileInScope(Long fileId, boolean allowDeleted) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (fileId == null || fileId <= 0L) {
            throw new BusinessException(400, "fileId 不能为空");
        }
        UploadFile uf = uploadFileService.getById(fileId);
        if (uf == null) {
            throw new BusinessException(404, "文件不存在");
        }
        if (!allowDeleted) {
            if (uf.getStatus() == null || uf.getStatus() != 1) {
                throw new BusinessException(404, "文件不存在");
            }
        }
        if (!Objects.equals(uf.getSystemId(), systemId) || !Objects.equals(uf.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权限访问该文件");
        }
        return uf;
    }

    private static String extractExt(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        String f = filename.trim();
        int idx = f.lastIndexOf('.');
        if (idx < 0 || idx == f.length() - 1) {
            return null;
        }
        String ext = f.substring(idx + 1).trim().toLowerCase();
        if (ext.isEmpty() || ext.length() > 32) {
            return null;
        }
        // basic hardening
        if (!ext.matches("^[a-z0-9]+$")) {
            return null;
        }
        return ext;
    }

    private static String sanitizeFilename(String s) {
        if (!StringUtils.hasText(s)) {
            return "file";
        }
        return s.replace("\\", "_").replace("/", "_").replace("\"", "");
    }
}

