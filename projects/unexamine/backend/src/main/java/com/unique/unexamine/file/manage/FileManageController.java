package com.unique.unexamine.file.manage;

import com.unique.unexamine.platform.manage.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 记录附件和待办附件的上传、列表、预览、缩略图、下载及移除接口。 */
@RestController
@RequestMapping("/api/systems/{systemId}")
public class FileManageController {
    private final AccountSessionService sessions;
    private final FileManageService files;

    public FileManageController(AccountSessionService sessions, FileManageService files) {
        this.sessions = sessions;
        this.files = files;
    }

    /** 上传时由业务服务检查记录状态和负责范围。 */
    @PostMapping(value = "/modules/{moduleId}/records/{recordId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileManageService.FileView upload(@PathVariable Long systemId, @PathVariable Long moduleId,
                                              @PathVariable Long recordId, @RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        return files.upload(accountId(request), systemId, moduleId, recordId, file);
    }

    /** 列出当前业务记录中的附件。 */
    @GetMapping("/modules/{moduleId}/records/{recordId}/files")
    public List<FileManageService.FileView> list(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                 @PathVariable Long recordId, HttpServletRequest request) {
        return files.list(accountId(request), systemId, moduleId, recordId);
    }

    /** 下载要求同时满足业务记录和附件引用的归属校验。 */
    @GetMapping("/modules/{moduleId}/records/{recordId}/files/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long systemId, @PathVariable Long moduleId,
                                           @PathVariable Long recordId, @PathVariable Long fileId,
                                           HttpServletRequest request) {
        return response(files.read(accountId(request), systemId, moduleId, recordId, fileId, false, false));
    }

    /** 仅允许安全识别的图片和 PDF 内联预览。 */
    @GetMapping("/modules/{moduleId}/records/{recordId}/files/{fileId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long systemId, @PathVariable Long moduleId,
                                          @PathVariable Long recordId, @PathVariable Long fileId,
                                          HttpServletRequest request) {
        return response(files.read(accountId(request), systemId, moduleId, recordId, fileId, true, false));
    }

    /** 图片缩略图按请求生成，不引入额外缩略图表。 */
    @GetMapping("/modules/{moduleId}/records/{recordId}/files/{fileId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable Long systemId, @PathVariable Long moduleId,
                                            @PathVariable Long recordId, @PathVariable Long fileId,
                                            HttpServletRequest request) {
        return response(files.read(accountId(request), systemId, moduleId, recordId, fileId, false, true));
    }

    /** 文件移除后不能再通过旧地址读取。 */
    @DeleteMapping("/modules/{moduleId}/records/{recordId}/files/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long systemId, @PathVariable Long moduleId,
                       @PathVariable Long recordId, @PathVariable Long fileId, HttpServletRequest request) {
        files.remove(accountId(request), systemId, moduleId, recordId, fileId);
    }

    /** 审批人可从本人待办直接查看对应附件。 */
    @GetMapping("/approval/tasks/{taskId}/files")
    public List<FileManageService.FileView> taskFiles(@PathVariable Long systemId, @PathVariable Long taskId,
                                                       HttpServletRequest request) {
        return files.listForTask(accountId(request), systemId, taskId);
    }

    /** 待办下载使用审批任务授权，避免扩大记录列表权限。 */
    @GetMapping("/approval/tasks/{taskId}/files/{fileId}/download")
    public ResponseEntity<byte[]> taskDownload(@PathVariable Long systemId, @PathVariable Long taskId,
                                               @PathVariable Long fileId, HttpServletRequest request) {
        return response(files.readForTask(accountId(request), systemId, taskId, fileId, false, false));
    }

    /** 待办中的图片和 PDF 可直接预览。 */
    @GetMapping("/approval/tasks/{taskId}/files/{fileId}/preview")
    public ResponseEntity<byte[]> taskPreview(@PathVariable Long systemId, @PathVariable Long taskId,
                                              @PathVariable Long fileId, HttpServletRequest request) {
        return response(files.readForTask(accountId(request), systemId, taskId, fileId, true, false));
    }

    private ResponseEntity<byte[]> response(FileManageService.FileBytes file) {
        ContentDisposition disposition = (file.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType())).body(file.bytes());
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }
}
