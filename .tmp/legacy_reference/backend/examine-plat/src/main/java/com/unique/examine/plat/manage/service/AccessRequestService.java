package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.AccessRequest;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatAccessRequestMapper;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import com.unique.examine.plat.manage.security.TokenService;
import com.unique.examine.plat.manage.vo.AccessRequestModels;
import com.unique.examine.plat.manage.vo.PageResultVo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
public class AccessRequestService {
    private final PlatAccessRequestMapper requestMapper;
    private final PlatAccountMapper accountMapper;
    private final PlatSystemMapper systemMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatMemberMapper memberMapper;
    private final IdService idService;
    private final IdempotencyFacade idempotencyFacade;
    private final OperationAuditFacade auditFacade;
    private final OutboxFacade outboxFacade;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    public AccessRequestService(
            PlatAccessRequestMapper requestMapper,
            PlatAccountMapper accountMapper,
            PlatSystemMapper systemMapper,
            PlatTenantMapper tenantMapper,
            PlatMemberMapper memberMapper,
            IdService idService,
            IdempotencyFacade idempotencyFacade,
            OperationAuditFacade auditFacade,
            OutboxFacade outboxFacade,
            TokenService tokenService,
            ObjectMapper objectMapper
    ) {
        this.requestMapper = requestMapper;
        this.accountMapper = accountMapper;
        this.systemMapper = systemMapper;
        this.tenantMapper = tenantMapper;
        this.memberMapper = memberMapper;
        this.idService = idService;
        this.idempotencyFacade = idempotencyFacade;
        this.auditFacade = auditFacade;
        this.outboxFacade = outboxFacade;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    public PageResultVo<AccessRequestModels.View> listOwn(
            AuthenticatedSession session,
            Long systemId,
            int page,
            int size
    ) {
        var safePage = Math.max(page, 1);
        var safeSize = Math.min(Math.max(size, 1), 100);
        IPage<AccessRequest> result = requestMapper.selectPage(
                Page.of(safePage, safeSize),
                Wrappers.<AccessRequest>lambdaQuery()
                        .eq(AccessRequest::getAccountId, session.accountId())
                        .eq(systemId != null, AccessRequest::getSystemId, systemId)
                        .orderByDesc(AccessRequest::getCreatedAt)
        );
        return new PageResultVo<>(
                result.getRecords().stream().map(this::view).toList(),
                result.getCurrent(), result.getSize(), result.getTotal()
        );
    }

    @Transactional
    public AccessRequestModels.View submit(
            AuthenticatedSession session,
            long systemId,
            AccessRequestModels.Submit input,
            String idempotencyKey,
            ClientRequest client
    ) {
        requireIdempotencyKey(idempotencyKey);
        var system = systemMapper.selectById(systemId);
        if (system == null || !Set.of("ACTIVE", "INITIALIZING").contains(system.getStatus())) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "目标系统不存在或当前不可申请", HttpStatus.NOT_FOUND);
        }
        var existingMember = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getAccountId, session.accountId()));
        if (existingMember != null && "ACTIVE".equals(existingMember.getStatus())) {
            throw new BusinessException("ACCESS_REQUEST_CONFLICT", "当前账号已经是系统成员", HttpStatus.CONFLICT);
        }
        var targetTenantId = parseNullableId(input.targetTenantId(), "targetTenantId");
        if (targetTenantId != null) {
            requireTenant(systemId, targetTenantId);
        }

        var scope = session.accountId() + "|" + systemId + "|" + (targetTenantId == null ? 0 : targetTenantId);
        var requestHash = tokenService.requestHash(write(input).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var replay = idempotencyFacade.find("ACCESS_REQUEST_SUBMIT", scope, idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "相同幂等键对应了不同申请", HttpStatus.CONFLICT);
            }
            if (!"COMPLETED".equals(replay.get().status()) || replay.get().responseBody() == null) {
                throw new BusinessException("REQUEST_IN_PROGRESS", "申请正在处理中", HttpStatus.CONFLICT);
            }
            return view(requestMapper.selectById(readId(replay.get().responseBody())));
        }

        var active = requestMapper.selectOne(Wrappers.<AccessRequest>lambdaQuery()
                .eq(AccessRequest::getAccountId, session.accountId())
                .eq(AccessRequest::getSystemId, systemId)
                .eq(targetTenantId != null, AccessRequest::getTargetTenantId, targetTenantId)
                .isNull(targetTenantId == null, AccessRequest::getTargetTenantId)
                .eq(AccessRequest::getStatus, "SUBMITTED"));
        if (active != null) {
            return view(active);
        }

        var idempotencyId = idempotencyFacade.begin(
                "ACCESS_REQUEST_SUBMIT", scope, idempotencyKey, requestHash, Duration.ofHours(24)
        );
        var now = LocalDateTime.now();
        var request = new AccessRequest();
        request.setId(idService.nextId());
        request.setAccountId(session.accountId());
        request.setSystemId(systemId);
        request.setTargetTenantId(targetTenantId);
        request.setRequestReason(input.reason().trim());
        request.setStatus("SUBMITTED");
        request.setCreatedAt(now);
        request.setCreatedBy(session.accountId());
        request.setUpdatedAt(now);
        request.setUpdatedBy(session.accountId());
        request.setVersion(0L);
        requestMapper.insert(request);

        var aggregate = new AggregateRef("ACCESS_REQUEST", Long.toString(request.getId()));
        auditFacade.recordSuccess(OperationAudit.success(
                actor(session), context(session), aggregate, "ACCESS_REQUEST_SUBMITTED",
                null, Map.of("status", "SUBMITTED"), client.requestId(), client.traceId()
        ));
        outboxFacade.enqueue(new OutboxEvent(
                "ACCESS_REQUEST_SUBMITTED", 1, aggregate,
                new OutboxEvent.Context(systemId, targetTenantId),
                idempotencyKey,
                Map.of("requestId", Long.toString(request.getId()), "accountId", Long.toString(session.accountId())),
                client.traceId()
        ));
        idempotencyFacade.complete(idempotencyId, 200, "OK", Long.toString(request.getId()));
        return view(request);
    }

    @Transactional
    public AccessRequestModels.View cancel(
            AuthenticatedSession session,
            long requestId,
            AccessRequestModels.Cancel input,
            String idempotencyKey,
            ClientRequest client
    ) {
        requireIdempotencyKey(idempotencyKey);
        var scope = session.accountId() + "|" + requestId;
        var requestHash = tokenService.requestHash(write(input).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var replay = idempotencyFacade.find("ACCESS_REQUEST_CANCEL", scope, idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "相同幂等键对应了不同取消请求", HttpStatus.CONFLICT);
            }
            if (!"COMPLETED".equals(replay.get().status()) || replay.get().responseBody() == null) {
                throw new BusinessException("REQUEST_IN_PROGRESS", "取消请求正在处理中", HttpStatus.CONFLICT);
            }
            return view(requestMapper.selectById(readId(replay.get().responseBody())));
        }
        var idempotencyId = idempotencyFacade.begin(
                "ACCESS_REQUEST_CANCEL", scope, idempotencyKey, requestHash, Duration.ofHours(24)
        );
        var request = requestMapper.selectOne(Wrappers.<AccessRequest>lambdaQuery()
                .eq(AccessRequest::getId, requestId)
                .eq(AccessRequest::getAccountId, session.accountId()));
        if (request == null) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "申请不存在", HttpStatus.NOT_FOUND);
        }
        requireVersion(input.version(), request.getVersion());
        if (!"SUBMITTED".equals(request.getStatus())) {
            throw new BusinessException("STATE_TRANSITION_INVALID", "当前申请不能取消", HttpStatus.CONFLICT);
        }
        var before = Map.of("status", request.getStatus());
        request.setStatus("CANCELLED");
        request.setUpdatedAt(LocalDateTime.now());
        request.setUpdatedBy(session.accountId());
        if (requestMapper.updateById(request) != 1) {
            throw new BusinessException("VERSION_CONFLICT", "申请状态已变化", HttpStatus.CONFLICT);
        }
        auditFacade.recordSuccess(OperationAudit.success(
                actor(session), context(session), new AggregateRef("ACCESS_REQUEST", Long.toString(requestId)),
                "ACCESS_REQUEST_CANCELLED", before, Map.of("status", "CANCELLED"),
                client.requestId(), client.traceId()
        ));
        outboxFacade.enqueue(new OutboxEvent(
                "ACCESS_REQUEST_CANCELLED", 1,
                new AggregateRef("ACCESS_REQUEST", Long.toString(requestId)),
                new OutboxEvent.Context(request.getSystemId(), request.getTargetTenantId()),
                idempotencyKey,
                Map.of("requestId", Long.toString(requestId), "accountId", Long.toString(session.accountId())),
                client.traceId()
        ));
        idempotencyFacade.complete(idempotencyId, 200, "OK", Long.toString(requestId));
        return view(request);
    }

    private AccessRequestModels.View view(AccessRequest request) {
        if (request == null) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "申请不存在", HttpStatus.NOT_FOUND);
        }
        var account = accountMapper.selectById(request.getAccountId());
        var system = systemMapper.selectById(request.getSystemId());
        var tenant = request.getTargetTenantId() == null ? null : tenantMapper.selectById(request.getTargetTenantId());
        return new AccessRequestModels.View(
                Long.toString(request.getId()),
                Long.toString(request.getSystemId()),
                system == null ? null : system.getName(),
                Long.toString(request.getAccountId()),
                account == null ? null : account.getDisplayName(),
                id(request.getTargetTenantId()),
                tenant == null ? null : tenant.getName(),
                request.getRequestReason(),
                request.getStatus(),
                request.getDecisionReason(),
                request.getCreatedAt(),
                request.getReviewedAt(),
                Long.toString(request.getVersion())
        );
    }

    private Tenant requireTenant(long systemId, long tenantId) {
        var tenant = tenantMapper.selectOne(Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getId, tenantId)
                .eq(Tenant::getSystemId, systemId));
        if (tenant == null || !"ACTIVE".equals(tenant.getStatus())) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "目标租户不存在或当前不可申请", HttpStatus.NOT_FOUND);
        }
        return tenant;
    }

    private static void requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "缺少有效的 Idempotency-Key", HttpStatus.BAD_REQUEST);
        }
    }

    private static Long parseNullableId(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException("VALIDATION_FAILED", field + " 格式无效", HttpStatus.BAD_REQUEST);
        }
    }

    private static long readId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid idempotency response", exception);
        }
    }

    private static void requireVersion(String value, long current) {
        if (!Long.toString(current).equals(value)) {
            throw new BusinessException("VERSION_CONFLICT", "数据已被其他操作修改", HttpStatus.CONFLICT);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize access request", exception);
        }
    }

    private static OperationAudit.Actor actor(AuthenticatedSession session) {
        return new OperationAudit.Actor(session.accountId(), "WEB");
    }

    private static OperationAudit.Context context(AuthenticatedSession session) {
        return new OperationAudit.Context(session.contextType(), session.systemId(), session.tenantId());
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
