package com.unique.examine.module.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.dto.ModuleRecordDslFilter;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleRecord;
import com.unique.examine.module.entity.po.ModuleRecordData;
import com.unique.examine.module.entity.po.ModuleRecordHistory;
import com.unique.examine.module.field.ModuleFieldConfigSupport;
import com.unique.examine.module.field.ModuleFieldType;
import com.unique.examine.module.mapper.ModuleRecordMapper;
import com.unique.examine.module.service.IModuleRecordDataService;
import com.unique.examine.module.service.IModuleFieldService;
import com.unique.examine.module.service.IModuleRecordHistoryService;
import com.unique.examine.module.service.IModuleRecordService;
import com.unique.examine.upload.entity.po.UploadFile;
import com.unique.examine.upload.service.IUploadFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

@Service
public class ModuleRecordFacadeService {

    private static final Pattern FIELD_CODE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    private static final Set<String> RESERVED_RECORD_FIELDS = Set.of("id", "createTime", "updateTime");

    @Autowired
    private IModuleRecordService moduleRecordService;
    @Autowired
    private IModuleRecordDataService moduleRecordDataService;
    @Autowired
    private IModuleRecordHistoryService moduleRecordHistoryService;
    @Autowired
    private IModuleFieldService moduleFieldService;
    @Autowired
    private ModuleRecordMapper moduleRecordMapper;
    @Autowired
    private IUploadFileService uploadFileService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ModuleFlowTriggerService moduleFlowTriggerService;
    @Autowired
    private ModuleSerialNoService moduleSerialNoService;

