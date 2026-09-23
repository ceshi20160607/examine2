package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.runtimedata.base.entity.BizTenantShare;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.service.BizTenantShareBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.manage.TenantShareScopeService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 当前有效共享提供的来源配置索引；仅供公共权限目录使用，不开放来源任意配置查询。 */
@Service
public class SharedSourceMetadataService {
    private final BizTenantShareBaseService shareService;
    private final BusinessRecordBaseService recordService;
    private final SystemTenantBaseService tenantService;
    private final ModulePublicationService publicationService;
    private final ObjectMapper objectMapper;
    private final ConfiguredModuleBaseService moduleService;
    private final ModuleIdentifierIntegrity identifierIntegrity;
    private final TenantShareScopeService shareScope;

    public SharedSourceMetadataService(BizTenantShareBaseService shareService, BusinessRecordBaseService recordService,
                                       SystemTenantBaseService tenantService, ModulePublicationService publicationService,
                                       ObjectMapper objectMapper, ConfiguredModuleBaseService moduleService,
                                       ModuleIdentifierIntegrity identifierIntegrity, TenantShareScopeService shareScope) {
        this.shareService = shareService;
        this.recordService = recordService;
        this.tenantService = tenantService;
        this.publicationService = publicationService;
        this.objectMapper = objectMapper;
        this.moduleService = moduleService;
        this.identifierIntegrity = identifierIntegrity;
        this.shareScope = shareScope;
    }

