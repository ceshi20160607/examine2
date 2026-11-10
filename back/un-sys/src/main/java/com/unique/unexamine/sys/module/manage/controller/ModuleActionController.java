package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportJobView;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.ImportResult;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.Mode;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.JobView;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.RowView;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.RowSearch;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.PreviewResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.ActionView;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.ActionResult;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.ExecuteRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RecordInformation;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RuntimeAction;
import com.unique.unexamine.sys.module.manage.service.ModuleExportService;
import com.unique.unexamine.sys.module.manage.service.ModuleExportJobService;
import com.unique.unexamine.sys.module.manage.service.ModuleMenuService;
import com.unique.unexamine.sys.module.manage.service.ModuleImportService;
import com.unique.unexamine.sys.module.manage.service.ModuleImportRollbackService;
import com.unique.unexamine.sys.module.manage.service.ModulePrintService;
import com.unique.unexamine.sys.module.manage.service.ModulePrintPdfService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 模块默认和自定义动作共用菜单配置与执行入口，Controller 不执行字段查询或保存。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/actions")
@Tag(name="模块菜单动作")
public class ModuleActionController {
    private final ModuleMenuService menus;
    private final ModuleRecordService records;
    private final ModuleExportService exports;
    private final ModuleExportJobService exportJobs;
    private final ModuleImportService imports;
    private final ModuleImportRollbackService importRollback;
    private final ModulePrintService prints;
    private final ModulePrintPdfService printPdf;
    /** 注入模块默认动作的导入、打印执行服务。 */
    public ModuleActionController(ModuleMenuService menus,ModuleRecordService records,ModuleExportService exports,ModuleExportJobService exportJobs,
            ModuleImportService imports,ModuleImportRollbackService importRollback,
            ModulePrintService prints,ModulePrintPdfService printPdf) {
        this.menus=menus; this.records=records; this.exports=exports; this.exportJobs=exportJobs;
        this.imports=imports; this.importRollback=importRollback; this.prints=prints; this.printPdf=printPdf;
    }

    /** 后台查看十个默认菜单和自定义动作。 */
    @GetMapping("/configuration")
    @Operation(summary="读取模块动作配置")
    public Result<List<ActionView>> configuration(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(menus.configuration(token,systemId,tenantId,moduleId));
    }
    /** 运行态只返回已生效、启用且角色获权的菜单。 */
    @GetMapping
    @Operation(summary="读取可用模块动作")
    public Result<List<RuntimeAction>> available(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(records.availableActions(token,systemId,tenantId,moduleId));
    }

    /** 按当前菜单及字段权限读取指定记录的字段更新动作表单。 */
    @GetMapping("/{menuId}/records/{recordId}/form")
    @Operation(summary="读取字段更新动作表单")
    public Result<RecordInformation> actionForm(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long menuId,@PathVariable Long recordId) {
        return Result.ok(records.actionForm(token,systemId,tenantId,moduleId,menuId,recordId));
    }

    /** 按菜单配置执行当前记录动作，字段保存及后续流程均由业务服务处理。 */
    @PostMapping("/{menuId}/records/{recordId}/execute")
    @Operation(summary="执行模块记录动作")
    public Result<ActionResult> execute(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long menuId,@PathVariable Long recordId,
            @Valid @RequestBody ExecuteRequest request) {
        return Result.ok(records.execute(token,systemId,tenantId,moduleId,menuId,recordId,request));
    }

