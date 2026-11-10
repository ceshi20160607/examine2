package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.common.base.entity.UnCommonAi;
import com.unique.unexamine.core.common.base.service.IUnCommonAiService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.manage.dto.PlatformAiDto.ConfigSearch;
import com.unique.unexamine.plat.manage.dto.PlatformAiDto.ConfigView;
import com.unique.unexamine.plat.manage.dto.PlatformAiDto.SaveRequest;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台模型授权的配置、发布快照与停用规则。 */
@Service
public class PlatformAiConfigService {
    private static final Set<String> CAPABILITIES = Set.of("DIALOG", "QUERY", "WRITE", "FLOW_DRAFT",
            "REPORT", "ERROR_EXPLAIN");
    private static final Set<String> CONFIG_KEYS = Set.of("model", "apiUrl", "monthlyQuota",
            "maxConcurrency", "capabilities", "authorizedSystemIds", "redactLogs",
            "dataExportPolicy", "requireConfirmation", "conversationRetentionDays");
    private final AuthManageService auth;
    private final IUnCommonAiService configs;
    private final IUnPlatLogOptService logs;
    private final ObjectMapper json;

    public PlatformAiConfigService(AuthManageService auth, IUnCommonAiService configs,
            IUnPlatLogOptService logs, ObjectMapper json) {
        this.auth = auth; this.configs = configs; this.logs = logs; this.json = json;
    }

