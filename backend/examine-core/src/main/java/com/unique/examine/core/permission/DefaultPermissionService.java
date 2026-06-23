package com.unique.examine.core.permission;

import java.util.List;
import java.util.Objects;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认统一权限服务。
 */
@Service
@RequiredArgsConstructor
public class DefaultPermissionService implements PermissionService {

    private static final String PERM_DENIED = "PERM_DENIED";

    private static final String SYSTEM_MANAGE_ALL = "SYS_MANAGE_ALL";

    private final List<PermissionSnapshotProvider> providers;

    @Override
    public EffectivePermissionVO currentPermission() {
        RequestContext context = RequestContextHolder.get();
        if (context == null) {
            return EffectivePermissionVO.empty();
        }
        return providers.stream()
                .filter(provider -> provider.supports(context))
                .findFirst()
                .map(provider -> provider.load(context))
                .orElseGet(EffectivePermissionVO::empty);
    }

    @Override
    public PermissionDecision decide(PermissionCheck check) {
        EffectivePermissionVO permission = currentPermission();
        if (permission.getOperations().contains(SYSTEM_MANAGE_ALL)) {
            return PermissionDecision.allow();
        }
        if (StringUtils.hasText(check.getMenuCode()) && !permission.getMenus().contains(check.getMenuCode())) {
            return PermissionDecision.deny(PERM_DENIED, "菜单不可见");
        }
        if (StringUtils.hasText(check.getOperationCode())
                && !permission.getOperations().contains(check.getOperationCode())) {
            return PermissionDecision.deny(PERM_DENIED, "操作无权限");
        }
        if (StringUtils.hasText(check.getOpenApiScope())
                && !permission.getOpenapiScopes().contains(check.getOpenApiScope())) {
            return PermissionDecision.deny(PERM_DENIED, "OpenAPI scope 无权限");
        }
        PermissionDecision fieldDecision = decideField(permission, check);
        if (!fieldDecision.isAllowed()) {
            return fieldDecision;
        }
        return decideDataScope(permission, check);
    }

    @Override
    public void requireOperation(String operationCode) {
        require(PermissionCheck.builder().operationCode(operationCode).build());
    }

    @Override
    public void requireOpenApiScope(String openApiScope) {
        require(PermissionCheck.builder().openApiScope(openApiScope).build());
    }

    @Override
    public void requireFieldWritable(String fieldCode) {
        require(PermissionCheck.builder().fieldCode(fieldCode).fieldAction("WRITABLE").build());
    }

    @Override
    public void requireDataScope(String resourceType, String resourceId, String targetMemberId) {
        require(PermissionCheck.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .targetMemberId(targetMemberId)
                .build());
    }

    private void require(PermissionCheck check) {
        PermissionDecision decision = decide(check);
        if (!decision.isAllowed()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    StringUtils.hasText(decision.getMessage()) ? decision.getMessage()
                            : CommonErrorCode.FORBIDDEN.getMessage());
        }
    }

    private PermissionDecision decideField(EffectivePermissionVO permission, PermissionCheck check) {
        if (!StringUtils.hasText(check.getFieldCode())) {
            return PermissionDecision.allow();
        }
        FieldPermissionVO fieldPermission = permission.getFieldPermissions().get(check.getFieldCode());
        if (fieldPermission == null) {
            return PermissionDecision.deny(PERM_DENIED, "字段不可见");
        }
        String action = StringUtils.hasText(check.getFieldAction()) ? check.getFieldAction() : "VISIBLE";
        boolean allowed = switch (action) {
            case "WRITABLE" -> fieldPermission.isWritable();
            case "EXPORT_PLAIN" -> fieldPermission.isExportPlain();
            case "OPENAPI_READ" -> fieldPermission.isOpenapiReadable();
            case "OPENAPI_WRITE" -> fieldPermission.isOpenapiWritable();
            default -> fieldPermission.isVisible();
        };
        return allowed ? PermissionDecision.allow() : PermissionDecision.deny(PERM_DENIED, "字段无权限");
    }

    private PermissionDecision decideDataScope(EffectivePermissionVO permission, PermissionCheck check) {
        if (!StringUtils.hasText(check.getResourceType()) || !StringUtils.hasText(check.getTargetMemberId())) {
            return PermissionDecision.allow();
        }
        boolean allowed = permission.getDataScopes().stream()
                .filter(rule -> resourceMatches(rule, check))
                .anyMatch(rule -> dataScopeMatches(permission, rule, check));
        return allowed ? PermissionDecision.allow() : PermissionDecision.deny(PERM_DENIED, "数据范围越界");
    }

    private boolean resourceMatches(DataScopeRuleVO rule, PermissionCheck check) {
        if (!Objects.equals(rule.getResourceType(), check.getResourceType())) {
            return false;
        }
        return !StringUtils.hasText(rule.getResourceId()) || "0".equals(rule.getResourceId())
                || Objects.equals(rule.getResourceId(), check.getResourceId());
    }

    private boolean dataScopeMatches(EffectivePermissionVO permission, DataScopeRuleVO rule, PermissionCheck check) {
        return switch (rule.getScopeType()) {
            case "ALL" -> true;
            case "SELF" -> Objects.equals(permission.getMemberId(), check.getTargetMemberId());
            case "CUSTOM" -> rule.getMemberIds() != null && rule.getMemberIds().contains(check.getTargetMemberId());
            default -> Objects.equals(permission.getMemberId(), check.getTargetMemberId());
        };
    }
}
