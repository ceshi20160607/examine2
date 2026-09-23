package com.unique.unexamine.application.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.application.base.entity.AppDefinition;
import com.unique.unexamine.application.base.entity.AppPublication;
import com.unique.unexamine.application.base.entity.AppVersion;
import com.unique.unexamine.application.base.service.AppDefinitionBaseService;
import com.unique.unexamine.application.base.service.AppPublicationBaseService;
import com.unique.unexamine.application.base.service.AppVersionBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.operations.manage.OperationsSecretResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 已发布应用回调是流程外部节点的唯一配置来源，草稿和任意地址不能进入运行快照。 */
@Service
public class ApplicationFlowCallbackService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final AppDefinitionBaseService definitions;
    private final AppPublicationBaseService publications;
    private final AppVersionBaseService versions;
    private final OperationsSecretResolver secretResolver;
    private final ObjectMapper objectMapper;

    public ApplicationFlowCallbackService(
            AppDefinitionBaseService definitions,
            AppPublicationBaseService publications,
            AppVersionBaseService versions,
            OperationsSecretResolver secretResolver,
            ObjectMapper objectMapper) {
        this.definitions = definitions;
        this.publications = publications;
        this.versions = versions;
        this.secretResolver = secretResolver;
        this.objectMapper = objectMapper;
    }

    /** 目录仅返回当前系统、当前租户、当前发布版本中可以签名的 EVENT 回调。 */
    @Transactional(readOnly = true)
    public List<ApplicationModels.FlowCallbackOption> catalog(AuthenticatedContext context) {
        requireSystemContext(context);
        List<ApplicationModels.FlowCallbackOption> result = new ArrayList<>();
        for (AppDefinition application : definitions.selectList(Wrappers.<AppDefinition>lambdaQuery()
                .eq(AppDefinition::getContextType, "SYSTEM")
                .eq(AppDefinition::getPlatformId, context.platformId())
                .eq(AppDefinition::getOwnerSystemId, context.systemId())
                .eq(AppDefinition::getOwnerTenantId, context.tenantId())
                .eq(AppDefinition::getStatus, "ACTIVE")
                .eq(AppDefinition::getApplicationType, "WEBHOOK")
                .orderByAsc(AppDefinition::getName).orderByAsc(AppDefinition::getId))) {
            AppPublication publication = publication(application.getId());
            AppVersion version = publication == null ? null : versions.selectById(publication.getCurrentVersionId());
            if (version == null || !Objects.equals(version.getApplicationId(), application.getId())) continue;
            for (PublishedCallback callback : callbacks(application, version)) {
                if (!"EVENT".equals(callback.callbackType())) continue;
                try {
                    secretResolver.requireWebhookReference(context.systemId(), callback.signingSecretRef());
                    result.add(callback.option());
                } catch (DomainException unavailable) {
                    // 目录不展示失效引用；发布检查会给保留旧选择的草稿明确错误。
                }
            }
        }
        return result.stream().sorted(Comparator
                .comparing(ApplicationModels.FlowCallbackOption::applicationName)
                .thenComparing(ApplicationModels.FlowCallbackOption::callbackId)).toList();
    }

    /** 发布 Flow 时再次读取当前应用发布指针，并冻结地址、签名引用和投递策略。 */
    @Transactional(readOnly = true)
    public PublishedCallback requirePublished(
            AuthenticatedContext context, Long applicationId, Long callbackId) {
        requireSystemContext(context);
        AppDefinition application = definitions.selectById(applicationId);
        if (application == null || !"SYSTEM".equals(application.getContextType())
                || !Objects.equals(application.getPlatformId(), context.platformId())
                || !Objects.equals(application.getOwnerSystemId(), context.systemId())
                || !Objects.equals(application.getOwnerTenantId(), context.tenantId())
                || !"ACTIVE".equals(application.getStatus())
                || !"WEBHOOK".equals(application.getApplicationType())) {
            throw invalid("FLOW_CALLBACK_APPLICATION_UNAVAILABLE", "所选应用未发布、已停用或不属于当前工作空间");
        }
        AppPublication publication = publication(applicationId);
        AppVersion version = publication == null ? null : versions.selectById(publication.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getApplicationId(), applicationId)) {
            throw invalid("FLOW_CALLBACK_APPLICATION_UNPUBLISHED", "所选应用没有可用的发布版本");
        }
        PublishedCallback callback = callbacks(application, version).stream()
                .filter(item -> Objects.equals(item.callbackId(), callbackId))
                .findFirst().orElseThrow(() -> invalid("FLOW_CALLBACK_NOT_FOUND", "所选回调不在应用当前发布版本中"));
        if (!"EVENT".equals(callback.callbackType())) {
            throw invalid("FLOW_CALLBACK_TYPE_INVALID", "流程外部节点只能选择业务事件回调");
        }
        secretResolver.requireWebhookReference(context.systemId(), callback.signingSecretRef());
        return callback;
    }

    private List<PublishedCallback> callbacks(AppDefinition application, AppVersion version) {
        Object raw = readMap(version.getSnapshotJson()).get("callbacks");
        if (!(raw instanceof List<?> items)) return List.of();
        List<PublishedCallback> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> source)) continue;
            Map<String, Object> callback = new LinkedHashMap<>();
            source.forEach((key, value) -> callback.put(String.valueOf(key), value));
            Long callbackId = number(callback.get("callbackId"));
            String callbackType = text(callback.get("callbackType"));
            String url = text(callback.get("url"));
            String signingSecretRef = text(callback.get("signingSecretRef"));
            Integer timeoutMillis = integer(callback.get("timeoutMillis"));
            Integer maxAttempts = integer(callback.get("maxAttempts"));
            if (callbackId == null || url.isBlank() || signingSecretRef.isBlank()
                    || timeoutMillis == null || maxAttempts == null) continue;
            result.add(new PublishedCallback(application.getId(), application.getCode(), application.getName(),
                    version.getId(), version.getVersionNumber(), version.getSnapshotHash(), callbackId, callbackType,
                    strings(callback.get("eventCodes")), url, signingSecretRef, timeoutMillis, maxAttempts));
        }
        return result;
    }

    private AppPublication publication(Long applicationId) {
        return publications.selectList(Wrappers.<AppPublication>lambdaQuery()
                        .eq(AppPublication::getApplicationId, applicationId).last("limit 1"))
                .stream().findFirst().orElse(null);
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException failure) {
            throw invalid("FLOW_CALLBACK_SNAPSHOT_INVALID", "应用发布快照无法读取");
        }
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context == null || context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "流程外部节点需要进入系统工作空间配置", HttpStatus.CONFLICT);
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).map(String::strip).filter(item -> !item.isBlank()).toList();
    }

    /** 地址和密钥只提供给发布冻结及后台投递，不能用于前端目录响应。 */
    public record PublishedCallback(
            Long applicationId,
            String applicationCode,
            String applicationName,
            Long applicationVersionId,
            Integer applicationVersionNumber,
            String applicationSnapshotHash,
            Long callbackId,
            String callbackType,
            List<String> eventCodes,
            String url,
            String signingSecretRef,
            Integer timeoutMillis,
            Integer maxAttempts) {
        public ApplicationModels.FlowCallbackOption option() {
            return new ApplicationModels.FlowCallbackOption(applicationId, applicationCode, applicationName,
                    applicationVersionId, applicationVersionNumber, callbackId, callbackType, eventCodes,
                    timeoutMillis, maxAttempts);
        }
    }
}
