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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            JsonNode document = snapshot.path("extension");
            if (!base.isObject() || !document.isObject()) {
                throw new IllegalStateException("Tenant extension snapshot is incomplete: " + version.getId());
            }
            // 已发布扩展只保存自己的增量；每次运行都叠加当前主租户发布版本，避免继续读取旧主版本快照。
            ObjectNode effective = merge(decorateBase((ObjectNode) base, baseTenantId), document);
            effective.put("tenantExtensionVersionId", version.getId());
            effective.put("tenantExtensionVersionNumber", version.getVersionNumber());
            effective.put("tenantExtensionBaseModuleVersionId", version.getBaseModuleVersionId());
            return effective;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read tenant extension snapshot " + version.getId(), exception);
        }
    }

    /** 主模块发布或回滚前检查所有仍在使用的租户扩展，问题需指出具体租户与配置项。 */
    public List<PublicationCheckIssue> checkPublishedExtensions(Long systemId, Long baseModuleId, JsonNode candidateBase) {
        List<PublicationCheckIssue> issues = new ArrayList<>();
        for (CfgTenantExtension extension : extensionService.selectList(Wrappers.<CfgTenantExtension>lambdaQuery()
                .eq(CfgTenantExtension::getSystemId, systemId)
                .eq(CfgTenantExtension::getBaseModuleId, baseModuleId))) {
            if ("DELETED".equals(extension.getStatus()) || extension.getCurrentVersionId() == null) continue;
            CfgTenantExtensionVersion version = versionService.selectById(extension.getCurrentVersionId());
            if (version == null || !extension.getId().equals(version.getExtensionId())) {
                issues.add(new PublicationCheckIssue("tenants." + extension.getTenantId(), "TENANT_EXTENSION_VERSION_INVALID",
                        "租户 " + extension.getTenantId() + " 的扩展发布版本无效"));
                continue;
            }
            try {
                JsonNode document = objectMapper.readTree(version.getSnapshotJson()).path("extension");
                issues.addAll(checkDocument(extension.getTenantId(), candidateBase, document));
            } catch (Exception exception) {
                issues.add(new PublicationCheckIssue("tenants." + extension.getTenantId(), "TENANT_EXTENSION_SNAPSHOT_INVALID",
                        "租户 " + extension.getTenantId() + " 的扩展快照无法读取"));
            }
        }
        return issues;
    }

    /** 校验主配置变更后的稳定编码和强制字段，供主模块切版与租户扩展回滚共用。 */
    public List<PublicationCheckIssue> checkDocument(Long tenantId, JsonNode base, JsonNode document) {
        List<PublicationCheckIssue> issues = new ArrayList<>();
        String prefix = "tenants." + tenantId + ".";
        if (!base.isObject() || !document.isObject()) {
            issues.add(new PublicationCheckIssue(prefix + "extension", "TENANT_EXTENSION_SNAPSHOT_INVALID",
                    "租户 " + tenantId + " 的扩展快照缺少配置内容"));
            return issues;
        }
        Set<String> fields = new HashSet<>();
        Set<String> required = new HashSet<>();
        base.path("fields").forEach(field -> {
            String code = field.path("code").asText();
            fields.add(code);
            if (field.path("required").asBoolean(false)) required.add(code);
        });
        document.path("fields").forEach(field -> {
            String code = field.path("code").asText();
            if (!fields.add(code)) issues.add(new PublicationCheckIssue(prefix + "fields." + code,
                    "TENANT_FIELD_CODE_CONFLICT", "租户 " + tenantId + " 的扩展字段与新主配置编码冲突：" + code));
        });
        Set<String> actions = new HashSet<>();
        base.path("actions").forEach(action -> actions.add(action.path("code").asText()));
        document.path("actions").forEach(action -> {
            String code = action.path("code").asText();
            if (!actions.add(code)) issues.add(new PublicationCheckIssue(prefix + "actions." + code,
                    "TENANT_ACTION_CODE_CONFLICT", "租户 " + tenantId + " 的扩展动作与新主配置编码冲突：" + code));
            try {
                objectMapper.readTree(action.path("configJson").asText("{}"))
                        .path("parameterFieldCodes").forEach(fieldCode -> {
                            if (!fields.contains(fieldCode.asText())) issues.add(new PublicationCheckIssue(
                                    prefix + "actions." + code + ".parameterFieldCodes", "TENANT_ACTION_FIELD_MISSING",
                                    "租户 " + tenantId + " 的扩展动作引用已移除字段：" + fieldCode.asText()));
                        });
            } catch (Exception exception) {
                issues.add(new PublicationCheckIssue(prefix + "actions." + code + ".configJson",
                        "TENANT_ACTION_CONFIG_INVALID", "租户 " + tenantId + " 的扩展动作配置无法读取：" + code));
            }
        });
        document.path("pages").forEach(page -> {
            String type = page.path("pageType").asText();
            Set<String> pageFields = new HashSet<>();
            page.path("fieldCodes").forEach(code -> {
                pageFields.add(code.asText());
                if (!fields.contains(code.asText())) issues.add(new PublicationCheckIssue(prefix + "pages." + type,
                        "TENANT_PAGE_FIELD_MISSING", "租户 " + tenantId + " 的扩展页面引用已移除字段：" + code.asText()));
            });
            if ("FORM".equals(type)) for (String code : required) {
                if (!pageFields.contains(code)) issues.add(new PublicationCheckIssue(prefix + "pages.FORM",
                        "MAIN_MANDATORY_FIELD_HIDDEN", "租户 " + tenantId + " 的表单缺少新主配置必填字段：" + code));
            }
        });
        document.path("applicationBindings").forEach(binding -> binding.path("fieldCodes").forEach(code -> {
            if (!fields.contains(code.asText())) issues.add(new PublicationCheckIssue(prefix + "applicationBindings",
                    "TENANT_APPLICATION_FIELD_MISSING", "租户 " + tenantId + " 的应用绑定引用已移除字段：" + code.asText()));
        }));
        return issues;
    }

    /** 主租户字段和动作保留来源与强制语义，供扩展预览和运行态合并使用。 */
    public ObjectNode decorateBase(ObjectNode base, Long mainTenantId) {
        ObjectNode decorated = base.deepCopy();
        decorated.path("actions").forEach(action -> {
            if (action.isObject()) {
                ((ObjectNode) action).put("source", "MAIN");
                ((ObjectNode) action).put("sourceTenantId", mainTenantId);
                ((ObjectNode) action).put("mandatory", true);
            }
        });
        decorated.path("fields").forEach(field -> {
            if (field.isObject()) {
                ((ObjectNode) field).put("source", "MAIN");
                ((ObjectNode) field).put("sourceTenantId", mainTenantId);
                ((ObjectNode) field).put("mandatory", field.path("required").asBoolean(false));
            }
        });
        decorated.put("tenantExtensionApplied", false);
        return decorated;
    }

    public ObjectNode merge(ObjectNode base, JsonNode extensionDocument) {
        ObjectNode merged = base.deepCopy();
        ArrayNode fields = merged.withArray("fields");
        extensionDocument.path("fields").forEach(field -> fields.add(field.deepCopy()));
        // 基础动作保持原样，租户只能追加自己的动作；保存和发布均校验冲突。
        ArrayNode actions = merged.withArray("actions");
        extensionDocument.path("actions").forEach(action -> actions.add(action.deepCopy()));
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
