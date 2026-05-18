package com.unique.examine.module.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.dto.ModuleRecordDslFilter;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleRecord;
import com.unique.examine.module.entity.po.ModuleRecordData;
import com.unique.examine.module.entity.po.ModuleRelation;
import com.unique.examine.module.service.IModuleRecordDataService;
import com.unique.examine.module.service.IModuleRecordService;
import com.unique.examine.module.service.IModuleRelationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 按 un_module_relation 查询子表/关联侧记录（运行时）。 */
@Service
public class ModuleRelationRecordService {

    private static final int NN_LINK_LIMIT = 500;

    @Autowired
    private IModuleRelationService moduleRelationService;
    @Autowired
    private IModuleRecordService moduleRecordService;
    @Autowired
    private IModuleRecordDataService moduleRecordDataService;
    @Autowired
    private ModuleRecordFacadeService moduleRecordFacadeService;
    @Autowired
    private ObjectMapper objectMapper;

    public Map<String, Object> queryByRelation(Long relationId, Long parentRecordId, ModuleRecordDslQuery body) {
        if (relationId == null || parentRecordId == null) {
            throw new BusinessException(400, "relationId/parentRecordId 不能为空");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }

        ModuleRelation rel = moduleRelationService.getById(relationId);
        if (rel == null || !Objects.equals(rel.getSystemId(), systemId) || !Objects.equals(rel.getTenantId(), tenantId)) {
            throw new BusinessException(404, "关系不存在");
        }

        ModuleRecord parent = moduleRecordService.getById(parentRecordId);
        if (parent == null || !Objects.equals(parent.getSystemId(), systemId) || !Objects.equals(parent.getTenantId(), tenantId)) {
            throw new BusinessException(404, "父记录不存在");
        }
        if (!Objects.equals(parent.getModelId(), rel.getSrcModelId())) {
            throw new BusinessException(400, "父记录模型与关系的源模型不一致");
        }

        String relType = rel.getRelType() == null ? "1-n" : rel.getRelType().trim();
        if ("n-n".equalsIgnoreCase(relType)) {
            return queryNn(rel, parentRecordId, body);
        }

        String fkField = resolveFkField(rel);
        if (fkField == null || fkField.isBlank()) {
            throw new BusinessException(400, "关系 config_json 须包含 fkField（子表指向父记录 ID 的字段编码）");
        }

        ModuleRecordDslQuery q = body == null ? new ModuleRecordDslQuery() : body;
        q.setAppId(rel.getAppId());
        q.setModelId(rel.getDstModelId());
        if (q.getFilters() == null) {
            q.setFilters(new ArrayList<>());
        }
        ModuleRecordDslFilter fk = new ModuleRecordDslFilter();
        fk.setField(fkField);
        fk.setOp("eq");
        fk.setValue(parentRecordId);
        q.getFilters().add(fk);

        Map<String, Object> result = moduleRecordFacadeService.queryDsl(q);
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("relationId", relationId);
        out.put("parentRecordId", parentRecordId);
        out.put("fkField", fkField);
        return out;
    }

