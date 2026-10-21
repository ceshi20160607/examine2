package com.unique.unexamine.print.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.unique.unexamine.print.manage.PrintService.PrintPage;
import com.unique.unexamine.print.manage.PrintService.RenderView;
import com.unique.unexamine.print.manage.PrintService.TemplateInput;
import com.unique.unexamine.print.manage.PrintService.TemplateUpdate;
import com.unique.unexamine.print.manage.PrintService.TemplateView;

/** 打印模板 HTTP 入口：只绑定参数并调用打印业务服务。 */
@Tag(name = "打印模板", description = "打印模板和模块记录预览。")
@RestController
@RequestMapping("/api/tenants/{tenantId}/prints")
public class PrintController {
    private final PrintService service;

    public PrintController(PrintService service) {
        this.service = service;
    }

    /** 查询租户模块下的模板列表。 */
    @Operation(summary = "查询列表（打印模板）")
    @GetMapping
    public PrintPage list(@PathVariable long tenantId, @RequestParam long moduleId,
                          @RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long size,
                          HttpServletRequest request) {
        return service.list(tenantId, moduleId, page, size, request);
    }

    /** 创建租户模块模板。 */
    @Operation(summary = "新建（打印模板）")
    @PostMapping
    public TemplateView create(@PathVariable long tenantId, @Valid @RequestBody TemplateInput input,
                               HttpServletRequest request) {
        return service.create(tenantId, input, request);
    }

    /** 查询租户模板详情。 */
    @Operation(summary = "查询详情（打印模板）")
    @GetMapping("/{templateId}")
    public TemplateView detail(@PathVariable long tenantId, @PathVariable long templateId,
                               HttpServletRequest request) {
        return service.detail(tenantId, templateId, request);
    }

    /** 更新租户模板。 */
    @Operation(summary = "修改（打印模板）")
    @PutMapping("/{templateId}")
    public TemplateView update(@PathVariable long tenantId, @PathVariable long templateId,
                               @Valid @RequestBody TemplateUpdate input, HttpServletRequest request) {
        return service.update(tenantId, templateId, input, request);
    }

    /** 预览模板对应的可读模块记录。 */
    @Operation(summary = "渲染打印预览（打印模板）")
    @GetMapping("/{templateId}/render")
    public RenderView render(@PathVariable long tenantId, @PathVariable long templateId,
                             @RequestParam long recordId, HttpServletRequest request) {
        return service.render(tenantId, templateId, recordId, request);
    }
}
