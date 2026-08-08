package com.unique.examine.openapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OpenApiPrincipalFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.openapi.api.OpenApiAdminSession;
import com.unique.examine.openapi.api.OpenApiRequests;
import com.unique.examine.openapi.api.OpenApiViews;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.secret.SecretRefResolver;
import com.unique.examine.openapi.security.IpAllowlist;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public class OpenApiApplicationService implements OpenApiApplicationUseCase {
    private static final String IDEMPOTENCY_SCOPE = "OPENAPI_APPLICATION";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final OpenApiRepository repository;
    private final OpenApiPrincipalFacade principals;
    private final SecretRefResolver secrets;
    private final IdempotencyFacade idempotency;
    private final OperationAuditFacade audits;
    private final IdService ids;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random;

    public OpenApiApplicationService(
            OpenApiRepository repository,
            OpenApiPrincipalFacade principals,
            SecretRefResolver secrets,
            IdempotencyFacade idempotency,
            OperationAuditFacade audits,
            IdService ids,
            ObjectMapper json,
            Clock clock
    ) {
        this(repository, principals, secrets, idempotency, audits, ids, json, clock,
                new SecureRandom());
    }

    OpenApiApplicationService(
            OpenApiRepository repository,
            OpenApiPrincipalFacade principals,
            SecretRefResolver secrets,
            IdempotencyFacade idempotency,
            OperationAuditFacade audits,
            IdService ids,
            ObjectMapper json,
            Clock clock,
            SecureRandom random
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.principals = Objects.requireNonNull(principals, "principals");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    @Transactional
    public OpenApiViews.Application create(
            OpenApiAdminSession session,
            OpenApiRequests.CreateApplication request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return mutate(
                session,
                "create",
                null,
                request,
                idempotencyKey,
                requestId,
                traceId,
                201,
                "OPENAPI_APPLICATION_CREATED",
                () -> createApplication(session, request)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OpenApiViews.ApplicationPage list(OpenApiAdminSession session, int page, int size) {
        Objects.requireNonNull(session, "session");
        var offset = offset(page, size);
        return new OpenApiViews.ApplicationPage(
                repository.list(session.systemId(), session.tenantId(), offset, size)
                        .stream().map(OpenApiViews.Application::from).toList(),
                page,
                size,
                repository.count(session.systemId(), session.tenantId())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OpenApiViews.Application detail(OpenApiAdminSession session, long applicationId) {
        return OpenApiViews.Application.from(require(session, applicationId));
    }

    @Override
    @Transactional(readOnly = true)
    public OpenApiViews.CallLogPage callLogs(
            OpenApiAdminSession session,
            long applicationId,
            String resultCategoryValue,
            String requestMethodValue,
            int page,
            int size) {
        Objects.requireNonNull(session, "session");
        var resultCategory = resultFilter(resultCategoryValue);
        var requestMethod = methodFilter(requestMethodValue);
        var offset = callLogOffset(page, size);
        var application = require(session, applicationId);
        var applicationIdValue = application.application().id();
        var total = repository.countApplicationCallLogs(
                applicationIdValue, resultCategory, requestMethod);
        var items = repository.listApplicationCallLogs(
                        applicationIdValue, resultCategory, requestMethod,
                        offset, size)
                .stream().map(OpenApiViews.CallLog::from).toList();
        return new OpenApiViews.CallLogPage(items, page, size, total);
    }

    @Override
    @Transactional
    public OpenApiViews.Application updatePolicy(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.UpdatePolicy request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return mutate(
                session, "policy", applicationId, request, idempotencyKey, requestId, traceId,
                200, "OPENAPI_APPLICATION_POLICY_UPDATED",
                () -> updatePolicyValue(session, applicationId, request)
        );
    }

    @Override
    @Transactional
    public OpenApiViews.Application rotateSecretRef(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.RotateSecretRef request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return mutate(
                session, "rotate", applicationId, request, idempotencyKey, requestId, traceId,
                200, "OPENAPI_CREDENTIAL_ROTATED",
                () -> rotate(session, applicationId, request)
        );
    }

    @Override
    @Transactional
    public OpenApiViews.Application enable(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.ChangeStatus request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return status(session, applicationId, request, idempotencyKey, requestId, traceId,
                OpenApiApplication.Status.ACTIVE, "enable", "OPENAPI_APPLICATION_ENABLED");
    }

    @Override
    @Transactional
    public OpenApiViews.Application disable(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.ChangeStatus request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return status(session, applicationId, request, idempotencyKey, requestId, traceId,
                OpenApiApplication.Status.DISABLED, "disable", "OPENAPI_APPLICATION_DISABLED");
    }

    private OpenApiViews.Application createApplication(
            OpenApiAdminSession session,
            OpenApiRequests.CreateApplication request
    ) {
        requireRequest(request);
        var requestedTenantId = positiveId(request.tenantId(), "tenantId");
        if (requestedTenantId != session.tenantId()) {
            throw requestInvalid("tenantId must match the authenticated system context");
        }
        var serviceMemberId = positiveId(request.serviceMemberId(), "serviceMemberId");
        requireActivePrincipal(session.systemId(), session.tenantId(), serviceMemberId);
        var scopes = scopes(request.scopes());
        var allowlist = IpAllowlist.validate(
                Objects.requireNonNullElse(request.ipAllowlist(), java.util.List.of()));
        var rate = rate(request.rateLimitPerMinute());
        requireSecret(request.secretRef());
        var now = clock.instant();
        var application = new OpenApiApplication(
                ids.nextId(),
                session.systemId(),
                session.tenantId(),
                serviceMemberId,
                appKey(),
                request.name(),
                OpenApiApplication.Status.ACTIVE,
                scopes,
                allowlist,
                rate,
                1,
                now,
                session.accountId(),
                now,
                session.accountId(),
                0
        );
        var credential = new OpenApiCredential(
                ids.nextId(),
                application.id(),
                1,
                request.secretRef(),
                OpenApiCredential.Status.ACTIVE,
                now,
                null,
                now,
                session.accountId()
        );
        repository.insertApplication(application);
        repository.insertCredential(credential);
        return OpenApiViews.Application.from(
                new OpenApiRepository.ApplicationBundle(application, credential));
    }

    private OpenApiViews.Application updatePolicyValue(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.UpdatePolicy request
    ) {
        requireRequest(request);
        var current = require(session, applicationId);
        var expectedVersion = expected(request.version());
        requireVersion(current.application(), expectedVersion);
        var updated = new OpenApiApplication(
                current.application().id(),
                current.application().systemId(),
                current.application().tenantId(),
                current.application().serviceMemberId(),
                current.application().appKey(),
                current.application().name(),
                current.application().status(),
                scopes(request.scopes()),
                IpAllowlist.validate(
                        Objects.requireNonNullElse(request.ipAllowlist(), java.util.List.of())),
                rate(request.rateLimitPerMinute()),
                current.application().currentCredentialVersion(),
                current.application().createdAt(),
                current.application().createdBy(),
                clock.instant(),
                session.accountId(),
                expectedVersion + 1
        );
        if (!repository.updatePolicy(updated, expectedVersion)) {
            throw versionConflict();
        }
        return OpenApiViews.Application.from(
                new OpenApiRepository.ApplicationBundle(updated, current.credential()));
    }

    private OpenApiViews.Application rotate(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.RotateSecretRef request
    ) {
        requireRequest(request);
        var current = require(session, applicationId);
        var expectedVersion = expected(request.version());
        requireVersion(current.application(), expectedVersion);
        requireSecret(request.secretRef());
        var now = clock.instant();
        var nextCredentialVersion = Math.addExact(
                current.credential().credentialVersion(), 1);
        var updatedApplication = new OpenApiApplication(
                current.application().id(),
                current.application().systemId(),
                current.application().tenantId(),
                current.application().serviceMemberId(),
                current.application().appKey(),
                current.application().name(),
                current.application().status(),
                current.application().scopes(),
                current.application().ipAllowlist(),
                current.application().rateLimitPerMinute(),
                nextCredentialVersion,
                current.application().createdAt(),
                current.application().createdBy(),
                now,
                session.accountId(),
                expectedVersion + 1
        );
        var revoked = new OpenApiCredential(
                current.credential().id(),
                current.credential().applicationId(),
                current.credential().credentialVersion(),
                current.credential().secretRef(),
                OpenApiCredential.Status.REVOKED,
                current.credential().activatedAt(),
                now,
                current.credential().createdAt(),
                current.credential().createdBy()
        );
        var replacement = new OpenApiCredential(
                ids.nextId(),
                current.application().id(),
                nextCredentialVersion,
                request.secretRef(),
                OpenApiCredential.Status.ACTIVE,
                now,
                null,
                now,
                session.accountId()
        );
        if (!repository.rotateCredential(
                updatedApplication, revoked, replacement, expectedVersion)) {
            throw versionConflict();
        }
        return OpenApiViews.Application.from(
                new OpenApiRepository.ApplicationBundle(updatedApplication, replacement));
    }

    private OpenApiViews.Application status(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.ChangeStatus request,
            String idempotencyKey,
            String requestId,
            String traceId,
            OpenApiApplication.Status status,
            String action,
            String auditAction
    ) {
        return mutate(
                session, action, applicationId, request, idempotencyKey, requestId, traceId,
                200, auditAction,
                () -> changeStatus(session, applicationId, request, status)
        );
    }

    private OpenApiViews.Application changeStatus(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.ChangeStatus request,
            OpenApiApplication.Status status
    ) {
        requireRequest(request);
        requireReason(request.reason());
        var current = require(session, applicationId);
        var expectedVersion = expected(request.version());
        requireVersion(current.application(), expectedVersion);
        if (status == OpenApiApplication.Status.ACTIVE) {
            requireActivePrincipal(
                    session.systemId(),
                    session.tenantId(),
                    current.application().serviceMemberId()
            );
            requireSecret(current.credential().secretRef());
        }
        if (current.application().status() == status) {
            return OpenApiViews.Application.from(current);
        }
        var now = clock.instant();
        if (!repository.updateStatus(
                session.systemId(),
                session.tenantId(),
                applicationId,
                status,
                session.accountId(),
                now,
                expectedVersion
        )) {
            throw versionConflict();
        }
        var updated = new OpenApiApplication(
                current.application().id(),
                current.application().systemId(),
                current.application().tenantId(),
                current.application().serviceMemberId(),
                current.application().appKey(),
                current.application().name(),
                status,
                current.application().scopes(),
                current.application().ipAllowlist(),
                current.application().rateLimitPerMinute(),
                current.application().currentCredentialVersion(),
                current.application().createdAt(),
                current.application().createdBy(),
                now,
                session.accountId(),
                expectedVersion + 1
        );
        return OpenApiViews.Application.from(
                new OpenApiRepository.ApplicationBundle(updated, current.credential()));
    }

    private OpenApiViews.Application mutate(
            OpenApiAdminSession session,
            String action,
            Long applicationId,
            Object request,
            String key,
            String requestId,
            String traceId,
            int successStatus,
            String auditAction,
            Supplier<OpenApiViews.Application> mutation
    ) {
        Objects.requireNonNull(session, "session");
        requireKey(key);
        var scopeKey = session.systemId() + ":" + session.tenantId() + ":"
                + session.memberId() + ":" + action + ":"
                + (applicationId == null ? "new" : applicationId);
        var requestHash = sha256(write(request));
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }
        final long id;
        try {
            id = idempotency.begin(
                    IDEMPOTENCY_SCOPE, scopeKey, key, requestHash, IDEMPOTENCY_TTL);
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "REQUEST_IN_PROGRESS",
                    "The OpenAPI application request is already being processed"
            );
        }
        var result = mutation.get();
        audits.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(
                        ContextType.SYSTEM, session.systemId(), session.tenantId()),
                new AggregateRef("OPENAPI_APPLICATION", result.id()),
                auditAction,
                null,
                Map.of(
                        "status", result.status(),
                        "credentialVersion", result.credentialVersion(),
                        "policyVersion", result.version(),
                        "scopeCount", result.scopes().size(),
                        "ipRuleCount", result.ipAllowlist().size(),
                        "rateLimitPerMinute", result.rateLimitPerMinute()
                ),
                requestId,
                traceId
        ));
        idempotency.complete(id, successStatus, "OK", write(result));
        return result;
    }

    private OpenApiRepository.ApplicationBundle require(
            OpenApiAdminSession session,
            long applicationId
    ) {
        if (applicationId <= 0) {
            throw requestInvalid("applicationId must be positive");
        }
        return repository.find(session.systemId(), session.tenantId(), applicationId)
                .orElseThrow(() -> new BusinessException(
                        "OPENAPI_APPLICATION_NOT_FOUND",
                        "OpenAPI application was not found",
                        HttpStatus.NOT_FOUND
                ));
    }

    private void requireActivePrincipal(long systemId, long tenantId, long memberId) {
        var principal = principals.resolve(systemId, tenantId, memberId);
        if (principal == null
                || principal.accountId() <= 0
                || !principal.systemActive()
                || !principal.tenantActive()
                || !principal.memberActive()) {
            throw new BusinessException(
                    "OPENAPI_SERVICE_MEMBER_INVALID",
                    "OpenAPI service member must be active in the current system and tenant",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private void requireSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank() || secretRef.length() > 512) {
            throw requestInvalid("secretRef is invalid");
        }
        var secret = secrets.resolve(secretRef)
                .orElseThrow(() -> new BusinessException(
                        "OPENAPI_CREDENTIAL_UNAVAILABLE",
                        "OpenAPI secretRef cannot be resolved",
                        HttpStatus.UNPROCESSABLE_ENTITY
                ));
        Arrays.fill(secret, (byte) 0);
    }

    private String appKey() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private OpenApiViews.Application replay(IdempotencyRecord record, String requestHash) {
        if (!requestHash.equals(record.requestHash())) {
            throw conflict(
                    "IDEMPOTENCY_CONFLICT",
                    "The idempotency key cannot be reused for another OpenAPI application request"
            );
        }
        if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
            throw conflict(
                    "REQUEST_IN_PROGRESS",
                    "The OpenAPI application request is already being processed"
            );
        }
        try {
            return json.readValue(record.responseBody(), OpenApiViews.Application.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot read idempotent OpenAPI application response", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw requestInvalid("OpenAPI application request must be JSON serializable");
        }
    }

    private static Set<String> scopes(Set<String> values) {
        if (values == null || values.isEmpty() || values.size() > 64) {
            throw requestInvalid("scopes must contain between 1 and 64 values");
        }
        var normalized = new TreeSet<String>();
        for (var value : values) {
            if (value == null
                    || !value.matches("^[a-z][a-z0-9]*(?:[._:-][a-z0-9]+)*$")) {
                throw requestInvalid("scope value is invalid");
            }
            normalized.add(value);
        }
        return Set.copyOf(normalized);
    }

    private static int rate(Integer value) {
        if (value == null || value < 1 || value > 60_000) {
            throw requestInvalid("rateLimitPerMinute must be between 1 and 60000");
        }
        return value;
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw requestInvalid(field + " must be a positive integer string");
        }
    }

    private static long expected(Long value) {
        if (value == null || value < 0) {
            throw requestInvalid("version must be a non-negative integer");
        }
        return value;
    }

    private static void requireReason(String value) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw requestInvalid("reason must contain between 1 and 500 characters");
        }
    }

    private static void requireVersion(OpenApiApplication value, long expectedVersion) {
        if (value.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private static int offset(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw requestInvalid("page must be positive and size must be between 1 and 100");
        }
        var offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) {
            throw requestInvalid("page and size produce an unsupported offset");
        }
        return (int) offset;
    }

    private static int callLogOffset(int page, int size) {
        if (page < 1 || page > 10_000 || size < 1 || size > 100) {
            throw requestInvalid(
                    "page must be between 1 and 10000 and size between 1 and 100");
        }
        return Math.multiplyExact(page - 1, size);
    }

    private static OpenApiRepository.CallLogResultFilter resultFilter(
            String value) {
        return exactFilter(
                Objects.requireNonNullElse(value, "ALL"),
                OpenApiRepository.CallLogResultFilter.class,
                "resultCategory");
    }

    private static OpenApiRepository.CallLogMethodFilter methodFilter(
            String value) {
        return exactFilter(
                Objects.requireNonNullElse(value, "ALL"),
                OpenApiRepository.CallLogMethodFilter.class,
                "requestMethod");
    }

    private static <E extends Enum<E>> E exactFilter(
            String value, Class<E> type, String field) {
        if (!value.equals(value.toUpperCase(Locale.ROOT))) {
            throw requestInvalid(field + " is invalid");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw requestInvalid(field + " is invalid");
        }
    }

    private static void requireKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static void requireRequest(Object value) {
        if (value == null) {
            throw requestInvalid("OpenAPI application request is required");
        }
    }

    private static BusinessException versionConflict() {
        return conflict(
                "OPENAPI_VERSION_CONFLICT",
                "OpenAPI application version changed concurrently"
        );
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private static BusinessException requestInvalid(String message) {
        return new BusinessException(
                "OPENAPI_REQUEST_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
