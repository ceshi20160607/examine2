package com.unique.unexamine.application.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.application.base.entity.AppCall;
import com.unique.unexamine.application.base.entity.AppDefinition;
import com.unique.unexamine.application.base.entity.AppProviderConnection;
import com.unique.unexamine.application.base.entity.AppProviderDelivery;
import com.unique.unexamine.application.base.entity.AppProviderEvent;
import com.unique.unexamine.application.base.entity.AppProviderIdentity;
import com.unique.unexamine.application.base.entity.AppProviderRoute;
import com.unique.unexamine.application.base.entity.AppProviderSecret;
import com.unique.unexamine.application.base.entity.AppPublication;
import com.unique.unexamine.application.base.entity.AppVersion;
import com.unique.unexamine.application.base.service.AppCallBaseService;
import com.unique.unexamine.application.base.service.AppDefinitionBaseService;
import com.unique.unexamine.application.base.service.AppProviderConnectionBaseService;
import com.unique.unexamine.application.base.service.AppProviderDeliveryBaseService;
import com.unique.unexamine.application.base.service.AppProviderEventBaseService;
import com.unique.unexamine.application.base.service.AppProviderIdentityBaseService;
import com.unique.unexamine.application.base.service.AppProviderRouteBaseService;
import com.unique.unexamine.application.base.service.AppProviderSecretBaseService;
import com.unique.unexamine.application.base.service.AppPublicationBaseService;
import com.unique.unexamine.application.base.service.AppVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.PlatformPermissionResolver;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.notification.base.entity.MsgDelivery;
import com.unique.unexamine.notification.base.service.MsgDeliveryBaseService;
import com.unique.unexamine.notification.manage.MessageModels;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 外部平台连接复用应用发布版本、凭证和调用网关。平台协议只负责确认来源，不能扩大应用授权。
 */
