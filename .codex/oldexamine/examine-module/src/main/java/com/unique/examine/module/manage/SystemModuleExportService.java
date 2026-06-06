package com.unique.examine.module.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.unique.examine.module.manage.ModuleRecordFacadeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SystemModuleExportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public record UpsertTplCmd(
            Long id,
            Long appId,
            Long modelId,
            Long menuId,
            String tplCode,
            String tplName,
            String fileType,
            Integer status
    ) {}

    public record UpsertFieldCmd(
            Long id,
            Long tplId,
            Long fieldId,
            String colTitle,
            Integer sortNo,
            String formatJson
    ) {}

    public record ExportFile(byte[] bytes, String filename, String extension, String contentType) {}

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
    public ModuleExportTpl upsertTpl(Long operatorPlatId, UpsertTplCmd body) {
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
        Long menuId = normalizeNullableId(body.menuId());
        String tplCode = body.tplCode().trim();
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
            tpl = new ModuleExportTpl();
            tpl.setSystemId(systemId);
            tpl.setTenantId(tenantId);
            tpl.setCreateUserId(operatorPlatId);
        }
        if (countSameTplCode(systemId, tenantId, body.modelId(), menuId, tplCode, tpl.getId()) > 0) {
            throw new BusinessException(400, "tplCode 已存在");
        }

        tpl.setAppId(body.appId());
        tpl.setModelId(body.modelId());
        tpl.setMenuId(menuId);
        tpl.setTplCode(tplCode);
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
    public ModuleExportTplField upsertField(Long operatorPlatId, UpsertFieldCmd body) {
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
        ModuleField field = moduleFieldService.getById(body.fieldId());
        if (!isFieldInModel(field, systemId, tenantId, tpl.getAppId(), tpl.getModelId())) {
            throw new BusinessException(400, "field 不属于该导出模板模型");
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
        if (countSameTplField(systemId, tenantId, tpl.getId(), field.getId(), f.getId()) > 0) {
            throw new BusinessException(400, "该字段已在导出模板中");
        }

        f.setAppId(tpl.getAppId());
        f.setModelId(tpl.getModelId());
        f.setTplId(tpl.getId());
        f.setFieldId(field.getId());
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
        ExportFile file = exportFile(tplId, operatorPlatId, query, "csv");
        writeExportResponse(file, response);
    }

    public void exportXlsx(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query, HttpServletResponse response) {
        ExportFile file = exportFile(tplId, operatorPlatId, query, "xlsx");
        writeExportResponse(file, response);
    }

    public void exportByTemplateType(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query, HttpServletResponse response) {
        ExportFile file = exportFile(tplId, operatorPlatId, query, null);
        writeExportResponse(file, response);
    }

    private void writeExportResponse(ExportFile file, HttpServletResponse response) {
        try {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(file.contentType());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + file.filename() + "\"");
            response.getOutputStream().write(file.bytes());
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(500, "导出失败: " + e.getMessage());
        }
    }

    public byte[] exportCsvBytes(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query) {
        return exportFile(tplId, operatorPlatId, query, "csv").bytes();
    }

    public byte[] exportXlsxBytes(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query) {
        return exportFile(tplId, operatorPlatId, query, "xlsx").bytes();
    }

    public ExportFile exportFile(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query, String requestedFileType) {
        ExportData data = buildExportData(tplId, operatorPlatId, query);
        String type = normalizeFileType(requestedFileType == null ? data.tpl().getFileType() : requestedFileType);
        byte[] bytes = "xlsx".equals(type) ? toXlsxBytes(data) : toCsvBytes(data);
        String base = safeFileBaseName(data.tpl().getTplCode());
        String contentType = "xlsx".equals(type)
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv; charset=utf-8";
        return new ExportFile(bytes, base + "." + type, type, contentType);
    }

    private ExportData buildExportData(Long tplId, Long operatorPlatId, ModuleRecordDslQuery query) {
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
        List<ExportColumn> exportColumns = new ArrayList<>();
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
            exportColumns.add(new ExportColumn(title, mf.getFieldCode(), parseFormatOptions(f.getFormatJson())));
        }
        if (exportColumns.isEmpty()) {
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

        List<List<String>> rows = new ArrayList<>();
        for (Long rid : recordIds) {
            Map<String, String> row = data.get(rid);
            List<String> cells = new ArrayList<>(fieldCodes.size());
            for (ExportColumn col : exportColumns) {
                String v = row == null ? null : row.get(col.fieldCode());
                cells.add(formatExportValue(v, col.format()));
            }
            rows.add(cells);
        }
        return new ExportData(tpl, colTitles, rows);
    }

    private byte[] toCsvBytes(ExportData data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
                w.write('\uFEFF');
                writeCsvRow(w, data.headers());
                for (List<String> row : data.rows()) {
                    writeCsvRow(w, row);
                }
                w.flush();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "导出失败: " + e.getMessage());
        }
    }

    private byte[] toXlsxBytes(ExportData data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                putZip(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                        </Types>
                        """);
                putZip(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>
                        """);
                putZip(zip, "xl/_rels/workbook.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                        </Relationships>
                        """);
                putZip(zip, "xl/workbook.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                          <sheets><sheet name="export" sheetId="1" r:id="rId1"/></sheets>
                        </workbook>
                        """);
                putZip(zip, "xl/styles.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                          <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                          <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                          <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                          <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
                        </styleSheet>
                        """);
                putZip(zip, "xl/worksheets/sheet1.xml", buildSheetXml(data));
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "XLSX 导出失败: " + e.getMessage());
        }
    }

    private record ExportData(ModuleExportTpl tpl, List<String> headers, List<List<String>> rows) {}

    private record ExportColumn(String title, String fieldCode, FormatOptions format) {}

    private record FormatOptions(
            String emptyText,
            String prefix,
            String suffix,
            Boolean trim,
            Boolean dateOnly,
            Integer numberScale,
            Map<String, String> mappings,
            String boolTrueText,
            String boolFalseText
    ) {}

    private static FormatOptions parseFormatOptions(String formatJson) {
        String raw = trimToNull(formatJson);
        if (raw == null) {
            return new FormatOptions(null, null, null, null, null, null, Collections.emptyMap(), null, null);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) {
                throw new BusinessException(400, "formatJson must be a JSON object");
            }
            Map<String, String> mappings = new LinkedHashMap<>();
            JsonNode mapNode = root.has("mappings") ? root.get("mappings") : root.get("mapping");
            if (mapNode != null && mapNode.isObject()) {
                mapNode.fields().forEachRemaining(e -> mappings.put(e.getKey(), e.getValue().asText("")));
            }
            return new FormatOptions(
                    textOption(root, "emptyText"),
                    textOption(root, "prefix"),
                    textOption(root, "suffix"),
                    boolOption(root, "trim"),
                    boolOption(root, "dateOnly"),
                    intOption(root, "numberScale"),
                    mappings,
                    textOption(root, "boolTrueText"),
                    textOption(root, "boolFalseText")
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "formatJson invalid: " + e.getMessage());
        }
    }

    private static String formatExportValue(String value, FormatOptions format) {
        String text = value;
        if (text == null || text.isBlank()) {
            return format.emptyText() == null ? "" : format.emptyText();
        }
        if (Boolean.TRUE.equals(format.trim())) {
            text = text.trim();
        }
        if (format.mappings() != null && format.mappings().containsKey(text)) {
            text = format.mappings().get(text);
        } else if (format.boolTrueText() != null && isTrueValue(text)) {
            text = format.boolTrueText();
        } else if (format.boolFalseText() != null && isFalseValue(text)) {
            text = format.boolFalseText();
        }
        if (Boolean.TRUE.equals(format.dateOnly()) && text.length() >= 10 && text.charAt(4) == '-' && text.charAt(7) == '-') {
            text = text.substring(0, 10);
        }
        if (format.numberScale() != null) {
            try {
                text = new BigDecimal(text).setScale(format.numberScale(), RoundingMode.HALF_UP).toPlainString();
            } catch (NumberFormatException ignore) {
                // keep original text when the stored value is not numeric
            }
        }
        String prefix = format.prefix() == null ? "" : format.prefix();
        String suffix = format.suffix() == null ? "" : format.suffix();
        return prefix + text + suffix;
    }

    private static String textOption(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String text = v.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Boolean boolOption(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asBoolean();
    }

    private static Integer intOption(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }

    private static boolean isTrueValue(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "y".equals(v);
    }

    private static boolean isFalseValue(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "0".equals(v) || "false".equals(v) || "no".equals(v) || "n".equals(v);
    }

    private static String normalizeFileType(String fileType) {
        String t = fileType == null ? "xlsx" : fileType.trim().toLowerCase(Locale.ROOT);
        if (!"xlsx".equals(t) && !"csv".equals(t)) {
            throw new BusinessException(400, "fileType 须为 xlsx|csv");
        }
        return t;
    }

    private static void putZip(ZipOutputStream zip, String path, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String buildSheetXml(ExportData data) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetData>");
        appendSheetRow(sb, 1, data.headers());
        int rowNum = 2;
        for (List<String> row : data.rows()) {
            appendSheetRow(sb, rowNum++, row);
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void appendSheetRow(StringBuilder sb, int rowNum, List<String> cells) {
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int i = 0; i < cells.size(); i++) {
            sb.append("<c r=\"").append(cellRef(i, rowNum)).append("\" t=\"inlineStr\"><is><t>");
            sb.append(escapeXml(cells.get(i)));
            sb.append("</t></is></c>");
        }
        sb.append("</row>");
    }

    private static String cellRef(int zeroBasedCol, int rowNum) {
        int col = zeroBasedCol + 1;
        StringBuilder name = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            name.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return name + String.valueOf(rowNum);
    }

    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if ((ch >= 0x20 && ch <= 0xD7FF) || ch == '\n' || ch == '\r' || ch == '\t' || ch >= 0xE000) {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String safeFileBaseName(String raw) {
        String s = raw == null || raw.isBlank() ? "export" : raw.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return s.isBlank() ? "export" : s;
    }

    private long countSameTplCode(long systemId, long tenantId, Long modelId, Long menuId, String tplCode, Long excludeId) {
        var q = moduleExportTplService.lambdaQuery()
                .eq(ModuleExportTpl::getSystemId, systemId)
                .eq(ModuleExportTpl::getTenantId, tenantId)
                .eq(ModuleExportTpl::getModelId, modelId)
                .eq(ModuleExportTpl::getTplCode, tplCode);
        if (menuId == null) {
            q.isNull(ModuleExportTpl::getMenuId);
        } else {
            q.eq(ModuleExportTpl::getMenuId, menuId);
        }
        if (excludeId != null) {
            q.ne(ModuleExportTpl::getId, excludeId);
        }
        return q.count();
    }

    private long countSameTplField(long systemId, long tenantId, Long tplId, Long fieldId, Long excludeId) {
        var q = moduleExportTplFieldService.lambdaQuery()
                .eq(ModuleExportTplField::getSystemId, systemId)
                .eq(ModuleExportTplField::getTenantId, tenantId)
                .eq(ModuleExportTplField::getTplId, tplId)
                .eq(ModuleExportTplField::getFieldId, fieldId);
        if (excludeId != null) {
            q.ne(ModuleExportTplField::getId, excludeId);
        }
        return q.count();
    }

    private static boolean isFieldInModel(ModuleField field, long systemId, long tenantId, Long appId, Long modelId) {
        return field != null
                && Objects.equals(field.getSystemId(), systemId)
                && Objects.equals(field.getTenantId(), tenantId)
                && Objects.equals(field.getAppId(), appId)
                && Objects.equals(field.getModelId(), modelId)
                && Objects.equals(field.getStatus(), 1);
    }

    private static Long normalizeNullableId(Long id) {
        return id == null || id <= 0L ? null : id;
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

