package com.unique.unexamine.manage.commonresource;

import com.unique.unexamine.commonresource.base.entity.UnCommonFile;
import com.unique.unexamine.commonresource.base.mapper.UnCommonFileMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** 公共文件元数据与磁盘文件的上传、列表、下载和删除入口。 */
@RestController
@RequestMapping("/api/common/files")
public class CommonFileController {
    private static final long MAX_SIZE = 25L * 1024 * 1024;
    private final UnCommonFileMapper files;
    private final ResourceScopeAccess scope;
    private final Path root;

    public CommonFileController(UnCommonFileMapper files, ResourceScopeAccess scope) {
        this.files = files;
        this.scope = scope;
        // 上传目录可由部署环境指定，默认目录位于当前服务工作目录下。
        this.root = Paths.get(System.getenv().getOrDefault("APP_FILE_ROOT", "runtime/uploads"))
                .toAbsolutePath().normalize();
    }

    /** 列表只展示业务元数据，不暴露内部文件路径。 */
    @GetMapping
    public IPage<FileView> list(@RequestParam String scopeType, @RequestParam long scopeId,
                                @RequestParam(defaultValue = "1") long page,
                                @RequestParam(defaultValue = "20") long size,
                                @RequestParam(defaultValue = "createTime") String sort,
                                HttpServletRequest request) {
        scope.read(scopeType, scopeId, request);
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        LambdaQueryWrapper<UnCommonFile> query = new LambdaQueryWrapper<UnCommonFile>()
                .eq(UnCommonFile::getScopeType, scopeType).eq(UnCommonFile::getScopeId, scopeId);
        switch (sort) {
            case "originalName" -> query.orderByAsc(UnCommonFile::getOriginalName);
            case "fileSize" -> query.orderByDesc(UnCommonFile::getFileSize);
            case "createTime" -> query.orderByDesc(UnCommonFile::getCreateTime);
            default -> throw bad("不支持的文件排序字段");
        }
        return files.selectPage(new Page<>(page, size), query).convert(this::view);
    }

    /** 上传先写入随机文件名，再保存范围和元数据；失败时清理未入库文件。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileView upload(@RequestParam String scopeType, @RequestParam long scopeId,
                           @RequestParam("file") MultipartFile upload, HttpServletRequest request) {
        Long uploaderId = scope.fileUploader(scopeType, scopeId, request);
        if (upload.isEmpty() || upload.getSize() > MAX_SIZE) throw bad("文件为空或超过25 MB");
        String submitted = upload.getOriginalFilename();
        String originalName = submitted == null ? "file" : submitted.replace('\\', '/');
        originalName = originalName.substring(originalName.lastIndexOf('/') + 1);
        if (originalName.isBlank() || originalName.length() > 255) throw bad("文件名无效");
        String storageKey = UUID.randomUUID().toString().replace("-", "");
        Path target = safePath(storageKey);
        try {
            Files.createDirectories(root);
            try (var input = upload.getInputStream()) {
                Files.copy(input, target);
            }
            UnCommonFile item = new UnCommonFile();
            item.setScopeType(scopeType);
            item.setScopeId(scopeId);
            item.setOriginalName(originalName);
            item.setStorageKey(storageKey);
            String contentType = upload.getContentType();
            item.setContentType(contentType != null && contentType.length() <= 150 ? contentType : null);
            item.setFileSize(upload.getSize());
            item.setUploaderId(uploaderId);
            try {
                files.insert(item);
            } catch (RuntimeException failure) {
                Files.deleteIfExists(target);
                throw failure;
            }
            return view(item);
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败", failure);
        }
    }

    /** 下载前重新核对文件归属与磁盘真实路径，拒绝越界和符号链接。 */
    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable long id, HttpServletRequest request) {
        UnCommonFile item = file(id);
        scope.read(item.getScopeType(), item.getScopeId(), request);
        Path target = safePath(item.getStorageKey());
        try {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || !target.toRealPath().startsWith(root.toRealPath())) {
                throw missing("文件内容不存在");
            }
        } catch (IOException failure) {
            throw missing("文件内容不存在");
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(item.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(item.getFileSize())
                .body(new FileSystemResource(target));
    }

    /** 删除元数据与文件时需要对应范围管理员权限。 */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable long id, HttpServletRequest request) {
        UnCommonFile item = file(id);
        scope.admin(item.getScopeType(), item.getScopeId(), request);
        Path target = safePath(item.getStorageKey());
        files.deleteById(id);
        try {
            Files.deleteIfExists(target);
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件删除失败", failure);
        }
    }

    private Path safePath(String key) {
        if (key == null || !key.matches("[0-9a-f]{32}")) throw bad("文件存储标识无效");
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw bad("文件路径越界");
        return target;
    }

    private UnCommonFile file(long id) {
        UnCommonFile item = files.selectById(id);
        if (item == null) throw missing("文件不存在");
        return item;
    }

    private FileView view(UnCommonFile item) {
        return new FileView(item.getId(), item.getScopeType(), item.getScopeId(),
                item.getOriginalName(), item.getContentType(), item.getFileSize(), item.getCreateTime());
    }

    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private ResponseStatusException missing(String text) { return new ResponseStatusException(HttpStatus.NOT_FOUND, text); }

    /** 文件响应不暴露服务器内部存储位置。 */
    public record FileView(Long id, String scopeType, Long scopeId, String originalName,
                           String contentType, Long fileSize, java.time.LocalDateTime createTime) {}
}
