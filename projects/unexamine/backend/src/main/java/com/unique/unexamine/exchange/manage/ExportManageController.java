package com.unique.unexamine.exchange.manage;

import com.unique.unexamine.platform.manage.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 模块当前筛选数据的异步 CSV 导出任务与结果下载接口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/export-jobs")
public class ExportManageController {
    private final AccountSessionService sessions;
    private final ExportManageService exports;

    public ExportManageController(AccountSessionService sessions, ExportManageService exports) {
        this.sessions = sessions;
        this.exports = exports;
    }

    /** 提交任务后立即返回状态，后台按相同权限和筛选生成结果。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExportManageService.JobView submit(@PathVariable Long systemId, @PathVariable Long moduleId,
                                              @RequestBody ExportManageService.ExportRequest body,
                                              HttpServletRequest request) {
        return exports.submit(accountId(request), systemId, moduleId, body);
    }

    /** 只展示当前账号提交的最近任务。 */
    @GetMapping
    public List<ExportManageService.JobView> list(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                  HttpServletRequest request) {
        return exports.list(accountId(request), systemId, moduleId);
    }

    /** 结果文件只向任务提交人流式返回。 */
    @GetMapping("/{jobId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                       @PathVariable Long jobId, HttpServletRequest request) {
        ExportManageService.ExportFile file = exports.download(accountId(request), systemId, moduleId, jobId);
        ContentDisposition disposition = ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(new FileSystemResource(file.path()));
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }
}
