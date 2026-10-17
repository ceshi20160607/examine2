package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.analytics.base.entity.AnaKpi;
import com.unique.unexamine.analytics.base.service.AnaKpiBaseService;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.entity.FlowNode;
import com.unique.unexamine.flow.base.entity.FlowPublication;
import com.unique.unexamine.flow.base.entity.FlowVersion;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.base.service.FlowInstanceBaseService;
import com.unique.unexamine.flow.base.service.FlowNodeBaseService;
import com.unique.unexamine.flow.base.service.FlowPublicationBaseService;
import com.unique.unexamine.flow.base.service.FlowVersionBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.system.base.entity.SysSsoBinding;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.service.SysSsoBindingBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 检查部门停用后仍会读取该部门的租户配置；发布快照与在途流程只报告影响，不改写历史。 */
@Service
public class DepartmentConfigurationReferences {
    private final ObjectMapper json;
    private final SysSsoBindingBaseService ssoBindings;
    private final FlowDefinitionBaseService flows;
    private final FlowNodeBaseService flowNodes;
    private final FlowPublicationBaseService flowPublications;
    private final FlowVersionBaseService flowVersions;
    private final FlowInstanceBaseService flowInstances;
    private final AnaKpiBaseService kpis;
    private final SystemRolePermissionBaseService rolePermissions;
    private final ConfiguredModuleFieldBaseService moduleFields;
    private final ConfiguredModulePublicationBaseService modulePublications;
    private final ConfiguredModuleVersionBaseService moduleVersions;

    public DepartmentConfigurationReferences(ObjectMapper json, SysSsoBindingBaseService ssoBindings,
            FlowDefinitionBaseService flows, FlowNodeBaseService flowNodes,
            FlowPublicationBaseService flowPublications, FlowVersionBaseService flowVersions,
            FlowInstanceBaseService flowInstances, AnaKpiBaseService kpis,
            SystemRolePermissionBaseService rolePermissions, ConfiguredModuleFieldBaseService moduleFields,
            ConfiguredModulePublicationBaseService modulePublications,
            ConfiguredModuleVersionBaseService moduleVersions) {
        this.json = json;
        this.ssoBindings = ssoBindings;
        this.flows = flows;
        this.flowNodes = flowNodes;
        this.flowPublications = flowPublications;
        this.flowVersions = flowVersions;
        this.flowInstances = flowInstances;
        this.kpis = kpis;
        this.rolePermissions = rolePermissions;
        this.moduleFields = moduleFields;
        this.modulePublications = modulePublications;
        this.moduleVersions = moduleVersions;
    }

    /** 每种引用按配置行或在途实例计数；无法解析的相关配置也算一条阻断引用。 */
    public List<SystemAuthorizationModels.DepartmentReference> inspect(
            Long systemId, Long tenantId, Long departmentId) {
        List<SystemAuthorizationModels.DepartmentReference> result = new ArrayList<>();
        add(result, "SSO_MAPPING", "身份源部门映射", ssoCount(systemId, tenantId, departmentId));
        List<FlowDefinition> tenantFlows = flows.selectList(Wrappers.<FlowDefinition>lambdaQuery()
                .eq(FlowDefinition::getSystemId, systemId)
                .eq(FlowDefinition::getOwnerTenantId, tenantId));
        Set<Long> flowIds = new HashSet<>();
        tenantFlows.forEach(flow -> flowIds.add(flow.getId()));
        add(result, "FLOW_DRAFT_ASSIGNEE", "流程草稿审批部门", flowDraftCount(flowIds, departmentId));
        add(result, "FLOW_PUBLISHED_ASSIGNEE", "当前发布流程审批部门",
                flowPublishedCount(flowIds, departmentId));
        add(result, "FLOW_ACTIVE_INSTANCE_ASSIGNEE", "在途流程审批部门",
                flowInstanceCount(systemId, tenantId, departmentId));
        add(result, "KPI_TARGET", "指标目标部门", kpis.selectCount(Wrappers.<AnaKpi>lambdaQuery()
                .eq(AnaKpi::getSystemId, systemId).eq(AnaKpi::getOwnerTenantId, tenantId)
                .eq(AnaKpi::getTargetSubjectType, "DEPARTMENT")
                .eq(AnaKpi::getTargetSubjectId, departmentId)));
        add(result, "ROLE_DATA_SCOPE", "角色授权部门范围", roleCount(systemId, tenantId, departmentId));
        add(result, "MODULE_FIELD_DEFAULT_DRAFT", "模块草稿部门默认值",
                moduleDraftCount(systemId, tenantId, departmentId));
        add(result, "MODULE_FIELD_DEFAULT_PUBLISHED", "当前发布模块部门默认值",
                modulePublishedCount(systemId, tenantId, departmentId));
        return result;
    }

