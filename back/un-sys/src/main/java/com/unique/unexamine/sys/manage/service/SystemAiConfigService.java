package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.common.base.entity.UnCommonAi;
import com.unique.unexamine.core.common.base.service.IUnCommonAiService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.manage.service.PlatformAiConfigService;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.manage.dto.SystemAiDto.ConfigSearch;
import com.unique.unexamine.sys.manage.dto.SystemAiDto.ConfigView;
import com.unique.unexamine.sys.manage.dto.SystemAiDto.SaveRequest;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.service.IUnModuleFieldService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统 Agent 策略只收窄平台模型授权和当前成员权限，不自行授予业务权限。 */
@Service
public class SystemAiConfigService {
    private static final Set<String> POLICY_KEYS = Set.of("moduleIds", "fieldIds", "actions",
            "dataScopeExpression", "outboundPolicy", "maskingPolicy", "requireConfirmation",
            "failMode", "promptVersion", "conversationRetentionDays");
    private final SystemManageService systems;
    private final PlatformAiConfigService platformModels;
    private final IUnCommonAiService configs;
    private final ModuleConfigService modules;
    private final IUnModuleFieldService fields;
    private final IUnSysLogOptService logs;
    private final ObjectMapper json;

    public SystemAiConfigService(SystemManageService systems, PlatformAiConfigService platformModels,
            IUnCommonAiService configs, ModuleConfigService modules, IUnModuleFieldService fields,
            IUnSysLogOptService logs, ObjectMapper json) {
        this.systems = systems; this.platformModels = platformModels; this.configs = configs;
        this.modules = modules; this.fields = fields; this.logs = logs; this.json = json;
    }

