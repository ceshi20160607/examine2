package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.moduleconfig.manage.*;
import com.unique.unexamine.runtimedata.base.entity.BizTenantShare;
import com.unique.unexamine.runtimedata.base.service.BizTenantShareBaseService;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.unique.unexamine.runtimedata.manage.TenantRangeShareModels.*;

/** 来源管理模块范围授权；业务读取和执行仍由原公共运行入口处理。 */
@Service
public class TenantRangeSharingService {
    private final BizTenantShareBaseService shares;
    private final SystemTenantBaseService tenants;
    private final ModulePublicationService publications;
    private final SharedSourceMetadataService metadata;
    private final TenantShareScopeService scope;
    private final RuntimeDataService runtime;
    private final PermissionChecker permissions;
    private final ChannelFieldPolicyResolver fields;
    private final DictionaryConfigurationService dictionaries;
    private final ObjectMapper json;
    private final AuditRecorder audit;

    public TenantRangeSharingService(BizTenantShareBaseService shares, SystemTenantBaseService tenants,
            ModulePublicationService publications, SharedSourceMetadataService metadata, TenantShareScopeService scope,
            RuntimeDataService runtime, PermissionChecker permissions, ChannelFieldPolicyResolver fields,
            DictionaryConfigurationService dictionaries, ObjectMapper json, AuditRecorder audit) {
        this.shares = shares; this.tenants = tenants; this.publications = publications;this.metadata = metadata;
        this.scope = scope; this.runtime = runtime; this.permissions = permissions; this.fields = fields;
        this.dictionaries = dictionaries; this.json = json; this.audit = audit;
    }

    /** 有外来共享目录不代表拥有来源配置，更不能以它再次共享。 */
    private RuntimeModuleConfiguration source(AuthenticatedContext actor, String code) {
        if (!permissions.allows(actor, "MODULE", code, "SHARE")) throw new DomainException("PERMISSION_DENIED", "没有共享此模块的权限", HttpStatus.FORBIDDEN);
        var access = metadata.requireRuntimeAccess(actor, code);
        if (!access.currentConfiguration()) throw new DomainException("SHARE_SOURCE_REQUIRED", "只能共享本租户拥有的数据", HttpStatus.FORBIDDEN);
        RuntimeModuleConfiguration publication = publications.published(actor, code);
        if (!access.module().getId().equals(publication.moduleId())) throw new DomainException("SHARE_CONFIGURATION_CONFLICT", "模块来源配置不一致", HttpStatus.CONFLICT);
        return publication;
    }

    @Transactional(readOnly = true)
    public List<ConditionField> conditionFields(AuthenticatedContext actor, String code) { return conditionFields(actor, code, source(actor, code)); }

