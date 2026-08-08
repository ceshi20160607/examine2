package com.unique.examine.plat.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.manage.service.ClientRequest;
import com.unique.examine.plat.manage.service.IssuedSession;
import com.unique.examine.plat.manage.service.PlatformMutationSupport;
import com.unique.examine.plat.manage.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;

@Service
public class EnterpriseIdentityService {
    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final Duration MFA_TTL = Duration.ofMinutes(5);
    private final IdentityRepository repository;
    private final EnterpriseIdentityClient client;
    private final TotpService totp;
    private final SessionService sessions;
    private final IdService ids;
    private final AuditFacade audit;
    private final PlatformMutationSupport mutations;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EnterpriseIdentityService(IdentityRepository repository, EnterpriseIdentityClient client,
                                     TotpService totp, SessionService sessions, IdService ids,
                                     AuditFacade audit, PlatformMutationSupport mutations,
                                     ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.totp = totp;
        this.sessions = sessions;
        this.ids = ids;
        this.audit = audit;
        this.mutations = mutations;
        this.json = json;
        this.clock = clock;
    }

    public List<IdentityApi.ProviderView> list() {
        return repository.listProviders().stream().map(this::view).toList();
    }

    @Transactional
    public IdentityApi.ProviderView create(AuthenticatedSession session, IdentityApi.ProviderCommand raw,
                                            ClientRequest request) {
        var command = validate(raw, false);
        var provider = repository.insertProvider(ids.nextId(), command, session.accountId(), now());
        mutations.success(session, request, "IDENTITY_PROVIDER", Long.toString(provider.id()),
                "IDENTITY_PROVIDER_DRAFT_CREATED", null, view(provider), null);
        return view(provider);
    }

    @Transactional
    public IdentityApi.ProviderView update(AuthenticatedSession session, long providerId,
                                            IdentityApi.ProviderCommand raw, ClientRequest request) {
        var command = validate(raw, true);
        var before = repository.findProvider(providerId).orElseThrow(EnterpriseIdentityService::notFound);
        var provider = repository.updateProvider(providerId, command, session.accountId(), now())
                .orElseThrow(EnterpriseIdentityService::versionConflict);
        mutations.success(session, request, "IDENTITY_PROVIDER", Long.toString(provider.id()),
                "IDENTITY_PROVIDER_DRAFT_UPDATED", view(before), view(provider), null);
        return view(provider);
    }

    public IdentityApi.PreflightView preflight(AuthenticatedSession session, long providerId,
                                                long expectedVersion, ClientRequest request) {
        var provider = repository.findProvider(providerId).orElseThrow(EnterpriseIdentityService::notFound);
        if (provider.version() != expectedVersion) throw versionConflict();
        var result = client.preflight(provider);
        var checkedAt = now();
        var updated = repository.recordPreflight(providerId, expectedVersion, result.successful(),
                result.failureCode(), checkedAt).orElseThrow(EnterpriseIdentityService::versionConflict);
        recordSecurity("IDENTITY_PROVIDER_PREFLIGHT", session.accountId(), updated, null,
                result.successful() ? "SUCCESS" : "FAILED", result.failureCode(), request,
                Map.of("checks", result.checks(), "version", updated.version()));
        return new IdentityApi.PreflightView(Long.toString(providerId), provider.protocol().name(),
                result.successful(), result.failureCode(), result.checks(), checkedAt, updated.version());
    }

    @Transactional
    public IdentityApi.ProviderView publish(AuthenticatedSession session, long providerId,
                                             long expectedVersion, ClientRequest request) {
        var before = repository.findProvider(providerId).orElseThrow(EnterpriseIdentityService::notFound);
        if (!"PASSED".equals(before.preflightStatus()) || before.preflightVersion() == null
                || before.preflightVersion() != before.version()) {
            throw failure("IDENTITY_PREFLIGHT_REQUIRED", "当前版本必须先通过预检", HttpStatus.CONFLICT);
        }
        var provider = repository.transitionProvider(providerId, expectedVersion,
                IdentityApi.Status.PUBLISHED, session.accountId(), now())
                .orElseThrow(EnterpriseIdentityService::versionConflict);
        mutations.success(session, request, "IDENTITY_PROVIDER", Long.toString(provider.id()),
                "IDENTITY_PROVIDER_PUBLISHED", view(before), view(provider), null);
        return view(provider);
    }