    /** 管理员分页查看当前系统租户的智能配置。 */
    public Page<ConfigView> list(String token, Long systemId, Long tenantId, ConfigSearch search) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        ConfigSearch criteria = search == null ? new ConfigSearch() : search;
        var where = new LambdaQueryWrapper<UnCommonAi>()
                .eq(UnCommonAi::getScopeType, "SYSTEM")
                .eq(UnCommonAi::getSystemId, context.systemId())
                .eq(UnCommonAi::getTenantId, context.tenantId())
                .orderByDesc(UnCommonAi::getId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank())
            where.eq(UnCommonAi::getStatus, status(criteria.getStatus()));
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            if (criteria.getKeyword().length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "智能配置搜索内容过长");
            where.and(w -> w.like(UnCommonAi::getCode, criteria.getKeyword().trim())
                    .or().like(UnCommonAi::getName, criteria.getKeyword().trim()));
        }
        Page<UnCommonAi> page = configs.page(criteria.parse(), where);
        return new Page<ConfigView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 保存草稿前核对平台授权，并把平台配置主键与系统策略一起持久化。 */
    @Transactional(rollbackFor = Exception.class)
    public ConfigView save(String token, Long systemId, Long tenantId, SaveRequest request) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        if (request == null || request.code() == null || !request.code().matches("[A-Za-z][A-Za-z0-9_.-]{1,79}")
                || request.name() == null || request.name().isBlank() || request.name().length() > 100
                || request.platformConfigId() == null || request.platformConfigId() < 1
                || request.policy() == null || !request.policy().isObject()
                || request.policy().toString().length() > 20000)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统智能配置无效");
        UnCommonAi platform = platformModels.requireAuthorizedModel(request.platformConfigId(), context.systemId());
        ObjectNode config = json.createObjectNode();
        config.put("platformConfigId", platform.getId());
        config.set("policy", request.policy());
        UnCommonAi row = request.id() == null ? new UnCommonAi() : owned(context, request.id());
        boolean creating = row.getId() == null;
        if (creating) {
            if (configs.exists(new LambdaQueryWrapper<UnCommonAi>()
                    .eq(UnCommonAi::getScopeType, "SYSTEM")
                    .eq(UnCommonAi::getSystemId, context.systemId())
                    .eq(UnCommonAi::getTenantId, context.tenantId())
                    .eq(UnCommonAi::getCode, request.code())))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "系统智能配置编码已存在");
            row.setScopeType("SYSTEM"); row.setSystemId(context.systemId()); row.setTenantId(context.tenantId());
            row.setCode(request.code()); row.setCreateAccountId(context.accountId());
            row.setCreateTime(LocalDateTime.now()); row.setPublishedVersion(0);
        } else if (!row.getCode().equals(request.code())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "系统智能配置编码不能修改");
        }
        row.setName(request.name().trim()); row.setProviderCode(platform.getProviderCode());
        row.setSecretRef(null); row.setConfigJson(config.toString());
        row.setStatus("DRAFT"); row.setUpdateTime(LocalDateTime.now());
        if (creating) configs.addOrUpdate(row);
        else configs.update(new LambdaUpdateWrapper<UnCommonAi>().eq(UnCommonAi::getId, row.getId())
                .set(UnCommonAi::getName, row.getName())
                .set(UnCommonAi::getProviderCode, row.getProviderCode())
                .set(UnCommonAi::getSecretRef, null)
                .set(UnCommonAi::getConfigJson, row.getConfigJson())
                .set(UnCommonAi::getStatus, "DRAFT")
                .set(UnCommonAi::getUpdateTime, row.getUpdateTime()));
        audit(context, "SYSTEM_AI_SAVE", row.getId());
        return view(row);
    }

    /** 发布时重新检查平台授权和当前模块字段，固定平台及系统策略版本。 */
    @Transactional(rollbackFor = Exception.class)
    public ConfigView publish(String token, Long systemId, Long tenantId, Long id) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnCommonAi row = owned(context, id);
        JsonNode config = config(row);
        Long platformId = platformId(config);
        UnCommonAi platform = platformModels.requireAuthorizedModel(platformId, context.systemId());
        JsonNode policy = config.path("policy");
        validatePolicy(context, policy, platform);
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("platformConfigId", platformId);
        snapshot.put("platformPublishedVersion", platform.getPublishedVersion());
        snapshot.set("policy", policy);
        row.setPublishedConfigJson(snapshot.toString());
        row.setPublishedVersion((row.getPublishedVersion() == null ? 0 : row.getPublishedVersion()) + 1);
        row.setStatus("PUBLISHED"); row.setUpdateTime(LocalDateTime.now());
        configs.addOrUpdate(row); audit(context, "SYSTEM_AI_PUBLISH", row.getId());
        return view(row);
    }

    /** 停用系统策略后运行态不得继续使用对应模型授权。 */
    @Transactional(rollbackFor = Exception.class)
    public ConfigView disable(String token, Long systemId, Long tenantId, Long id) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnCommonAi row = owned(context, id);
        row.setStatus("DISABLED"); row.setUpdateTime(LocalDateTime.now());
        configs.addOrUpdate(row); audit(context, "SYSTEM_AI_DISABLE", row.getId());
        return view(row);
    }

    private void validatePolicy(SystemContext context, JsonNode policy, UnCommonAi platform) {
        if (!policy.isObject() || policy.size() != POLICY_KEYS.size())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 策略不完整");
        policy.fieldNames().forEachRemaining(key -> {
            if (!POLICY_KEYS.contains(key)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 策略包含未知字段");
        });
        Set<Long> moduleIds = ids(policy.path("moduleIds"), 100);
        Set<Long> fieldIds = ids(policy.path("fieldIds"), 1000);
        if (moduleIds.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 必须限定模块");
        Map<Long, Long> moduleTenants = new HashMap<>();
        moduleIds.forEach(id -> moduleTenants.put(id,
                modules.activeModule(context.systemId(), id, context.tenantId()).getTenantId()));
        fieldIds.forEach(id -> {
            UnModuleField field = fields.getById(id);
            if (field == null || !"ACTIVE".equals(field.getStatus())
                    || !context.systemId().equals(field.getSystemId()) || !moduleIds.contains(field.getModuleId())
                    || !(context.tenantId().equals(field.getTenantId())
                    || moduleTenants.get(field.getModuleId()).equals(field.getTenantId())))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 字段范围无效");
        });
        if (!policy.path("requireConfirmation").isBoolean() || !policy.path("requireConfirmation").asBoolean()
                || !policy.path("outboundPolicy").isTextual()
                || !Set.of("DENY", "REDACTED").contains(policy.path("outboundPolicy").asText())
                || !"REDACT".equals(policy.path("maskingPolicy").asText())
                || !"DISABLE".equals(policy.path("failMode").asText())
                || !policy.path("promptVersion").isTextual()
                || policy.path("promptVersion").asText().isBlank()
                || policy.path("promptVersion").asText().length() > 80
                || !policy.path("conversationRetentionDays").isIntegralNumber()
                || policy.path("conversationRetentionDays").asInt() < 1
                || policy.path("conversationRetentionDays").asInt() > 3650
                || !policy.path("dataScopeExpression").isObject()
                || !policy.path("dataScopeExpression").isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 权限与确认策略无效");
        JsonNode actions = policy.path("actions");
        if (!actions.isArray() || actions.isEmpty() || actions.size() > 6)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 动作范围无效");
        Set<String> allowed = new HashSet<>();
        try {
            JsonNode platformSnapshot = json.readTree(platform.getPublishedConfigJson());
            platformSnapshot.path("capabilities").forEach(item -> allowed.add(item.asText()));
            if ("REDACTED".equals(policy.path("outboundPolicy").asText())
                    && "DENY".equals(platformSnapshot.path("dataExportPolicy").asText()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台模型禁止业务数据外发");
        } catch (BaseException ex) { throw ex; }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型能力快照无法读取", ex); }
        Set<String> seen = new HashSet<>();
        actions.forEach(item -> {
            if (!item.isTextual() || !allowed.contains(item.asText()) || !seen.add(item.asText()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 动作超出平台模型授权");
        });
    }

    private Set<Long> ids(JsonNode values, int max) {
        if (!values.isArray() || values.size() > max)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 范围必须是 ID 数组");
        Set<Long> result = new HashSet<>();
        values.forEach(item -> {
            if (!item.canConvertToLong() || item.longValue() < 1 || !result.add(item.longValue()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Agent 范围主键无效或重复");
        });
        return result;
    }

    private Long platformId(JsonNode config) {
        JsonNode id = config.path("platformConfigId");
        if (!id.canConvertToLong() || id.longValue() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台模型配置主键无效");
        return id.longValue();
    }

    private UnCommonAi owned(SystemContext context, Long id) {
        UnCommonAi row = id == null ? null : configs.getById(id);
        if (row == null || !"SYSTEM".equals(row.getScopeType())
                || !context.systemId().equals(row.getSystemId()) || !context.tenantId().equals(row.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "系统智能配置不存在");
        return row;
    }

    private JsonNode config(UnCommonAi row) {
        if (row.getConfigJson() == null) return json.createObjectNode();
        try { return json.readTree(row.getConfigJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "系统智能配置无法读取", ex); }
    }

    private String status(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(normalized))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统智能配置状态无效");
        return normalized;
    }

    private ConfigView view(UnCommonAi row) {
        JsonNode config = config(row);
        return new ConfigView(row.getId(), row.getSystemId(), row.getTenantId(), row.getCode(), row.getName(),
                platformId(config), config.path("policy"), row.getStatus(), row.getPublishedVersion(),
                row.getCreateTime(), row.getUpdateTime());
    }

    private void audit(SystemContext context, String action, Long id) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId()); log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId()); log.setActionCode(action); log.setObjectType("SYSTEM_AI");
        log.setObjectId(id); log.setCreateTime(LocalDateTime.now()); logs.addOrUpdate(log);
    }
}