@Service
public class ApplicationProviderService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final long CALLBACK_WINDOW_SECONDS = 300;
    private static final int MAX_CALLBACK_BYTES = 1024 * 1024;
    private static final Set<String> SECRET_TYPES = Set.of("API_SECRET", "CALLBACK_TOKEN", "ENCODING_AES_KEY");

    private final AppDefinitionBaseService definitions;
    private final AppPublicationBaseService publications;
    private final AppVersionBaseService versions;
    private final AppProviderConnectionBaseService connections;
    private final AppProviderSecretBaseService secrets;
    private final AppProviderRouteBaseService routes;
    private final AppProviderEventBaseService events;
    private final AppProviderIdentityBaseService identities;
    private final AppProviderDeliveryBaseService deliveries;
    private final AppCallBaseService calls;
    private final PlatformAccountBaseService accounts;
    private final PlatformMemberBaseService platformMembers;
    private final SystemMemberBaseService systemMembers;
    private final SystemTenantMemberBaseService tenantMembers;
    private final PlatformPermissionResolver platformPermissions;
    private final PermissionResolver systemPermissions;
    private final MsgDeliveryBaseService messageDeliveries;
    private final MessageService messageService;
    private final PermissionChecker permissionChecker;
    private final ApplicationSecretCipher secretCipher;
    private final ApplicationBridgeService bridge;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ApplicationProviderService(
            AppDefinitionBaseService definitions,
            AppPublicationBaseService publications,
            AppVersionBaseService versions,
            AppProviderConnectionBaseService connections,
            AppProviderSecretBaseService secrets,
            AppProviderRouteBaseService routes,
            AppProviderEventBaseService events,
            AppProviderIdentityBaseService identities,
            AppProviderDeliveryBaseService deliveries,
            AppCallBaseService calls,
            PlatformAccountBaseService accounts,
            PlatformMemberBaseService platformMembers,
            SystemMemberBaseService systemMembers,
            SystemTenantMemberBaseService tenantMembers,
            PlatformPermissionResolver platformPermissions,
            PermissionResolver systemPermissions,
            MsgDeliveryBaseService messageDeliveries,
            MessageService messageService,
            PermissionChecker permissionChecker,
            ApplicationSecretCipher secretCipher,
            ApplicationBridgeService bridge,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.definitions = definitions;
        this.publications = publications;
        this.versions = versions;
        this.connections = connections;
        this.secrets = secrets;
        this.routes = routes;
        this.events = events;
        this.identities = identities;
        this.deliveries = deliveries;
        this.calls = calls;
        this.accounts = accounts;
        this.platformMembers = platformMembers;
        this.systemMembers = systemMembers;
        this.tenantMembers = tenantMembers;
        this.platformPermissions = platformPermissions;
        this.systemPermissions = systemPermissions;
        this.messageDeliveries = messageDeliveries;
        this.messageService = messageService;
        this.permissionChecker = permissionChecker;
        this.secretCipher = secretCipher;
        this.bridge = bridge;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ApplicationProviderModels.CapabilityView> capabilities(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return List.of(
                new ApplicationProviderModels.CapabilityView("WECOM", "企业微信", "INTERNAL_APP", "企业内部自建应用",
                        "LOCAL_PROTOCOL_READY", "CorpID、AgentID、Secret及企业可见范围共同约束身份",
                        "URL、Token、EncodingAESKey；GET校验与POST事件均验签解密",
                        "https://developer.work.weixin.qq.com/document/path/90930"),
                new ApplicationProviderModels.CapabilityView("FEISHU", "飞书", "CUSTOM_APP", "企业自建应用",
                        "LOCAL_PROTOCOL_READY", "App ID/App Secret与应用权限、可用范围共同约束身份",
                        "请求地址或长连接；请求地址模式支持Verification Token、Encrypt Key和challenge",
                        "https://open.feishu.cn/document/server-docs/event-subscription-guide/event-subscription-configure-/request-url-configuration-case"),
                new ApplicationProviderModels.CapabilityView("DINGTALK", "钉钉", "INTERNAL_APP", "企业内部应用",
                        "LOCAL_PROTOCOL_READY", "Client ID、Client Secret、CorpID及应用权限共同约束身份",
                        "HTTP事件回调支持Token、AES Key、验签解密和加密成功响应；Stream模式需真实租户选择",
                        "https://open.dingtalk.com/document/orgapp/event-subscription-overview"),
                new ApplicationProviderModels.CapabilityView("WECHAT", "微信", "OFFICIAL_ACCOUNT", "公众号",
                        "PRODUCT_SELECTION_REQUIRED", "公众号AppID/Secret与公众号授权范围",
                        "公众号服务器接入协议，不能与小程序消息推送混用",
                        "https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/Access_Overview.html"),
                new ApplicationProviderModels.CapabilityView("WECHAT", "微信", "MINI_PROGRAM", "小程序",
                        "PRODUCT_SELECTION_REQUIRED", "小程序AppID/Secret与小程序授权范围",
                        "小程序消息推送协议，不能与公众号服务器接入混用",
                        "https://developers.weixin.qq.com/miniprogram/dev/framework/server-ability/message-push.html"));
    }

    @Transactional(readOnly = true)
    public List<ApplicationProviderModels.ConnectionView> connections(
            AuthenticatedContext context, Long applicationId) {
        requireAction(context, "VIEW");
        requireOwned(context, applicationId);
        return connections.selectList(Wrappers.<AppProviderConnection>lambdaQuery()
                        .eq(AppProviderConnection::getApplicationId, applicationId)
                        .orderByAsc(AppProviderConnection::getId))
                .stream().map(this::view).toList();
    }

    @Transactional
    public ApplicationProviderModels.ConnectionView save(
            AuthenticatedContext context,
            Long applicationId,
            ApplicationProviderModels.SaveConnectionRequest input,
            String requestId) {
        requireAction(context, "MANAGE");
        AppDefinition application = requireOwned(context, applicationId);
        String provider = input.providerCode().strip().toUpperCase(Locale.ROOT);
        String product = input.productCode().strip().toUpperCase(Locale.ROOT);
        boolean wecom = "WECOM".equals(provider) && "INTERNAL_APP".equals(product);
        boolean feishu = "FEISHU".equals(provider) && "CUSTOM_APP".equals(product);
        boolean dingTalk = "DINGTALK".equals(provider) && "INTERNAL_APP".equals(product);
        if (!wecom && !feishu && !dingTalk) {
            throw invalid("PROVIDER_PROTOCOL_NOT_IMPLEMENTED", "当前连接产品协议尚未实现，本连接不会被虚构为已接通");
        }
        if (wecom) validateWeComIdentity(input.tenantKey(), input.applicationKey());
        else if (feishu) validateFeishuIdentity(input.tenantKey());
        else validateDingTalkIdentity(input.tenantKey(), input.applicationKey());

        AppProviderConnection connection;
        boolean created = input.id() == null;
        if (created) {
            connection = new AppProviderConnection();
            connection.setApplicationId(applicationId);
            connection.setProviderCode(provider);
            connection.setProductCode(product);
            connection.setConnectionKey(UUID.randomUUID().toString().replace("-", ""));
            connection.setStatus("DRAFT");
            connection.setDiagnosticStatus("NOT_RUN");
            connection.setCreatedByAccountId(context.accountId());
            connection.setVersion(0);
        } else {
            connection = requireConnection(applicationId, input.id());
            if (input.expectedVersion() == null || !Objects.equals(input.expectedVersion(), connection.getVersion())) {
                throw conflict("PROVIDER_CONNECTION_VERSION_CONFLICT", "外部平台连接已变化，请刷新后重试");
            }
            if (!provider.equals(connection.getProviderCode()) || !product.equals(connection.getProductCode())) {
                throw invalid("PROVIDER_CONNECTION_IDENTITY_IMMUTABLE", "连接创建后不能改变平台或产品形态");
            }
            connection.setStatus("DRAFT");
            connection.setDiagnosticStatus("NOT_RUN");
            connection.setDiagnosticSummary("配置已更新，请重新执行本地协议诊断");
            connection.setDiagnosticTime(null);
        }
        connection.setDisplayName(input.displayName().strip());
        connection.setTenantKey(input.tenantKey().strip());
        connection.setApplicationKey(blankToNull(input.applicationKey()));
        try {
            if (created) connections.insert(connection);
            else if (connections.updateById(connection) != 1) {
                throw conflict("PROVIDER_CONNECTION_VERSION_CONFLICT", "外部平台连接已变化，请刷新后重试");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("PROVIDER_CONNECTION_EXISTS", "当前应用已存在相同平台、产品和租户的连接");
        }

        storeSecret(application, connection, "API_SECRET", input.apiSecret(), context.accountId(), created && (feishu || dingTalk));
        storeSecret(application, connection, "CALLBACK_TOKEN", input.callbackToken(), context.accountId(), created);
        storeSecret(application, connection, "ENCODING_AES_KEY", input.encodingAesKey(), context.accountId(), created && (wecom || dingTalk));
        saveRoute(application, connection, input.route());
        audit(context, requestId, "APPLICATION_PROVIDER_CONNECTION_SAVED", applicationId,
                Map.of("connectionId", connection.getId(), "provider", provider, "product", product,
                        "secretPlaintextLogged", false, "status", connection.getStatus()));
        return view(connections.selectById(connection.getId()));
    }

    @Transactional(noRollbackFor = DomainException.class)
    public ApplicationProviderModels.DiagnosticResult diagnose(
            AuthenticatedContext context,
            Long applicationId,
            Long connectionId,
            ApplicationProviderModels.DiagnosticRequest input,
            String requestId) {
        requireAction(context, "MANAGE");
        AppDefinition application = requireOwned(context, applicationId);
        AppProviderConnection connection = requireConnection(applicationId, connectionId);
        List<String> checks = new ArrayList<>();
        try {
            if ("WECOM".equals(connection.getProviderCode()) && "INTERNAL_APP".equals(connection.getProductCode())) {
                diagnoseWeCom(application, connection, checks);
            } else if ("FEISHU".equals(connection.getProviderCode()) && "CUSTOM_APP".equals(connection.getProductCode())) {
                diagnoseFeishu(application, connection, checks);
            } else if ("DINGTALK".equals(connection.getProviderCode()) && "INTERNAL_APP".equals(connection.getProductCode())) {
                diagnoseDingTalk(application, connection, checks);
            } else {
                throw invalid("PROVIDER_PROTOCOL_NOT_IMPLEMENTED", "连接的平台协议尚未实现");
            }
            if (activeRoutes(connectionId).isEmpty()) {
                checks.add("未配置事件路由，合法回调将安全忽略");
            } else {
                for (AppProviderRoute route : activeRoutes(connectionId)) requirePublishedGrant(application, route);
                checks.add("事件路由与当前应用发布授权一致");
            }
            if (activeIdentities(connectionId).isEmpty()) {
                throw invalid("PROVIDER_IDENTITY_REQUIRED", "至少配置一个启用的外部平台成员映射后才能通过诊断");
            }
            for (AppProviderIdentity identity : activeIdentities(connectionId)) {
                resolveProviderContext(application, identity);
            }
            checks.add("外部平台成员标识已映射到当前应用范围内的有效成员");
            if (Boolean.TRUE.equals(input.activateAfterPass()) && !"ACTIVE".equals(application.getStatus())) {
                throw invalid("PROVIDER_APPLICATION_NOT_ACTIVE", "应用发布并启用后才能启用外部平台连接");
            }
            connection.setDiagnosticStatus("LOCAL_PASSED");
            connection.setDiagnosticSummary("本地协议与应用授权检查通过；尚未验证真实"
                    + providerName(connection.getProviderCode()) + "租户连通");
            connection.setDiagnosticTime(LocalDateTime.now());
            connection.setStatus(Boolean.TRUE.equals(input.activateAfterPass()) ? "ACTIVE" : "DRAFT");
            if (connections.updateById(connection) != 1) {
                throw conflict("PROVIDER_CONNECTION_VERSION_CONFLICT", "外部平台连接已变化，请刷新后重试");
            }
            audit(context, requestId, "APPLICATION_PROVIDER_DIAGNOSTIC_PASSED", applicationId,
                    Map.of("connectionId", connectionId, "provider", connection.getProviderCode(),
                            "externalVerified", false, "activated", "ACTIVE".equals(connection.getStatus())));
            ApplicationProviderModels.ConnectionView current = view(connections.selectById(connectionId));
            return new ApplicationProviderModels.DiagnosticResult(connectionId, "LOCAL_PASSED",
                    current.diagnosticSummary(), checks, false, current);
        } catch (DomainException exception) {
            connection.setDiagnosticStatus("FAILED");
            connection.setDiagnosticSummary(exception.getMessage());
            connection.setDiagnosticTime(LocalDateTime.now());
            connection.setStatus("DRAFT");
            connections.updateById(connection);
            throw exception;
        }
    }

    @Transactional
    public ApplicationProviderModels.ConnectionView disable(
            AuthenticatedContext context, Long applicationId, Long connectionId, String requestId) {
        requireAction(context, "MANAGE");
        requireOwned(context, applicationId);
        AppProviderConnection connection = requireConnection(applicationId, connectionId);
        connection.setStatus("DISABLED");
        if (connections.updateById(connection) != 1) {
            throw conflict("PROVIDER_CONNECTION_VERSION_CONFLICT", "外部平台连接已变化，请刷新后重试");
        }
        audit(context, requestId, "APPLICATION_PROVIDER_CONNECTION_DISABLED", applicationId,
                Map.of("connectionId", connectionId, "provider", connection.getProviderCode()));
        return view(connections.selectById(connectionId));
    }

    @Transactional(readOnly = true)
    public List<ApplicationProviderModels.EventView> events(
            AuthenticatedContext context, Long applicationId, Long connectionId) {
        requireAction(context, "VIEW");
        requireOwned(context, applicationId);
        requireConnection(applicationId, connectionId);
        return events.selectList(Wrappers.<AppProviderEvent>lambdaQuery()
                        .eq(AppProviderEvent::getConnectionId, connectionId)
                        .orderByDesc(AppProviderEvent::getReceivedTime, AppProviderEvent::getId)
                        .last("limit 100"))
                .stream().map(event -> new ApplicationProviderModels.EventView(
                        event.getId(), event.getProviderEventId(), event.getEventCode(),
                        event.getSenderExternalId(), event.getStatus(), event.getApplicationCallId(),
                        event.getResponseCode(), event.getReceivedTime(), event.getProcessedTime())).toList();
    }

    /** 返回当前应用范围内可选的真实成员，管理页不要求用户填写数据库主键。 */
    @Transactional(readOnly = true)
    public List<ApplicationProviderModels.IdentityOptionView> identityOptions(
            AuthenticatedContext context, Long applicationId, Long connectionId) {
        requireAction(context, "VIEW");
        AppDefinition application = requireOwned(context, applicationId);
        requireConnection(applicationId, connectionId);
        return identityOptions(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationProviderModels.IdentityView> identities(
            AuthenticatedContext context, Long applicationId, Long connectionId) {
        requireAction(context, "VIEW");
        AppDefinition application = requireOwned(context, applicationId);
        requireConnection(applicationId, connectionId);
        return identities.selectList(Wrappers.<AppProviderIdentity>lambdaQuery()
                        .eq(AppProviderIdentity::getConnectionId, connectionId)
                        .orderByAsc(AppProviderIdentity::getId))
                .stream().map(identity -> identityView(application, identity)).toList();
    }

    @Transactional
    public ApplicationProviderModels.IdentityView saveIdentity(
            AuthenticatedContext context,
            Long applicationId,
            Long connectionId,
            ApplicationProviderModels.SaveIdentityRequest input,
            String requestId) {
        requireAction(context, "MANAGE");
        AppDefinition application = requireOwned(context, applicationId);
        AppProviderConnection connection = requireConnection(applicationId, connectionId);
        requireImplementedProvider(connection);
        String externalUserId = normalizeExternalUserId(input.externalUserId());
        ApplicationProviderModels.IdentityOptionView target = identityOptions(application).stream()
                .filter(option -> option.targetType().equals(input.targetType())
                        && Objects.equals(option.targetMemberId(), input.targetMemberId()))
                .findFirst().orElseThrow(() -> invalid(
                        "PROVIDER_IDENTITY_TARGET_INVALID", "所选成员已停用或不在当前应用范围"));
        AppProviderIdentity identity;
        boolean created = input.id() == null;
        if (created) {
            identity = new AppProviderIdentity();
            identity.setConnectionId(connectionId);
            identity.setCreatedByAccountId(context.accountId());
            identity.setVersion(0);
        } else {
            identity = requireIdentity(connectionId, input.id());
            if (input.expectedVersion() == null || !Objects.equals(input.expectedVersion(), identity.getVersion())) {
                throw conflict("PROVIDER_IDENTITY_VERSION_CONFLICT", "成员映射已变化，请刷新后重试");
            }
        }
        identity.setExternalUserId(externalUserId);
        bindIdentityTarget(application, identity, target);
        identity.setStatus("ACTIVE");
        try {
            if (created) identities.insert(identity);
            else if (identities.updateById(identity) != 1) {
                throw conflict("PROVIDER_IDENTITY_VERSION_CONFLICT", "成员映射已变化，请刷新后重试");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("PROVIDER_IDENTITY_EXISTS", "此外部平台成员标识已配置映射");
        }
        audit(context, requestId, "APPLICATION_PROVIDER_IDENTITY_SAVED", applicationId,
                Map.of("connectionId", connectionId, "identityId", identity.getId(),
                        "targetType", identity.getTargetType(), "externalUserIdHash", sha256(externalUserId)));
        return identityView(application, identities.selectById(identity.getId()));
    }

    @Transactional
    public ApplicationProviderModels.IdentityView disableIdentity(
            AuthenticatedContext context,
            Long applicationId,
            Long connectionId,
            Long identityId,
            String requestId) {
        requireAction(context, "MANAGE");
        AppDefinition application = requireOwned(context, applicationId);
        requireConnection(applicationId, connectionId);
        AppProviderIdentity identity = requireIdentity(connectionId, identityId);
        identity.setStatus("DISABLED");
        if (identities.updateById(identity) != 1) {
            throw conflict("PROVIDER_IDENTITY_VERSION_CONFLICT", "成员映射已变化，请刷新后重试");
        }
        audit(context, requestId, "APPLICATION_PROVIDER_IDENTITY_DISABLED", applicationId,
                Map.of("connectionId", connectionId, "identityId", identityId));
        return identityView(application, identities.selectById(identityId));
    }

    /**
     * 复用统一消息、站内投递和应用发布路由构造外部平台投递意图；没有真实租户凭证时明确记为外部阻塞。
     */
    @Transactional
    public ApplicationProviderModels.DeliveryView sendMessage(
            AuthenticatedContext context,
            Long applicationId,
            Long connectionId,
            ApplicationProviderModels.SendMessageRequest input,
            String requestId) {
        requireAction(context, "MANAGE");
        AppDefinition application = requireOwned(context, applicationId);
        AppProviderConnection connection = requireConnection(applicationId, connectionId);
        requireImplementedProvider(connection);
        if (!"ACTIVE".equals(connection.getStatus())) {
            throw forbidden("PROVIDER_CONNECTION_INACTIVE", "外部平台连接未启用，不能创建外部投递");
        }
        AppProviderIdentity identity = requireIdentity(connectionId, input.identityId());
        if (!"ACTIVE".equals(identity.getStatus())) {
            throw forbidden("PROVIDER_IDENTITY_DISABLED", "外部平台成员映射已停用");
        }
        resolveProviderContext(application, identity);
        AppProviderRoute route = activeRoutes(connectionId).stream().findFirst().orElseThrow(() -> invalid(
                "PROVIDER_ROUTE_REQUIRED", "请先为连接配置已发布的业务入口"));
        PublishedRoute published = requirePublishedGrant(application, route);
        validateMessageVariables(input.variables());
        String deepLink = providerDeepLink(application, published);
        Map<String, Object> normalizedVariables = input.variables() == null
                ? Map.of() : new java.util.TreeMap<>(input.variables());
        String requestHash = sha256(writeJson(Map.of(
                "identityId", identity.getId(), "templateCode", input.templateCode(),
                "variables", normalizedVariables, "deepLink", deepLink)));
        AppProviderDelivery existing = deliveries.selectList(Wrappers.<AppProviderDelivery>lambdaQuery()
                        .eq(AppProviderDelivery::getConnectionId, connectionId)
                        .eq(AppProviderDelivery::getIdempotencyKey, input.idempotencyKey()).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                throw conflict("PROVIDER_DELIVERY_IDEMPOTENCY_CONFLICT", "同一幂等键不能用于不同消息内容");
            }
            return deliveryView(application, existing);
        }
        MessageModels.RecipientRef recipient = "PLATFORM".equals(identity.getTargetType())
                ? new MessageModels.RecipientRef("PLATFORM_MEMBER", identity.getTargetPlatformMemberId())
                : new MessageModels.RecipientRef("TENANT_MEMBER", identity.getTargetTenantMemberId());
        MessageModels.MessageView message = messageService.send(context, new MessageModels.SendEventRequest(
                input.templateCode(), "APPLICATION_PROVIDER", providerMessageDedup(connectionId, input.idempotencyKey()),
                List.of(recipient), normalizedVariables,
                context.systemId() == null ? "PLATFORM_ROUTE" : "SYSTEM_ROUTE", null, deepLink, "NORMAL"), requestId);
        MsgDelivery messageDelivery = messageDeliveries.selectList(Wrappers.<MsgDelivery>lambdaQuery()
                        .eq(MsgDelivery::getMessageId, message.id())
                        .eq(MsgDelivery::getChannel, connection.getProviderCode())
                        .orderByAsc(MsgDelivery::getId).last("limit 1"))
                .stream().findFirst().orElseThrow(() -> invalid(
                        "PROVIDER_MESSAGE_TEMPLATE_CHANNEL_INVALID", "请选择已发布的"
                                + providerName(connection.getProviderCode()) + "应用消息模板"));
        AppProviderDelivery delivery = new AppProviderDelivery();
        delivery.setConnectionId(connectionId);
        delivery.setIdentityId(identity.getId());
        delivery.setMessageId(message.id());
        delivery.setMessageDeliveryId(messageDelivery.getId());
        delivery.setIdempotencyKey(input.idempotencyKey());
        delivery.setRequestHash(requestHash);
        delivery.setDeepLink(deepLink);
        delivery.setStatus("BLOCKED_EXTERNAL");
        delivery.setErrorCode(connection.getProviderCode() + "_EXTERNAL_CREDENTIALS_UNAVAILABLE");
        delivery.setCreatedByAccountId(context.accountId());
        delivery.setVersion(0);
        try {
            deliveries.insert(delivery);
        } catch (DataIntegrityViolationException exception) {
            AppProviderDelivery raced = deliveries.selectList(Wrappers.<AppProviderDelivery>lambdaQuery()
                            .eq(AppProviderDelivery::getConnectionId, connectionId)
                            .eq(AppProviderDelivery::getIdempotencyKey, input.idempotencyKey()).last("limit 1"))
                    .stream().findFirst().orElseThrow(() -> exception);
            if (!Objects.equals(raced.getRequestHash(), requestHash)) {
                throw conflict("PROVIDER_DELIVERY_IDEMPOTENCY_CONFLICT", "同一幂等键不能用于不同消息内容");
            }
            return deliveryView(application, raced);
        }
        audit(context, requestId, "APPLICATION_PROVIDER_MESSAGE_BLOCKED_EXTERNAL", applicationId,
                Map.of("connectionId", connectionId, "identityId", identity.getId(),
                        "deliveryId", delivery.getId(), "messageId", message.id(),
                        "errorCode", delivery.getErrorCode()));
        return deliveryView(application, delivery);
    }

    @Transactional(readOnly = true)
    public List<ApplicationProviderModels.DeliveryView> deliveries(
            AuthenticatedContext context, Long applicationId, Long connectionId) {
        requireAction(context, "VIEW");
        AppDefinition application = requireOwned(context, applicationId);
        requireConnection(applicationId, connectionId);
        return deliveries.selectList(Wrappers.<AppProviderDelivery>lambdaQuery()
                        .eq(AppProviderDelivery::getConnectionId, connectionId)
                        .orderByDesc(AppProviderDelivery::getCreateTime, AppProviderDelivery::getId)
                        .last("limit 100"))
                .stream().map(delivery -> deliveryView(application, delivery)).toList();
    }

    /** 企业微信保存回调地址时的GET校验，必须在返回明文前完成验签、解密和接收方校验。 */
    public String verifyWeComUrl(
            String connectionKey, String suppliedSignature, String timestamp, String nonce,
            String encryptedEcho, String requestId) {
        AppProviderConnection connection = requireCallableConnection(connectionKey, "WECOM");
        AppDefinition application = requireCallableApplication(connection.getApplicationId());
        requireCallbackTimestamp(timestamp);
        String token = resolveSecret(application, connection, "CALLBACK_TOKEN");
        WeComCallbackCodec.verify(token, timestamp, nonce, encryptedEcho, suppliedSignature);
        WeComCallbackCodec.Decoded decoded = WeComCallbackCodec.decrypt(
                resolveSecret(application, connection, "ENCODING_AES_KEY"), encryptedEcho);
        requireReceiver(connection, decoded.receiveId());
        auditProvider(application, requestId, "APPLICATION_PROVIDER_CALLBACK_URL_VERIFIED", connection,
                Map.of("provider", "WECOM", "externalVerified", false));
        return decoded.message();
    }

    /** 回调只保存安全摘要；验签和去重通过后才允许进入应用网关。 */
    public String handleWeComEvent(
            String connectionKey, String suppliedSignature, String timestamp, String nonce,
            String rawBody, String sourceAddress, String requestId) {
        if (rawBody == null || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BYTES) {
            throw invalid("PROVIDER_CALLBACK_BODY_INVALID", "企业微信回调载荷为空或超过1MB限制");
        }
        AppProviderConnection connection = requireCallableConnection(connectionKey, "WECOM");
        AppDefinition application = requireCallableApplication(connection.getApplicationId());
        requireCallbackTimestamp(timestamp);
        Map<String, String> envelope = readXml(rawBody);
        String encrypted = required(envelope, "Encrypt", "PROVIDER_CALLBACK_ENCRYPT_REQUIRED", "企业微信回调缺少Encrypt");
        String token = resolveSecret(application, connection, "CALLBACK_TOKEN");
        WeComCallbackCodec.verify(token, timestamp, nonce, encrypted, suppliedSignature);
        WeComCallbackCodec.Decoded decoded = WeComCallbackCodec.decrypt(
                resolveSecret(application, connection, "ENCODING_AES_KEY"), encrypted);
        requireReceiver(connection, decoded.receiveId());
        Map<String, String> payload = readXml(decoded.message());
        String eventCode = eventCode(payload);
        String senderExternalId = required(payload, "FromUserName", "PROVIDER_SENDER_REQUIRED",
                "企业微信回调缺少发送成员UserID");
        String payloadHash = sha256(decoded.message());
        String providerEventId = providerEventId(payload, payloadHash);
        Map<String, Object> variables = new LinkedHashMap<>();
        putIfPresent(variables, "providerMessageText", payload.get("Content"));
        putIfPresent(variables, "providerEventKey", payload.get("EventKey"));
        processProviderEvent(application, connection, providerEventId, eventCode,
                senderExternalId, payloadHash, variables, requestId);
        return "success";
    }

    /** 飞书请求地址模式同时处理URL challenge与2.0事件；只返回平台要求的最小JSON响应。 */
    public Map<String, Object> handleFeishuEvent(
            String connectionKey, String timestamp, String nonce, String suppliedSignature,
            String rawBody, String sourceAddress, String requestId) {
        if (rawBody == null || rawBody.isBlank()
                || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BYTES) {
            throw invalid("PROVIDER_CALLBACK_BODY_INVALID", "飞书回调载荷为空或超过1MB限制");
        }
        AppProviderConnection connection = requireCallableConnection(connectionKey, "FEISHU");
        AppDefinition application = requireCallableApplication(connection.getApplicationId());
        JsonNode envelope = readJson(rawBody, "FEISHU_CALLBACK_JSON_INVALID", "飞书回调JSON格式无效");
        AppProviderSecret encryptSecret = activeSecret(connection.getId(), "ENCODING_AES_KEY");
        if (encryptSecret != null) {
            requireCallbackTimestamp(timestamp);
            String encryptKey = resolveSecret(application, connection, "ENCODING_AES_KEY");
            FeishuCallbackCodec.verifySignature(encryptKey, timestamp, nonce, rawBody, suppliedSignature);
            if (envelope.hasNonNull("encrypt")) {
                envelope = readJson(FeishuCallbackCodec.decrypt(encryptKey, envelope.path("encrypt").asText()),
                        "FEISHU_CALLBACK_DECRYPTED_JSON_INVALID", "飞书解密后的事件JSON格式无效");
            }
        } else if (envelope.hasNonNull("encrypt")) {
            throw unauthorized("FEISHU_CALLBACK_ENCRYPT_KEY_REQUIRED", "飞书连接未配置事件Encrypt Key");
        }
        String configuredToken = resolveSecret(application, connection, "CALLBACK_TOKEN");
        String suppliedToken = envelope.path("header").path("token").asText(envelope.path("token").asText(null));
        if (suppliedToken == null || !MessageDigest.isEqual(configuredToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw unauthorized("FEISHU_CALLBACK_TOKEN_INVALID", "飞书事件Verification Token无效");
        }
        String callbackAppId = envelope.path("header").path("app_id").asText(null);
        if (callbackAppId != null && !callbackAppId.isBlank()
                && !MessageDigest.isEqual(connection.getTenantKey().getBytes(StandardCharsets.UTF_8),
                callbackAppId.getBytes(StandardCharsets.UTF_8))) {
            throw forbidden("FEISHU_CALLBACK_APP_MISMATCH", "飞书事件App ID与当前连接不一致");
        }
        if ("url_verification".equals(envelope.path("type").asText())) {
            String challenge = envelope.path("challenge").asText(null);
            if (challenge == null || challenge.isBlank()) {
                throw invalid("FEISHU_CALLBACK_CHALLENGE_REQUIRED", "飞书URL校验缺少challenge");
            }
            auditProvider(application, requestId, "APPLICATION_PROVIDER_CALLBACK_URL_VERIFIED", connection,
                    Map.of("provider", "FEISHU", "externalVerified", false));
            return Map.of("challenge", challenge);
        }
        JsonNode header = envelope.path("header");
        JsonNode eventPayload = envelope.path("event");
        String providerType = header.path("event_type").asText(null);
        if (providerType == null || providerType.isBlank() || !eventPayload.isObject()) {
            throw invalid("FEISHU_EVENT_TYPE_REQUIRED", "飞书事件缺少header.event_type或event载荷");
        }
        String eventCode = "im.message.receive_v1".equals(providerType)
                ? "MESSAGE_TEXT" : "EVENT_" + providerType.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        String providerEventId = header.path("event_id").asText(null);
        if (providerEventId == null || providerEventId.isBlank()) providerEventId = "event:" + sha256(envelope.toString());
        JsonNode senderIds = eventPayload.path("sender").path("sender_id");
        String senderExternalId = firstText(senderIds, "open_id", "user_id", "union_id");
        if (senderExternalId == null) {
            throw invalid("PROVIDER_SENDER_REQUIRED", "飞书事件缺少发送成员open_id或user_id");
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("providerEventType", providerType);
        String content = eventPayload.path("message").path("content").asText(null);
        if (content != null) {
            JsonNode contentJson = readJson(content, "FEISHU_MESSAGE_CONTENT_INVALID", "飞书消息正文格式无效");
            if (contentJson.hasNonNull("text")) variables.put("providerMessageText", contentJson.path("text").asText());
        }
        processProviderEvent(application, connection, providerEventId, eventCode, senderExternalId,
                sha256(envelope.toString()), variables, requestId);
        return Map.of();
    }

    /** 钉钉HTTP事件回调在执行业务前完成时间窗、SHA-1验签、AES解密和Client ID所有者校验。 */
    public Map<String, String> handleDingTalkEvent(
            String connectionKey, String timestamp, String nonce, String suppliedSignature,
            String rawBody, String sourceAddress, String requestId) {
        if (rawBody == null || rawBody.isBlank()
                || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BYTES) {
            throw invalid("PROVIDER_CALLBACK_BODY_INVALID", "钉钉回调载荷为空或超过1MB限制");
        }
        AppProviderConnection connection = requireCallableConnection(connectionKey, "DINGTALK");
        AppDefinition application = requireCallableApplication(connection.getApplicationId());
        requireCallbackTimestamp(timestamp);
        JsonNode envelope = readJson(rawBody, "DINGTALK_CALLBACK_JSON_INVALID", "钉钉回调JSON格式无效");
        String encrypted = envelope.path("encrypt").asText(null);
        if (encrypted == null || encrypted.isBlank()) {
            throw invalid("DINGTALK_CALLBACK_ENCRYPT_REQUIRED", "钉钉回调缺少encrypt");
        }
        String token = resolveSecret(application, connection, "CALLBACK_TOKEN");
        String encodingKey = resolveSecret(application, connection, "ENCODING_AES_KEY");
        DingTalkCallbackCodec.verify(token, timestamp, nonce, encrypted, suppliedSignature);
        DingTalkCallbackCodec.Decoded decoded = DingTalkCallbackCodec.decrypt(encodingKey, encrypted);
        requireDingTalkOwner(connection, decoded.ownerKey());
        JsonNode payload = readJson(decoded.message(), "DINGTALK_CALLBACK_DECRYPTED_JSON_INVALID",
                "钉钉解密后的事件JSON格式无效");
        String providerType = firstText(payload, "EventType", "eventType");
        if (providerType == null) {
            throw invalid("DINGTALK_EVENT_TYPE_REQUIRED", "钉钉事件缺少EventType");
        }
        if ("check_url".equalsIgnoreCase(providerType)) {
            auditProvider(application, requestId, "APPLICATION_PROVIDER_CALLBACK_URL_VERIFIED", connection,
                    Map.of("provider", "DINGTALK", "externalVerified", false));
            return dingTalkSuccess(token, encodingKey, connection.getApplicationKey());
        }
        String eventCode = "chatbot_message".equalsIgnoreCase(providerType)
                ? "MESSAGE_TEXT"
                : "EVENT_" + providerType.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        String senderExternalId = firstText(payload,
                "UserId", "userid", "userId", "StaffId", "staffId", "senderStaffId");
        if (senderExternalId == null) {
            throw invalid("PROVIDER_SENDER_REQUIRED", "钉钉事件缺少成员UserId或StaffId");
        }
        String payloadHash = sha256(decoded.message());
        String providerEventId = firstText(payload, "EventId", "eventId", "processInstanceId", "ProcessInstanceId");
        if (providerEventId == null) providerEventId = "event:" + payloadHash;
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("providerEventType", providerType);
        String messageText = firstText(payload, "Content", "content", "text");
        if (messageText == null && payload.path("text").isObject()) {
            messageText = firstText(payload.path("text"), "content");
        }
        putIfPresent(variables, "providerMessageText", messageText);
        processProviderEvent(application, connection, providerEventId, eventCode,
                senderExternalId, payloadHash, variables, requestId);
        return dingTalkSuccess(token, encodingKey, connection.getApplicationKey());
    }

    /** 各渠道完成来源校验后统一进入成员映射、事件幂等和应用权限执行链。 */
    private void processProviderEvent(
            AppDefinition application,
            AppProviderConnection connection,
            String providerEventId,
            String eventCode,
            String senderExternalId,
            String payloadHash,
            Map<String, Object> eventVariables,
            String requestId) {
        AppProviderIdentity providerIdentity = requireActiveIdentity(connection.getId(), senderExternalId);
        AuthenticatedContext providerContext = resolveProviderContext(application, providerIdentity);
        AppProviderEvent existing = event(connection.getId(), providerEventId);
        if (existing != null) {
            if (!Objects.equals(payloadHash, existing.getPayloadHash())) {
                throw conflict("PROVIDER_EVENT_ID_CONFLICT", "相同平台事件标识对应了不同载荷");
            }
            return;
        }

        AppProviderEvent event = new AppProviderEvent();
        event.setConnectionId(connection.getId());
        event.setProviderEventId(providerEventId);
        event.setEventCode(eventCode);
        event.setSenderExternalId(senderExternalId);
        event.setPayloadHash(payloadHash);
        event.setStatus("RECEIVED");
        event.setReceivedTime(LocalDateTime.now());
        try {
            events.insert(event);
        } catch (DataIntegrityViolationException duplicate) {
            return;
        }

        AppProviderRoute route = routes.selectList(Wrappers.<AppProviderRoute>lambdaQuery()
                        .eq(AppProviderRoute::getConnectionId, connection.getId())
                        .eq(AppProviderRoute::getEventCode, eventCode)
                        .eq(AppProviderRoute::getStatus, "ACTIVE").last("limit 1"))
                .stream().findFirst().orElse(null);
        if (route == null) {
            finishEvent(event, "IGNORED", null, "NO_ACTIVE_ROUTE");
            return;
        }

        try {
            PublishedRoute published = requirePublishedGrant(application, route);
            ApplicationBridgeModels.CallRequest call = new ApplicationBridgeModels.CallRequest(
                    published.resourceType(), published.resourceId(), published.actionCode(),
                    published.targetSystemId(), published.targetTenantId(), published.dataScope(),
                    providerPayload(connection, event, senderExternalId, eventVariables));
            String idempotency = "provider:" + connection.getId() + ":" + sha256(event.getProviderEventId());
            ApplicationBridgeModels.CallResult result = bridge.callInternal(
                    providerContext, application.getId(), idempotency, call, requestId);
            AppCall applicationCall = calls.selectList(Wrappers.<AppCall>lambdaQuery()
                            .eq(AppCall::getApplicationId, application.getId())
                            .eq(AppCall::getRequestId, result.requestId()).last("limit 1"))
                    .stream().findFirst().orElse(null);
            finishEvent(event, "PROCESSED", applicationCall == null ? null : applicationCall.getId(), "OK");
            auditProvider(application, requestId, "APPLICATION_PROVIDER_EVENT_PROCESSED", connection,
                    Map.of("eventId", event.getId(), "eventCode", eventCode,
                            "applicationCallId", applicationCall == null ? 0L : applicationCall.getId(),
                            "identityId", providerIdentity.getId(), "actorAccountId", providerContext.accountId()));
        } catch (DomainException exception) {
            finishEvent(event, "FAILED", null, exception.code());
            auditProvider(application, requestId, "APPLICATION_PROVIDER_EVENT_FAILED", connection,
                    Map.of("eventId", event.getId(), "eventCode", eventCode, "resultCode", exception.code()));
            throw exception;
        }
    }

    private Map<String, Object> providerPayload(
            AppProviderConnection connection,
            AppProviderEvent event,
            String senderExternalId,
            Map<String, Object> eventVariables) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("provider", connection.getProviderCode());
        variables.put("providerConnectionId", connection.getId());
        variables.put("providerEventId", event.getProviderEventId());
        variables.put("providerEventCode", event.getEventCode());
        putIfPresent(variables, "providerSenderId", senderExternalId);
        variables.putAll(eventVariables);
        return Map.of("title", providerName(connection.getProviderCode()) + "事件 · "
                + event.getEventCode(), "variables", variables);
    }

    private void saveRoute(
            AppDefinition application, AppProviderConnection connection, ApplicationProviderModels.RouteInput input) {
        List<AppProviderRoute> current = routes.selectList(Wrappers.<AppProviderRoute>lambdaQuery()
                .eq(AppProviderRoute::getConnectionId, connection.getId()).orderByAsc(AppProviderRoute::getId));
        for (AppProviderRoute item : current) {
            item.setStatus("DISABLED");
            routes.updateById(item);
        }
        if (input == null) return;
        String eventCode = input.eventCode().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("MESSAGE_TEXT", "EVENT_CLICK", "EVENT_VIEW", "EVENT_CHANGE_CONTACT",
                "EVENT_USER_ADD_ORG").contains(eventCode)) {
            throw invalid("PROVIDER_EVENT_CODE_UNSUPPORTED", "当前外部平台事件类型尚未开放");
        }
        AppProviderRoute route = current.stream().filter(item -> eventCode.equals(item.getEventCode()))
                .findFirst().orElseGet(AppProviderRoute::new);
        route.setConnectionId(connection.getId());
        route.setEventCode(eventCode);
        route.setResourceType(input.resourceType().strip().toUpperCase(Locale.ROOT));
        route.setResourceId(input.resourceId().strip());
        route.setActionCode(input.actionCode().strip().toUpperCase(Locale.ROOT));
        route.setTargetSystemId(input.targetSystemId());
        route.setTargetTenantId(input.targetTenantId());
        route.setStatus("ACTIVE");
        if (route.getId() == null) {
            route.setVersion(0);
            routes.insert(route);
        } else {
            routes.updateById(route);
        }
        requirePublishedGrant(application, route);
    }

    private PublishedRoute requirePublishedGrant(AppDefinition application, AppProviderRoute route) {
        AppPublication publication = publications.selectList(Wrappers.<AppPublication>lambdaQuery()
                        .eq(AppPublication::getApplicationId, application.getId()).last("limit 1"))
                .stream().findFirst().orElseThrow(() -> invalid(
                        "PROVIDER_ROUTE_APPLICATION_NOT_PUBLISHED", "事件路由需要应用先发布资源授权"));
        AppVersion version = versions.selectById(publication.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getApplicationId(), application.getId())) {
            throw conflict("PROVIDER_ROUTE_APPLICATION_VERSION_INVALID", "应用当前发布版本不可用");
        }
        try {
            JsonNode root = objectMapper.readTree(version.getSnapshotJson());
            for (JsonNode grant : root.path("grants")) {
                if (!route.getResourceType().equals(grant.path("resourceType").asText())
                        || !route.getResourceId().equals(grant.path("resourceId").asText())
                        || !route.getActionCode().equals(grant.path("actionCode").asText())
                        || !Objects.equals(route.getTargetSystemId(), longOrNull(grant.get("targetSystemId")))
                        || !Objects.equals(route.getTargetTenantId(), longOrNull(grant.get("targetTenantId")))) {
                    continue;
                }
                if (!"FLOW".equals(route.getResourceType()) || !"START".equals(route.getActionCode())) {
                    throw invalid("PROVIDER_ROUTE_ACTION_UNSUPPORTED", "外部平台事件当前只路由到已发布Flow发起授权");
                }
                boolean external = false;
                for (JsonNode channel : grant.path("rateLimit").path("accessChannels")) {
                    if ("EXTERNAL".equals(channel.asText())) external = true;
                }
                if (!external) {
                    throw invalid("PROVIDER_ROUTE_EXTERNAL_ACCESS_REQUIRED", "事件路由目标必须允许外部应用访问");
                }
                Map<String, Object> dataScope = objectMapper.convertValue(grant.path("dataScope"), MAP_TYPE);
                return new PublishedRoute(route.getResourceType(), route.getResourceId(), route.getActionCode(),
                        route.getTargetSystemId(), route.getTargetTenantId(), dataScope);
            }
        } catch (JsonProcessingException exception) {
            throw conflict("PROVIDER_ROUTE_APPLICATION_VERSION_INVALID", "应用发布版本无法读取");
        }
        throw invalid("PROVIDER_ROUTE_GRANT_NOT_PUBLISHED", "事件路由没有匹配当前应用发布授权");
    }

    private void storeSecret(
            AppDefinition application,
            AppProviderConnection connection,
            String type,
            String plaintext,
            Long accountId,
            boolean required) {
        if (!SECRET_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported provider secret type");
        if (plaintext == null || plaintext.isBlank()) {
            if (required && activeSecret(connection.getId(), type) == null) {
                throw invalid("PROVIDER_SECRET_REQUIRED", "新建外部平台连接缺少必填凭证：" + type);
            }
            return;
        }
        String value = plaintext.strip();
        AppProviderSecret old = activeSecret(connection.getId(), type);
        int nextVersion = secrets.selectList(Wrappers.<AppProviderSecret>lambdaQuery()
                        .eq(AppProviderSecret::getConnectionId, connection.getId())
                        .eq(AppProviderSecret::getSecretType, type))
                .stream().map(AppProviderSecret::getSecretVersion).max(Integer::compareTo).orElse(0) + 1;
        if (old != null) {
            old.setStatus("REVOKED");
            old.setRevokedTime(LocalDateTime.now());
            secrets.updateById(old);
        }
        AppProviderSecret created = new AppProviderSecret();
        created.setConnectionId(connection.getId());
        created.setSecretType(type);
        created.setSecretVersion(nextVersion);
        created.setSecretCiphertext(secretCipher.encrypt(value,
                secretBinding(application.getId(), connection.getId(), type, nextVersion)));
        created.setSecretHint(value.substring(Math.max(0, value.length() - Math.min(4, value.length()))));
        created.setStatus("ACTIVE");
        created.setCreatedByAccountId(accountId);
        secrets.insert(created);
    }

    private String resolveSecret(AppDefinition application, AppProviderConnection connection, String type) {
        AppProviderSecret secret = activeSecret(connection.getId(), type);
        if (secret == null) throw invalid("PROVIDER_SECRET_NOT_CONFIGURED", "外部平台连接缺少" + type + "密钥引用");
        try {
            return secretCipher.decrypt(secret.getSecretCiphertext(), secretBinding(
                    application.getId(), connection.getId(), type, secret.getSecretVersion()));
        } catch (RuntimeException exception) {
            throw invalid("PROVIDER_SECRET_UNAVAILABLE", "外部平台密钥引用无法读取");
        }
    }

    private AppProviderSecret activeSecret(Long connectionId, String type) {
        return secrets.selectList(Wrappers.<AppProviderSecret>lambdaQuery()
                        .eq(AppProviderSecret::getConnectionId, connectionId)
                        .eq(AppProviderSecret::getSecretType, type)
                        .eq(AppProviderSecret::getStatus, "ACTIVE")
                        .orderByDesc(AppProviderSecret::getSecretVersion).last("limit 1"))
                .stream().findFirst().orElse(null);
    }

    private ApplicationProviderModels.ConnectionView view(AppProviderConnection connection) {
        List<ApplicationProviderModels.SecretReferenceView> secretViews = secrets.selectList(
                        Wrappers.<AppProviderSecret>lambdaQuery()
                                .eq(AppProviderSecret::getConnectionId, connection.getId())
                                .orderByAsc(AppProviderSecret::getSecretType)
                                .orderByDesc(AppProviderSecret::getSecretVersion))
                .stream().map(secret -> new ApplicationProviderModels.SecretReferenceView(
                        secret.getSecretType(), secret.getSecretVersion(), secretReference(connection, secret),
                        secret.getSecretHint(), secret.getStatus(), secret.getCreatedTime())).toList();
        AppProviderRoute route = activeRoutes(connection.getId()).stream().findFirst().orElse(null);
        return new ApplicationProviderModels.ConnectionView(
                connection.getId(), connection.getApplicationId(), connection.getProviderCode(),
                connection.getProductCode(), connection.getConnectionKey(), connection.getDisplayName(),
                connection.getTenantKey(), connection.getApplicationKey(), callbackPath(connection),
                connection.getStatus(), connection.getDiagnosticStatus(), connection.getDiagnosticSummary(),
                connection.getDiagnosticTime(), secretViews, route == null ? null : routeView(route),
                connection.getVersion(), connection.getCreatedTime(), connection.getUpdatedTime());
    }

    private ApplicationProviderModels.RouteView routeView(AppProviderRoute route) {
        return new ApplicationProviderModels.RouteView(route.getId(), route.getEventCode(),
                route.getResourceType(), route.getResourceId(), route.getActionCode(), route.getTargetSystemId(),
                route.getTargetTenantId(), route.getStatus(), route.getVersion());
    }

    private List<ApplicationProviderModels.IdentityOptionView> identityOptions(AppDefinition application) {
        List<ApplicationProviderModels.IdentityOptionView> result = new ArrayList<>();
        if ("PLATFORM".equals(application.getContextType())) {
            for (PlatformMember member : platformMembers.selectList(Wrappers.<PlatformMember>lambdaQuery()
                    .eq(PlatformMember::getPlatformId, application.getPlatformId())
                    .eq(PlatformMember::getStatus, "ACTIVE").orderByAsc(PlatformMember::getId))) {
                PlatformAccount account = accounts.selectById(member.getAccountId());
                if (account != null && "ACTIVE".equals(account.getStatus())) {
                    result.add(new ApplicationProviderModels.IdentityOptionView(
                            "PLATFORM", member.getId(), account.getDisplayName(), account.getUsername(),
                            "平台 · " + application.getName()));
                }
            }
            return result;
        }
        for (SystemTenantMember tenantMember : tenantMembers.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, application.getOwnerSystemId())
                .eq(SystemTenantMember::getTenantId, application.getOwnerTenantId())
                .eq(SystemTenantMember::getStatus, "ACTIVE").orderByAsc(SystemTenantMember::getId))) {
            SystemMember member = systemMembers.selectById(tenantMember.getSystemMemberId());
            PlatformAccount account = member == null ? null : accounts.selectById(member.getAccountId());
            if (member != null && "ACTIVE".equals(member.getStatus())
                    && Objects.equals(member.getSystemId(), application.getOwnerSystemId())
                    && account != null && "ACTIVE".equals(account.getStatus())) {
                result.add(new ApplicationProviderModels.IdentityOptionView(
                        "SYSTEM", tenantMember.getId(), member.getDisplayName(), member.getEmployeeNumber(),
                        "系统租户 · " + application.getName()));
            }
        }
        return result;
    }

    private void bindIdentityTarget(
            AppDefinition application,
            AppProviderIdentity identity,
            ApplicationProviderModels.IdentityOptionView target) {
        identity.setTargetType(target.targetType());
        if ("PLATFORM".equals(target.targetType())) {
            identity.setTargetPlatformId(application.getPlatformId());
            identity.setTargetPlatformMemberId(target.targetMemberId());
            identity.setTargetSystemId(null);
            identity.setTargetTenantId(null);
            identity.setTargetTenantMemberId(null);
        } else {
            identity.setTargetPlatformId(null);
            identity.setTargetPlatformMemberId(null);
            identity.setTargetSystemId(application.getOwnerSystemId());
            identity.setTargetTenantId(application.getOwnerTenantId());
            identity.setTargetTenantMemberId(target.targetMemberId());
        }
    }

    private ApplicationProviderModels.IdentityView identityView(
            AppDefinition application, AppProviderIdentity identity) {
        IdentityTarget target = identityTarget(application, identity, false);
        Long memberId = "PLATFORM".equals(identity.getTargetType())
                ? identity.getTargetPlatformMemberId() : identity.getTargetTenantMemberId();
        return new ApplicationProviderModels.IdentityView(
                identity.getId(), identity.getConnectionId(), identity.getExternalUserId(), identity.getTargetType(),
                memberId, target.memberName(), target.employeeNumber(), target.contextName(), identity.getStatus(),
                identity.getVersion(), identity.getCreatedTime(), identity.getUpdatedTime());
    }

    private ApplicationProviderModels.DeliveryView deliveryView(
            AppDefinition application, AppProviderDelivery delivery) {
        AppProviderIdentity identity = identities.selectById(delivery.getIdentityId());
        IdentityTarget target = identity == null
                ? new IdentityTarget(null, "成员映射已删除", null, application.getName(), null, null)
                : identityTarget(application, identity, false);
        return new ApplicationProviderModels.DeliveryView(
                delivery.getId(), delivery.getConnectionId(), delivery.getIdentityId(),
                identity == null ? null : identity.getExternalUserId(), target.memberName(), delivery.getMessageId(),
                delivery.getMessageDeliveryId(), delivery.getIdempotencyKey(), delivery.getDeepLink(),
                delivery.getStatus(), delivery.getErrorCode(), delivery.getCreateTime(), delivery.getUpdateTime(),
                delivery.getSentTime());
    }

    private IdentityTarget identityTarget(
            AppDefinition application, AppProviderIdentity identity, boolean requireActive) {
        if ("PLATFORM".equals(identity.getTargetType())) {
            PlatformMember member = platformMembers.selectById(identity.getTargetPlatformMemberId());
            PlatformAccount account = member == null ? null : accounts.selectById(member.getAccountId());
            boolean valid = member != null && account != null
                    && Objects.equals(identity.getTargetPlatformId(), application.getPlatformId())
                    && Objects.equals(member.getPlatformId(), application.getPlatformId())
                    && (!requireActive || "ACTIVE".equals(member.getStatus()) && "ACTIVE".equals(account.getStatus()));
            if (!valid && requireActive) {
                throw forbidden("PROVIDER_IDENTITY_TARGET_INACTIVE", "映射的平台成员已停用或离开当前平台");
            }
            return new IdentityTarget(account == null ? null : account.getId(),
                    account == null ? "无效平台成员" : account.getDisplayName(),
                    account == null ? null : account.getUsername(), "平台 · " + application.getName(),
                    member == null ? null : member.getId(), null);
        }
        SystemTenantMember tenantMember = tenantMembers.selectById(identity.getTargetTenantMemberId());
        SystemMember member = tenantMember == null ? null : systemMembers.selectById(tenantMember.getSystemMemberId());
        PlatformAccount account = member == null ? null : accounts.selectById(member.getAccountId());
        boolean valid = tenantMember != null && member != null && account != null
                && Objects.equals(identity.getTargetSystemId(), application.getOwnerSystemId())
                && Objects.equals(identity.getTargetTenantId(), application.getOwnerTenantId())
                && Objects.equals(tenantMember.getSystemId(), application.getOwnerSystemId())
                && Objects.equals(tenantMember.getTenantId(), application.getOwnerTenantId())
                && Objects.equals(member.getSystemId(), application.getOwnerSystemId())
                && (!requireActive || "ACTIVE".equals(tenantMember.getStatus())
                && "ACTIVE".equals(member.getStatus()) && "ACTIVE".equals(account.getStatus()));
        if (!valid && requireActive) {
            throw forbidden("PROVIDER_IDENTITY_TARGET_INACTIVE", "映射的系统租户成员已停用或离开当前租户");
        }
        return new IdentityTarget(account == null ? null : account.getId(),
                member == null ? "无效系统成员" : member.getDisplayName(),
                member == null ? null : member.getEmployeeNumber(), "系统租户 · " + application.getName(),
                member == null ? null : member.getId(), tenantMember == null ? null : tenantMember.getId());
    }

    private AuthenticatedContext resolveProviderContext(AppDefinition application, AppProviderIdentity identity) {
        IdentityTarget target = identityTarget(application, identity, true);
        if ("PLATFORM".equals(identity.getTargetType())) {
            ResolvedPermissions resolved = platformPermissions.resolve(application.getPlatformId(), target.accountId());
            PlatformAccount account = accounts.selectById(target.accountId());
            return new AuthenticatedContext(null, account.getId(), application.getPlatformId(), null, null,
                    target.memberId(), null, account.getUsername(), account.getDisplayName(), "PROVIDER",
                    resolved.roleIds(), resolved.permissions(), resolved.dataScopes());
        }
        ResolvedPermissions resolved = systemPermissions.resolve(
                application.getOwnerSystemId(), application.getOwnerTenantId(), target.tenantMemberId());
        PlatformAccount account = accounts.selectById(target.accountId());
        return new AuthenticatedContext(null, account.getId(), application.getPlatformId(),
                application.getOwnerSystemId(), application.getOwnerTenantId(), target.memberId(),
                target.tenantMemberId(), account.getUsername(), target.memberName(), "PROVIDER",
                resolved.roleIds(), resolved.permissions(), resolved.dataScopes());
    }

    private List<AppProviderIdentity> activeIdentities(Long connectionId) {
        return identities.selectList(Wrappers.<AppProviderIdentity>lambdaQuery()
                .eq(AppProviderIdentity::getConnectionId, connectionId)
                .eq(AppProviderIdentity::getStatus, "ACTIVE").orderByAsc(AppProviderIdentity::getId));
    }

    private AppProviderIdentity requireIdentity(Long connectionId, Long identityId) {
        AppProviderIdentity identity = identities.selectById(identityId);
        if (identity == null || !Objects.equals(identity.getConnectionId(), connectionId)) {
            throw notFound("PROVIDER_IDENTITY_NOT_FOUND", "外部平台成员映射不存在");
        }
        return identity;
    }

    private AppProviderIdentity requireActiveIdentity(Long connectionId, String externalUserId) {
        AppProviderIdentity identity = identities.selectList(Wrappers.<AppProviderIdentity>lambdaQuery()
                        .eq(AppProviderIdentity::getConnectionId, connectionId)
                        .eq(AppProviderIdentity::getExternalUserId, externalUserId).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (identity == null) {
            throw forbidden("PROVIDER_IDENTITY_UNMAPPED", "外部平台发送成员尚未映射到系统成员");
        }
        if (!"ACTIVE".equals(identity.getStatus())) {
            throw forbidden("PROVIDER_IDENTITY_DISABLED", "外部平台发送成员映射已停用");
        }
        return identity;
    }

    private String normalizeExternalUserId(String value) {
        String normalized = value == null ? "" : value.strip();
        if (!normalized.matches("[A-Za-z0-9._@-]{1,255}")) {
            throw invalid("PROVIDER_EXTERNAL_USER_ID_INVALID", "外部成员标识须为1到255位字母、数字或 ._-@ 字符");
        }
        return normalized;
    }

    private void validateMessageVariables(Map<String, Object> variables) {
        if (variables == null) return;
        if (variables.size() > 20) throw invalid("PROVIDER_MESSAGE_VARIABLES_INVALID", "模板变量不能超过20个");
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z][a-zA-Z0-9_]{0,99}")) {
                throw invalid("PROVIDER_MESSAGE_VARIABLES_INVALID", "模板变量名称格式无效");
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw invalid("PROVIDER_MESSAGE_VARIABLES_INVALID", "模板变量只能填写文本、数字或布尔值");
            }
            if (value instanceof String text && text.length() > 2000) {
                throw invalid("PROVIDER_MESSAGE_VARIABLES_INVALID", "单个模板变量不能超过2000字符");
            }
        }
    }

    private String providerDeepLink(AppDefinition application, PublishedRoute route) {
        String resource = URLEncoder.encode(route.resourceId(), StandardCharsets.UTF_8);
        return "PLATFORM".equals(application.getContextType())
                ? "/platform/flows?flowCode=" + resource
                : "/systems/" + application.getOwnerSystemId() + "?workspace=flow&flowCode=" + resource;
    }

    private String providerMessageDedup(Long connectionId, String idempotencyKey) {
        return "provider-" + connectionId + "-" + sha256(idempotencyKey);
    }

    private void requireImplementedProvider(AppProviderConnection connection) {
        boolean wecom = "WECOM".equals(connection.getProviderCode())
                && "INTERNAL_APP".equals(connection.getProductCode());
        boolean feishu = "FEISHU".equals(connection.getProviderCode())
                && "CUSTOM_APP".equals(connection.getProductCode());
        boolean dingTalk = "DINGTALK".equals(connection.getProviderCode())
                && "INTERNAL_APP".equals(connection.getProductCode());
        if (!wecom && !feishu && !dingTalk) {
            throw invalid("PROVIDER_PROTOCOL_NOT_IMPLEMENTED", "当前连接的平台产品协议尚未实现");
        }
    }

    private List<AppProviderRoute> activeRoutes(Long connectionId) {
        return routes.selectList(Wrappers.<AppProviderRoute>lambdaQuery()
                .eq(AppProviderRoute::getConnectionId, connectionId)
                .eq(AppProviderRoute::getStatus, "ACTIVE").orderByAsc(AppProviderRoute::getId));
    }

    private AppProviderConnection requireConnection(Long applicationId, Long connectionId) {
        AppProviderConnection connection = connections.selectById(connectionId);
        if (connection == null || !Objects.equals(connection.getApplicationId(), applicationId)) {
            throw notFound("PROVIDER_CONNECTION_NOT_FOUND", "外部平台连接不存在");
        }
        return connection;
    }

    private AppProviderConnection requireCallableConnection(String connectionKey, String provider) {
        AppProviderConnection connection = connections.selectList(Wrappers.<AppProviderConnection>lambdaQuery()
                        .eq(AppProviderConnection::getConnectionKey, connectionKey).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (connection == null || !provider.equals(connection.getProviderCode())) {
            throw notFound("PROVIDER_CONNECTION_NOT_FOUND", "外部平台回调连接不存在");
        }
        if (!"ACTIVE".equals(connection.getStatus())
                || !Set.of("LOCAL_PASSED", "EXTERNAL_PASSED").contains(connection.getDiagnosticStatus())) {
            throw forbidden("PROVIDER_CONNECTION_INACTIVE", "外部平台连接未启用或尚未通过协议诊断");
        }
        return connection;
    }

    private AppDefinition requireCallableApplication(Long applicationId) {
        AppDefinition application = definitions.selectById(applicationId);
        if (application == null || !"ACTIVE".equals(application.getStatus())) {
            throw forbidden("APPLICATION_DISABLED", "外部平台连接所属应用未发布或已停用");
        }
        return application;
    }

    private AppDefinition requireOwned(AuthenticatedContext context, Long applicationId) {
        AppDefinition application = definitions.selectById(applicationId);
        boolean owned = application != null && !"DELETED".equals(application.getStatus())
                && Objects.equals(context.platformId(), application.getPlatformId())
                && (context.systemId() == null
                ? "PLATFORM".equals(application.getContextType())
                && application.getOwnerSystemId() == null && application.getOwnerTenantId() == null
                : "SYSTEM".equals(application.getContextType())
                && Objects.equals(context.systemId(), application.getOwnerSystemId())
                && Objects.equals(context.tenantId(), application.getOwnerTenantId()));
        if (!owned) throw notFound("APPLICATION_NOT_FOUND", "应用不存在或不在当前上下文");
        return application;
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (context == null || context.platformId() == null) {
            throw unauthorized("AUTHENTICATION_REQUIRED", "请先登录");
        }
        String resourceCode = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "APPLICATION", resourceCode, action)
                && !permissionChecker.allows(context, "APPLICATION", "*", action)) {
            throw forbidden("PERMISSION_DENIED", "没有应用 " + action + " 权限");
        }
    }

    private void validateWeComIdentity(String tenantKey, String applicationKey) {
        if (tenantKey == null || !tenantKey.strip().matches("[A-Za-z0-9_-]{2,255}")) {
            throw invalid("PROVIDER_TENANT_KEY_INVALID", "企业微信CorpID格式无效");
        }
        if (applicationKey == null || !applicationKey.strip().matches("[1-9][0-9]{0,19}")) {
            throw invalid("PROVIDER_APPLICATION_KEY_INVALID", "企业微信AgentID必须为有效数字");
        }
    }

    private void validateFeishuIdentity(String appId) {
        if (appId == null || !appId.strip().matches("[A-Za-z0-9_-]{3,255}")) {
            throw invalid("PROVIDER_TENANT_KEY_INVALID", "飞书App ID格式无效");
        }
    }

    private void validateDingTalkIdentity(String corpId, String clientId) {
        if (corpId == null || !corpId.strip().matches("[A-Za-z0-9_-]{2,255}")) {
            throw invalid("PROVIDER_TENANT_KEY_INVALID", "钉钉CorpID格式无效");
        }
        if (clientId == null || !clientId.strip().matches("[A-Za-z0-9_-]{3,255}")) {
            throw invalid("PROVIDER_APPLICATION_KEY_INVALID", "钉钉Client ID格式无效");
        }
    }

    /** 企业微信诊断仅证明本地凭证格式和协议算法可用，不冒充真实企业连通。 */
    private void diagnoseWeCom(
            AppDefinition application, AppProviderConnection connection, List<String> checks) {
        validateWeComIdentity(connection.getTenantKey(), connection.getApplicationKey());
        String callbackToken = resolveSecret(application, connection, "CALLBACK_TOKEN");
        String encodingKey = resolveSecret(application, connection, "ENCODING_AES_KEY");
        if (callbackToken.isBlank()) {
            throw invalid("PROVIDER_CALLBACK_TOKEN_INVALID", "企业微信回调Token不能为空");
        }
        String probe = "wecom-local-probe-" + connection.getConnectionKey();
        String encrypted = WeComCallbackCodec.encrypt(encodingKey, probe, connection.getTenantKey());
        String signature = WeComCallbackCodec.signature(callbackToken, "1", "local", encrypted);
        WeComCallbackCodec.verify(callbackToken, "1", "local", encrypted, signature);
        WeComCallbackCodec.Decoded decoded = WeComCallbackCodec.decrypt(encodingKey, encrypted);
        if (!probe.equals(decoded.message()) || !connection.getTenantKey().equals(decoded.receiveId())) {
            throw invalid("PROVIDER_LOCAL_PROTOCOL_FAILED", "企业微信本地回调协议自检失败");
        }
        checks.add("CorpID、AgentID和回调凭证格式有效");
        checks.add("EncodingAESKey本地验签、加密与解密一致");
        checks.add("外部UserID将映射到成员当前权限后再进入统一应用网关");
    }

    /** 飞书诊断覆盖Verification Token及可选Encrypt Key的官方请求地址模式。 */
    private void diagnoseFeishu(
            AppDefinition application, AppProviderConnection connection, List<String> checks) {
        validateFeishuIdentity(connection.getTenantKey());
        String appSecret = resolveSecret(application, connection, "API_SECRET");
        String verificationToken = resolveSecret(application, connection, "CALLBACK_TOKEN");
        if (appSecret.isBlank() || verificationToken.isBlank()) {
            throw invalid("PROVIDER_SECRET_INVALID", "飞书App Secret和Verification Token不能为空");
        }
        checks.add("App ID、App Secret和Verification Token已安全保存");
        AppProviderSecret encryptSecret = activeSecret(connection.getId(), "ENCODING_AES_KEY");
        if (encryptSecret == null) {
            checks.add("当前使用Verification Token明文请求地址模式；可选配Encrypt Key增强保护");
        } else {
            String encryptKey = resolveSecret(application, connection, "ENCODING_AES_KEY");
            if (encryptKey.isBlank()) {
                throw invalid("FEISHU_ENCRYPT_KEY_INVALID", "飞书Encrypt Key不能为空");
            }
            String probe = writeJson(Map.of("type", "url_verification", "token", verificationToken,
                    "challenge", "local-probe"));
            String encrypted = FeishuCallbackCodec.encrypt(encryptKey, probe);
            String decrypted = FeishuCallbackCodec.decrypt(encryptKey, encrypted);
            String raw = writeJson(Map.of("encrypt", encrypted));
            String signature = FeishuCallbackCodec.signature(encryptKey, "1", "local", raw);
            FeishuCallbackCodec.verifySignature(encryptKey, "1", "local", raw, signature);
            if (!probe.equals(decrypted)) {
                throw invalid("PROVIDER_LOCAL_PROTOCOL_FAILED", "飞书本地加密协议自检失败");
            }
            checks.add("Encrypt Key本地签名、AES-256-CBC加密与解密一致");
        }
        checks.add("飞书Open ID或User ID将映射到成员当前权限后再进入统一应用网关");
    }

    /** 钉钉诊断使用官方HTTP回调算法做本地闭环，Stream模式和真实API调用仍需外部租户。 */
    private void diagnoseDingTalk(
            AppDefinition application, AppProviderConnection connection, List<String> checks) {
        validateDingTalkIdentity(connection.getTenantKey(), connection.getApplicationKey());
        String clientSecret = resolveSecret(application, connection, "API_SECRET");
        String callbackToken = resolveSecret(application, connection, "CALLBACK_TOKEN");
        String encodingKey = resolveSecret(application, connection, "ENCODING_AES_KEY");
        if (clientSecret.isBlank() || callbackToken.isBlank()) {
            throw invalid("PROVIDER_SECRET_INVALID", "钉钉Client Secret和回调Token不能为空");
        }
        String probe = writeJson(Map.of("EventType", "check_url", "Random", connection.getConnectionKey()));
        String encrypted = DingTalkCallbackCodec.encrypt(encodingKey, probe, connection.getApplicationKey());
        String signature = DingTalkCallbackCodec.signature(callbackToken, "1", "local", encrypted);
        DingTalkCallbackCodec.verify(callbackToken, "1", "local", encrypted, signature);
        DingTalkCallbackCodec.Decoded decoded = DingTalkCallbackCodec.decrypt(encodingKey, encrypted);
        if (!probe.equals(decoded.message()) || !connection.getApplicationKey().equals(decoded.ownerKey())) {
            throw invalid("PROVIDER_LOCAL_PROTOCOL_FAILED", "钉钉本地回调协议自检失败");
        }
        checks.add("CorpID、Client ID、Client Secret和回调Token已安全保存");
        checks.add("AES Key本地SHA-1验签、加密、解密和Client ID所有者校验一致");
        checks.add("HTTP事件回调已就绪；Stream模式和真实消息发送等待外部租户验证");
        checks.add("钉钉UserId或StaffId将映射到成员当前权限后再进入统一应用网关");
    }

    private String providerName(String providerCode) {
        return switch (providerCode) {
            case "WECOM" -> "企业微信";
            case "FEISHU" -> "飞书";
            case "DINGTALK" -> "钉钉";
            case "WECHAT" -> "微信";
            default -> "外部平台";
        };
    }

    private void requireCallbackTimestamp(String timestamp) {
        try {
            long epoch = Long.parseLong(timestamp);
            if (epoch > 10_000_000_000L) epoch /= 1000;
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - epoch) > CALLBACK_WINDOW_SECONDS) {
                throw unauthorized("PROVIDER_CALLBACK_TIMESTAMP_EXPIRED", "外部平台回调时间戳已超出五分钟窗口");
            }
        } catch (NumberFormatException exception) {
            throw unauthorized("PROVIDER_CALLBACK_TIMESTAMP_INVALID", "外部平台回调时间戳无效");
        }
    }

    private void requireReceiver(AppProviderConnection connection, String receiveId) {
        if (!Objects.equals(connection.getTenantKey(), receiveId)) {
            throw forbidden("PROVIDER_CALLBACK_RECEIVER_MISMATCH", "企业微信回调接收方与连接CorpID不一致");
        }
    }

    private void requireDingTalkOwner(AppProviderConnection connection, String ownerKey) {
        if (!Objects.equals(connection.getApplicationKey(), ownerKey)) {
            throw forbidden("DINGTALK_CALLBACK_OWNER_MISMATCH", "钉钉回调所有者与当前连接Client ID不一致");
        }
    }

    private Map<String, String> dingTalkSuccess(String token, String encodingKey, String ownerKey) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String encrypted = DingTalkCallbackCodec.encrypt(encodingKey, "success", ownerKey);
        return Map.of(
                "msg_signature", DingTalkCallbackCodec.signature(token, timestamp, nonce, encrypted),
                "timeStamp", timestamp,
                "nonce", nonce,
                "encrypt", encrypted);
    }

    private Map<String, String> readXml(String value) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(value)));
            Element root = document.getDocumentElement();
            if (root == null || !"xml".equalsIgnoreCase(root.getTagName())) {
                throw new IllegalArgumentException("unexpected root");
            }
            Map<String, String> result = new LinkedHashMap<>();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) result.put(node.getNodeName(), node.getTextContent());
            }
            return result;
        } catch (Exception exception) {
            throw invalid("PROVIDER_CALLBACK_XML_INVALID", "企业微信回调XML格式无效");
        }
    }

    private JsonNode readJson(String value, String code, String message) {
        JsonNode result;
        try {
            result = objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw invalid(code, message);
        }
        if (result == null || !result.isObject()) throw invalid(code, message);
        return result;
    }

    private String firstText(JsonNode source, String... fields) {
        if (source == null || !source.isObject()) return null;
        for (String field : fields) {
            String value = source.path(field).asText(null);
            if (value != null && !value.isBlank()) return value.strip();
        }
        return null;
    }

    private String eventCode(Map<String, String> payload) {
        String type = required(payload, "MsgType", "PROVIDER_EVENT_TYPE_REQUIRED", "企业微信回调缺少MsgType")
                .strip().toLowerCase(Locale.ROOT);
        if ("text".equals(type)) return "MESSAGE_TEXT";
        if ("event".equals(type)) {
            String event = required(payload, "Event", "PROVIDER_EVENT_TYPE_REQUIRED", "企业微信事件缺少Event")
                    .strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
            return "EVENT_" + event;
        }
        return "MESSAGE_" + type.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private String providerEventId(Map<String, String> payload, String payloadHash) {
        String messageId = blankToNull(payload.get("MsgId"));
        if (messageId != null) return "msg:" + messageId;
        return "event:" + sha256(String.join("|",
                value(payload, "FromUserName"), value(payload, "CreateTime"), value(payload, "Event"),
                value(payload, "ChangeType"), value(payload, "EventKey"), payloadHash));
    }

    private AppProviderEvent event(Long connectionId, String eventId) {
        return events.selectList(Wrappers.<AppProviderEvent>lambdaQuery()
                        .eq(AppProviderEvent::getConnectionId, connectionId)
                        .eq(AppProviderEvent::getProviderEventId, eventId).last("limit 1"))
                .stream().findFirst().orElse(null);
    }

    private void finishEvent(AppProviderEvent event, String status, Long callId, String responseCode) {
        event.setStatus(status);
        event.setApplicationCallId(callId);
        event.setResponseCode(responseCode);
        event.setProcessedTime(LocalDateTime.now());
        events.updateById(event);
    }

    private String callbackPath(AppProviderConnection connection) {
        String providerPath = switch (connection.getProviderCode()) {
            case "FEISHU" -> "feishu";
            case "DINGTALK" -> "dingtalk";
            default -> "wecom";
        };
        return "/api/provider-callbacks/v1/" + providerPath + "/" + connection.getConnectionKey();
    }

    private String secretReference(AppProviderConnection connection, AppProviderSecret secret) {
        return "app-provider-secret/" + connection.getConnectionKey() + "/"
                + secret.getSecretType().toLowerCase(Locale.ROOT).replace('_', '-') + "/v" + secret.getSecretVersion();
    }

    private String secretBinding(Long applicationId, Long connectionId, String type, int version) {
        return "provider:" + applicationId + ":" + connectionId + ":" + type + ":" + version;
    }

    private Long longOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.longValue();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize provider application call", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String required(Map<String, String> values, String key, String code, String message) {
        String value = blankToNull(values.get(key));
        if (value == null) throw invalid(code, message);
        return value;
    }

    private String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "");
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void audit(
            AuthenticatedContext context, String requestId, String eventCode, Long applicationId, Map<String, ?> detail) {
        auditRecorder.record(requestId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, "APPLICATION", applicationId.toString(), "SUCCESS", detail);
    }

    private void auditProvider(
            AppDefinition application,
            String requestId,
            String eventCode,
            AppProviderConnection connection,
            Map<String, ?> detail) {
        auditRecorder.record(requestId, application.getCreatedByAccountId(), application.getOwnerSystemId(),
                application.getOwnerTenantId(), null, eventCode, "APPLICATION_PROVIDER_CONNECTION",
                connection.getId().toString(), "SUCCESS", detail);
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException unauthorized(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNAUTHORIZED);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private DomainException notFound(String code, String message) {
        return new DomainException(code, message, HttpStatus.NOT_FOUND);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private record PublishedRoute(
            String resourceType,
            String resourceId,
            String actionCode,
            Long targetSystemId,
            Long targetTenantId,
            Map<String, Object> dataScope) {
    }

    /** 统一保存映射成员的当前账号和上下文信息，避免回调使用过期快照。 */
    private record IdentityTarget(
            Long accountId,
            String memberName,
            String employeeNumber,
            String contextName,
            Long memberId,
            Long tenantMemberId) {
    }
}
