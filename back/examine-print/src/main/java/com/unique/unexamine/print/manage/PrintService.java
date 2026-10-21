package com.unique.unexamine.print.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.module.base.entity.UnModuleTenant;
import com.unique.unexamine.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.module.base.service.IUnModuleService;
import com.unique.unexamine.module.base.service.IUnModuleTenantService;
import com.unique.unexamine.module.manage.ModuleRecordAccess;
import com.unique.unexamine.print.base.entity.UnPrint;
import com.unique.unexamine.print.base.service.IUnPrintService;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.service.IUnSysTenantService;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 打印模板业务服务：租户授权、模块归属、模板持久化与记录预览都在此完成。 */
@Service
public class PrintService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z][a-z0-9_]*)}");
    private final SystemSessionAccess access;
    private final IUnPrintService prints;
    private final IUnSysTenantService tenants;
    private final IUnModuleService modules;
    private final IUnModuleTenantService tenantModules;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService values;
    private final ModuleRecordAccess recordAccess;

    public PrintService(SystemSessionAccess access, IUnPrintService prints, IUnSysTenantService tenants,
                        IUnModuleService modules, IUnModuleTenantService tenantModules,
                        IUnModuleRecordService records, IUnModuleRecordDataService values,
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

    /** 只查询当前租户已启用模块下的模板。 */
    public PrintPage list(long tenantId, long moduleId, long page, long size, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        requireModule(tenantId, moduleId);
        if (page < 1 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        }
        Page<UnPrint> result = prints.queryPageList(new Page<>(page, size), new LambdaQueryWrapper<UnPrint>()
                .eq(UnPrint::getTenantId, tenantId).eq(UnPrint::getModuleId, moduleId)
                .orderByAsc(UnPrint::getTemplateName, UnPrint::getId));
        return new PrintPage(result.getRecords().stream().map(this::summary).toList(),
                result.getTotal(), page, size);
    }

    /** 创建模板前检查租户管理员权限和模块归属。 */
    public TemplateView create(long tenantId, TemplateInput input, HttpServletRequest request) {
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
        prints.addOrUpdate(template);
        return detailView(template);
    }

    /** 详情查询同时校验模板属于当前租户。 */
    public TemplateView detail(long tenantId, long templateId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        return detailView(requireTemplate(tenantId, templateId));
    }

    /** 只允许租户管理员修改模板可编辑字段。 */
    public TemplateView update(long tenantId, long templateId, TemplateUpdate input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnPrint template = requireTemplate(tenantId, templateId);
        template.setTemplateName(input.templateName().trim());
        template.setTemplateContent(input.content());
        template.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        prints.addOrUpdate(template);
        return detailView(template);
    }

    /** 预览仅替换当前租户可读记录的已保存字段，返回纯文本。 */
    public RenderView render(long tenantId, long templateId, long recordId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        UnPrint template = requireTemplate(tenantId, templateId);
        if (template.getStatus() == null || template.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模板已停用");
        }
        UnModuleRecord record = records.getById(recordId);
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
        for (UnModuleRecordData value : values.list(new LambdaQueryWrapper<UnModuleRecordData>()
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

    /** 校验模块属于租户所用系统且已在该租户启用。 */
    private void requireModule(long tenantId, long moduleId) {
        UnSysTenant tenant = tenants.getById(tenantId);
        UnModule module = modules.getById(moduleId);
        if (tenant == null || module == null || !module.getSystemId().equals(tenant.getSystemId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模块不属于此租户的系统");
        }
        if (tenantModules.count(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getTenantId, tenantId)
                .eq(UnModuleTenant::getModuleId, moduleId)
                .eq(UnModuleTenant::getStatus, 1)) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "租户未启用此模块");
        }
    }

    /** 主键查询后再次限定租户，防止跨租户读取。 */
    private UnPrint requireTemplate(long tenantId, long templateId) {
        UnPrint template = prints.queryById(templateId);
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

    /** 创建模板的请求字段。 */
    public record TemplateInput(long moduleId, @NotBlank @Size(max = 64) String templateCode,
                                @NotBlank @Size(max = 120) String templateName, @NotBlank String content) { }
    /** 更新模板只允许修改名称、内容和启用状态。 */
    public record TemplateUpdate(@NotBlank @Size(max = 120) String templateName,
                                 @NotBlank String content, boolean enabled) { }
    /** 列表中无需返回模板正文。 */
    public record SummaryView(Long id, Long moduleId, String templateCode, String templateName, Byte status) { }
    /** 单个模板详情。 */
    public record TemplateView(Long id, Long moduleId, String templateCode,
                               String templateName, String content, Byte status) { }
    /** 模板分页结果。 */
    public record PrintPage(List<SummaryView> records, long total, long page, long size) { }
    /** 当前记录的纯文本预览结果。 */
    public record RenderView(Long templateId, long recordId, String renderedText) { }
}