    private long ssoCount(Long systemId, Long tenantId, Long departmentId) {
        long count = 0;
        for (SysSsoBinding binding : ssoBindings.selectList(Wrappers.<SysSsoBinding>lambdaQuery()
                .eq(SysSsoBinding::getSystemId, systemId).eq(SysSsoBinding::getTenantId, tenantId)
                .in(SysSsoBinding::getStatus, List.of("DRAFT", "ACTIVE")))) {
            if (binding.getDepartmentMappingJson() == null || binding.getDepartmentMappingJson().isBlank()) continue;
            JsonNode mapping = parse(binding.getDepartmentMappingJson());
            if (mapping == null || !mapping.isObject() || anyValueMatches(mapping, departmentId)) count++;
        }
        return count;
    }

    private long flowDraftCount(Set<Long> flowIds, Long departmentId) {
        if (flowIds.isEmpty()) return 0;
        long count = 0;
        for (FlowNode node : flowNodes.selectList(Wrappers.<FlowNode>lambdaQuery()
                .in(FlowNode::getFlowId, flowIds))) {
            if (node.getAssigneePolicyJson() == null || node.getAssigneePolicyJson().isBlank()) continue;
            JsonNode policy = parse(node.getAssigneePolicyJson());
            if (policy == null || !policy.isObject() || hasDepartmentId(policy, departmentId)) count++;
        }
        return count;
    }

    private long flowPublishedCount(Set<Long> flowIds, Long departmentId) {
        if (flowIds.isEmpty()) return 0;
        long count = 0;
        for (FlowPublication publication : flowPublications.selectList(Wrappers.<FlowPublication>lambdaQuery()
                .in(FlowPublication::getFlowId, flowIds))) {
            FlowVersion version = flowVersions.selectById(publication.getCurrentVersionId());
            if (version == null || !publication.getFlowId().equals(version.getFlowId())
                    || snapshotReferencesDepartment(version.getSnapshotJson(), departmentId)) count++;
        }
        return count;
    }

    private long flowInstanceCount(Long systemId, Long tenantId, Long departmentId) {
        long count = 0;
        for (FlowInstance instance : flowInstances.selectList(Wrappers.<FlowInstance>lambdaQuery()
                .eq(FlowInstance::getSystemId, systemId).eq(FlowInstance::getTenantId, tenantId)
                .in(FlowInstance::getStatus, List.of("RUNNING", "WAITING", "EXCEPTION")))) {
            FlowVersion version = flowVersions.selectById(instance.getFlowVersionId());
            if (version == null || !instance.getFlowId().equals(version.getFlowId())
                    || snapshotReferencesDepartment(version.getSnapshotJson(), departmentId)) count++;
        }
        return count;
    }

    private long roleCount(Long systemId, Long tenantId, Long departmentId) {
        long count = 0;
        for (SystemRolePermission permission : rolePermissions.selectList(Wrappers.<SystemRolePermission>lambdaQuery()
                .eq(SystemRolePermission::getSystemId, systemId)
                .eq(SystemRolePermission::getTenantId, tenantId))) {
            if (permission.getDataScopeJson() == null || permission.getDataScopeJson().isBlank()) continue;
            JsonNode scope = parse(permission.getDataScopeJson());
            if (scope == null || hasDepartmentId(scope, departmentId)) count++;
        }
        return count;
    }