    /** 从真实有效共享发现模块，候选物理表查询不向调用者开放其他租户配置。 */
    @Transactional(readOnly = true)
    public Map<Long, List<SourcePublication>> current(AuthenticatedContext actor) {
        return current(actor, moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, actor.systemId()).eq(ConfiguredModule::getStatus, "ACTIVE")));
    }

    /** 仅返回已被有效共享索引确认的同系统模块实体。 */
    public List<ConfiguredModule> modules(AuthenticatedContext actor, Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, actor.systemId()).in(ConfiguredModule::getId, ids)
                .eq(ConfiguredModule::getStatus, "ACTIVE").orderByAsc(ConfiguredModule::getId));
    }

    /** 判断是否有本租户/主租户配置；共享私有模块不冒充接收方配置。 */
    public boolean hasCurrentConfiguration(AuthenticatedContext actor, ConfiguredModule module) {
        if (actor.tenantId().equals(module.getOwnerTenantId())) return true;
        return !tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                .eq(SystemTenant::getSystemId, actor.systemId()).eq(SystemTenant::getId, module.getOwnerTenantId())
                .eq(SystemTenant::getMain, true).eq(SystemTenant::getStatus, "ACTIVE")).isEmpty();
    }

    /** 列表及常用视图入口需要可信模块身份；私有模块必须仍存在有效共享。 */
    @Transactional(readOnly = true)
    public RuntimeAccess requireRuntimeAccess(AuthenticatedContext actor, String moduleCode) {
        String code = moduleCode == null ? "" : moduleCode.strip().toLowerCase(Locale.ROOT);
        ConfiguredModule module = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, actor.systemId()).eq(ConfiguredModule::getCode, code)
                .eq(ConfiguredModule::getStatus, "ACTIVE")).stream().findFirst().orElse(null);
        if (module == null) throw new DomainException("SHARED_MODULE_NOT_AVAILABLE", "模块不存在或访问关系已失效", HttpStatus.NOT_FOUND);
        identifierIntegrity.requireUnambiguous(module);
        boolean current = hasCurrentConfiguration(actor, module);
        if (!current && current(actor, List.of(module)).getOrDefault(module.getId(), List.of()).isEmpty()) {
            throw new DomainException("SHARED_MODULE_NOT_AVAILABLE", "模块不存在或访问关系已失效", HttpStatus.NOT_FOUND);
        }
        return new RuntimeAccess(module, current);
    }

    /** 停用来源或接收租户立即失去共享访问，不借用来源成员身份。 */
    public boolean activeTenantPair(AuthenticatedContext actor, Long sourceTenantId) {
        Set<Long> ids = new LinkedHashSet<>(List.of(actor.tenantId(), sourceTenantId));
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                .eq(SystemTenant::getSystemId, actor.systemId()).in(SystemTenant::getId, ids)
                .eq(SystemTenant::getStatus, "ACTIVE")).size() == ids.size();
    }

    /** 批量绑定真实共享记录、同系统启用租户和指定模块，避免逐字段重复扫描共享关系。 */
    @Transactional(readOnly = true)
    public Map<Long, List<SourcePublication>> current(AuthenticatedContext actor, List<ConfiguredModule> modules) {
        Map<Long, ConfiguredModule> activeModules = modules.stream().filter(m -> "ACTIVE".equals(m.getStatus()))
                .filter(m -> actor.systemId().equals(m.getSystemId()))
                .collect(Collectors.toMap(ConfiguredModule::getId, Function.identity()));
        if (activeModules.isEmpty()) return Map.of();
        List<BizTenantShare> shares = shareService.selectList(Wrappers.<BizTenantShare>lambdaQuery()
                        .eq(BizTenantShare::getSystemId, actor.systemId()).eq(BizTenantShare::getTargetTenantId, actor.tenantId())
                        .eq(BizTenantShare::getStatus, "ACTIVE"))
                .stream().filter(s -> !actor.tenantId().equals(s.getSourceTenantId()))
                .filter(s -> "ACTIVE".equals(shareScope.status(s))).toList();
        if (shares.isEmpty()) return Map.of();
        List<Long> recordIds = shares.stream().map(BizTenantShare::getRecordId).filter(java.util.Objects::nonNull).toList();
        Map<Long, BusinessRecord> records = recordIds.isEmpty() ? Map.of() : recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                        .eq(BusinessRecord::getSystemId, actor.systemId())
                        .in(BusinessRecord::getId, recordIds)
                        .in(BusinessRecord::getModuleId, activeModules.keySet()))
                .stream().collect(Collectors.toMap(BusinessRecord::getId, Function.identity()));
        Set<Long> tenantIds = shares.stream().map(BizTenantShare::getSourceTenantId).collect(Collectors.toSet());
        tenantIds.add(actor.tenantId());
        Set<Long> activeTenants = tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, actor.systemId()).in(SystemTenant::getId, tenantIds)
                        .eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().map(SystemTenant::getId).collect(Collectors.toSet());
        if (!activeTenants.contains(actor.tenantId())) return Map.of();
        Map<SourceKey, Set<String>> grants = new LinkedHashMap<>();
        for (BizTenantShare share : shares) {
            ConfiguredModule module = activeModules.get(share.getModuleId());
            if (module == null || !activeTenants.contains(share.getSourceTenantId())) continue;
            if ("DATA_RANGE".equals(share.getScopeType())) {
                // 没有匹配记录时仍保留范围目录，后续新记录按当前条件加入，绝不复制数据。
                if (!shareScope.validRange(actor, share, module.getCode())) continue;
            } else {
                BusinessRecord record = records.get(share.getRecordId());
                if (!"RECORD".equals(share.getScopeType()) || record == null
                        || !record.getModuleId().equals(module.getId())
                        || !record.getTenantId().equals(share.getSourceTenantId())) continue;
            }
            // 归档/回收并不等于撤销共享；读历史所需目录仍保留，写动作另受记录生命周期约束。
            Set<String> allowed = allowedActions(share);
            if (!allowed.contains("LIST")) continue;
            grants.computeIfAbsent(new SourceKey(module.getId(), share.getSourceTenantId()), k -> new LinkedHashSet<>())
                    .addAll(allowed);
        }
        Map<Long, List<SourcePublication>> result = new LinkedHashMap<>();
        for (Map.Entry<SourceKey, Set<String>> entry : grants.entrySet()) {
            SourceKey key = entry.getKey();
            ConfiguredModule module = activeModules.get(key.moduleId());
            identifierIntegrity.requireUnambiguous(module);
            // 只切换经过真实记录确认的数据租户；操作者身份、角色与范围保持原值。
            AuthenticatedContext dataContext = new AuthenticatedContext(actor.sessionId(), actor.accountId(), actor.platformId(),
                    actor.systemId(), key.tenantId(), actor.memberId(), actor.tenantMemberId(), actor.username(),
                    actor.displayName(), actor.mfaLevel(), actor.roleIds(), actor.permissions(), actor.dataScopes());
            RuntimeModuleConfiguration publication = publicationService.published(dataContext, module.getCode());
            if (!key.moduleId().equals(publication.moduleId())) {
                throw new DomainException("SHARE_CONFIGURATION_CONFLICT", "共享记录与来源发布模块不一致", HttpStatus.CONFLICT);
            }
            Set<String> publishedActions = new LinkedHashSet<>();
            publication.configuration().path("actions").forEach(action -> {
                if ("ACTIVE".equals(action.path("status").asText())) publishedActions.add(action.path("code").asText());
            });
            Set<String> allowed = new LinkedHashSet<>(entry.getValue());
            allowed.retainAll(publishedActions);
            allowed.removeAll(Set.of("CREATE", "RESTORE", "SHARE"));
            result.computeIfAbsent(key.moduleId(), k -> new ArrayList<>())
                    .add(new SourcePublication(key.tenantId(), publication, Set.copyOf(allowed)));
        }
        return result;
    }

    /** 授权目录只消费明确动作编码，损坏授权不扩大为所有动作。 */
    private Set<String> allowedActions(BizTenantShare share) {
        try {
            JsonNode actions = objectMapper.readTree(share.getPermissionJson()).path("allowedActions");
            Set<String> result = new LinkedHashSet<>();
            if (actions.isArray()) actions.forEach(a -> { if (a.isTextual()) result.add(a.asText()); });
            return result;
        } catch (JsonProcessingException exception) {
            throw new DomainException("SHARE_CONFIGURATION_INVALID", "共享授权配置无效，请由来源管理员检查", HttpStatus.CONFLICT);
        }
    }

    /** 来源快照和共享允许的动作均由服务端关系推导，不接受客户端来源租户参数。 */
    public record SourcePublication(Long sourceTenantId, RuntimeModuleConfiguration configuration, Set<String> allowedActions) {
    }

    /** 当前配置模式只由可信模块归属和共享关系推导。 */
    public record RuntimeAccess(ConfiguredModule module, boolean currentConfiguration) {
    }

    private record SourceKey(Long moduleId, Long tenantId) {
    }
}
