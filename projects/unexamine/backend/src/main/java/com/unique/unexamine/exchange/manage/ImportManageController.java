package com.unique.unexamine.exchange.manage;

import com.unique.unexamine.platform.manage.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** 模块 Excel 模板、上传预检查、确认导入、历史与错误行下载。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/import-jobs")
public class ImportManageController {
    private final AccountSessionService sessions;
    private final ImportManageService imports;

    public ImportManageController(AccountSessionService sessions, ImportManageService imports) {
        this.sessions = sessions;
        this.imports = imports;
    }

    /** 按当前发布字段生成 Excel 模板。 */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@PathVariable Long systemId, @PathVariable Long moduleId,
                                           HttpServletRequest request) {
        return workbook(imports.template(accountId(request), systemId, moduleId), "module-" + moduleId + "-template.xlsx");
    }

    /** 上传只执行预检查，业务记录尚未写入。 */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportManageService.ImportView preview(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                   @RequestParam("mode") String mode,
                                                   @RequestParam("file") MultipartFile file,
                                                   HttpServletRequest request) {
        return imports.preview(accountId(request), systemId, moduleId, mode, file);
    }

    /** 预检查完全通过后整批确认，失败整批回滚。 */
    @PostMapping("/{jobId}/commit")
    public ImportManageService.ImportView commit(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                  @PathVariable Long jobId, HttpServletRequest request) {
        return imports.commit(accountId(request), systemId, moduleId, jobId);
    }

    /** 查询本人最近的导入任务，不暴露源文件位置。 */
    @GetMapping
    public List<ImportManageService.ImportView> history(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                        HttpServletRequest request) {
        return imports.history(accountId(request), systemId, moduleId);
    }

    /** 预检查失败行包含原值与原因，供提交人修正后重传。 */
    @GetMapping("/{jobId}/errors")
    public ResponseEntity<byte[]> errors(@PathVariable Long systemId, @PathVariable Long moduleId,
                                         @PathVariable Long jobId, HttpServletRequest request) {
        return workbook(imports.errors(accountId(request), systemId, moduleId, jobId),
                "module-" + moduleId + "-import-errors-" + jobId + ".xlsx");
    }

    private ResponseEntity<byte[]> workbook(byte[] bytes, String filename) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }
}
