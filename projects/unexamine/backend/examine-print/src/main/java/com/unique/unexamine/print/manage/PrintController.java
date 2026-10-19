package com.unique.unexamine.print.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.module.base.entity.UnModuleTenant;
import com.unique.unexamine.module.base.mapper.UnModuleMapper;
import com.unique.unexamine.module.base.mapper.UnModuleRecordDataMapper;
import com.unique.unexamine.module.base.mapper.UnModuleRecordMapper;
import com.unique.unexamine.module.base.mapper.UnModuleTenantMapper;
import com.unique.unexamine.print.base.entity.UnPrint;
import com.unique.unexamine.print.base.mapper.UnPrintMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import com.unique.unexamine.module.manage.ModuleRecordAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 打印模板按租户模块配置，预览只替换当前租户记录中的已保存字段。 */
@Tag(name = "打印模板", description = "打印模板和模块记录预览。")
@RestController
@RequestMapping("/api/tenants/{tenantId}/prints")
public class PrintController {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z][a-z0-9_]*)}");
    private final SystemSessionAccess access;
    private final UnPrintMapper prints;
    private final UnSysTenantMapper tenants;
    private final UnModuleMapper modules;
    private final UnModuleTenantMapper tenantModules;
    private final UnModuleRecordMapper records;
    private final UnModuleRecordDataMapper values;
    private final ModuleRecordAccess recordAccess;

    public PrintController(SystemSessionAccess access, UnPrintMapper prints, UnSysTenantMapper tenants,
                           UnModuleMapper modules, UnModuleTenantMapper tenantModules,
                           UnModuleRecordMapper records, UnModuleRecordDataMapper values,
                           ModuleRecordAccess recordAccess) {
        this.access = access;
        this.prints = prints;
        this.tenants = tenants;
        this.modules = modules;
        this.tenantModules = tenantModules;
        this.records = records;
        this.values = values;
        this.recordAccess = recordAccess;
    }

    @Operation(summary = "查询列表（打印模板）")
    @GetMapping
    public PrintPage list(@PathVariable long tenantId, @RequestParam long moduleId,
                          @RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long size,
                          HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        requireModule(tenantId, moduleId);
        if (page < 1 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        }
        Page<UnPrint> result = prints.selectPage(new Page<>(page, size), new LambdaQueryWrapper<UnPrint>()
                .eq(UnPrint::getTenantId, tenantId).eq(UnPrint::getModuleId, moduleId)
                .orderByAsc(UnPrint::getTemplateName, UnPrint::getId));
        return new PrintPage(result.getRecords().stream().map(this::summary).toList(),
                result.getTotal(), page, size);
    }

    @Operation(summary = "新建（打印模板）")
    @PostMapping
    public TemplateView create(@PathVariable long tenantId, @Valid @RequestBody TemplateInput input,
                               HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        requireModule(tenantId, input.moduleId());
        if (!input.templateCode().matches("[a-z][a-z0-9_]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板编码无效");
        }
        UnPrint template = new UnPrint();
        template.setTenantId(tenantId);
        template.setModuleId(input.moduleId());
        template.setTemplateCode(input.templateCode());
        template.setTemplateName(input.templateName().trim());
        template.setTemplateContent(input.content());
        template.setStatus((byte) 1);
        prints.insert(template);
        return detailView(template);
    }

    @Operation(summary = "查询详情（打印模板）")
    @GetMapping("/{templateId}")
    public TemplateView detail(@PathVariable long tenantId, @PathVariable long templateId,
                               HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        return detailView(requireTemplate(tenantId, templateId));
    }

    @Operation(summary = "修改（打印模板）")
    @PutMapping("/{templateId}")
    public TemplateView update(@PathVariable long tenantId, @PathVariable long templateId,
                               @Valid @RequestBody TemplateUpdate input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnPrint template = requireTemplate(tenantId, templateId);
        template.setTemplateName(input.templateName().trim());
        template.setTemplateContent(input.content());
        template.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        prints.updateById(template);
        return detailView(template);
    }

    /** 预览返回纯文本；模板和记录字段都不作为 HTML 或脚本执行。 */
    @Operation(summary = "渲染打印预览（打印模板）")
    @GetMapping("/{templateId}/render")
    public RenderView render(@PathVariable long tenantId, @PathVariable long templateId,
                             @RequestParam long recordId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        UnPrint template = requireTemplate(tenantId, templateId);
        if (template.getStatus() == null || template.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模板已停用");
        }
        UnModuleRecord record = records.selectById(recordId);
        if (record == null || !record.getTenantId().equals(tenantId)
                || !record.getModuleId().equals(template.getModuleId())
                || record.getRecordStatus() == null || record.getRecordStatus() == 2) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模块记录不存在");
        }
        recordAccess.requireRead(recordAccess.scope(request, tenantId), record);
        Map<String, String> data = new HashMap<>();
        data.put("name", record.getName());
        data.put("num", record.getNum());
        data.put("remark", record.getRemark());
        for (UnModuleRecordData value : values.selectList(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId, recordId)
                .eq(UnModuleRecordData::getTenantId, tenantId)
                .eq(UnModuleRecordData::getModuleId, template.getModuleId()))) {
            String text = switch (value.getValueType()) {
                case "number" -> value.getValueNumber() == null ? "" : value.getValueNumber().toPlainString();
                case "time" -> value.getValueTime() == null ? "" : value.getValueTime().toString();
                case "json" -> value.getValueJson();
                default -> value.getValueText();
            };
            data.put(value.getFieldName(), text);
        }
        Matcher matcher = PLACEHOLDER.matcher(template.getTemplateContent());
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(
                    data.getOrDefault(matcher.group(1), "") == null ? "" : data.get(matcher.group(1))));
        }
        matcher.appendTail(rendered);
        return new RenderView(template.getId(), recordId, rendered.toString());
    }

    private void requireModule(long tenantId, long moduleId) {
        UnSysTenant tenant = tenants.selectById(tenantId);
        UnModule module = modules.selectById(moduleId);
        if (tenant == null || module == null || !module.getSystemId().equals(tenant.getSystemId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模块不属于此租户的系统");
        }
        if (tenantModules.selectCount(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getTenantId, tenantId)
                .eq(UnModuleTenant::getModuleId, moduleId)
                .eq(UnModuleTenant::getStatus, 1)) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "租户未启用此模块");
        }
    }

    private UnPrint requireTemplate(long tenantId, long templateId) {
        UnPrint template = prints.selectById(templateId);
        if (template == null || !template.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "打印模板不存在");
        }
        return template;
    }

    private SummaryView summary(UnPrint template) {
        return new SummaryView(template.getId(), template.getModuleId(), template.getTemplateCode(),
                template.getTemplateName(), template.getStatus());
    }

    private TemplateView detailView(UnPrint template) {
        return new TemplateView(template.getId(), template.getModuleId(), template.getTemplateCode(),
                template.getTemplateName(), template.getTemplateContent(), template.getStatus());
    }

    public record TemplateInput(long moduleId, @NotBlank @Size(max = 64) String templateCode,
                                @NotBlank @Size(max = 120) String templateName, @NotBlank String content) { }
    public record TemplateUpdate(@NotBlank @Size(max = 120) String templateName,
                                 @NotBlank String content, boolean enabled) { }
    public record SummaryView(Long id, Long moduleId, String templateCode, String templateName, Byte status) { }
    public record TemplateView(Long id, Long moduleId, String templateCode,
                               String templateName, String content, Byte status) { }
    public record PrintPage(List<SummaryView> records, long total, long page, long size) { }
    public record RenderView(Long templateId, long recordId, String renderedText) { }
}