    private long moduleDraftCount(Long systemId, Long tenantId, Long departmentId) {
        long count = 0;
        for (ConfiguredModuleField field : moduleFields.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getSystemId, systemId)
                .eq(ConfiguredModuleField::getOwnerTenantId, tenantId)
                .eq(ConfiguredModuleField::getFieldType, "DEPARTMENT"))) {
            if (defaultReferencesDepartment(field.getConfigJson(), departmentId)) count++;
        }
        return count;
    }

    private long modulePublishedCount(Long systemId, Long tenantId, Long departmentId) {
        long count = 0;
        for (ConfiguredModulePublication publication : modulePublications.selectList(
                Wrappers.<ConfiguredModulePublication>lambdaQuery()
                        .eq(ConfiguredModulePublication::getSystemId, systemId)
                        .eq(ConfiguredModulePublication::getOwnerTenantId, tenantId))) {
            ConfiguredModuleVersion version = moduleVersions.selectById(publication.getCurrentVersionId());
            if (version == null || !publication.getModuleId().equals(version.getModuleId())
                    || !systemId.equals(version.getSystemId())
                    || !tenantId.equals(version.getOwnerTenantId())
                    || publishedFieldsReferenceDepartment(version.getSnapshotJson(), departmentId)) count++;
        }
        return count;
    }

    /** 发布流程的节点策略冻结于版本快照，旧版本仅在仍有在途实例时构成当前引用。 */
    private boolean snapshotReferencesDepartment(String source, Long departmentId) {
        JsonNode snapshot = parse(source);
        if (snapshot == null || !snapshot.isObject() || !snapshot.path("nodes").isArray()) return true;
        for (JsonNode node : snapshot.path("nodes")) {
            if (!node.isObject()) return true;
            JsonNode policy = node.path("assigneePolicy");
            if (!policy.isMissingNode() && !policy.isNull()
                    && (!policy.isObject() || hasDepartmentId(policy, departmentId))) return true;
        }
        return false;
    }

    private boolean publishedFieldsReferenceDepartment(String source, Long departmentId) {
        JsonNode snapshot = parse(source);
        if (snapshot == null || !snapshot.isObject() || !snapshot.path("fields").isArray()) return true;
        for (JsonNode field : snapshot.path("fields")) {
            if (!field.isObject()) return true;
            if ("DEPARTMENT".equals(field.path("fieldType").asText())
                    && defaultReferencesDepartment(field.path("configJson").asText(null), departmentId)) return true;
        }
        return false;
    }

    private boolean defaultReferencesDepartment(String source, Long departmentId) {
        if (source == null || source.isBlank()) return false;
        JsonNode config = parse(source);
        if (config == null || !config.isObject()) return true;
        JsonNode value = config.path("defaultValue");
        if (value.isMissingNode() || value.isNull() || value.isTextual() && value.textValue().isBlank()) return false;
        return !validId(value) || exactId(value, departmentId);
    }

    /** 只识别明确的部门键，避免字符串包含导致 12 与 112 混淆。 */
    private boolean hasDepartmentId(JsonNode node, Long departmentId) {
        if (node == null || node.isNull()) return false;
        if (node.isArray()) {
            for (JsonNode item : node) if (hasDepartmentId(item, departmentId)) return true;
            return false;
        }
        if (!node.isObject()) return false;
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if ("departmentId".equals(entry.getKey()) && !entry.getValue().isNull()
                    && (!validId(entry.getValue()) || exactId(entry.getValue(), departmentId))) return true;
            if ("departmentIds".equals(entry.getKey())) {
                if (!entry.getValue().isArray()) return true;
                for (JsonNode value : entry.getValue()) {
                    if (!validId(value) || exactId(value, departmentId)) return true;
                }
            }
            if (hasDepartmentId(entry.getValue(), departmentId)) return true;
        }
        return false;
    }

    private boolean anyValueMatches(JsonNode object, Long departmentId) {
        var values = object.elements();
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (!validId(value) || exactId(value, departmentId)) return true;
        }
        return false;
    }

    private boolean validId(JsonNode value) {
        if (value == null) return false;
        if (value.isIntegralNumber()) return value.canConvertToLong() && value.longValue() > 0;
        if (!value.isTextual()) return false;
        try {
            return Long.parseLong(value.textValue().strip()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean exactId(JsonNode value, Long departmentId) {
        if (value == null || departmentId == null) return false;
        if (value.isIntegralNumber()) return value.canConvertToLong() && value.longValue() == departmentId;
        if (!value.isTextual()) return false;
        try {
            return Long.parseLong(value.textValue().strip()) == departmentId;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private JsonNode parse(String source) {
        if (source == null || source.isBlank()) return null;
        try {
            return json.readTree(source);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void add(List<SystemAuthorizationModels.DepartmentReference> result,
            String key, String label, long count) {
        if (count > 0) result.add(new SystemAuthorizationModels.DepartmentReference(key, label, count, false));
    }
}
