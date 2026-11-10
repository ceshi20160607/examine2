package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.action.print.base.entity.UnPrint;
import com.unique.unexamine.sys.action.print.base.service.IUnPrintService;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintTemplateDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintTemplateDto.TemplateSearch;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintTemplateDto.TemplateView;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块打印模板按租户和模板编码维护独立版本，发布后内容不再修改。 */
@Service
public class ModulePrintTemplateService {
    private final SystemManageService systems;
    private final ModuleConfigService modules;
    private final IUnPrintService templates;
    private final IUnSysLogOptService logs;
    private final ObjectMapper json;

    public ModulePrintTemplateService(SystemManageService systems, ModuleConfigService modules,
            IUnPrintService templates, IUnSysLogOptService logs, ObjectMapper json) {
        this.systems = systems; this.modules = modules; this.templates = templates;
        this.logs = logs; this.json = json;
    }

    /** 只列出当前租户定义的模板版本，主租户继承由打印运行态处理。 */
    public Page<TemplateView> list(String token, Long systemId, Long tenantId, Long moduleId,
            TemplateSearch search) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        modules.activeModule(systemId, moduleId, context.tenantId());
        TemplateSearch criteria = search == null ? new TemplateSearch() : search;
        var where = new LambdaQueryWrapper<UnPrint>()
                .eq(UnPrint::getSystemId, systemId).eq(UnPrint::getTenantId, context.tenantId())
                .eq(UnPrint::getModuleId, moduleId)
                .orderByDesc(UnPrint::getId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank())
            where.eq(UnPrint::getStatus, status(criteria.getStatus()));
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            if (criteria.getKeyword().length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模板搜索内容过长");
            where.and(w -> w.like(UnPrint::getTemplateCode, criteria.getKeyword().trim())
                    .or().like(UnPrint::getName, criteria.getKeyword().trim()));
        }
        Page<UnPrint> page = templates.page(criteria.parse(), where);
        return new Page<TemplateView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 新建草稿时自动递增版本；只允许原位修改尚未发布的草稿。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView save(String token, Long systemId, Long tenantId, Long moduleId, SaveRequest request) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        modules.activeModule(systemId, moduleId, context.tenantId());
        if (request == null || request.templateCode() == null
                || !request.templateCode().matches("[A-Za-z][A-Za-z0-9_]{0,79}")
                || request.name() == null || request.name().isBlank() || request.name().length() > 120
                || request.paper() == null || !request.paper().isObject()
                || request.body() == null || !request.body().isObject()
                || request.paper().toString().length() > 20000 || request.body().toString().length() > 60000)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "打印模板内容无效");
        validatePaper(request.paper());
        validateBody(context, moduleId, request.body(),request.paper());
        UnPrint row = request.id() == null ? new UnPrint() : owned(context, moduleId, request.id());
        boolean creating = row.getId() == null;
        if (creating) {
            UnPrint latest = latest(context, moduleId, request.templateCode());
            row.setSystemId(context.systemId()); row.setTenantId(context.tenantId()); row.setModuleId(moduleId);
            row.setTemplateCode(request.templateCode());
            row.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
            row.setCreateAccountId(context.accountId()); row.setCreateTime(LocalDateTime.now());
        } else if (!"DRAFT".equals(row.getStatus()) || !row.getTemplateCode().equals(request.templateCode())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "只有同一编码的草稿版本可修改");
        }
        row.setName(request.name().trim()); row.setPaperConfigJson(request.paper().toString());
        row.setBodyConfigJson(request.body().toString()); row.setStatus("DRAFT");
        row.setUpdateTime(LocalDateTime.now());
        try { templates.addOrUpdate(row); }
        catch (DuplicateKeyException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模板版本已存在", ex); }
        audit(context, "PRINT_TEMPLATE_SAVE", row.getId());
        return view(row);
    }

    /** 发布时重验当前字段，避免草稿保存后字段已停用。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView publish(String token, Long systemId, Long tenantId, Long moduleId, Long id) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        modules.activeModule(systemId, moduleId, context.tenantId());
        UnPrint row = owned(context, moduleId, id);
        if (!"DRAFT".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "只有草稿版本可发布");
        UnPrint latest = latestEffective(context, moduleId, row.getTemplateCode());
        if (latest != null && latest.getVersionNo() >= row.getVersionNo())
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "存在更新的已发布或停用版本");
        validatePaper(parse(row.getPaperConfigJson()));
        validateBody(context, moduleId, parse(row.getBodyConfigJson()),parse(row.getPaperConfigJson()));
        row.setStatus("PUBLISHED"); row.setPublishTime(LocalDateTime.now()); row.setUpdateTime(LocalDateTime.now());
        templates.addOrUpdate(row); audit(context, "PRINT_TEMPLATE_PUBLISH", row.getId());
        return view(row);
    }

    /** 停用当前生效版本；后续打印不回退到更早的发布版本。 */
    @Transactional(rollbackFor = Exception.class)
    public TemplateView disable(String token, Long systemId, Long tenantId, Long moduleId, Long id) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        modules.activeModule(systemId, moduleId, context.tenantId());
        UnPrint row = owned(context, moduleId, id);
        UnPrint latest = latestEffective(context, moduleId, row.getTemplateCode());
        if (latest == null || !latest.getId().equals(id) || !"PUBLISHED".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "只能停用当前已发布的模板版本");
        row.setStatus("DISABLED"); row.setUpdateTime(LocalDateTime.now());
        templates.addOrUpdate(row); audit(context, "PRINT_TEMPLATE_DISABLE", row.getId());
        return view(row);
    }

    private void validatePaper(JsonNode paper) {
        if (paper == null || !paper.isObject() || paper.size() < 3 || paper.size() > 5
                || !Set.of("A4", "A5", "LETTER").contains(paper.path("size").asText())
                || !Set.of("PORTRAIT", "LANDSCAPE").contains(paper.path("orientation").asText())
                || !paper.path("marginMm").isNumber() || paper.path("marginMm").asDouble() < 0
                || paper.path("marginMm").asDouble() > 40)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "打印纸张配置无效");
        paper.fieldNames().forEachRemaining(key -> {
            if (!Set.of("size", "orientation", "marginMm", "header", "footer").contains(key))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "打印纸张配置包含未知字段");
        });
        for (String key : Set.of("header", "footer"))
            if (paper.has(key) && (!paper.get(key).isTextual() || paper.get(key).asText().length() > 500))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "打印页眉页脚无效");
    }

    private void validateBody(SystemContext context, Long moduleId, JsonNode body,JsonNode paper) {
        if (body == null || !body.isObject() || body.size() < 1 || body.size()>2 || !body.path("fieldKeys").isArray()
                || body.path("fieldKeys").isEmpty() || body.path("fieldKeys").size() > 100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "打印字段配置无效");
        body.fieldNames().forEachRemaining(key->{
            if (!Set.of("fieldKeys","positions").contains(key))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"打印正文包含未知配置");
        });
        Long moduleTenant = modules.activeModule(context.systemId(), moduleId, context.tenantId()).getTenantId();
        Map<String, UnModuleField> fields = new HashMap<>();
        for (UnModuleField field : modules.currentFields(moduleId))
            if (context.tenantId().equals(field.getTenantId()) || moduleTenant.equals(field.getTenantId()))
                fields.put(field.getFieldKey(), field);
        Set<String> seen = new HashSet<>();
        body.path("fieldKeys").forEach(item -> {
            if (!item.isTextual() || !seen.add(item.asText()) || !fields.containsKey(item.asText()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "打印字段已失效或重复");
        });
        JsonNode positions=body.path("positions");
        if (body.has("positions") && !positions.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"打印字段位置无效");
        double width=switch (paper.path("size").asText()) { case "A5" -> 148; case "LETTER" -> 216; default -> 210; };
        double height=switch (paper.path("size").asText()) { case "A5" -> 210; case "LETTER" -> 279; default -> 297; };
        if ("LANDSCAPE".equals(paper.path("orientation").asText())) { double swap=width; width=height; height=swap; }
        double margin=paper.path("marginMm").asDouble();
        java.util.List<double[]> boxes=new java.util.ArrayList<>();
        var names=positions.fieldNames();
        while (names.hasNext()) {
            String key=names.next(); JsonNode box=positions.path(key);
            if (!seen.contains(key) || !box.isObject() || box.size()!=4
                    || !box.path("xMm").isNumber() || !box.path("yMm").isNumber()
                    || !box.path("widthMm").isNumber() || !box.path("heightMm").isNumber())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"打印字段位置无效");
            double x=box.path("xMm").asDouble(),y=box.path("yMm").asDouble();
            double w=box.path("widthMm").asDouble(),h=box.path("heightMm").asDouble();
            double top=margin+(paper.has("header") && !paper.path("header").asText().isBlank()?12:0);
            if (x<margin || y<top || w<10 || h<6 || x+w>width-margin || y+h>height-margin)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"打印字段位置超出纸张可用区域");
            for (double[] other:boxes)
                if (x<other[0]+other[2] && other[0]<x+w && y<other[1]+other[3] && other[1]<y+h)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"打印字段位置相互重叠");
            boxes.add(new double[]{x,y,w,h});
        }
    }

    private UnPrint latest(SystemContext context, Long moduleId, String code) {
        return templates.getOne(new LambdaQueryWrapper<UnPrint>()
                .eq(UnPrint::getSystemId, context.systemId()).eq(UnPrint::getTenantId, context.tenantId())
                .eq(UnPrint::getModuleId, moduleId).eq(UnPrint::getTemplateCode, code)
                .orderByDesc(UnPrint::getVersionNo).last("LIMIT 1"), false);
    }

    private UnPrint latestEffective(SystemContext context, Long moduleId, String code) {
        return templates.getOne(new LambdaQueryWrapper<UnPrint>()
                .eq(UnPrint::getSystemId, context.systemId()).eq(UnPrint::getTenantId, context.tenantId())
                .eq(UnPrint::getModuleId, moduleId).eq(UnPrint::getTemplateCode, code)
                .in(UnPrint::getStatus, "PUBLISHED", "DISABLED")
                .orderByDesc(UnPrint::getVersionNo).last("LIMIT 1"), false);
    }

    private UnPrint owned(SystemContext context, Long moduleId, Long id) {
        UnPrint row = id == null ? null : templates.getById(id);
        if (row == null || !context.systemId().equals(row.getSystemId())
                || !context.tenantId().equals(row.getTenantId()) || !moduleId.equals(row.getModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "打印模板不存在");
        return row;
    }

    private String status(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(normalized))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "打印模板状态无效");
        return normalized;
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "打印模板内容无法读取", ex); }
    }

    private TemplateView view(UnPrint row) {
        return new TemplateView(row.getId(), row.getModuleId(), row.getTenantId(), row.getTemplateCode(),
                row.getName(), row.getVersionNo(), row.getStatus(), parse(row.getPaperConfigJson()),
                parse(row.getBodyConfigJson()), row.getPublishTime(), row.getCreateTime());
    }

    private void audit(SystemContext context, String action, Long id) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId()); log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId()); log.setActionCode(action);
        log.setObjectType("PRINT_TEMPLATE"); log.setObjectId(id);
        log.setCreateTime(LocalDateTime.now()); logs.addOrUpdate(log);
    }
}
