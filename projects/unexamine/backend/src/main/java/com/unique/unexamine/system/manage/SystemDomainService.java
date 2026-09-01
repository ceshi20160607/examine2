package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreSetting;
import com.unique.unexamine.foundation.base.service.CoreSettingBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SysSystemDomain;
import com.unique.unexamine.system.base.service.SysSystemDomainBaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SystemDomainService {
    private static final String VERIFICATION_PATH = "/.well-known/unexamine-domain-verification.txt";

    private final SysSystemDomainBaseService domainService;
    private final CoreSettingBaseService settingService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final boolean allowPrivateAddresses;

    public SystemDomainService(
            SysSystemDomainBaseService domainService,
            CoreSettingBaseService settingService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            @Value("${app.domain-verification.allow-private-addresses:false}") boolean allowPrivateAddresses) {
        this.domainService = domainService;
        this.settingService = settingService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    @Transactional(readOnly = true)
    public List<SystemDomainView> list(AuthenticatedContext context) {
        requireSystem(context);
        return domainService.selectList(Wrappers.<SysSystemDomain>lambdaQuery()
                        .eq(SysSystemDomain::getSystemId, context.systemId()))
                .stream().sorted(Comparator.comparing(SysSystemDomain::getId).reversed())
                .map(this::view).toList();
    }

    @Transactional
    public SystemDomainView save(AuthenticatedContext context, SaveSystemDomainRequest input, String traceId) {
        requireSystem(context);
        String host = normalizeHost(input.host());
        String basePath = normalizeBasePath(input.basePath());
        SysSystemDomain domain;
        if (input.id() == null) {
            if (input.expectedVersion() != null) {
                throw new DomainException("DOMAIN_VERSION_INVALID", "新访问地址不能指定历史版本", HttpStatus.CONFLICT);
            }
            domain = new SysSystemDomain();
            domain.setSystemId(context.systemId());
        } else {
            domain = requireDomain(context.systemId(), input.id());
            if (input.expectedVersion() == null || !input.expectedVersion().equals(domain.getVersion())) {
                throw conflict();
            }
        }
        domain.setDomainType(input.domainType());
        domain.setHost(host);
        domain.setBasePath(basePath);
        domain.setTlsRequired(input.tlsRequired());
        domain.setVerifiedAt(null);
        domain.setStatus("PENDING_VERIFICATION");
        if (domain.getId() == null) {
            domainService.insert(domain);
        } else if (domainService.updateById(domain) != 1) {
            throw conflict();
        }
        saveChallenge(context.platformId(), domain.getSystemId(), domain.getId(), UUID.randomUUID().toString());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_DOMAIN_SAVED", "SYSTEM_DOMAIN", domain.getId().toString(), "PENDING_VERIFICATION",
                Map.of("domainType", domain.getDomainType(), "host", domain.getHost(),
                        "basePath", domain.getBasePath(), "tlsRequired", domain.getTlsRequired()));
        return view(domainService.selectById(domain.getId()));
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SystemDomainView verify(AuthenticatedContext context, Long domainId, String traceId) {
        requireSystem(context);
        SysSystemDomain domain = requireDomain(context.systemId(), domainId);
        String token = challenge(domain.getSystemId(), domain.getId());
        URI uri = verificationUri(domain);
        try {
            validateDestination(uri);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4))
                    .header("Accept", "text/plain").GET().build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !token.equals(response.body().strip())) {
                failVerification(context, domain, traceId, "PROOF_MISMATCH");
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            failVerification(context, domain, traceId, "PROOF_UNREACHABLE");
        }
        domain.setVerifiedAt(LocalDateTime.now());
        domain.setStatus("VERIFIED");
        if (domainService.updateById(domain) != 1) {
            throw conflict();
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_DOMAIN_VERIFIED", "SYSTEM_DOMAIN", domainId.toString(), "SUCCESS",
                Map.of("verificationUrl", uri.toString(), "host", domain.getHost()));
        return view(domainService.selectById(domainId));
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Transactional(noRollbackFor = DomainException.class)
    public SystemDomainView publish(AuthenticatedContext context, Long domainId, String traceId) {
        requireSystem(context);
        SysSystemDomain domain = requireDomain(context.systemId(), domainId);
        if (!"VERIFIED".equals(domain.getStatus()) || domain.getVerifiedAt() == null) {
            auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "SYSTEM_DOMAIN_PUBLISH", "SYSTEM_DOMAIN", domainId.toString(),
                    "DOMAIN_NOT_VERIFIED", Map.of("status", domain.getStatus()));
            throw new DomainException("DOMAIN_NOT_VERIFIED", "访问地址通过所有权验证后才能发布", HttpStatus.CONFLICT);
        }
        domain.setStatus("PUBLISHED");
        if (domainService.updateById(domain) != 1) {
            throw conflict();
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_DOMAIN_PUBLISHED", "SYSTEM_DOMAIN", domainId.toString(), "SUCCESS",
                Map.of("host", domain.getHost(), "basePath", domain.getBasePath()));
        return view(domainService.selectById(domainId));
    }

    private void failVerification(AuthenticatedContext context, SysSystemDomain domain, String traceId, String reason) {
        domain.setVerifiedAt(null);
        domain.setStatus("VERIFICATION_FAILED");
        domainService.updateById(domain);
        auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "SYSTEM_DOMAIN_VERIFY", "SYSTEM_DOMAIN", domain.getId().toString(), reason,
                Map.of("host", domain.getHost(), "verificationUrl", verificationUri(domain).toString()));
        throw new DomainException("DOMAIN_VERIFICATION_FAILED",
                "未在验证地址读到正确证明，访问地址不会启用", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private void validateDestination(URI uri) throws Exception {
        if (uri.getHost() == null) {
            throw new DomainException("DOMAIN_HOST_INVALID", "访问地址主机名无效", HttpStatus.BAD_REQUEST);
        }
        if (allowPrivateAddresses) {
            return;
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new DomainException("DOMAIN_PRIVATE_ADDRESS_FORBIDDEN", "访问地址不能指向内网或本机地址",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    private URI verificationUri(SysSystemDomain domain) {
        String prefix = Boolean.TRUE.equals(domain.getTlsRequired()) ? "https://" : "http://";
        String base = "/".equals(domain.getBasePath()) ? "" : domain.getBasePath();
        return URI.create(prefix + domain.getHost() + base + VERIFICATION_PATH);
    }

    private String normalizeHost(String value) {
        String host = value.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("http://") || host.startsWith("https://") || host.contains("/") || host.contains(" ")
                || !host.matches("(?:[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?|\\[[0-9a-f:]+\\])(?::[0-9]{1,5})?")) {
            throw new DomainException("DOMAIN_HOST_INVALID", "主机只填写域名或主机名，可带端口但不能带协议和路径",
                    HttpStatus.BAD_REQUEST);
        }
        return host;
    }

    private String normalizeBasePath(String value) {
        String path = value.strip();
        if (!path.startsWith("/") || path.contains("..") || path.contains("?") || path.contains("#")) {
            throw new DomainException("DOMAIN_BASE_PATH_INVALID", "基础路径必须以 / 开头且不能包含跳转片段",
                    HttpStatus.BAD_REQUEST);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void saveChallenge(Long platformId, Long systemId, Long domainId, String token) {
        String key = challengeKey(domainId);
        CoreSetting setting = settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                        .eq(CoreSetting::getContextType, "SYSTEM")
                        .eq(CoreSetting::getSystemId, systemId)
                        .isNull(CoreSetting::getTenantId)
                        .eq(CoreSetting::getCategory, "SYSTEM_INFO")
                        .eq(CoreSetting::getSettingKey, key))
                .stream().findFirst().orElse(null);
        if (setting == null) {
            setting = new CoreSetting();
            setting.setContextType("SYSTEM");
            setting.setPlatformId(platformId);
            setting.setSystemId(systemId);
            setting.setTenantId(null);
            setting.setCategory("SYSTEM_INFO");
            setting.setSettingKey(key);
            setting.setValueType("JSON");
            setting.setSensitive(false);
            setting.setStatus("ACTIVE");
            setting.setValueJson("{\"token\":\"" + token + "\"}");
            settingService.insert(setting);
        } else {
            setting.setValueJson("{\"token\":\"" + token + "\"}");
            setting.setStatus("ACTIVE");
            settingService.updateById(setting);
        }
    }

    private String challenge(Long systemId, Long domainId) {
        CoreSetting setting = settingService.selectList(Wrappers.<CoreSetting>lambdaQuery()
                        .eq(CoreSetting::getContextType, "SYSTEM")
                        .eq(CoreSetting::getSystemId, systemId)
                        .isNull(CoreSetting::getTenantId)
                        .eq(CoreSetting::getCategory, "SYSTEM_INFO")
                        .eq(CoreSetting::getSettingKey, challengeKey(domainId)))
                .stream().findFirst().orElseThrow(() -> new DomainException(
                        "DOMAIN_VERIFICATION_CHALLENGE_MISSING", "验证挑战不存在，请重新保存访问地址", HttpStatus.CONFLICT));
        try {
            JsonNode value = objectMapper.readTree(setting.getValueJson());
            return value.path("token").asText();
        } catch (Exception exception) {
            throw new DomainException("DOMAIN_VERIFICATION_CHALLENGE_INVALID", "验证挑战无法读取，请重新保存访问地址",
                    HttpStatus.CONFLICT);
        }
    }

    private String challengeKey(Long domainId) {
        return "domain.verification." + domainId;
    }

    private SysSystemDomain requireDomain(Long systemId, Long domainId) {
        SysSystemDomain domain = domainService.selectById(domainId);
        if (domain == null || !systemId.equals(domain.getSystemId())) {
            throw new DomainException("SYSTEM_DOMAIN_NOT_FOUND", "访问地址不存在", HttpStatus.NOT_FOUND);
        }
        return domain;
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统后台", HttpStatus.BAD_REQUEST);
        }
    }

    private SystemDomainView view(SysSystemDomain domain) {
        String token = challenge(domain.getSystemId(), domain.getId());
        return new SystemDomainView(domain.getId(), domain.getDomainType(), domain.getHost(), domain.getBasePath(),
                Boolean.TRUE.equals(domain.getTlsRequired()), domain.getStatus(), domain.getVerifiedAt(), token,
                verificationUri(domain).toString(), domain.getVersion());
    }

    private DomainException conflict() {
        return new DomainException("CONCURRENT_MODIFICATION", "访问地址已被其他操作修改，请刷新后重试",
                HttpStatus.CONFLICT);
    }
}
