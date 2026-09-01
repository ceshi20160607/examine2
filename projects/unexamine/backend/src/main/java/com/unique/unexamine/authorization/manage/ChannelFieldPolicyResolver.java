package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.unexamine.authentication.base.entity.AuthorizationFieldPolicy;
import com.unique.unexamine.authentication.base.service.AuthorizationFieldPolicyBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChannelFieldPolicyResolver {
    private static final Set<String> CHANNELS = Set.of("PAGE", "APPLICATION", "FILE");
    private final SystemRolePermissionBaseService permissionService;
    private final AuthorizationFieldPolicyBaseService fieldPolicyService;

    public ChannelFieldPolicyResolver(SystemRolePermissionBaseService permissionService,
                                      AuthorizationFieldPolicyBaseService fieldPolicyService) {
        this.permissionService = permissionService;
        this.fieldPolicyService = fieldPolicyService;
    }

    public Map<String, FieldAccessDecision> resolve(AuthenticatedContext context, String resourceCode,
                                                     String actionCode, String channel, List<String> fieldCodes) {
        String normalizedChannel = normalizeChannel(channel);
        List<SystemRolePermission> grants = context.roleIds().isEmpty() ? List.of() : permissionService.selectList(
                        Wrappers.<SystemRolePermission>lambdaQuery()
                                .eq(SystemRolePermission::getSystemId, context.systemId())
                                .eq(SystemRolePermission::getTenantId, context.tenantId())
                                .in(SystemRolePermission::getRoleId, context.roleIds())
                                .eq(SystemRolePermission::getEffect, "ALLOW"))
                .stream().filter(permission -> matches(permission.getResourceType(), "MODULE")
                        && matches(permission.getResourceCode(), resourceCode)
                        && matches(permission.getActionCode(), actionCode)).toList();
        List<Long> contributingRoles = grants.stream().map(SystemRolePermission::getRoleId)
                .distinct().sorted().toList();
        List<AuthorizationFieldPolicy> policies = contributingRoles.isEmpty() ? List.of() : fieldPolicyService.selectList(
                Wrappers.<AuthorizationFieldPolicy>lambdaQuery()
                        .eq(AuthorizationFieldPolicy::getContextType, "SYSTEM")
                        .eq(AuthorizationFieldPolicy::getSystemId, context.systemId())
                        .eq(AuthorizationFieldPolicy::getTenantId, context.tenantId())
                        .in(AuthorizationFieldPolicy::getRoleId, contributingRoles)
                        .eq(AuthorizationFieldPolicy::getChannel, normalizedChannel));

        Map<String, FieldAccessDecision> result = new LinkedHashMap<>();
        for (String fieldCode : fieldCodes.stream().distinct().toList()) {
            boolean readable = !contributingRoles.isEmpty();
            boolean writable = !contributingRoles.isEmpty();
            List<String> masks = new ArrayList<>();
            for (Long roleId : contributingRoles) {
                List<AuthorizationFieldPolicy> rolePolicies = policies.stream()
                        .filter(policy -> roleId.equals(policy.getRoleId())
                                && matches(policy.getResourceCode(), resourceCode)
                                && matches(policy.getFieldCode(), fieldCode))
                        .toList();
                if (rolePolicies.isEmpty()) {
                    boolean unrestrictedWildcard = grants.stream().anyMatch(grant -> roleId.equals(grant.getRoleId())
                            && "*".equals(grant.getResourceType()) && "*".equals(grant.getResourceCode())
                            && "*".equals(grant.getActionCode()));
                    readable &= unrestrictedWildcard;
                    writable &= unrestrictedWildcard;
                    continue;
                }
                readable &= rolePolicies.stream().allMatch(policy -> Boolean.TRUE.equals(policy.getReadable()));
                writable &= rolePolicies.stream().allMatch(policy -> Boolean.TRUE.equals(policy.getWritable()));
                rolePolicies.stream().map(AuthorizationFieldPolicy::getMaskStrategy)
                        .filter(value -> value != null && !value.isBlank()).forEach(masks::add);
            }
            String mask = strongestMask(masks);
            String reason = contributingRoles.isEmpty()
                    ? "没有角色同时授予资源动作，字段默认拒绝"
                    : readable
                    ? (mask == null ? "字段通过所有贡献角色与" + normalizedChannel + "渠道硬限制"
                    : "字段可读但按" + normalizedChannel + "渠道执行" + mask + "脱敏")
                    : "至少一个贡献角色未授予该字段的" + normalizedChannel + "渠道读取权限";
            result.put(fieldCode, new FieldAccessDecision(fieldCode, normalizedChannel, readable,
                    readable && writable, mask, contributingRoles, reason));
        }
        return result;
    }

    public Map<String, FieldAccessDecision> resolveIntersection(AuthenticatedContext context, String resourceCode,
                                                                 String actionCode, List<String> channels,
                                                                 List<String> fieldCodes) {
        List<Map<String, FieldAccessDecision>> channelDecisions = channels.stream()
                .map(channel -> resolve(context, resourceCode, actionCode, channel, fieldCodes)).toList();
        Map<String, FieldAccessDecision> result = new LinkedHashMap<>();
        for (String fieldCode : fieldCodes.stream().distinct().toList()) {
            List<FieldAccessDecision> decisions = channelDecisions.stream().map(item -> item.get(fieldCode)).toList();
            boolean readable = decisions.stream().allMatch(FieldAccessDecision::readable);
            boolean writable = decisions.stream().allMatch(FieldAccessDecision::writable);
            String mask = strongestMask(decisions.stream().map(FieldAccessDecision::maskStrategy)
                    .filter(value -> value != null && !value.isBlank()).toList());
            List<Long> roles = decisions.stream().flatMap(decision -> decision.contributingRoleIds().stream())
                    .distinct().sorted().toList();
            String channelLabel = decisions.stream().map(FieldAccessDecision::channel).distinct()
                    .reduce((left, right) -> left + "∩" + right).orElse("PAGE");
            result.put(fieldCode, new FieldAccessDecision(fieldCode, channelLabel, readable,
                    readable && writable, mask, roles,
                    readable ? "字段通过" + channelLabel + "全部渠道硬限制，任何渠道均未扩大主体权限"
                            : "字段未通过" + channelLabel + "全部渠道硬限制，按默认拒绝隐藏"));
        }
        return result;
    }

    public boolean isSensitive(AuthenticatedContext context, String resourceCode, String fieldCode) {
        return fieldPolicyService.selectList(Wrappers.<AuthorizationFieldPolicy>lambdaQuery()
                        .eq(AuthorizationFieldPolicy::getContextType, "SYSTEM")
                        .eq(AuthorizationFieldPolicy::getSystemId, context.systemId())
                        .eq(AuthorizationFieldPolicy::getTenantId, context.tenantId())
                        .eq(AuthorizationFieldPolicy::getResourceCode, resourceCode)
                        .eq(AuthorizationFieldPolicy::getFieldCode, fieldCode))
                .stream().anyMatch(policy -> policy.getMaskStrategy() != null && !policy.getMaskStrategy().isBlank());
    }

    public JsonNode protect(JsonNode value, FieldAccessDecision decision) {
        if (!decision.readable()) {
            return null;
        }
        if (value == null || decision.maskStrategy() == null || decision.maskStrategy().isBlank()) {
            return value;
        }
        String original = value.isTextual() ? value.textValue() : value.toString();
        return TextNode.valueOf(mask(original, decision.maskStrategy()));
    }

    private String normalizeChannel(String channel) {
        String value = channel == null ? "" : channel.strip().toUpperCase();
        if (!CHANNELS.contains(value)) {
            throw new DomainException("AUTHORIZATION_CHANNEL_INVALID", "渠道仅支持 PAGE、APPLICATION 或 FILE",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return value;
    }

    private String strongestMask(List<String> source) {
        return source.stream().map(value -> value.strip().toUpperCase())
                .max(Comparator.comparingInt(this::maskPriority)).orElse(null);
    }

    private int maskPriority(String strategy) {
        return switch (strategy) {
            case "FULL", "MASK", "REDACT" -> 4;
            case "HASH" -> 3;
            case "PARTIAL" -> 2;
            case "LAST4" -> 1;
            default -> 4;
        };
    }

    private String mask(String value, String strategy) {
        return switch (strategy.strip().toUpperCase()) {
            case "PARTIAL" -> value.length() <= 2 ? "*".repeat(value.length())
                    : value.substring(0, 1) + "***" + value.substring(value.length() - 1);
            case "LAST4" -> "***" + value.substring(Math.max(0, value.length() - 4));
            case "HASH" -> "[已散列]";
            default -> "***";
        };
    }

    private boolean matches(String granted, String required) {
        return "*".equals(granted) || required.equals(granted);
    }
}