    /** 保存动作配置立即改变运行态。 */
    @PostMapping
    @Operation(summary="保存模块动作配置")
    public Result<ActionView> save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId,
            @Valid @RequestBody SaveRequest request) { return Result.ok(menus.save(token,systemId,tenantId,moduleId,request)); }

    /** 当前筛选的小批量 CSV 由导出菜单独立授权，列受字段导出许可限制。 */
    @PostMapping("/export")
    @Operation(summary="导出当前筛选的模块记录")
    public ResponseEntity<byte[]> export(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestBody ExportRequest request) {
        var file=exports.export(token,systemId,tenantId,moduleId,request);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(file.content());
    }

    /** 大批量 CSV 进入后台队列，使用作业 ID 查询结果和失败原因。 */
    @PostMapping("/exports")
    @Operation(summary="提交后台导出")
    public Result<ExportJobView> submitExport(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestBody ExportRequest request) {
        return Result.ok(exportJobs.submit(token,systemId,tenantId,moduleId,request));
    }

    /** 仅作业发起人或租户管理员可查询后台导出结果。 */
    @GetMapping("/exports/{jobId}")
    @Operation(summary="查询后台导出作业")
    public Result<ExportJobView> exportJob(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long jobId) {
        return Result.ok(exportJobs.job(token,systemId,tenantId,moduleId,jobId));
    }

    /** 分页读取当前可见的后台导出作业。 */
    @GetMapping("/exports")
    @Operation(summary="查询后台导出列表")
    public Result<Page<ExportJobView>> exportJobs(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@ModelAttribute SearchBO search) {
        return Result.ok(exportJobs.listJobs(token,systemId,tenantId,moduleId,search));
    }

    /** 作业成功后按当前导出权限流式下载结果。 */
    @GetMapping("/exports/{jobId}/download")
    @Operation(summary="下载后台导出结果")
    public ResponseEntity<InputStreamResource> downloadExport(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long jobId) throws IOException {
        var content=exportJobs.content(token,systemId,tenantId,moduleId,jobId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(content.name(),StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(content.size()).body(new InputStreamResource(content.stream()));
    }

    /** 上传 CSV 先做无副作用字段预检，返回短期确认凭据。 */
    @PostMapping(value="/import/preview", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="预检模块导入")
    public Result<PreviewResult> previewImport(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value="mode",required=false,defaultValue="CREATE") Mode mode) {
        return Result.ok(imports.previewCsv(token,systemId,tenantId,moduleId,file,mode));
    }

    /** 按当前导入动作和字段写入权限下载 CSV 标题模板。 */
    @GetMapping("/import/template")
    @Operation(summary="下载导入模板")
    public ResponseEntity<byte[]> importTemplate(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestParam(value="mode",defaultValue="CREATE") Mode mode) {
        var file=imports.template(token,systemId,tenantId,moduleId,mode);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(file.content());
    }

    /** 确认刚才预检的同一文件，逐行执行并返回持久化作业。 */
    @PostMapping(value="/import", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="确认导入模块记录")
    public Result<ImportResult> importRecords(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value="mode",required=false,defaultValue="CREATE") Mode mode,
            @RequestParam("previewId") String previewId) {
        return Result.ok(imports.confirmCsv(token,systemId,tenantId,moduleId,file,mode,previewId));
    }

    /** 导入者或当前租户管理员读取持久化作业结果。 */
    @GetMapping("/imports/{jobId}")
    @Operation(summary="查询导入作业")
    public Result<JobView> importJob(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long jobId) {
        return Result.ok(imports.job(token,systemId,tenantId,moduleId,jobId));
    }

    /** 分页读取本人或管理员当前租户的导入作业。 */
    @GetMapping("/imports")
    @Operation(summary="查询导入作业列表")
    public Result<Page<JobView>> importJobs(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@ModelAttribute SearchBO search) {
        return Result.ok(imports.listJobs(token,systemId,tenantId,moduleId,search));
    }

    /** 按成功或失败状态分页读取逐行结果。 */
    @GetMapping("/imports/{jobId}/rows")
    @Operation(summary="查询导入逐行结果")
    public Result<Page<RowView>> importRows(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long jobId,@ModelAttribute RowSearch search) {
        return Result.ok(imports.rows(token,systemId,tenantId,moduleId,jobId,search));
    }

    /** 仅导入者或管理员可下载当前作业的失败行及原始列。 */
    @GetMapping("/imports/{jobId}/errors")
    @Operation(summary="下载导入错误行")
    public ResponseEntity<byte[]> importErrors(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long jobId) {
        var file=imports.errors(token,systemId,tenantId,moduleId,jobId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(file.content());
    }

    /** 回滚前列出被后续修改、字段变更或流程关联阻断的具体行。 */
    @GetMapping("/imports/{jobId}/rollback-check")
    @Operation(summary="检查导入批次是否可回滚")
    public Result<com.unique.unexamine.sys.module.manage.dto.ModuleImportRollbackDto.Check> rollbackCheck(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long jobId) {
        return Result.ok(importRollback.check(token,systemId,tenantId,moduleId,jobId));
    }

    /** 确认后在同一事务中仅还原此批成功行。 */
    @PostMapping("/imports/{jobId}/rollback")
    @Operation(summary="回滚导入批次")
    public Result<com.unique.unexamine.sys.module.manage.dto.ModuleImportRollbackDto.Result> rollbackImport(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long jobId) {
        return Result.ok(importRollback.rollback(token,systemId,tenantId,moduleId,jobId));
    }

    /** 打印动作返回当前模板版本和已按字段权限过滤的业务值。 */
    @PostMapping("/print")
    @Operation(summary="打印模块记录")
    public Result<com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintView> print(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestBody PrintRequest request) {
        return Result.ok(prints.print(token,systemId,tenantId,moduleId,request));
    }

    /** 与打印预览共用模板和权限，下载服务端生成的 PDF。 */
    @PostMapping("/print/pdf")
    @Operation(summary="下载模块打印 PDF")
    public ResponseEntity<byte[]> printPdf(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestBody PrintRequest request) {
        byte[] content=printPdf.pdf(token,systemId,tenantId,moduleId,request);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("module-"+moduleId+"-record-"+request.recordId()+".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(content);
    }
}
