package com.unique.examine.app.manage.permission;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.app.base.entity.ClientScope;
import com.unique.examine.app.base.service.IClientScopeService;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.permission.DataScopeRuleVO;
import com.unique.examine.core.permission.EffectivePermissionVO;
import com.unique.examine.core.permission.FieldPermissionVO;
import com.unique.examine.core.permission.PermissionSnapshotProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAPI 客户端权限快照提供器。
 *
 * <p>外部应用通过 AK/SK 和 scope 完成认证授权后，仍会复用运行台内部服务。
 * 该提供器把 OpenAPI scope 转换成统一权限快照，避免外部请求绕过运行台的操作权限检查。</p>
 */
@Component
@RequiredArgsConstructor
public class OpenApiPermissionSnapshotProvider implements PermissionSnapshotProvider {

    private static final String ENABLED = "ENABLED";

    private static final String MODULE_RESOURCE = "MODULE";

    private final IClientScopeService clientScopeService;

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(RequestContext context) {
        return StringUtils.hasText(context.getClientId()) && StringUtils.hasText(context.getSystemId());
    }

    @Override
    public EffectivePermissionVO load(RequestContext context) {
        Long clientId = Long.valueOf(context.getClientId());
        Long systemId = Long.valueOf(context.getSystemId());
        List<ClientScope> scopes = clientScopeService.lambdaQuery()
                .eq(ClientScope::getClientId, clientId)
                .eq(ClientScope::getSystemId, systemId)
                .eq(ClientScope::getStatus, ENABLED)
                .list();
        return EffectivePermissionVO.builder()
                .systemId(context.getSystemId())
                .tenantId(context.getTenantId())
                .memberId(context.getMemberId())
                .roles(Set.of())
                .menus(Set.of())
                .operations(operationCodes(scopes))
                .openapiScopes(scopes.stream()
                        .map(ClientScope::getScopeCode)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .fieldPermissions(fieldPermissions(scopes))
                .dataScopes(dataScopes(scopes))
                .version("openapi-" + clientId)
                .build();
    }

    private Set<String> operationCodes(List<ClientScope> scopes) {
        Set<String> operations = new LinkedHashSet<>();
        for (ClientScope scope : scopes) {
            switch (scope.getScopeCode()) {
                case "record:read" -> {
                    operations.add("RECORD_VIEW");
                    operations.add("RECORD_HISTORY_VIEW");
                }
                case "record:create" -> operations.add("RECORD_CREATE");
                case "record:update" -> operations.add("RECORD_EDIT");
                case "record:submit" -> operations.add("RECORD_SUBMIT");
                case "file:download" -> operations.add("FILE_DOWNLOAD");
                case "flow:task:handle" -> operations.add("FLOW_TASK_HANDLE");
                default -> {
                    // 未知 scope 只保留在 openapiScopes 中，不自动映射运行台操作权限。
                }
            }
        }
        return operations;
    }

    private Map<String, FieldPermissionVO> fieldPermissions(List<ClientScope> scopes) {
        Map<String, FieldPermissionVO> permissions = new LinkedHashMap<>();
        for (ClientScope scope : scopes) {
            JsonNode root = readJson(scope.getFieldPermissionJson());
            Set<String> readable = readStringSet(root, "readableFieldCodes");
            Set<String> writable = readStringSet(root, "writableFieldCodes");
            for (String fieldCode : readable) {
                permissions.merge(fieldCode, fieldPermission(fieldCode, true, false), this::merge);
            }
            for (String fieldCode : writable) {
                permissions.merge(fieldCode, fieldPermission(fieldCode, true, true), this::merge);
            }
        }
        return permissions;
    }

    private FieldPermissionVO fieldPermission(String fieldCode, boolean readable, boolean writable) {
        return FieldPermissionVO.builder()
                .fieldCode(fieldCode)
                .visible(readable || writable)
                .writable(writable)
                .exportPlain(false)
                .openapiReadable(readable)
                .openapiWritable(writable)
                .build();
    }

    private FieldPermissionVO merge(FieldPermissionVO left, FieldPermissionVO right) {
        return FieldPermissionVO.builder()
                .fieldCode(left.getFieldCode())
                .visible(left.isVisible() || right.isVisible())
                .writable(left.isWritable() || right.isWritable())
                .exportPlain(left.isExportPlain() || right.isExportPlain())
                .openapiReadable(left.isOpenapiReadable() || right.isOpenapiReadable())
                .openapiWritable(left.isOpenapiWritable() || right.isOpenapiWritable())
                .build();
    }

    private List<DataScopeRuleVO> dataScopes(List<ClientScope> scopes) {
        return scopes.stream()
                .map(scope -> {
                    JsonNode root = readJson(scope.getDataScopeJson());
                    return DataScopeRuleVO.builder()
                            .resourceType(MODULE_RESOURCE)
                            .resourceId(scope.getModuleId() == null ? "0" : String.valueOf(scope.getModuleId()))
                            .scopeType(text(root, "scopeType", "ALL"))
                            .deptIds(readStringSet(root, "deptIds"))
                            .memberIds(readStringSet(root, "memberIds"))
                            .customConditions(text(root, "customConditions", null))
                            .minVisibleRule(text(root, "minVisibleRule", null))
                            .build();
                })
                .toList();
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private Set<String> readStringSet(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        node.forEach(item -> {
            if (StringUtils.hasText(item.asText())) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private String text(JsonNode root, String fieldName, String defaultValue) {
        JsonNode node = root.get(fieldName);
        return node == null || !StringUtils.hasText(node.asText()) ? defaultValue : node.asText();
    }
}
