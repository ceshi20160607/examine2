package com.unique.examine.web.manage.module;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleExportTpl;
import com.unique.examine.module.entity.po.ModuleExportTplField;
import com.unique.examine.module.entity.po.ModuleRecordData;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.mapper.ModuleRecordMapper;
import com.unique.examine.module.service.IModuleExportTplFieldService;
import com.unique.examine.module.service.IModuleExportTplService;
import com.unique.examine.module.service.IModuleFieldService;
import com.unique.examine.module.service.IModuleRecordDataService;
import com.unique.examine.web.controller.module.SystemModuleExportController;
import com.unique.examine.web.service.ModuleRecordFacadeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class SystemModuleExportService {

    @Autowired
    private IModuleExportTplService moduleExportTplService;
    @Autowired
    private IModuleExportTplFieldService moduleExportTplFieldService;
    @Autowired
    private IModuleFieldService moduleFieldService;
    @Autowired
    private IModuleRecordDataService moduleRecordDataService;
    @Autowired
    private ModuleRecordMapper moduleRecordMapper;
    @Autowired
    private ModuleRecordFacadeService moduleRecordFacadeService;

    public List<ModuleExportTpl> listTpls(Long modelId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (modelId == null) {
            throw new BusinessException(400, "modelId 不能为空");
        }
        return moduleExportTplService.lambdaQuery()
                .eq(ModuleExportTpl::getSystemId, systemId)
                .eq(ModuleExportTpl::getTenantId, tenantId)
                .eq(ModuleExportTpl::getModelId, modelId)
                .orderByAsc(ModuleExportTpl::getMenuId)
                .orderByAsc(ModuleExportTpl::getTplCode)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleExportTpl upsertTpl(Long operatorPlatId, SystemModuleExportController.UpsertTplBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.appId() == null || body.modelId() == null) {
            throw new BusinessException(400, "appId/modelId 不能为空");
        }
        if (body.tplCode() == null || body.tplCode().isBlank()) {
            throw new BusinessException(400, "tplCode 不能为空");
        }
        if (body.tplName() == null || body.tplName().isBlank()) {
            throw new BusinessException(400, "tplName 不能为空");
        }
        String fileType = normalizeFileType(body.fileType());
        int status = body.status() == null ? 1 : body.status();
        if (status != 1 && status != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }

        ModuleExportTpl tpl;
        if (body.id() != null) {
            tpl = moduleExportTplService.getById(body.id());
            if (tpl == null) {
                throw new BusinessException(404, "tpl 不存在");
            }
            if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 tpl");
            }
        } else {
            long existed = moduleExportTplService.lambdaQuery()
                    .eq(ModuleExportTpl::getSystemId, systemId)
                    .eq(ModuleExportTpl::getTenantId, tenantId)
                    .eq(ModuleExportTpl::getModelId, body.modelId())
                    .eq(ModuleExportTpl::getMenuId, body.menuId())
                    .eq(ModuleExportTpl::getTplCode, body.tplCode().trim())
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "tplCode 已存在");
            }
            tpl = new ModuleExportTpl();
            tpl.setSystemId(systemId);
            tpl.setTenantId(tenantId);
            tpl.setCreateUserId(operatorPlatId);
        }

        tpl.setAppId(body.appId());
        tpl.setModelId(body.modelId());
        tpl.setMenuId(body.menuId());
        tpl.setTplCode(body.tplCode().trim());
        tpl.setTplName(body.tplName().trim());
        tpl.setFileType(fileType);
        tpl.setStatus(status);
        tpl.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleExportTplService.updateById(tpl);
        } else {
            moduleExportTplService.save(tpl);
        }
        return tpl;
    }

    public List<ModuleExportTplField> listFields(Long tplId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (tplId == null) {
            throw new BusinessException(400, "tplId 不能为空");
        }
        ModuleExportTpl tpl = moduleExportTplService.getById(tplId);
        if (tpl == null) {
            throw new BusinessException(404, "tpl 不存在");
        }
        if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该 tpl");
        }
        return moduleExportTplFieldService.lambdaQuery()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .eq(ModuleExportTplField::getTplId, tplId)
                .orderByAsc(ModuleExportTplField::getSortNo)
                .orderByAsc(ModuleExportTplField::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModuleExportTplField upsertField(Long operatorPlatId, SystemModuleExportController.UpsertFieldBody body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.tplId() == null || body.fieldId() == null) {
            throw new BusinessException(400, "tplId/fieldId 不能为空");
        }
        ModuleExportTpl tpl = moduleExportTplService.getById(body.tplId());
        if (tpl == null) {
            throw new BusinessException(404, "tpl 不存在");
        }
        if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该 tpl");
        }

        ModuleExportTplField f;
        if (body.id() != null) {
            f = moduleExportTplFieldService.getById(body.id());
            if (f == null) {
                throw new BusinessException(404, "field 不存在");
            }
            if (!Objects.equals(f.getSystemId(), systemId) || !Objects.equals(f.getTenantId(), tenantId) || !Objects.equals(f.getTplId(), body.tplId())) {
                throw new BusinessException(403, "无权操作该导出字段");
            }
        } else {
            f = new ModuleExportTplField();
            f.setSystemId(systemId);
            f.setTenantId(tenantId);
            f.setCreateUserId(operatorPlatId);
        }

        f.setAppId(tpl.getAppId());
        f.setModelId(tpl.getModelId());
        f.setTplId(tpl.getId());
        f.setFieldId(body.fieldId());
        f.setColTitle(trimToNull(body.colTitle()));
        f.setSortNo(body.sortNo());
        f.setFormatJson(trimToNull(body.formatJson()));
        f.setUpdateUserId(operatorPlatId);

        if (body.id() != null) {
            moduleExportTplFieldService.updateById(f);
        } else {
            moduleExportTplFieldService.save(f);
        }
        return f;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTpls(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleExportTplService.lambdaUpdate()
                .eq(ModuleExportTpl::getSystemId, systemId)
                .eq(ModuleExportTpl::getTenantId, tenantId)
                .in(ModuleExportTpl::getId, ids)
                .remove();
        moduleExportTplFieldService.lambdaUpdate()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .in(ModuleExportTplField::getTplId, ids)
                .remove();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFields(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        moduleExportTplFieldService.lambdaUpdate()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .in(ModuleExportTplField::getId, ids)
                .remove();
    }

    public void exportCsv(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query, HttpServletResponse response) {
        byte[] bytes = exportCsvBytes(tplId, operatorPlatId, query);
        String filename = "export.csv";
        try {
            ModuleExportTpl tpl = moduleExportTplService.getById(tplId);
            if (tpl != null && tpl.getTplCode() != null && !tpl.getTplCode().isBlank()) {
                filename = tpl.getTplCode() + ".csv";
            }
        } catch (Exception ignore) {
            // keep default
        }
        try {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv; charset=utf-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(500, "导出失败: " + e.getMessage());
        }
    }

    public byte[] exportCsvBytes(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (tplId == null || tplId <= 0L) {
            throw new BusinessException(400, "tplId 不能为空");
        }

        ModuleExportTpl tpl = moduleExportTplService.getById(tplId);
        if (tpl == null) {
            throw new BusinessException(404, "tpl 不存在");
        }
        if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该 tpl");
        }

        List<ModuleExportTplField> fields = moduleExportTplFieldService.lambdaQuery()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .eq(ModuleExportTplField::getTplId, tplId)
                .orderByAsc(ModuleExportTplField::getSortNo)
                .orderByAsc(ModuleExportTplField::getId)
                .list();
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException(400, "导出字段为空");
        }

        List<String> colTitles = new ArrayList<>();
        List<String> fieldCodes = new ArrayList<>();
        for (ModuleExportTplField f : fields) {
            if (f.getFieldId() == null) {
                continue;
            }
            ModuleField mf = moduleFieldService.getById(f.getFieldId());
            if (mf == null) {
                continue;
            }
            if (!Objects.equals(mf.getSystemId(), systemId)
                    || !Objects.equals(mf.getTenantId(), tenantId)
                    || !Objects.equals(mf.getAppId(), tpl.getAppId())
                    || !Objects.equals(mf.getModelId(), tpl.getModelId())
                    || mf.getStatus() == null
                    || mf.getStatus() != 1) {
                continue;
            }
            String title = f.getColTitle();
            if (title == null || title.isBlank()) {
                title = mf.getFieldName() == null ? mf.getFieldCode() : mf.getFieldName();
            }
            colTitles.add(title);
            fieldCodes.add(mf.getFieldCode());
        }
        if (fieldCodes.isEmpty()) {
            throw new BusinessException(400, "导出字段无有效列");
        }

        ModuleRecordDslQuery q = query == null ? new ModuleRecordDslQuery() : query;
        q = moduleRecordFacadeService.prepareDslQuery(q, tpl.getAppId(), tpl.getModelId(), 2000L);

        List<Map<String, Object>> list = moduleRecordMapper.listDsl(q, 0, q.getLimit());
        List<Long> recordIds = new ArrayList<>();
        if (list != null) {
            for (Map<String, Object> row : list) {
                Object id = row == null ? null : row.get("id");
                if (id instanceof Number n) {
                    recordIds.add(n.longValue());
                } else if (id instanceof String s) {
                    try {
                        recordIds.add(Long.parseLong(s));
                    } catch (NumberFormatException ignore) {
                        // skip
                    }
                }
            }
        }

        Map<Long, Map<String, String>> data = new HashMap<>();
        if (!recordIds.isEmpty()) {
            List<ModuleRecordData> rows = moduleRecordDataService.lambdaQuery()
                    .eq(ModuleRecordData::getSystemId, systemId)
                    .eq(ModuleRecordData::getTenantId, tenantId)
                    .eq(ModuleRecordData::getAppId, tpl.getAppId())
                    .eq(ModuleRecordData::getModelId, tpl.getModelId())
                    .in(ModuleRecordData::getRecordId, recordIds)
                    .in(ModuleRecordData::getFieldCode, fieldCodes)
                    .list();
            if (rows != null) {
                for (ModuleRecordData row : rows) {
                    if (row.getRecordId() == null || row.getFieldCode() == null) {
                        continue;
                    }
                    data.computeIfAbsent(row.getRecordId(), k -> new LinkedHashMap<>())
                            .put(row.getFieldCode(), row.getValueText());
                }
            }
        }

        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
                w.write('\uFEFF');
                writeCsvRow(w, colTitles);
                for (Long rid : recordIds) {
                    Map<String, String> row = data.get(rid);
                    List<String> cells = new ArrayList<>(fieldCodes.size());
                    for (String fc : fieldCodes) {
                        String v = row == null ? null : row.get(fc);
                        cells.add(v == null ? "" : v);
                    }
                    writeCsvRow(w, cells);
                }
                w.flush();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "导出失败: " + e.getMessage());
        }
    }

    private static String normalizeFileType(String fileType) {
        String t = fileType == null ? "xlsx" : fileType.trim().toLowerCase(Locale.ROOT);
        if (!"xlsx".equals(t) && !"csv".equals(t)) {
            throw new BusinessException(400, "fileType 须为 xlsx|csv");
        }
        return t;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void requireOperator(Long platId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    private static long requireSystem() {
        long sid = AuthContextHolder.getSystemIdOrDefault();
        if (sid == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        return sid;
    }

    private static void writeCsvRow(OutputStreamWriter w, List<String> cells) throws Exception {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(escapeCsv(cells.get(i)));
        }
        w.write("\r\n");
    }

    private static String escapeCsv(String s) {
        if (s == null) {
            return "";
        }
        boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String t = s.replace("\"", "\"\"");
        return needQuote ? ("\"" + t + "\"") : t;
    }
}