    private Map<String, Object> queryNn(ModuleRelation rel, Long parentRecordId, ModuleRecordDslQuery body) {
        JsonNode cfg = parseJson(rel.getConfigJson());
        if (cfg == null) {
            throw new BusinessException(400, "n-n 关系 config_json 须包含 linkModelId、srcFkField、dstFkField");
        }

        Long linkModelId = readLong(cfg, "linkModelId");
        String srcFkField = readText(cfg, "srcFkField");
        String dstFkField = readText(cfg, "dstFkField");
        if (linkModelId == null || linkModelId <= 0L) {
            throw new BusinessException(400, "n-n 关系 config_json.linkModelId 无效");
        }
        if (srcFkField == null || srcFkField.isBlank() || dstFkField == null || dstFkField.isBlank()) {
            throw new BusinessException(400, "n-n 关系 config_json 须包含 srcFkField、dstFkField");
        }

        ModuleRecordDslQuery linkQ = new ModuleRecordDslQuery();
        linkQ.setAppId(rel.getAppId());
        linkQ.setModelId(linkModelId);
        linkQ.setPage(1L);
        linkQ.setLimit((long) NN_LINK_LIMIT);
        List<ModuleRecordDslFilter> linkFilters = new ArrayList<>();
        ModuleRecordDslFilter srcFk = new ModuleRecordDslFilter();
        srcFk.setField(srcFkField);
        srcFk.setOp("eq");
        srcFk.setValue(parentRecordId);
        linkFilters.add(srcFk);
        linkQ.setFilters(linkFilters);

        Map<String, Object> linkResult = moduleRecordFacadeService.queryDsl(linkQ);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> linkRows = (List<Map<String, Object>>) linkResult.get("list");
        if (linkRows == null || linkRows.isEmpty()) {
            Map<String, Object> empty = emptyQueryResult(body, rel);
            empty.put("relType", "n-n");
            empty.put("linkModelId", linkModelId);
            empty.put("dstIds", List.of());
            return empty;
        }

        List<Long> linkRecordIds = new ArrayList<>();
        for (Map<String, Object> row : linkRows) {
            Object id = row.get("id");
            if (id instanceof Number n) {
                linkRecordIds.add(n.longValue());
            }
        }

        List<ModuleRecordData> dstFkRows = moduleRecordDataService.lambdaQuery()
                .in(ModuleRecordData::getRecordId, linkRecordIds)
                .eq(ModuleRecordData::getFieldCode, dstFkField)
                .list();

        Set<Long> dstIds = new LinkedHashSet<>();
        for (ModuleRecordData row : dstFkRows) {
            Long dstId = parseRecordId(row.getValueText());
            if (dstId != null) {
                dstIds.add(dstId);
            }
        }

        if (dstIds.isEmpty()) {
            Map<String, Object> empty = emptyQueryResult(body, rel);
            empty.put("relType", "n-n");
            empty.put("linkModelId", linkModelId);
            empty.put("dstIds", List.of());
            return empty;
        }

        ModuleRecordDslQuery dstQ = body == null ? new ModuleRecordDslQuery() : body;
        dstQ.setAppId(rel.getAppId());
        dstQ.setModelId(rel.getDstModelId());
        if (dstQ.getFilters() == null) {
            dstQ.setFilters(new ArrayList<>());
        }
        ModuleRecordDslFilter inFilter = new ModuleRecordDslFilter();
        inFilter.setField("id");
        inFilter.setOp("in");
        inFilter.setValues(new ArrayList<>(dstIds));
        dstQ.getFilters().add(0, inFilter);

        Map<String, Object> result = moduleRecordFacadeService.queryDsl(dstQ);
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("relationId", rel.getId());
        out.put("parentRecordId", parentRecordId);
        out.put("relType", "n-n");
        out.put("linkModelId", linkModelId);
        out.put("srcFkField", srcFkField);
        out.put("dstFkField", dstFkField);
        out.put("dstIds", new ArrayList<>(dstIds));
        return out;
    }

    private Map<String, Object> emptyQueryResult(ModuleRecordDslQuery body, ModuleRelation rel) {
        ModuleRecordDslQuery q = body == null ? new ModuleRecordDslQuery() : body;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", q.getPage() == null ? 1L : q.getPage());
        m.put("limit", q.getLimit() == null ? 20L : q.getLimit());
        m.put("total", 0L);
        m.put("list", List.of());
        m.put("relationId", rel.getId());
        m.put("appId", rel.getAppId());
        m.put("modelId", rel.getDstModelId());
        return m;
    }

    private Long parseRecordId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long readLong(JsonNode cfg, String key) {
        JsonNode n = cfg.get(key);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.longValue();
        }
        try {
            return Long.parseLong(n.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String readText(JsonNode cfg, String key) {
        JsonNode n = cfg.get(key);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.asText().trim();
    }

    private String resolveFkField(ModuleRelation rel) {
        JsonNode cfg = parseJson(rel.getConfigJson());
        if (cfg == null) {
            return null;
        }
        JsonNode fk = cfg.get("fkField");
        if (fk == null) {
            fk = cfg.get("childFkField");
        }
        if (fk == null || fk.isNull()) {
            return null;
        }
        return fk.asText().trim();
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(400, "关系 config_json 非法");
        }
    }
}