    private List<ConditionField> conditionFields(AuthenticatedContext actor, String code, RuntimeModuleConfiguration publication) {
        List<String> codes = new ArrayList<>();publication.configuration().path("fields").forEach(f -> codes.add(f.path("code").asText()));
        var policies = fields.resolveIntersection(actor, code, "SHARE", List.of("PAGE"), codes);
        List<ConditionField> result = new ArrayList<>();
        for (JsonNode field : publication.configuration().path("fields")) {
            String type = field.path("fieldType").asText(); var decision = policies.get(field.path("code").asText());
            if (!TenantShareScopeService.CONDITION_TYPES.contains(type) || decision == null || !decision.readable()
                    || (decision.maskStrategy() != null && !decision.maskStrategy().isBlank())) continue;
            var config = scope.read(field.path("configJson").asText("{}"));var safe = json.createObjectNode();
            if (config.has("options")) safe.set("options", config.path("options"));
            if (Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS").contains(type) && field.path("dictionaryId").isIntegralNumber()) {
                var options = safe.putArray("options");
                dictionaries.publishedDisplayItems(actor, field.path("dictionaryId").longValue()).stream()
                        .filter(item -> "ACTIVE".equals(item.path("status").asText())).forEach(item -> options.addObject()
                            .put("value", item.path("code").asText()).put("label", item.path("label").asText()));
            }
            result.add(new ConditionField(field.path("code").asText(), field.path("name").asText(), type, safe.toString()));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<View> list(AuthenticatedContext actor, String code) {
        var publication = source(actor, code);
        return shares.selectList(Wrappers.<BizTenantShare>lambdaQuery().eq(BizTenantShare::getSystemId, actor.systemId())
                .eq(BizTenantShare::getSourceTenantId, actor.tenantId()).eq(BizTenantShare::getModuleId, publication.moduleId())
                .eq(BizTenantShare::getScopeType, "DATA_RANGE").orderByDesc(BizTenantShare::getGrantTime, BizTenantShare::getId))
                .stream().filter(share -> canManage(actor, code, share)).map(share -> view(actor, code, share)).toList();
    }

    /** 授予或修改范围时冻结来源 SHARE 数据权限上限，未来记录也不能越过此上限。 */
    @Transactional
    public View grant(AuthenticatedContext actor, String code, Grant request, String trace) {
        var publication = source(actor, code);var conditionFields = conditionFields(actor, code, publication);
        scope.validate(request.condition(), conditionFields);
        JsonNode condition = scope.normalizeCondition(request.condition(), conditionFields);
        JsonNode sourceScope = runtime.sourceShareScopeSnapshot(actor, code);
        if (sourceScope.isEmpty()) throw new DomainException("PERMISSION_DENIED", "没有可共享的数据范围", HttpStatus.FORBIDDEN);
        SystemTenant target = tenants.selectById(request.targetTenantId());
        if (target == null || !actor.systemId().equals(target.getSystemId()) || actor.tenantId().equals(target.getId()) || !"ACTIVE".equals(target.getStatus()))
            throw new DomainException("SHARE_TARGET_INVALID", "共享目标必须是同一系统内的其他启用租户", HttpStatus.UNPROCESSABLE_ENTITY);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (request.expireTime() != null && (!request.expireTime().isAfter(now)
                || (request.startTime() != null && !request.expireTime().isAfter(request.startTime()))))
            throw new DomainException("SHARE_EXPIRY_INVALID", "失效时间必须晚于当前时间及开始时间", HttpStatus.UNPROCESSABLE_ENTITY);
        Set<String> published = new HashSet<>();publication.configuration().path("actions").forEach(a -> {
            if ("ACTIVE".equals(a.path("status").asText())) published.add(a.path("code").asText().toUpperCase(Locale.ROOT));
        });
        Set<String> allowed = new LinkedHashSet<>(List.of("LIST", "DETAIL"));
        if (request.allowedActions() != null) request.allowedActions().stream().filter(Objects::nonNull).map(String::strip).map(a -> a.toUpperCase(Locale.ROOT)).forEach(allowed::add);
        // 共享只扩充可用动作的数据范围；转化必须由来源租户在本租户内执行。
        if (allowed.stream().anyMatch(a -> !published.contains(a) || Set.of("CREATE", "SHARE", "RESTORE", "CONVERT").contains(a)))
            throw new DomainException("SHARE_ACTION_INVALID", "共享动作必须已发布且不能新建、转化或再次共享", HttpStatus.UNPROCESSABLE_ENTITY);
        BizTenantShare share = request.id() == null ? new BizTenantShare() : owned(actor, code, publication.moduleId(), request.id());
        if (share.getId() != null && !Objects.equals(request.version(), share.getVersion())) throw conflict();
        share.setSystemId(actor.systemId());share.setModuleId(publication.moduleId());share.setSourceTenantId(actor.tenantId());
        share.setTargetTenantId(target.getId());share.setScopeType("DATA_RANGE");share.setRecordId(null);
        var snapshot = json.createObjectNode();snapshot.put("name", request.name().strip());snapshot.set("condition", condition);
        snapshot.set("fieldBindings", scope.bindings(condition, publication));snapshot.set("dictionaryBindings", scope.dictionaryBindings(condition, publication));
        snapshot.set("sourceShareScope", sourceScope);
        share.setScopeJson(snapshot.toString());share.setPermissionJson(json.valueToTree(Map.of("moduleCode", code, "allowedActions", allowed,
                "grantorRoleIds", actor.roleIds(), "grantorAccountId", actor.accountId())).toString());
        share.setStatus("ACTIVE");share.setStartTime(request.startTime());share.setExpireTime(request.expireTime());
        share.setGrantedByMemberId(actor.memberId());share.setGrantTime(now);share.setRevokeTime(null);
        if (share.getId() == null) { share.setVersion(0);shares.insert(share); }
        else {
            if (shares.updateById(share) != 1) throw conflict();
            // 条件更新明确写入空时间，保留原授权主键、使用历史和上一笔 Base 更新的版本约束。
            if (shares.update(Wrappers.<BizTenantShare>lambdaUpdate()
                    .eq(BizTenantShare::getId, share.getId())
                    .eq(BizTenantShare::getVersion, share.getVersion())
                    .set(BizTenantShare::getStartTime, request.startTime())
                    .set(BizTenantShare::getExpireTime, request.expireTime())
                    .set(BizTenantShare::getRevokeTime, null)) != 1) throw conflict();
        }
        recordAudit(actor, code, share, "BUSINESS_MODULE_RANGE_SHARED", trace, "");
        return view(actor, code, shares.selectById(share.getId()));
    }

    @Transactional
    public View revoke(AuthenticatedContext actor, String code, Long id, RevokeTenantShareRequest request, String trace) {
        var publication = source(actor, code);var share = owned(actor, code, publication.moduleId(), id);
        if (!Objects.equals(request.version(), share.getVersion())) throw conflict();
        if ("REVOKED".equals(share.getStatus())) throw new DomainException("SHARE_NOT_ACTIVE", "该范围授权已撤销", HttpStatus.CONFLICT);
        share.setStatus("REVOKED");share.setRevokeTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        if (shares.updateById(share) != 1) throw conflict();
        recordAudit(actor, code, share, "BUSINESS_MODULE_RANGE_SHARE_REVOKED", trace, request.reason().strip());
        return view(actor, code, shares.selectById(id));
    }

    private BizTenantShare owned(AuthenticatedContext actor, String code, Long moduleId, Long id) {
        var share = shares.selectById(id);
        if (share == null || !actor.systemId().equals(share.getSystemId()) || !actor.tenantId().equals(share.getSourceTenantId())
                || !moduleId.equals(share.getModuleId()) || !"DATA_RANGE".equals(share.getScopeType()))
            throw new DomainException("SHARE_NOT_FOUND", "范围共享不存在", HttpStatus.NOT_FOUND);
        if (!canManage(actor, code, share)) throw new DomainException("PERMISSION_DENIED", "只能管理自己授予的范围授权", HttpStatus.FORBIDDEN);
        return share;
    }
    /** 受限共享者不能撤销或修改其他人授予的整个范围；拥有全来源范围权限者可统一管理。 */
    private boolean canManage(AuthenticatedContext actor, String code, BizTenantShare share) {
        if (Objects.equals(actor.memberId(), share.getGrantedByMemberId())) return true;
        for (JsonNode clause : runtime.sourceShareScopeSnapshot(actor, code)) if ("ALL".equals(clause.path("type").asText())) return true;
        return false;
    }
    private View view(AuthenticatedContext actor, String code, BizTenantShare share) {
        String status = scope.status(share);if (Set.of("ACTIVE", "SCHEDULED").contains(status) && !scope.validRange(actor, share, code)) status = "INVALID";
        List<String> actions = new ArrayList<>();scope.read(share.getPermissionJson()).path("allowedActions").forEach(a -> actions.add(a.asText()));
        JsonNode config = scope.read(share.getScopeJson());
        Set<String> bound = new HashSet<>();config.path("fieldBindings").fieldNames().forEachRemaining(bound::add);
        var currentPolicies = fields.resolveIntersection(actor, code, "SHARE", List.of("PAGE"), List.copyOf(bound));
        Set<String> protectedFields = new HashSet<>();
        currentPolicies.forEach((field, decision) -> {
            if (!decision.readable() || (decision.maskStrategy() != null && !decision.maskStrategy().isBlank())) protectedFields.add(field);
        });
        publications.published(actor, code).configuration().path("fields").forEach(field -> {
            if ("SECRET".equals(field.path("fieldType").asText())) protectedFields.add(field.path("code").asText());
        });
        JsonNode visibleCondition = config.path("condition").deepCopy();protectCondition(visibleCondition, protectedFields);
        return new View(share.getId(), config.path("name").asText(), share.getSourceTenantId(), tenantName(share.getSourceTenantId()),
                share.getTargetTenantId(), tenantName(share.getTargetTenantId()), code, actions, visibleCondition, status,
                share.getStartTime(), share.getExpireTime(), share.getGrantTime(), share.getRevokeTime(), share.getGrantedByMemberId(), share.getVersion());
    }
    /** 历史条件仍保存在授权中，但来源当前失去字段读取权时不能通过授权列表读回比较值。 */
    private void protectCondition(JsonNode condition, Set<String> protectedFields) {
        if (condition.has("conditions")) condition.path("conditions").forEach(child -> protectCondition(child, protectedFields));
        else if (protectedFields.contains(condition.path("field").asText())) ((com.fasterxml.jackson.databind.node.ObjectNode) condition).remove("value");
    }
    private String tenantName(Long id) { var tenant = tenants.selectById(id);return tenant == null ? "不可用租户" : tenant.getName(); }
    private DomainException conflict() { return new DomainException("SHARE_VERSION_CONFLICT", "范围授权已修改，请刷新后重试", HttpStatus.CONFLICT); }
    private void recordAudit(AuthenticatedContext actor, String code, BizTenantShare share, String event, String trace, String reason) {
        audit.recordWithPermissionSnapshot(trace, actor.accountId(), actor.systemId(), actor.tenantId(), actor.memberId(), event,
                "TENANT_SHARE", share.getId().toString(), "SUCCESS", Map.of("roleIds", actor.roleIds(), "resource", "MODULE:" + code, "action", "SHARE"),
                Map.of("moduleCode", code, "sourceTenantId", share.getSourceTenantId(), "targetTenantId", share.getTargetTenantId(),
                        "scopeType", "DATA_RANGE", "scopeSnapshot", scope.read(share.getScopeJson()), "reason", reason));
    }
}
