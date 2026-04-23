package com.unique.examine.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.dto.ModuleRecordDslFilter;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleRecord;
import com.unique.examine.module.entity.po.ModuleRecordData;
import com.unique.examine.module.entity.po.ModuleRecordHistory;
import com.unique.examine.module.mapper.ModuleRecordMapper;
import com.unique.examine.module.service.IModuleRecordDataService;
import com.unique.examine.module.service.IModuleFieldService;
import com.unique.examine.module.service.IModuleRecordHistoryService;
import com.unique.examine.module.service.IModuleRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private ObjectMapper objectMapper;

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

        if (data != null && data.isObject()) {
            ObjectNode obj = (ObjectNode) data;
            var it = obj.fields();
            while (it.hasNext()) {
                var e = it.next();
                String fieldCode = e.getKey();
                if (!FIELD_CODE.matcher(fieldCode).matches()) {
                    throw new BusinessException("data 中存在非法 fieldCode: " + fieldCode);
                }
                requireFieldExists(systemId, tenantId, appId, modelId, fieldCode);
                JsonNode v = e.getValue();
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

        saveHistory("create", r, dataToSnapshotJson(data));

        return r;
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

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("record", r);
        m.put("data", dataNode);
        return m;
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
            var it = obj.fields();
            while (it.hasNext()) {
                var e = it.next();
                String fieldCode = e.getKey();
                if (!FIELD_CODE.matcher(fieldCode).matches()) {
                    throw new BusinessException("data 中存在非法 fieldCode: " + fieldCode);
                }
                requireFieldExists(systemId, tenantId, r.getAppId(), r.getModelId(), fieldCode);
                JsonNode v = e.getValue();
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
        if (body.getAppId() == null || body.getAppId() <= 0L) {
            throw new BusinessException("appId 不能为空");
        }
        if (body.getModelId() == null || body.getModelId() <= 0L) {
            throw new BusinessException("modelId 不能为空");
        }

        long page = body.getPage() == null ? 1L : body.getPage();
        long limit = body.getLimit() == null ? 20L : body.getLimit();
        page = Math.max(1L, page);
        limit = Math.max(1L, Math.min(limit, 200L));

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

        long offset = (page - 1) * limit;
        Long total = moduleRecordMapper.countDsl(body);
        List<Map<String, Object>> list = total != null && total > 0
                ? moduleRecordMapper.listDsl(body, offset, limit)
                : List.of();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", page);
        m.put("limit", limit);
        m.put("total", total == null ? 0L : total);
        m.put("list", list);
        return m;
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