    @Transactional(rollbackFor = Exception.class)
    public ModuleRecord createWithData(Long appId, Long modelId, JsonNode data) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null || appId <= 0L) {
            throw new BusinessException("appId 不能为空");
        }
        if (modelId == null || modelId <= 0L) {
            throw new BusinessException("modelId 不能为空");
        }

        ModuleRecord r = new ModuleRecord();
        r.setSystemId(systemId);
        r.setTenantId(tenantId);
        r.setAppId(appId);
        r.setModelId(modelId);
        r.setStatus(1);
        r.setCreateUserId(platId);
        r.setUpdateUserId(platId);
        moduleRecordService.save(r);

        ObjectNode obj = ensureObjectNode(data);
        fillSerialNumbers(systemId, tenantId, appId, modelId, obj);
        validateRequiredFields(systemId, tenantId, appId, modelId, obj);

        if (obj != null && !obj.isEmpty()) {
            var it = obj.fields();
            while (it.hasNext()) {
                var e = it.next();
                String fieldCode = e.getKey();
                if (!FIELD_CODE.matcher(fieldCode).matches()) {
                    throw new BusinessException("data 中存在非法 fieldCode: " + fieldCode);
                }
                JsonNode v = e.getValue();
                ModuleField f = requireField(systemId, tenantId, appId, modelId, fieldCode);
                validateFileFieldValue(systemId, tenantId, appId, modelId, f, v);
                validateRefFieldValue(systemId, tenantId, f, v);
                String text = valueToText(v);
                if (text != null && text.length() > 65535) {
                    throw new BusinessException("字段 " + fieldCode + " 值过长");
                }
                ModuleRecordData row = new ModuleRecordData();
                row.setSystemId(systemId);
                row.setTenantId(tenantId);
                row.setAppId(appId);
                row.setModelId(modelId);
                row.setRecordId(r.getId());
                row.setFieldCode(fieldCode);
                row.setValueText(text);
                row.setCreateUserId(platId);
                row.setUpdateUserId(platId);
                moduleRecordDataService.save(row);
            }
        }

        saveHistory("create", r, dataToSnapshotJson(obj));

        moduleFlowTriggerService.tryTriggerAfterRecordChange(r, "create", obj);

        return r;
    }

    private ObjectNode ensureObjectNode(JsonNode data) {
        if (data != null && data.isObject()) {
            return (ObjectNode) data;
        }
        return objectMapper.createObjectNode();
    }

    private void validateRequiredFields(long systemId, long tenantId, Long appId, Long modelId, ObjectNode obj) {
        List<ModuleField> defs = moduleFieldService.lambdaQuery()
                .eq(ModuleField::getSystemId, systemId)
                .eq(ModuleField::getTenantId, tenantId)
                .eq(ModuleField::getAppId, appId)
                .eq(ModuleField::getModelId, modelId)
                .eq(ModuleField::getStatus, 1)
                .eq(ModuleField::getRequiredFlag, 1)
                .list();
        if (defs == null || defs.isEmpty()) {
            return;
        }
        ObjectNode dataObj = obj == null ? objectMapper.createObjectNode() : obj;
        for (ModuleField f : defs) {
            if (f == null || f.getFieldCode() == null || f.getFieldCode().isBlank()) {
                continue;
            }
            if (f.getHiddenFlag() != null && f.getHiddenFlag() == 1) {
                continue;
            }
            ModuleFieldType type = ModuleFieldConfigSupport.typeOf(f);
            if (type != null && type.isDisplayOnly()) {
                continue;
            }
            JsonNode v = dataObj.get(f.getFieldCode());
            if (isEmptyFieldValue(v, f)) {
                String label = f.getFieldName() != null && !f.getFieldName().isBlank()
                        ? f.getFieldName()
                        : f.getFieldCode();
                throw new BusinessException(400, "字段「" + label + "」不能为空");
            }
        }
    }

    private boolean isEmptyFieldValue(JsonNode v, ModuleField field) {
        if (v == null || v.isNull()) {
            return true;
        }
        ModuleFieldType type = ModuleFieldConfigSupport.typeOf(field);
        if (type == ModuleFieldType.BOOLEAN) {
            return false;
        }
        if (v.isBoolean()) {
            return false;
        }
        if (v.isNumber()) {
            return false;
        }
        if (v.isArray()) {
            return v.isEmpty();
        }
        if (v.isObject()) {
            return v.isEmpty();
        }
        if (v.isTextual()) {
            return v.asText().trim().isEmpty();
        }
        return false;
    }

    private void fillSerialNumbers(long systemId, long tenantId, Long appId, Long modelId, ObjectNode obj) {
        List<ModuleField> defs = moduleFieldService.lambdaQuery()
                .eq(ModuleField::getSystemId, systemId)
                .eq(ModuleField::getTenantId, tenantId)
                .eq(ModuleField::getModelId, modelId)
                .eq(ModuleField::getStatus, 1)
                .list();
        for (ModuleField f : defs) {
            if (ModuleFieldConfigSupport.typeOf(f) != ModuleFieldType.SERIAL_NO) {
                continue;
            }
            String code = f.getFieldCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            JsonNode cur = obj.get(code);
            if (cur != null && !cur.isNull() && !cur.asText("").isBlank()) {
                continue;
            }
            String generated = moduleSerialNoService.generate(systemId, tenantId, appId, modelId, f, obj);
            obj.set(code, TextNode.valueOf(generated));
        }
    }

    private static String valueToText(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isTextual()) {
            return v.asText();
        }
        if (v.isNumber() || v.isBoolean()) {
            return v.asText();
        }
        return v.toString();
    }

    private void validateFileFieldValue(long systemId,
                                        long tenantId,
                                        Long appId,
                                        Long modelId,
                                        ModuleField field,
                                        JsonNode value) {
        if (field == null) {
            return;
        }
        ModuleFieldType type = ModuleFieldConfigSupport.typeOf(field);
        if (type == null) {
            String ft = field.getFieldType();
            if (ft == null) return;
            String t = ft.trim().toLowerCase();
            if (!(t.equals("file") || t.equals("upload") || t.equals("attachment") || t.equals("image"))) return;
        } else if (!type.isFileType()) {
            return;
        }

        if (value == null || value.isNull()) {
            return;
        }

        HashSet<Long> ids = new HashSet<>();
        if (value.isNumber()) {
            long v = value.asLong();
            if (v > 0) {
                ids.add(v);
            }
        } else if (value.isTextual()) {
            String s = value.asText().trim();
            if (!s.isEmpty()) {
                try {
                    long v = Long.parseLong(s);
                    if (v > 0) {
                        ids.add(v);
                    } else {
                        throw new BusinessException(400, "附件字段 " + field.getFieldCode() + " 的 fileId 非法");
                    }
                } catch (NumberFormatException e) {
                    throw new BusinessException(400, "附件字段 " + field.getFieldCode() + " 仅支持 fileId 或 fileId 数组");
                }
            }
        } else if (value.isArray()) {
            for (JsonNode it : value) {
                Long id = parseFileIdNode(it);
                if (id != null) {
                    ids.add(id);
                } else if (it != null && !it.isNull()) {
                    throw new BusinessException(400, "附件字段 " + field.getFieldCode() + " 数组元素非法");
                }
            }
        } else if (value.isObject()) {
            Long id = parseFileIdNode(value);
            if (id != null) {
                ids.add(id);
            } else {
                throw new BusinessException(400, "附件字段 " + field.getFieldCode() + " 仅支持 fileId 或 fileId 数组");
            }
        } else {
            throw new BusinessException(400, "附件字段 " + field.getFieldCode() + " 仅支持 fileId 或 fileId 数组");
        }

        if (ids.isEmpty()) {
            return;
        }

        Long cnt = uploadFileService.lambdaQuery()
                .eq(UploadFile::getSystemId, systemId)
                .eq(UploadFile::getTenantId, tenantId)
                .eq(UploadFile::getStatus, 1)
                .in(UploadFile::getId, ids)
                .count();
        if (cnt == null || cnt != ids.size()) {
            throw new BusinessException(400, "附件字段 " + field.getFieldCode() + " 引用了不存在或无权限的 fileId");
        }
    }

    private void validateRefFieldValue(long systemId, long tenantId, ModuleField field, JsonNode value) {
        if (field == null || value == null || value.isNull()) {
            return;
        }
        ModuleFieldType type = ModuleFieldConfigSupport.typeOf(field);
        boolean isRef = type != null ? type.isRefType() : false;
        if (!isRef) {
            String ft = field.getFieldType();
            if (ft == null) return;
            String t = ft.trim().toLowerCase();
            if (!(t.equals("ref") || t.equals("relation") || t.equals("lookup"))) return;
        }
        Long refModelId = field.getRefModelId();
        if (refModelId == null || refModelId <= 0L) {
            return;
        }

        HashSet<Long> ids = new HashSet<>();
        if (value.isNumber()) {
            long v = value.asLong();
            if (v > 0) {
                ids.add(v);
            }
        } else if (value.isTextual()) {
            String s = value.asText().trim();
            if (s.isEmpty()) {
                return;
            }
            if (s.startsWith("[") && s.endsWith("]")) {
                try {
                    JsonNode arr = objectMapper.readTree(s);
                    collectRefIds(arr, ids, field.getFieldCode());
                } catch (Exception e) {
                    throw new BusinessException(400, "关联字段 " + field.getFieldCode() + " JSON 数组非法");
                }
            } else {
                try {
                    long v = Long.parseLong(s);
                    if (v > 0) {
                        ids.add(v);
                    }
                } catch (NumberFormatException e) {
                    throw new BusinessException(400, "关联字段 " + field.getFieldCode() + " 须为 recordId");
                }
            }
        } else if (value.isArray()) {
            collectRefIds(value, ids, field.getFieldCode());
        } else {
            throw new BusinessException(400, "关联字段 " + field.getFieldCode() + " 须为 recordId 或 recordId 数组");
        }

        if (ids.isEmpty()) {
            return;
        }

        for (Long id : ids) {
            ModuleRecord rec = moduleRecordService.getById(id);
            if (rec == null) {
                throw new BusinessException(400, "关联字段 " + field.getFieldCode() + " 引用的记录不存在: " + id);
            }
            if (rec.getSystemId() == null || rec.getTenantId() == null
                    || rec.getSystemId() != systemId
                    || rec.getTenantId() != tenantId) {
                throw new BusinessException(403, "关联字段 " + field.getFieldCode() + " 引用的记录无权限: " + id);
            }
            if (!Objects.equals(rec.getModelId(), refModelId)) {
                throw new BusinessException(400, "关联字段 " + field.getFieldCode() + " 须指向 modelId=" + refModelId);
            }
            if (rec.getStatus() != null && rec.getStatus() != 1) {
                throw new BusinessException(400, "关联字段 " + field.getFieldCode() + " 引用的记录不可用: " + id);
            }
        }
    }

    private void collectRefIds(JsonNode arr, HashSet<Long> ids, String fieldCode) {
        if (arr == null || !arr.isArray()) {
            throw new BusinessException(400, "关联字段 " + fieldCode + " 数组非法");
        }
        for (JsonNode it : arr) {
            if (it == null || it.isNull()) {
                continue;
            }
            if (it.isNumber()) {
                long v = it.asLong();
                if (v > 0) {
                    ids.add(v);
                }
            } else if (it.isTextual()) {
                String s = it.asText().trim();
                if (!s.isEmpty()) {
                    try {
                        long v = Long.parseLong(s);
                        if (v > 0) {
                            ids.add(v);
                        }
                    } catch (NumberFormatException e) {
                        throw new BusinessException(400, "关联字段 " + fieldCode + " 数组元素非法");
                    }
                }
            } else {
                throw new BusinessException(400, "关联字段 " + fieldCode + " 数组元素非法");
            }
        }
    }

    public Map<String, Object> detailWithData(Long recordId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null || recordId <= 0L) {
            throw new BusinessException("recordId 不能为空");
        }

        ModuleRecord r = moduleRecordService.getById(recordId);
        if (r == null) {
            throw new BusinessException(404, "记录不存在");
        }
        if (r.getSystemId() == null || r.getTenantId() == null
                || r.getSystemId() != systemId
                || r.getTenantId() != tenantId) {
            throw new BusinessException(403, "无权限访问该记录");
        }

        List<ModuleRecordData> rows = moduleRecordDataService.lambdaQuery()
                .eq(ModuleRecordData::getRecordId, recordId)
                .orderByAsc(ModuleRecordData::getFieldCode)
                .list();

        ObjectNode dataNode = objectMapper.createObjectNode();
        if (rows != null) {
            for (ModuleRecordData row : rows) {
                if (row.getFieldCode() == null) {
                    continue;
                }
                String vt = row.getValueText();
                dataNode.put(row.getFieldCode(), vt == null ? "" : vt);
            }
        }

        Map<String, Object> filesMeta = buildFilesMetaForRecord(systemId, tenantId, r.getAppId(), r.getModelId(), rows);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("record", r);
        m.put("data", dataNode);
        if (!filesMeta.isEmpty()) {
            m.put("files", filesMeta);
        }
        return m;
    }

    public List<ModuleRecordHistory> listHistoryForRecord(Long recordId, int limit) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null || recordId <= 0L) {
            throw new BusinessException("recordId 不能为空");
        }

        ModuleRecord r = moduleRecordService.getById(recordId);
        if (r == null) {
            throw new BusinessException(404, "记录不存在");
        }
        if (r.getSystemId() == null || r.getTenantId() == null
                || r.getSystemId() != systemId
                || r.getTenantId() != tenantId) {
            throw new BusinessException(403, "无权限访问该记录");
        }

        int lim = Math.min(Math.max(limit, 1), 100);
        return moduleRecordHistoryService.lambdaQuery()
                .eq(ModuleRecordHistory::getRecordId, recordId)
                .eq(ModuleRecordHistory::getSystemId, systemId)
                .eq(ModuleRecordHistory::getTenantId, tenantId)
                .orderByDesc(ModuleRecordHistory::getCreateTime)
                .last("limit " + lim)
                .list();
    }

    private Map<String, Object> buildFilesMetaForRecord(long systemId,
                                                        long tenantId,
                                                        Long appId,
                                                        Long modelId,
                                                        List<ModuleRecordData> rows) {
        if (rows == null || rows.isEmpty() || appId == null || modelId == null) {
            return Map.of();
        }

        // 1) collect file-like fieldCodes from model fields
        List<ModuleField> fields = moduleFieldService.lambdaQuery()
                .eq(ModuleField::getSystemId, systemId)
                .eq(ModuleField::getTenantId, tenantId)
                .eq(ModuleField::getAppId, appId)
                .eq(ModuleField::getModelId, modelId)
                .eq(ModuleField::getStatus, 1)
                .list();
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        HashSet<String> fileFieldCodes = new HashSet<>();
        for (ModuleField f : fields) {
            if (f == null || f.getFieldCode() == null) {
                continue;
            }
            ModuleFieldType type = ModuleFieldConfigSupport.typeOf(f);
            if (type != null && type.isFileType()) {
                fileFieldCodes.add(f.getFieldCode());
            }
        }
        if (fileFieldCodes.isEmpty()) {
            return Map.of();
        }

        // 2) parse record values -> fileIds
        HashSet<Long> fileIds = new HashSet<>();
        for (ModuleRecordData row : rows) {
            if (row == null || row.getFieldCode() == null || row.getValueText() == null) {
                continue;
            }
            if (!fileFieldCodes.contains(row.getFieldCode())) {
                continue;
            }
            String vt = row.getValueText().trim();
            if (vt.isEmpty()) {
                continue;
            }
            // support: "123" or ["123","456"] or [123,456]
            try {
                if (vt.startsWith("[") || vt.startsWith("{")) {
                    JsonNode n = objectMapper.readTree(vt);
                    if (n != null && n.isArray()) {
                        for (JsonNode it : n) {
                            Long id = parseFileIdNode(it);
                            if (id != null) {
                                fileIds.add(id);
                            }
                        }
                    } else {
                        Long id = parseFileIdNode(n);
                        if (id != null) {
                            fileIds.add(id);
                        }
                    }
                } else {
                    Long id = Long.parseLong(vt);
                    if (id > 0) {
                        fileIds.add(id);
                    }
                }
            } catch (Exception ignore) {
                // ignore malformed values
            }
        }
        if (fileIds.isEmpty()) {
            return Map.of();
        }

        // 3) load UploadFile meta under same scope
        List<UploadFile> files = uploadFileService.lambdaQuery()
                .eq(UploadFile::getSystemId, systemId)
                .eq(UploadFile::getTenantId, tenantId)
                .eq(UploadFile::getStatus, 1)
                .in(UploadFile::getId, fileIds)
                .list();
        if (files == null || files.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> out = new HashMap<>();
        for (UploadFile uf : files) {
            if (uf == null || uf.getId() == null) {
                continue;
            }
            out.put(String.valueOf(uf.getId()), Map.of(
                    "id", uf.getId(),
                    "originalName", uf.getOriginalName(),
                    "contentType", uf.getContentType(),
                    "fileSize", uf.getFileSize()
            ));
        }
        return out;
    }

    private static Long parseFileIdNode(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            long v = n.asLong();
            return v > 0 ? v : null;
        }
        if (n.isTextual()) {
            try {
                long v = Long.parseLong(n.asText().trim());
                return v > 0 ? v : null;
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        if (n.isObject()) {
            JsonNode id = n.get("id");
            if (id != null) {
                return parseFileIdNode(id);
            }
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateWithData(Long recordId, JsonNode data) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null || recordId <= 0L) {
            throw new BusinessException("recordId 不能为空");
        }
        ModuleRecord r = moduleRecordService.getById(recordId);
        if (r == null) {
            throw new BusinessException(404, "记录不存在");
        }
        if (r.getSystemId() == null || r.getTenantId() == null
                || r.getSystemId() != systemId
                || r.getTenantId() != tenantId) {
            throw new BusinessException(403, "无权限访问该记录");
        }
        if (r.getStatus() != null && r.getStatus() != 1) {
            throw new BusinessException(400, "记录已删除/不可更新");
        }

        moduleRecordDataService.lambdaUpdate()
                .eq(ModuleRecordData::getRecordId, recordId)
                .remove();

        if (data != null && data.isObject()) {
            ObjectNode obj = (ObjectNode) data;
            validateRequiredFields(systemId, tenantId, r.getAppId(), r.getModelId(), obj);
            var it = obj.fields();
            while (it.hasNext()) {
                var e = it.next();
                String fieldCode = e.getKey();
                if (!FIELD_CODE.matcher(fieldCode).matches()) {
                    throw new BusinessException("data 中存在非法 fieldCode: " + fieldCode);
                }
                JsonNode v = e.getValue();
                ModuleField f = requireField(systemId, tenantId, r.getAppId(), r.getModelId(), fieldCode);
                validateFileFieldValue(systemId, tenantId, r.getAppId(), r.getModelId(), f, v);
                validateRefFieldValue(systemId, tenantId, f, v);
                String text = valueToText(v);
                if (text != null && text.length() > 65535) {
                    throw new BusinessException("字段 " + fieldCode + " 值过长");
                }
                ModuleRecordData row = new ModuleRecordData();
                row.setSystemId(systemId);
                row.setTenantId(tenantId);
                row.setAppId(r.getAppId());
                row.setModelId(r.getModelId());
                row.setRecordId(r.getId());
                row.setFieldCode(fieldCode);
                row.setValueText(text);
                row.setCreateUserId(platId);
                row.setUpdateUserId(platId);
                moduleRecordDataService.save(row);
            }
        }

        r.setUpdateUserId(platId);
        moduleRecordService.updateById(r);

        saveHistory("update", r, dataToSnapshotJson(data));

        moduleFlowTriggerService.tryTriggerAfterRecordChange(r, "update", data);

        return detailWithData(recordId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long recordId) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (recordId == null || recordId <= 0L) {
            throw new BusinessException("recordId 不能为空");
        }
        ModuleRecord r = moduleRecordService.getById(recordId);
        if (r == null) {
            throw new BusinessException(404, "记录不存在");
        }
        if (r.getSystemId() == null || r.getTenantId() == null
                || r.getSystemId() != systemId
                || r.getTenantId() != tenantId) {
            throw new BusinessException(403, "无权限访问该记录");
        }
        if (r.getStatus() != null && r.getStatus() == 2) {
            return;
        }

        // snapshot before delete
        String snapshot = snapshotFromDb(recordId);

        r.setStatus(2);
        r.setUpdateUserId(platId);
        moduleRecordService.updateById(r);
        moduleRecordDataService.lambdaUpdate()
                .eq(ModuleRecordData::getRecordId, recordId)
                .remove();

        saveHistory("delete", r, snapshot);
    }

    public Map<String, Object> queryDsl(ModuleRecordDslQuery body) {
        ModuleRecordDslQuery q = prepareDslQuery(body, null, null, 200L);

        long offset = (q.getPage() - 1) * q.getLimit();
        Long total = moduleRecordMapper.countDsl(q);
        List<Map<String, Object>> list = total != null && total > 0
                ? moduleRecordMapper.listDsl(q, offset, q.getLimit())
                : List.of();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", q.getPage());
        m.put("limit", q.getLimit());
        m.put("total", total == null ? 0L : total);
        m.put("list", list);
        return m;
    }

    /**
     * 导出/查询共用的 DSL 规范化与白名单校验。
     * forceAppId/forceModelId 不为空时将覆盖 body 中的 appId/modelId（用于“按模板导出”强制作用域）。
     */
    public ModuleRecordDslQuery prepareDslQuery(ModuleRecordDslQuery body, Long forceAppId, Long forceModelId, long maxLimit) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException("body 不能为空");
        }

        if (forceAppId != null) {
            body.setAppId(forceAppId);
        }
        if (forceModelId != null) {
            body.setModelId(forceModelId);
        }
        if (body.getAppId() == null || body.getAppId() <= 0L) {
            throw new BusinessException("appId 不能为空");
        }
        if (body.getModelId() == null || body.getModelId() <= 0L) {
            throw new BusinessException("modelId 不能为空");
        }

        long page = body.getPage() == null ? 1L : body.getPage();
        long limit = body.getLimit() == null ? 20L : body.getLimit();
        page = Math.max(1L, page);
        long upper = Math.max(1L, maxLimit);
        limit = Math.max(1L, Math.min(limit, upper));

        body.setSystemId(systemId);
        body.setTenantId(tenantId);
        body.setPage(page);
        body.setLimit(limit);

        String sortBy = body.getSortBy() == null ? "updateTime" : body.getSortBy().trim();
        String sortDir = body.getSortDir() == null ? "desc" : body.getSortDir().trim().toLowerCase();
        Set<String> sortByAllow = Set.of("updateTime", "createTime", "id");
        if (!sortByAllow.contains(sortBy)) {
            throw new BusinessException("sortBy 不在白名单");
        }
        if (!sortDir.equals("asc") && !sortDir.equals("desc")) {
            throw new BusinessException("sortDir 必须为 asc/desc");
        }
        body.setSortBy(sortBy);
        body.setSortDir(sortDir);

        List<ModuleRecordDslFilter> filters = body.getFilters();
        if (filters != null && filters.size() > 10) {
            throw new BusinessException("filters 数量超限（最多 10 条）");
        }
        if (filters != null) {
            for (ModuleRecordDslFilter f : filters) {
                normalizeAndValidateFilter(body.getModelId(), body.getAppId(), systemId, tenantId, f);
            }
        }
        return body;
    }

    private void normalizeAndValidateFilter(Long modelId, Long appId, long systemId, long tenantId, ModuleRecordDslFilter f) {
        if (f == null) {
            throw new BusinessException("filter 不能为空");
        }
        String field = f.getField() == null ? "" : f.getField().trim();
        String op = f.getOp() == null ? "" : f.getOp().trim().toLowerCase();
        if (field.isEmpty() || op.isEmpty()) {
            throw new BusinessException("filter.field/op 不能为空");
        }

        Set<String> opAllow = Set.of("eq", "in", "like", "gte", "lte");
        if (!opAllow.contains(op)) {
            throw new BusinessException("filter.op 不在白名单");
        }

        if (RESERVED_RECORD_FIELDS.contains(field)) {
            if (field.equals("id")) {
                if (!(op.equals("eq") || op.equals("in"))) {
                    throw new BusinessException("id 仅支持 eq/in");
                }
            } else if (field.equals("createTime") || field.equals("updateTime")) {
                if (!(op.equals("gte") || op.equals("lte"))) {
                    throw new BusinessException(field + " 仅支持 gte/lte");
                }
                if (f.getValue() == null) {
                    throw new BusinessException(field + " 的 value 不能为空");
                }
            }
        } else {
            if (!FIELD_CODE.matcher(field).matches()) {
                throw new BusinessException("filter.field 须为 id/createTime/updateTime 或合法 field_code（字母开头，字母数字下划线）");
            }
            if (modelId == null || modelId <= 0L || appId == null || appId <= 0L) {
                throw new BusinessException("modelId/appId 不能为空");
            }
            requireFieldExists(systemId, tenantId, appId, modelId, field);
            if (!(op.equals("eq") || op.equals("like"))) {
                throw new BusinessException("field_code 条件仅支持 eq/like");
            }
            if (f.getValue() == null) {
                throw new BusinessException(field + " 的 value 不能为空");
            }
            String s = String.valueOf(f.getValue());
            if (s.length() > 200) {
                throw new BusinessException(field + " 的 value 太长");
            }
        }

        if (op.equals("in")) {
            if (f.getValues() == null || f.getValues().isEmpty()) {
                throw new BusinessException("in 的 values 不能为空");
            }
            if (f.getValues().size() > 200) {
                throw new BusinessException("in 的 values 超限（最多 200）");
            }
        } else {
            if (f.getValue() == null) {
                throw new BusinessException("value 不能为空");
            }
        }

        f.setField(field);
        f.setOp(op);
    }

    private void requireFieldExists(long systemId, long tenantId, Long appId, Long modelId, String fieldCode) {
        if (fieldCode == null || fieldCode.isBlank()) {
            throw new BusinessException("fieldCode 不能为空");
        }
        Long cnt = moduleFieldService.lambdaQuery()
                .eq(ModuleField::getSystemId, systemId)
                .eq(ModuleField::getTenantId, tenantId)
                .eq(ModuleField::getAppId, appId)
                .eq(ModuleField::getModelId, modelId)
                .eq(ModuleField::getFieldCode, fieldCode)
                .eq(ModuleField::getStatus, 1)
                .count();
        if (cnt == null || cnt == 0L) {
            throw new BusinessException("字段不存在或已停用: " + fieldCode);
        }
    }

    private ModuleField requireField(long systemId, long tenantId, Long appId, Long modelId, String fieldCode) {
        if (fieldCode == null || fieldCode.isBlank()) {
            throw new BusinessException("fieldCode 不能为空");
        }
        ModuleField f = moduleFieldService.lambdaQuery()
                .eq(ModuleField::getSystemId, systemId)
                .eq(ModuleField::getTenantId, tenantId)
                .eq(ModuleField::getAppId, appId)
                .eq(ModuleField::getModelId, modelId)
                .eq(ModuleField::getFieldCode, fieldCode)
                .eq(ModuleField::getStatus, 1)
                .last("limit 1")
                .one();
        if (f == null) {
            throw new BusinessException("字段不存在或已停用: " + fieldCode);
        }
        return f;
    }

    private void saveHistory(String action, ModuleRecord r, String snapshotJson) {
        if (r == null) {
            return;
        }
        ModuleRecordHistory h = new ModuleRecordHistory();
        h.setSystemId(r.getSystemId());
        h.setTenantId(r.getTenantId());
        h.setAppId(r.getAppId());
        h.setModelId(r.getModelId());
        h.setRecordId(r.getId());
        h.setAction(action);
        h.setDataJson(snapshotJson);
        h.setDiffJson(null);
        moduleRecordHistoryService.save(h);
    }

    private String dataToSnapshotJson(JsonNode data) {
        try {
            if (data == null || data.isNull()) {
                return "{}";
            }
            if (data.isObject()) {
                return objectMapper.writeValueAsString(data);
            }
            return objectMapper.writeValueAsString(objectMapper.createObjectNode());
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private String snapshotFromDb(Long recordId) {
        ObjectNode dataNode = objectMapper.createObjectNode();
        List<ModuleRecordData> rows = moduleRecordDataService.lambdaQuery()
                .eq(ModuleRecordData::getRecordId, recordId)
                .list();
        if (rows != null) {
            for (ModuleRecordData row : rows) {
                if (row == null || row.getFieldCode() == null) {
                    continue;
                }
                dataNode.put(row.getFieldCode(), row.getValueText() == null ? "" : row.getValueText());
            }
        }
        try {
            return objectMapper.writeValueAsString(dataNode);
        } catch (Exception ignore) {
            return "{}";
        }
    }
}