    @Transactional
    public IdentityApi.ProviderView disable(AuthenticatedSession session, long providerId,
                                             long expectedVersion, ClientRequest request) {
        var before = repository.findProvider(providerId).orElseThrow(EnterpriseIdentityService::notFound);
        var provider = repository.transitionProvider(providerId, expectedVersion,
                IdentityApi.Status.DISABLED, session.accountId(), now())
                .orElseThrow(EnterpriseIdentityService::versionConflict);
        mutations.success(session, request, "IDENTITY_PROVIDER", Long.toString(provider.id()),
                "IDENTITY_PROVIDER_DISABLED", view(before), view(provider), null);
        return view(provider);
    }

    @Transactional
    public IdentityApi.LoginStart start(String providerCode, String systemIdValue,
                                         String tenantIdValue, String redirectUri) {
        var systemId = optionalId(systemIdValue);
        var tenantId = optionalId(tenantIdValue);
        if ((systemId == null) != (tenantId == null)) throw validation("systemId 和 tenantId 必须同时提供");
        if (systemId != null && !repository.tenantExists(systemId, tenantId)) {
            throw failure("IDENTITY_SCOPE_NOT_FOUND", "系统租户范围不存在", HttpStatus.NOT_FOUND);
        }
        var provider = repository.findPublishedProvider(token(providerCode, "providerCode", 64), systemId, tenantId)
                .orElseThrow(EnterpriseIdentityService::notFound);
        if (provider.protocol() == IdentityApi.Protocol.LDAP || provider.protocol() == IdentityApi.Protocol.AD) {
            throw failure("IDENTITY_PROTOCOL_CALLBACK_UNSUPPORTED", "当前身份源尚不支持浏览器回调登录",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (provider.systemId() != null
                && (!provider.systemId().equals(systemId) || !provider.tenantId().equals(tenantId))) {
            throw failure("IDENTITY_SCOPE_MISMATCH", "身份源不属于请求的系统租户", HttpStatus.FORBIDDEN);
        }
        var safeRedirect = redirectUri == null || redirectUri.isBlank() ? "/" : redirectUri.strip();
        if (!safeRedirect.startsWith("/") || safeRedirect.startsWith("//") || safeRedirect.contains("\\")) {
            throw validation("redirectUri 必须是站内绝对路径");
        }
        var state = randomToken(32);
        var nonce = provider.protocol() == IdentityApi.Protocol.SAML2
                ? "_" + randomToken(24) : randomToken(24);
        var verifier = randomToken(48);
        var expiresAt = now().plus(STATE_TTL);
        repository.createAuthState(ids.nextId(), hash(state), provider, systemId, tenantId,
                safeRedirect, nonce, verifier, expiresAt, now());
        var challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        var uri = authorizationUrl(provider, state, nonce, challenge);
        return new IdentityApi.LoginStart(uri, state, expiresAt);
    }

    @Transactional
    public Completion callback(IdentityApi.CallbackCommand command, ClientRequest request) {
        if (command == null) throw validation("回调参数不能为空");
        var stateValue = token(command.state(), "state", 256);
        var code = token(command.code(), "code", 1_000_000);
        var state = repository.consumeAuthState(hash(stateValue), now())
                .orElseThrow(() -> failure("IDENTITY_STATE_INVALID", "登录状态已失效", HttpStatus.UNAUTHORIZED));
        var provider = state.provider();
        if (provider.status() != IdentityApi.Status.PUBLISHED) {
            throw failure("IDENTITY_PROVIDER_DISABLED", "身份源已停用", HttpStatus.UNAUTHORIZED);
        }
        try {
            var identity = client.exchange(provider, code, state.codeVerifier(), state.nonce(), now());
            enforceDomain(provider, identity.email());
            var accountId = mapAccount(provider, identity);
            var memberId = mapMember(provider, identity, accountId);
            repository.touchBinding(provider.id(), identity.subject(), now());
            var enrollment = repository.findEnrollment(accountId, state.systemId(), state.tenantId());
            boolean requireMfa = provider.mfaPolicy() == IdentityApi.MfaPolicy.REQUIRED
                    || provider.mfaPolicy() == IdentityApi.MfaPolicy.OPTIONAL && enrollment.isPresent();
            if (requireMfa) {
                var rawChallenge = randomToken(32);
                repository.createMfaChallenge(ids.nextId(), hash(rawChallenge), provider, accountId,
                        state.systemId(), state.tenantId(), now().plus(MFA_TTL), now());
                recordSecurity("SSO_LOGIN", accountId, provider, identity.subject(), "SUCCESS", null,
                    request, loginDetail(provider, identity, memberId, false, "MFA_REQUIRED"));
                return new Completion(null, rawChallenge, enrollment.isEmpty(), state.redirectUri());
            }
            var issued = sessions.issuePlatform(repository.account(accountId));
            recordSecurity("SSO_LOGIN", accountId, provider, identity.subject(), "SUCCESS", null,
                    request, loginDetail(provider, identity, memberId, false, "COMPLETED"));
            return new Completion(issued, null, false, state.redirectUri());
        } catch (BusinessException e) {
            recordSecurity("SSO_LOGIN", null, provider, null, "DENIED", e.code(), request, Map.of());
            throw e;
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            recordSecurity("SSO_LOGIN", null, provider, null, "FAILED", e.code, request, Map.of());
            throw failure(e.code, "企业身份认证失败", HttpStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    public Completion directoryLogin(IdentityApi.DirectoryLoginCommand command, ClientRequest request) {
        if (command == null) throw validation("目录登录参数不能为空");
        var systemId = optionalId(command.systemId());
        var tenantId = optionalId(command.tenantId());
        if ((systemId == null) != (tenantId == null)) throw validation("systemId 和 tenantId 必须同时提供");
        if (systemId != null && !repository.tenantExists(systemId, tenantId)) {
            throw failure("IDENTITY_SCOPE_NOT_FOUND", "系统租户范围不存在", HttpStatus.NOT_FOUND);
        }
        var provider = repository.findPublishedProvider(token(command.providerCode(), "providerCode", 64),
                        systemId, tenantId).orElseThrow(EnterpriseIdentityService::notFound);
        if (provider.protocol() != IdentityApi.Protocol.LDAP && provider.protocol() != IdentityApi.Protocol.AD) {
            throw failure("IDENTITY_PROTOCOL_DIRECTORY_UNSUPPORTED", "当前身份源不是 LDAP/AD",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var password = command.password() == null ? null : command.password().toCharArray();
        try {
            var external = client.authenticateDirectory(provider,
                    token(command.username(), "username", 254), password, now());
            enforceDomain(provider, external.email());
            var accountId = mapAccount(provider, external);
            var memberId = mapMember(provider, external, accountId);
            repository.touchBinding(provider.id(), external.subject(), now());
            var enrollment = repository.findEnrollment(accountId, systemId, tenantId);
            var requireMfa = provider.mfaPolicy() == IdentityApi.MfaPolicy.REQUIRED
                    || provider.mfaPolicy() == IdentityApi.MfaPolicy.OPTIONAL && enrollment.isPresent();
            if (requireMfa) {
                var rawChallenge = randomToken(32);
                repository.createMfaChallenge(ids.nextId(), hash(rawChallenge), provider, accountId,
                        systemId, tenantId, now().plus(MFA_TTL), now());
                recordSecurity("DIRECTORY_LOGIN", accountId, provider, external.subject(), "SUCCESS", null,
                        request, loginDetail(provider, external, memberId, false, "MFA_REQUIRED"));
                return new Completion(null, rawChallenge, enrollment.isEmpty(), "/");
            }
            var issued = sessions.issuePlatform(repository.account(accountId));
            recordSecurity("DIRECTORY_LOGIN", accountId, provider, external.subject(), "SUCCESS", null,
                    request, loginDetail(provider, external, memberId, false, "COMPLETED"));
            return new Completion(issued, null, false, "/");
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            recordSecurity("DIRECTORY_LOGIN", null, provider, null, "DENIED", e.code, request, Map.of());
            throw failure(e.code, "企业目录认证失败", HttpStatus.UNAUTHORIZED);
        } finally {
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }

    @Transactional
    public EnrollmentCompletion enrollFromChallenge(IdentityApi.MfaEnrollmentCommand command,
                                                      ClientRequest request) {
        var challenge = repository.consumeMfaChallenge(hash(token(command.challenge(), "challenge", 256)), now())
                .orElseThrow(() -> failure("MFA_CHALLENGE_INVALID", "MFA 挑战已失效", HttpStatus.UNAUTHORIZED));
        var view = enroll(challenge.accountId(), challenge.systemId(), challenge.tenantId(), command, request);
        var issued = sessions.issuePlatform(repository.account(challenge.accountId()));
        recordSecurity("MFA_ENROLL", challenge.accountId(), challenge.provider(), null,
                "SUCCESS", null, request, Map.of("scope", scope(challenge.systemId(), challenge.tenantId())));
        return new EnrollmentCompletion(view, issued);
    }

    @Transactional
    public IdentityApi.MfaEnrollmentView enrollAuthenticated(AuthenticatedSession session,
                                                              IdentityApi.MfaEnrollmentCommand command,
                                                              ClientRequest request) {
        var result = enroll(session.accountId(), session.systemId(), session.tenantId(), command, request);
        recordSecurity("MFA_ENROLL", session.accountId(), null, null, "SUCCESS", null, request,
                Map.of("scope", scope(session.systemId(), session.tenantId())));
        return result;
    }

    @Transactional
    public Completion verify(IdentityApi.MfaVerifyCommand command, ClientRequest request) {
        var challenge = repository.consumeMfaChallenge(hash(token(command.challenge(), "challenge", 256)), now())
                .orElseThrow(() -> failure("MFA_CHALLENGE_INVALID", "MFA 挑战已失效", HttpStatus.UNAUTHORIZED));
        var enrollment = repository.findEnrollment(challenge.accountId(), challenge.systemId(), challenge.tenantId())
                .orElseThrow(() -> failure("MFA_ENROLLMENT_REQUIRED", "需要先绑定 MFA", HttpStatus.CONFLICT));
        boolean accepted;
        String method;
        if (command.totpCode() != null && !command.totpCode().isBlank()) {
            var step = totp.verify(enrollment.secretRef(), command.totpCode(), now());
            accepted = step >= 0 && (enrollment.lastUsedStep() == null || step > enrollment.lastUsedStep())
                    && repository.advanceTotpStep(enrollment.id(), enrollment.version(),
                    enrollment.lastUsedStep(), step, now());
            method = "TOTP";
        } else {
            accepted = command.recoveryCode() != null
                    && repository.consumeRecoveryCode(enrollment.id(), hash(normalizeRecovery(command.recoveryCode())), now());
            method = "RECOVERY_CODE";
        }
        if (!accepted) {
            recordSecurity("MFA_VERIFY", challenge.accountId(), challenge.provider(), null,
                    "DENIED", "MFA_CODE_INVALID", request, Map.of("method", method));
            throw failure("MFA_CODE_INVALID", "MFA 验证失败", HttpStatus.UNAUTHORIZED);
        }
        var issued = sessions.issuePlatform(repository.account(challenge.accountId()));
        recordSecurity("MFA_VERIFY", challenge.accountId(), challenge.provider(), null,
                "SUCCESS", null, request, Map.of("method", method));
        return new Completion(issued, null, false, "/");
    }

    private IdentityApi.MfaEnrollmentView enroll(long accountId, Long systemId, Long tenantId,
                                                  IdentityApi.MfaEnrollmentCommand command,
                                                  ClientRequest request) {
        var secretRef = secretRef(command.secretRef());
        var version = token(command.secretVersion(), "secretVersion", 64);
        var initialStep = totp.verify(secretRef, command.totpCode(), now());
        if (initialStep < 0) {
            throw failure("MFA_CODE_INVALID", "TOTP 初始验证码无效", HttpStatus.UNAUTHORIZED);
        }
        var enrollment = repository.upsertEnrollment(ids.nextId(), accountId, systemId, tenantId,
                secretRef, version, now());
        if (!repository.advanceTotpStep(enrollment.id(), enrollment.version(), enrollment.lastUsedStep(),
                initialStep, now())) {
            throw failure("MFA_ENROLLMENT_CONFLICT", "MFA 绑定状态已变化，请重试", HttpStatus.CONFLICT);
        }
        var rawCodes = new ArrayList<String>();
        var hashes = new ArrayList<IdentityRepository.RecoveryCodeHash>();
        for (int i = 0; i < 8; i++) {
            var raw = randomToken(9).toUpperCase(Locale.ROOT);
            var shown = raw.substring(0, 6) + "-" + raw.substring(6, 12);
            rawCodes.add(shown);
            hashes.add(new IdentityRepository.RecoveryCodeHash(ids.nextId(), hash(normalizeRecovery(shown))));
        }
        repository.replaceRecoveryCodes(enrollment.id(), hashes, now());
        return new IdentityApi.MfaEnrollmentView("ACTIVE", mask(secretRef), version,
                List.copyOf(rawCodes), enrollment.verifiedAt());
    }

    private long mapAccount(IdentityApi.Provider p, IdentityApi.ExternalIdentity identity) {
        var bound = repository.findBoundAccount(p.id(), identity.subject(), p.systemId(), p.tenantId());
        if (bound.isPresent()) return bound.get();
        var normalizedEmail = normalizeEmail(identity.email());
        var existing = normalizedEmail == null ? java.util.Optional.<Long>empty()
                : repository.findAccountByEmail(normalizedEmail);
        long accountId;
        if (existing.isPresent()) {
            accountId = existing.get();
        } else {
            if (!p.jitAccount()) {
                throw failure("IDENTITY_ACCOUNT_UNMAPPED", "身份未绑定平台账号", HttpStatus.FORBIDDEN);
            }
            accountId = ids.nextId();
            var account = new Account();
            var username = normalizedEmail != null ? normalizedEmail
                    : "sso_" + p.providerCode().toLowerCase(Locale.ROOT) + "_" + hash(identity.subject()).substring(0, 16);
            var local = LocalDateTime.ofInstant(now(), ZoneOffset.UTC);
            account.setId(accountId); account.setAccountCode("ACC_" + accountId);
            account.setUsername(username); account.setUsernameNormalized(username);
            account.setEmail(identity.email()); account.setEmailNormalized(normalizedEmail);
            account.setPhone(identity.phone());
            account.setDisplayName(identity.displayName() == null || identity.displayName().isBlank()
                    ? username : identity.displayName().strip());
            account.setLocale("zh-CN"); account.setTimeZone("Asia/Shanghai"); account.setStatus("ACTIVE");
            account.setCreatedAt(local); account.setCreatedBy(accountId); account.setUpdatedAt(local);
            account.setUpdatedBy(accountId); account.setVersion(0L);
            repository.insertJitAccount(account);
        }
        repository.bindIdentity(ids.nextId(), p, identity, accountId, null, now());
        return accountId;
    }

    private Long mapMember(IdentityApi.Provider p, IdentityApi.ExternalIdentity identity, long accountId) {
        if (p.systemId() == null) return null;
        var existing = repository.findMember(p.systemId(), p.tenantId(), accountId);
        if (existing.isPresent()) {
            repository.bindIdentity(ids.nextId(), p, identity, accountId, existing.get(), now());
            return existing.get();
        }
        if (!p.jitSystemMember()) {
            throw failure("IDENTITY_SYSTEM_MEMBER_UNMAPPED", "账号尚未绑定系统成员", HttpStatus.FORBIDDEN);
        }
        var memberId = repository.insertJitMember(ids.nextId(), ids.nextId(), p.systemId(), p.tenantId(),
                accountId, "SSO_" + hash(identity.subject()).substring(0, 20),
                identity.displayName() == null ? identity.subject() : identity.displayName(), now());
        // Update the binding with the member projection after account JIT.
        repository.bindIdentity(ids.nextId(), p, identity, accountId, memberId, now());
        return memberId;
    }

    private void enforceDomain(IdentityApi.Provider p, String email) {
        if (p.allowedDomains().isEmpty()) return;
        var normalized = normalizeEmail(email);
        var domain = normalized == null || !normalized.contains("@") ? null
                : normalized.substring(normalized.lastIndexOf('@') + 1);
        if (domain == null || p.allowedDomains().stream().noneMatch(domain::equals)) {
            throw failure("IDENTITY_DOMAIN_DENIED", "企业邮箱域名不在白名单", HttpStatus.FORBIDDEN);
        }
    }

    private IdentityApi.ProviderCommand validate(IdentityApi.ProviderCommand c, boolean update) {
        if (c == null || c.protocol() == null || c.mfaPolicy() == null) throw validation("身份源配置不完整");
        var system = optionalId(c.systemId()); var tenant = optionalId(c.tenantId());
        if ((system == null) != (tenant == null)) throw validation("systemId 和 tenantId 必须同时提供");
        if (c.jitSystemMember() && (!c.jitAccount() || system == null)) {
            throw validation("JIT 系统成员要求同时开启 JIT 账号并指定系统租户");
        }
        var domains = c.allowedDomains() == null ? List.<String>of() : c.allowedDomains().stream()
                .map(value -> token(value, "allowedDomain", 253).toLowerCase(Locale.ROOT))
                .distinct().sorted().toList();
        var mapping = c.attributeMapping() == null ? Map.<String, String>of()
                : Map.copyOf(c.attributeMapping());
        if (mapping.size() > 32 || mapping.entrySet().stream().anyMatch(e -> e.getKey() == null
                || e.getValue() == null || e.getKey().length() > 64 || e.getValue().length() > 128)) {
            throw validation("attributeMapping 无效");
        }
        var ref = secretRef(c.secretRef());
        URI callback;
        try { callback = URI.create(token(c.callbackUri(), "callbackUri", 1000)); }
        catch (Exception e) { throw validation("callbackUri 无效"); }
        if (!"https".equalsIgnoreCase(callback.getScheme())
                && !("http".equalsIgnoreCase(callback.getScheme()) && "127.0.0.1".equals(callback.getHost()))) {
            throw validation("callbackUri 必须使用 HTTPS");
        }
        return new IdentityApi.ProviderCommand(token(c.providerCode(), "providerCode", 64),
                token(c.name(), "name", 160), c.protocol(), optional(c.issuerUri(), 1000),
                optional(c.authorizationEndpoint(), 1000), optional(c.tokenEndpoint(), 1000),
                optional(c.jwksUri(), 1000), optional(c.directoryEndpoint(), 1000),
                optional(c.clientId(), 255), ref, token(c.secretVersion(), "secretVersion", 64),
                callback.toString(), c.scopes() == null || c.scopes().isBlank() ? "openid profile email"
                : token(c.scopes(), "scopes", 500), domains, mapping, c.jitAccount(),
                c.jitSystemMember(), system == null ? null : system.toString(),
                tenant == null ? null : tenant.toString(), c.mfaPolicy(),
                update ? java.util.Objects.requireNonNull(c.expectedVersion(), "expectedVersion") : null);
    }

    private IdentityApi.ProviderView view(IdentityApi.Provider p) {
        return new IdentityApi.ProviderView(Long.toString(p.id()), p.providerCode(), p.name(), p.protocol(),
                p.issuerUri(), p.authorizationEndpoint(), p.tokenEndpoint(), p.jwksUri(), p.directoryEndpoint(),
                p.clientId(), mask(p.secretRef()), p.secretVersion(), p.callbackUri(), p.scopes(),
                p.allowedDomains(), p.attributeMapping(), p.jitAccount(), p.jitSystemMember(),
                string(p.systemId()), string(p.tenantId()), p.mfaPolicy(), p.status(), p.preflightStatus(),
                p.preflightVersion(), p.preflightFailureCode(), p.preflightAt(), p.publishedAt(), p.version());
    }

    private Map<String, Object> loginDetail(IdentityApi.Provider provider,
                                             IdentityApi.ExternalIdentity identity, Long memberId,
                                             boolean mfa, String status) {
        var value = new LinkedHashMap<String, Object>();
        value.put("externalUserId", identity.subject()); value.put("accountBinding", "BOUND");
        value.put("systemMemberId", memberId == null ? "UNMAPPED" : Long.toString(memberId));
        value.put("authMethod", provider.protocol().name()); value.put("mfa", mfa); value.put("status", status);
        return value;
    }

    private String authorizationUrl(IdentityApi.Provider provider, String state,
                                    String nonce, String challenge) {
        var separator = provider.authorizationEndpoint().contains("?") ? "&" : "?";
        if (provider.protocol() == IdentityApi.Protocol.SAML2) {
            var request = "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                    + "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ID=\"" + xml(nonce)
                    + "\" Version=\"2.0\" IssueInstant=\"" + now() + "\" Destination=\""
                    + xml(provider.authorizationEndpoint()) + "\" AssertionConsumerServiceURL=\""
                    + xml(provider.callbackUri()) + "\"><saml:Issuer>" + xml(provider.clientId())
                    + "</saml:Issuer></samlp:AuthnRequest>";
            return provider.authorizationEndpoint() + separator + "SAMLRequest=" + enc(deflate(request))
                    + "&RelayState=" + enc(state);
        }
        var clientParameter = provider.protocol() == IdentityApi.Protocol.WECOM ? "appid" : "client_id";
        var value = provider.authorizationEndpoint() + separator + "response_type=code&" + clientParameter
                + "=" + enc(provider.clientId()) + "&redirect_uri=" + enc(provider.callbackUri())
                + "&scope=" + enc(provider.scopes()) + "&state=" + enc(state);
        if (provider.protocol() == IdentityApi.Protocol.OIDC || provider.protocol() == IdentityApi.Protocol.OAUTH2) {
            value += "&nonce=" + enc(nonce) + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256";
        }
        return provider.protocol() == IdentityApi.Protocol.WECOM ? value + "#wechat_redirect" : value;
    }

    private void recordSecurity(String event, Long accountId, IdentityApi.Provider provider,
                                String externalUserId, String result, String failureCode,
                                ClientRequest request, Map<String, ?> detail) {
        var values = new LinkedHashMap<String, Object>();
        if (provider != null) values.put("identityProvider", provider.providerCode());
        if (externalUserId != null) values.put("externalUserIdHash", hash(externalUserId).substring(0, 16));
        values.putAll(detail);
        audit.recordSecurity(new AuditEvent(event, accountId,
                externalUserId == null ? null : hash(externalUserId).substring(0, 16),
                provider == null ? null : provider.systemId(), provider == null ? null : provider.tenantId(),
                "WEB", request.remoteAddress(), request.userAgent(), request.requestId(), request.traceId(),
                result, failureCode, write(values)));
    }

    private static IdentityApi.ProviderCommand requireCommand(IdentityApi.ProviderCommand command) { return command; }
    private static BusinessException notFound() { return failure("IDENTITY_PROVIDER_NOT_FOUND", "身份源不存在", HttpStatus.NOT_FOUND); }
    private static BusinessException versionConflict() { return failure("VERSION_CONFLICT", "版本已变化，请刷新后重试", HttpStatus.CONFLICT); }
    private static BusinessException validation(String message) { return failure("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY); }
    private static BusinessException failure(String code, String message, HttpStatus status) { return new BusinessException(code, message, status); }
    private static String optional(String value, int max) { if (value == null) return null; var v = value.strip(); if (v.length() > max) throw validation("字段长度超限"); return v.isEmpty() ? null : v; }
    private static String token(String value, String field, int max) { if (value == null || value.isBlank() || value.strip().length() > max) throw validation(field + " 无效"); return value.strip(); }
    private static String secretRef(String value) { var v = token(value, "secretRef", 512); if (!(v.startsWith("env://") || v.startsWith("file://"))) throw validation("secretRef 只允许 env:// 或 file://"); return v; }
    private static String mask(String ref) { if (ref == null) return null; var p = ref.indexOf("://"); return (p < 0 ? "secret" : ref.substring(0, p)) + "://********"; }
    private static Long optionalId(String value) { if (value == null || value.isBlank()) return null; try { var id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; } catch (Exception e) { throw validation("资源标识无效"); } }
    private static String string(Long value) { return value == null ? null : value.toString(); }
    private static String normalizeEmail(String value) { if (value == null || value.isBlank()) return null; var v = value.strip().toLowerCase(Locale.ROOT); return v.matches("^[^@\\s]+@[^@\\s]+$") ? v : null; }
    private static String normalizeRecovery(String value) { return value == null ? "" : value.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT); }
    private static String scope(Long systemId, Long tenantId) { return systemId == null ? "PLATFORM" : systemId + ":" + tenantId; }
    private static String xml(String value) { return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static String deflate(String value) {
        var deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            var input = value.getBytes(StandardCharsets.UTF_8);
            deflater.setInput(input); deflater.finish();
            var output = new ByteArrayOutputStream(input.length);
            var buffer = new byte[512];
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer));
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } finally { deflater.end(); }
    }
    private String randomToken(int bytes) { var value = new byte[bytes]; random.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String hash(String value) { return java.util.HexFormat.of().formatHex(sha256(value)); }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private Instant now() { return clock.instant(); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }

    public record Completion(IssuedSession issued, String challenge, boolean enrollmentRequired,
                             String redirectUri) { }
    public record EnrollmentCompletion(IdentityApi.MfaEnrollmentView enrollment, IssuedSession issued) { }
}