    /** 平台管理员分页查看本平台的模型配置。 */
    public Page<ConfigView> list(String token, ConfigSearch search) {
        auth.requirePlatformAdmin(token);
        ConfigSearch criteria = search == null ? new ConfigSearch() : search;
        var where = new LambdaQueryWrapper<UnCommonAi>()
                .eq(UnCommonAi::getScopeType, "PLATFORM").orderByDesc(UnCommonAi::getId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank())
            where.eq(UnCommonAi::getStatus, status(criteria.getStatus()));
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            if (criteria.getKeyword().length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型配置搜索内容过长");
            where.and(w -> w.like(UnCommonAi::getCode, criteria.getKeyword().trim())
                    .or().like(UnCommonAi::getName, criteria.getKeyword().trim()));
        }
        Page<UnCommonAi> page = configs.page(criteria.parse(), where);
        return new Page<ConfigView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 草稿允许尚未填全模型参数，但禁止明文凭据进入配置 JSON。 */
    @Transactional(rollbackFor = Exception.class)
    public ConfigView save(String token, SaveRequest request) {
        Long operatorId = auth.requirePlatformAdmin(token).getId();
        if (request == null || request.code() == null || !request.code().matches("[A-Za-z][A-Za-z0-9_.-]{1,79}")
                || request.name() == null || request.name().isBlank() || request.name().length() > 100
                || request.providerCode() == null || !request.providerCode().matches("[A-Za-z][A-Za-z0-9_.-]{1,79}")
                || request.config() == null || !request.config().isObject()
                || request.config().toString().length() > 20000)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型配置无效");
        if (request.secretRef() != null && !request.secretRef().matches("env:[A-Z][A-Z0-9_]{1,240}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型凭据必须使用环境变量引用");
        rejectCredentials(request.config());
        UnCommonAi row = request.id() == null ? new UnCommonAi() : owned(request.id());
        boolean creating = row.getId() == null;
        if (creating) {
            if (configs.exists(new LambdaQueryWrapper<UnCommonAi>()
                    .eq(UnCommonAi::getScopeType, "PLATFORM").eq(UnCommonAi::getCode, request.code())))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模型配置编码已存在");
            row.setScopeType("PLATFORM"); row.setCode(request.code()); row.setCreateAccountId(operatorId);
            row.setCreateTime(LocalDateTime.now()); row.setPublishedVersion(0);
        } else if (!row.getCode().equals(request.code())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模型配置编码不能修改");
        }
        row.setName(request.name().trim()); row.setProviderCode(request.providerCode());
        row.setSecretRef(request.secretRef()); row.setConfigJson(request.config().toString());
        row.setStatus("DRAFT"); row.setUpdateTime(LocalDateTime.now());
        try {
            if (creating) configs.addOrUpdate(row);
            else configs.update(new LambdaUpdateWrapper<UnCommonAi>().eq(UnCommonAi::getId, row.getId())
                    .set(UnCommonAi::getName, row.getName())
                    .set(UnCommonAi::getProviderCode, row.getProviderCode())
                    .set(UnCommonAi::getSecretRef, row.getSecretRef())
                    .set(UnCommonAi::getConfigJson, row.getConfigJson())
                    .set(UnCommonAi::getStatus, "DRAFT")
                    .set(UnCommonAi::getUpdateTime, row.getUpdateTime()));
        } catch (DuplicateKeyException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模型配置编码已存在", ex);
        }
        audit(operatorId, "PLATFORM_AI_SAVE", row.getId());
        return view(row);
    }

    /** 发布前验证模型地址、额度、授权系统和人工确认策略，并固定运行快照。 */
    @Transactional(rollbackFor = Exception.class)
    public ConfigView publish(String token, Long id) {
        Long operatorId = auth.requirePlatformAdmin(token).getId();
        UnCommonAi row = owned(id);
        JsonNode config = config(row);
        validatePublish(row, config);
        row.setPublishedConfigJson(row.getConfigJson());
        row.setPublishedVersion((row.getPublishedVersion() == null ? 0 : row.getPublishedVersion()) + 1);
        row.setStatus("PUBLISHED"); row.setUpdateTime(LocalDateTime.now());
        configs.addOrUpdate(row); audit(operatorId, "PLATFORM_AI_PUBLISH", row.getId());
        return view(row);
    }

    /** 停用模型后不再作为系统授权来源。 */
    @Transactional(rollbackFor = Exception.class)
    public ConfigView disable(String token, Long id) {
        Long operatorId = auth.requirePlatformAdmin(token).getId();
        UnCommonAi row = owned(id);
        row.setStatus("DISABLED"); row.setUpdateTime(LocalDateTime.now());
        configs.addOrUpdate(row); audit(operatorId, "PLATFORM_AI_DISABLE", row.getId());
        return view(row);
    }

    /** 系统发布智能配置时核对平台发布快照中的系统授权。 */
    public UnCommonAi requireAuthorizedModel(Long id, Long systemId) {
        UnCommonAi row = owned(id);
        if (!"PUBLISHED".equals(row.getStatus()) || row.getPublishedConfigJson() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "平台模型授权未发布");
        try {
            JsonNode published = json.readTree(row.getPublishedConfigJson());
            if (!ids(published, "authorizedSystemIds").contains(systemId))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前系统未获模型授权");
        } catch (BaseException ex) { throw ex; }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台模型授权无法读取", ex); }
        return row;
    }

    private void validatePublish(UnCommonAi row, JsonNode config) {
        if (config == null || !config.isObject() || config.size() != CONFIG_KEYS.size())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型发布参数不完整");
        config.fieldNames().forEachRemaining(key -> {
            if (!CONFIG_KEYS.contains(key)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型参数包含未知字段");
        });
        if (!text(config, "model", 100) || !positive(config, "monthlyQuota")
                || !positive(config, "maxConcurrency") || !config.path("redactLogs").isBoolean()
                || !positive(config, "conversationRetentionDays")
                || config.path("conversationRetentionDays").asInt() > 3650
                || !config.path("redactLogs").asBoolean() || !config.path("requireConfirmation").isBoolean()
                || !config.path("requireConfirmation").asBoolean()
                || !config.path("dataExportPolicy").isTextual()
                || !Set.of("DENY", "ALLOW_WITH_APPROVAL").contains(config.path("dataExportPolicy").asText()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型安全与额度配置无效");
        if (!"LOCAL".equalsIgnoreCase(row.getProviderCode())) {
            if (row.getSecretRef() == null || !text(config, "apiUrl", 500))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部模型缺少地址或凭据引用");
            try {
                URI uri = URI.create(config.get("apiUrl").asText());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            } catch (RuntimeException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部模型地址无效"); }
        } else {
            if (!text(config, "apiUrl", 500))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "本地模型地址无效");
            try {
                URI uri = URI.create(config.get("apiUrl").asText());
                if (!Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                        || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                        || !Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException();
            } catch (RuntimeException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "本地模型地址必须指向本机"); }
        }
        ids(config, "authorizedSystemIds");
        JsonNode abilities = config.path("capabilities");
        if (!abilities.isArray() || abilities.isEmpty() || abilities.size() > CAPABILITIES.size())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型能力范围无效");
        Set<String> seen = new HashSet<>();
        abilities.forEach(item -> {
            if (!item.isTextual() || !CAPABILITIES.contains(item.asText()) || !seen.add(item.asText()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型能力范围无效");
        });
    }

    private boolean text(JsonNode config, String key, int max) {
        JsonNode value = config.path(key);
        return value.isTextual() && !value.asText().isBlank() && value.asText().length() <= max;
    }

    private boolean positive(JsonNode config, String key) {
        JsonNode value = config.path(key);
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() > 0;
    }

    private Set<Long> ids(JsonNode config, String key) {
        JsonNode values = config.path(key);
        if (!values.isArray() || values.size() > 500)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "授权系统范围无效");
        Set<Long> seen = new HashSet<>();
        values.forEach(item -> {
            if (!item.canConvertToLong() || item.longValue() < 1 || !seen.add(item.longValue()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "授权系统主键无效或重复");
        });
        return seen;
    }

    /** 拒绝把密钥字段混进普通配置；真正凭据只从引用位置读取。 */
    private void rejectCredentials(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            String key = field.getKey().toLowerCase(Locale.ROOT);
            if (Set.of("apikey", "api_key", "secret", "password", "token", "credential").contains(key))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型密钥不能写入配置内容");
            rejectCredentials(field.getValue());
        });
        else if (node.isArray()) node.forEach(this::rejectCredentials);
    }

    private UnCommonAi owned(Long id) {
        UnCommonAi row = id == null ? null : configs.getById(id);
        if (row == null || !"PLATFORM".equals(row.getScopeType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台模型配置不存在");
        return row;
    }

    private JsonNode config(UnCommonAi row) {
        if (row.getConfigJson() == null) return null;
        try { return json.readTree(row.getConfigJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "模型配置无法读取", ex); }
    }

    private String status(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED", "DISABLED").contains(normalized))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模型配置状态无效");
        return normalized;
    }

    private ConfigView view(UnCommonAi row) {
        return new ConfigView(row.getId(), row.getCode(), row.getName(), row.getProviderCode(),
                row.getSecretRef(), config(row), row.getStatus(), row.getPublishedVersion(),
                row.getCreateTime(), row.getUpdateTime());
    }

    private void audit(Long accountId, String action, Long id) {
        UnPlatLogOpt log = new UnPlatLogOpt();
        log.setAccountId(accountId); log.setActionCode(action); log.setObjectType("PLATFORM_AI");
        log.setObjectId(id); log.setCreateTime(LocalDateTime.now()); logs.addOrUpdate(log);
    }
}
