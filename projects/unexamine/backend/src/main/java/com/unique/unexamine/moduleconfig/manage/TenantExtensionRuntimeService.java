package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.base.entity.CfgTenantExtension;
import com.unique.unexamine.moduleconfig.base.entity.CfgTenantExtensionVersion;
import com.unique.unexamine.moduleconfig.base.service.CfgTenantExtensionBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgTenantExtensionVersionBaseService;
import org.springframework.stereotype.Service;

@Service
public class TenantExtensionRuntimeService {
    private final CfgTenantExtensionBaseService extensionService;
    private final CfgTenantExtensionVersionBaseService versionService;
    private final ObjectMapper objectMapper;

    public TenantExtensionRuntimeService(
            CfgTenantExtensionBaseService extensionService,
            CfgTenantExtensionVersionBaseService versionService,
            ObjectMapper objectMapper) {
        this.extensionService = extensionService;
        this.versionService = versionService;
        this.objectMapper = objectMapper;
    }

    public JsonNode mergePublished(AuthenticatedContext context, Long baseModuleId, Long baseTenantId, JsonNode base) {
        if (context.tenantId().equals(baseTenantId)) return base;
        CfgTenantExtension extension = extensionService.selectList(Wrappers.<CfgTenantExtension>lambdaQuery()
                        .eq(CfgTenantExtension::getSystemId, context.systemId())
                        .eq(CfgTenantExtension::getTenantId, context.tenantId())
                        .eq(CfgTenantExtension::getBaseModuleId, baseModuleId))
                .stream().findFirst().orElse(null);
        if (extension == null || "DELETED".equals(extension.getStatus()) || extension.getCurrentVersionId() == null) return base;
        CfgTenantExtensionVersion version = versionService.selectById(extension.getCurrentVersionId());
        if (version == null || !extension.getId().equals(version.getExtensionId())) return base;
        try {
            JsonNode snapshot = objectMapper.readTree(version.getSnapshotJson());
            JsonNode merged = snapshot.path("mergedConfiguration");
            if (!merged.isObject()) return base;
            ObjectNode effective = merged.deepCopy();
            effective.put("tenantExtensionApplied", true);
            effective.put("tenantExtensionVersionId", version.getId());
            effective.put("tenantExtensionVersionNumber", version.getVersionNumber());
            effective.put("tenantExtensionBaseModuleVersionId", version.getBaseModuleVersionId());
            return effective;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read tenant extension snapshot " + version.getId(), exception);
        }
    }

    public ObjectNode merge(ObjectNode base, JsonNode extensionDocument) {
        ObjectNode merged = base.deepCopy();
        ArrayNode fields = merged.withArray("fields");
        extensionDocument.path("fields").forEach(field -> fields.add(field.deepCopy()));
        extensionDocument.path("pages").forEach(override -> applyPageOverride(merged.withArray("pages"), override));
        merged.put("tenantExtensionApplied", true);
        merged.set("tenantExtensionSource", extensionDocument.path("source").deepCopy());
        merged.set("applicationBindings", extensionDocument.path("applicationBindings").deepCopy());
        return merged;
    }

    private void applyPageOverride(ArrayNode pages, JsonNode override) {
        String pageType = override.path("pageType").asText();
        for (JsonNode page : pages) {
            if (!pageType.equals(page.path("pageType").asText()) || !page.isObject()) continue;
            ObjectNode configured = (ObjectNode) page;
            ObjectNode layout;
            try { layout = (ObjectNode) objectMapper.readTree(page.path("layoutJson").asText("{}")); }
            catch (Exception ignored) { layout = objectMapper.createObjectNode(); }
            layout.set("fieldCodes", override.path("fieldCodes").deepCopy());
            layout.put("source", "TENANT");
            try { configured.put("layoutJson", objectMapper.writeValueAsString(layout)); }
            catch (Exception exception) { throw new IllegalStateException("Cannot merge tenant page override", exception); }
        }
    }
}
