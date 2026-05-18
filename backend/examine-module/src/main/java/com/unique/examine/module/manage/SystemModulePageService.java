package com.unique.examine.module.manage;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleListViewCol;
import com.unique.examine.module.entity.po.ModulePage;
import com.unique.examine.module.entity.po.ModulePageBlock;
import com.unique.examine.module.service.IModuleFieldService;
import com.unique.examine.module.service.IModuleListViewColService;
import com.unique.examine.module.service.IModulePageBlockService;
import com.unique.examine.module.service.IModulePageService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemModulePageService {

    private static final Set<String> PAGE_TYPES = Set.of("list", "form", "detail", "custom");
    private static final Set<String> BLOCK_TYPES = Set.of("form", "table", "chart", "text", "custom");

    public record UpsertPageCmd(
            Long id,
            Long appId,
            String pageCode,
            String pageName,
            String pageType,
            String routePath,
            String configJson,
            String formFieldsJson,
            Integer status
    ) {}

    public record UpsertBlockCmd(
            Long id,
            Long appId,
            Long pageId,
            String blockType,
            Integer sortNo,
            String configJson
    ) {}

    @Autowired
    private IModulePageService modulePageService;
    @Autowired
    private IModulePageBlockService modulePageBlockService;
    @Autowired
    private IModuleListViewColService moduleListViewColService;
    @Autowired
    private IModuleFieldService moduleFieldService;
    @Autowired
    private ObjectMapper objectMapper;

    public List<ModulePage> listPages(Long appId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (appId == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        return modulePageService.lambdaQuery()
                .eq(ModulePage::getSystemId, systemId)
                .eq(ModulePage::getTenantId, tenantId)
                .eq(ModulePage::getAppId, appId)
                .orderByAsc(ModulePage::getPageCode)
                .list();
    }

    public Map<String, Object> getPageDetail(Long pageId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        ModulePage page = requirePage(pageId, systemId, tenantId);
        List<ModulePageBlock> blocks = modulePageBlockService.lambdaQuery()
                .eq(ModulePageBlock::getSystemId, systemId)
                .eq(ModulePageBlock::getTenantId, tenantId)
                .eq(ModulePageBlock::getPageId, pageId)
                .orderByAsc(ModulePageBlock::getSortNo)
                .orderByAsc(ModulePageBlock::getId)
                .list();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", page);
        out.put("blocks", blocks);
        return out;
    }

    /**
     * 运行时解析：modelId、列表列、搜索字段、表单字段覆盖等。
     */
    public Map<String, Object> getPageRuntime(Long pageId, Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        ModulePage page = requirePage(pageId, systemId, tenantId);
        List<ModulePageBlock> blocks = modulePageBlockService.lambdaQuery()
                .eq(ModulePageBlock::getSystemId, systemId)
                .eq(ModulePageBlock::getTenantId, tenantId)
                .eq(ModulePageBlock::getPageId, pageId)
                .orderByAsc(ModulePageBlock::getSortNo)
                .orderByAsc(ModulePageBlock::getId)
                .list();

        JsonNode pageConfig = parseJsonNode(page.getConfigJson());
        Long modelId = longFromJson(pageConfig, "modelId");
        Long listViewId = longFromJson(pageConfig, "listViewId");
        if (modelId == null) {
            modelId = resolveModelIdFromBlocks(blocks);
        }

        List<String> titleFieldCodes = stringListFromJson(pageConfig, "titleFieldCodes");
        List<String> columnFieldCodes = stringListFromJson(pageConfig, "columnFieldCodes");
        String searchFieldCode = textFromJson(pageConfig, "searchFieldCode");

        if ((columnFieldCodes == null || columnFieldCodes.isEmpty()) && listViewId != null) {
            columnFieldCodes = resolveColumnCodesFromListView(listViewId, systemId, tenantId);
        }
        if ((titleFieldCodes == null || titleFieldCodes.isEmpty()) && columnFieldCodes != null && !columnFieldCodes.isEmpty()) {
            titleFieldCodes = columnFieldCodes.size() > 2 ? columnFieldCodes.subList(0, 2) : columnFieldCodes;
        }

        List<Map<String, Object>> fieldOverrides = parseFieldOverrides(page.getFormFieldsJson());

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("pageId", page.getId());
        runtime.put("appId", page.getAppId());
        runtime.put("pageCode", page.getPageCode());
        runtime.put("pageName", page.getPageName());
        runtime.put("pageType", page.getPageType());
        runtime.put("routePath", page.getRoutePath());
        runtime.put("modelId", modelId);
        runtime.put("listViewId", listViewId);
        runtime.put("searchFieldCode", searchFieldCode);
        runtime.put("titleFieldCodes", titleFieldCodes == null ? List.of() : titleFieldCodes);
        runtime.put("columnFieldCodes", columnFieldCodes == null ? List.of() : columnFieldCodes);
        runtime.put("fieldOverrides", fieldOverrides);
        runtime.put("blocks", blocks);
        return runtime;
    }

    @Transactional(rollbackFor = Exception.class)
    public ModulePage upsertPage(Long operatorPlatId, UpsertPageCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null || body.appId() == null) {
            throw new BusinessException(400, "appId 不能为空");
        }
        String pageCode = trimRequired(body.pageCode(), "pageCode");
        String pageName = trimRequired(body.pageName(), "pageName");
        String pageType = normalizePageType(body.pageType());
        int status = body.status() == null ? 1 : body.status();

        ModulePage page;
        if (body.id() != null) {
            page = requirePage(body.id(), systemId, tenantId);
            if (!Objects.equals(page.getAppId(), body.appId())) {
                throw new BusinessException(400, "appId 与页面不匹配");
            }
        } else {
            long existed = modulePageService.lambdaQuery()
                    .eq(ModulePage::getSystemId, systemId)
                    .eq(ModulePage::getTenantId, tenantId)
                    .eq(ModulePage::getAppId, body.appId())
                    .eq(ModulePage::getPageCode, pageCode)
                    .count();
            if (existed > 0) {
                throw new BusinessException(400, "pageCode 已存在");
            }
            page = new ModulePage();
            page.setSystemId(systemId);
            page.setTenantId(tenantId);
            page.setAppId(body.appId());
            page.setCreateUserId(operatorPlatId);
        }
        page.setPageCode(pageCode);
        page.setPageName(pageName);
        page.setPageType(pageType);
        page.setRoutePath(trimToNull(body.routePath()));
        page.setConfigJson(trimToNull(body.configJson()));
        page.setFormFieldsJson(trimToNull(body.formFieldsJson()));
        page.setStatus(status);
        page.setUpdateUserId(operatorPlatId);
        if (body.id() != null) {
            modulePageService.updateById(page);
        } else {
            modulePageService.save(page);
        }
        return page;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePages(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        modulePageBlockService.lambdaUpdate()
                .eq(ModulePageBlock::getSystemId, systemId)
                .eq(ModulePageBlock::getTenantId, tenantId)
                .in(ModulePageBlock::getPageId, ids)
                .remove();
        modulePageService.lambdaUpdate()
                .eq(ModulePage::getSystemId, systemId)
                .eq(ModulePage::getTenantId, tenantId)
                .in(ModulePage::getId, ids)
                .remove();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModulePageBlock upsertBlock(Long operatorPlatId, UpsertBlockCmd body) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null || body.appId() == null || body.pageId() == null) {
            throw new BusinessException(400, "appId/pageId 不能为空");
        }
        requirePage(body.pageId(), systemId, tenantId);
        String blockType = normalizeBlockType(body.blockType());
        int sortNo = body.sortNo() == null ? 0 : body.sortNo();

        ModulePageBlock block;
        if (body.id() != null) {
            block = modulePageBlockService.getById(body.id());
            if (block == null) {
                throw new BusinessException(404, "block 不存在");
            }
            if (!Objects.equals(block.getSystemId(), systemId) || !Objects.equals(block.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该 block");
            }
        } else {
            block = new ModulePageBlock();
            block.setSystemId(systemId);
            block.setTenantId(tenantId);
            block.setCreateUserId(operatorPlatId);
        }
        block.setAppId(body.appId());
        block.setPageId(body.pageId());
        block.setBlockType(blockType);
        block.setSortNo(sortNo);
        block.setConfigJson(trimToNull(body.configJson()));
        block.setUpdateUserId(operatorPlatId);
        if (body.id() != null) {
            modulePageBlockService.updateById(block);
        } else {
            modulePageBlockService.save(block);
        }
        return block;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBlocks(Long operatorPlatId, List<Long> ids) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        modulePageBlockService.lambdaUpdate()
                .eq(ModulePageBlock::getSystemId, systemId)
                .eq(ModulePageBlock::getTenantId, tenantId)
                .in(ModulePageBlock::getId, ids)
                .remove();
    }

    public List<Map<String, Object>> listPagePickerOptions(Long appId, Long operatorPlatId) {
        List<ModulePage> pages = listPages(appId, operatorPlatId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModulePage p : pages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", p.getId());
            row.put("text", (p.getPageName() != null ? p.getPageName() : p.getPageCode())
                    + " [" + p.getPageType() + "]");
            row.put("pageCode", p.getPageCode());
            row.put("pageType", p.getPageType());
            out.add(row);
        }
        return out;
    }

    private ModulePage requirePage(Long pageId, long systemId, long tenantId) {
        if (pageId == null) {
            throw new BusinessException(400, "pageId 不能为空");
        }
        ModulePage page = modulePageService.getById(pageId);
        if (page == null || !Objects.equals(page.getSystemId(), systemId) || !Objects.equals(page.getTenantId(), tenantId)) {
            throw new BusinessException(404, "页面不存在");
        }
        return page;
    }

    private static String normalizePageType(String pageType) {
        String t = pageType == null ? "custom" : pageType.trim().toLowerCase();
        if (!PAGE_TYPES.contains(t)) {
            throw new BusinessException(400, "pageType 须为 list|form|detail|custom");
        }
        return t;
    }

    private static String normalizeBlockType(String blockType) {
        String t = blockType == null ? "custom" : blockType.trim().toLowerCase();
        if (!BLOCK_TYPES.contains(t)) {
            throw new BusinessException(400, "blockType 须为 form|table|chart|text|custom");
        }
        return t;
    }

    private static String trimRequired(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new BusinessException(400, name + " 不能为空");
        }
        return v.trim();
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static void requireOperator(Long platId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    private static long requireSystem() {
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        return systemId;
    }

    private JsonNode parseJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(400, "JSON 解析失败: " + e.getMessage());
        }
    }

    private static Long longFromJson(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.longValue();
        }
        if (v.isTextual()) {
            try {
                return Long.parseLong(v.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String textFromJson(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        String t = v.asText(null);
        return t == null || t.isBlank() ? null : t.trim();
    }

    private static List<String> stringListFromJson(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode arr = node.get(key);
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : arr) {
            if (item != null && !item.isNull()) {
                String s = item.asText(null);
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private Long resolveModelIdFromBlocks(List<ModulePageBlock> blocks) {
        if (blocks == null) {
            return null;
        }
        for (ModulePageBlock b : blocks) {
            JsonNode cfg = parseJsonNode(b.getConfigJson());
            Long mid = longFromJson(cfg, "modelId");
            if (mid != null) {
                return mid;
            }
        }
        return null;
    }

    private List<String> resolveColumnCodesFromListView(Long viewId, long systemId, long tenantId) {
        List<ModuleListViewCol> cols = moduleListViewColService.lambdaQuery()
                .eq(ModuleListViewCol::getSystemId, systemId)
                .eq(ModuleListViewCol::getTenantId, tenantId)
                .eq(ModuleListViewCol::getViewId, viewId)
                .eq(ModuleListViewCol::getVisibleFlag, 1)
                .orderByAsc(ModuleListViewCol::getSortNo)
                .orderByAsc(ModuleListViewCol::getId)
                .list();
        if (cols.isEmpty()) {
            return List.of();
        }
        Set<Long> fieldIds = new LinkedHashSet<>();
        for (ModuleListViewCol c : cols) {
            if (c.getFieldId() != null) {
                fieldIds.add(c.getFieldId());
            }
        }
        Map<Long, String> codeByFieldId = new LinkedHashMap<>();
        if (!fieldIds.isEmpty()) {
            List<ModuleField> fields = moduleFieldService.lambdaQuery()
                    .eq(ModuleField::getSystemId, systemId)
                    .eq(ModuleField::getTenantId, tenantId)
                    .in(ModuleField::getId, fieldIds)
                    .list();
            for (ModuleField f : fields) {
                if (f.getId() != null && f.getFieldCode() != null) {
                    codeByFieldId.put(f.getId(), f.getFieldCode());
                }
            }
        }
        List<String> codes = new ArrayList<>();
        for (ModuleListViewCol c : cols) {
            if (c.getFieldId() == null) {
                continue;
            }
            String code = codeByFieldId.get(c.getFieldId());
            if (code != null && !code.isBlank()) {
                codes.add(code);
            }
        }
        return codes;
    }

    private List<Map<String, Object>> parseFieldOverrides(String formFieldsJson) {
        JsonNode root = parseJsonNode(formFieldsJson);
        if (root == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode item : root) {
                Map<String, Object> row = overrideFromNode(item);
                if (row != null) {
                    out.add(row);
                }
            }
            return out;
        }
        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode != null && fieldsNode.isArray()) {
            for (JsonNode item : fieldsNode) {
                Map<String, Object> row = overrideFromNode(item);
                if (row != null) {
                    out.add(row);
                }
            }
            return out;
        }
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String code = names.next();
            JsonNode item = root.get(code);
            if (item == null || item.isNull()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fieldCode", code);
            if (item.isObject()) {
                if (item.has("hidden")) {
                    row.put("hidden", item.get("hidden").asBoolean());
                }
                if (item.has("required")) {
                    row.put("required", item.get("required").asBoolean());
                }
                if (item.has("sortNo")) {
                    row.put("sortNo", item.get("sortNo").asInt());
                }
            }
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> overrideFromNode(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String code = textFromJson(item, "fieldCode");
        if (code == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fieldCode", code);
        if (item.has("hidden")) {
            row.put("hidden", item.get("hidden").asBoolean());
        }
        if (item.has("required")) {
            row.put("required", item.get("required").asBoolean());
        }
        if (item.has("sortNo")) {
            row.put("sortNo", item.get("sortNo").asInt());
        }
        return row;
    }
}
