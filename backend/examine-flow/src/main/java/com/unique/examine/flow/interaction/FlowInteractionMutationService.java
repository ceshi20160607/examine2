package com.unique.examine.flow.interaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.domain.ApprovalDomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class FlowInteractionMutationService {
    private static final String IDEMPOTENCY_SCOPE = "FLOW_INSTANCE";
    private static final String URGE_TEMPLATE = "FLOW_INSTANCE_URGED";
    private static final String URGE_TITLE = "流程催办";
    private static final String COPY_TEMPLATE = "FLOW_INSTANCE_COPIED";
    private static final String COPY_TITLE = "流程实例抄送";

    private final FlowInteractionServiceFactory services;
    private final IdempotencyFacade idempotency;
    private final ObjectMapper objectMapper;
    private final MemberMessageFacade messages;
    private final RuntimeActiveMemberFacade activeMembers;

    public FlowInteractionMutationService(
            FlowInteractionServiceFactory services,
            IdempotencyFacade idempotency,
            ObjectMapper objectMapper,
            MemberMessageFacade messages,
            RuntimeActiveMemberFacade activeMembers
    ) {
        this.services = Objects.requireNonNull(services, "services");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.activeMembers = Objects.requireNonNull(activeMembers, "activeMembers");
    }

    @Transactional
    public FlowViews.Urge urge(
            FlowSession session,
            long instanceId,
            FlowRequests.Urge request,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        var requestHash = requestHash(request, idempotencyKey);
        var scopeKey = scopeKey(session, instanceId, "urge");
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, FlowViews.Urge.class, "urge");
        }
        var id = begin(scopeKey, idempotencyKey, requestHash, "urge");
        var dispatch = services.forTenant(session.systemId(), session.tenantId())
                .urge(instanceId, session.memberId(), request == null ? null : request.message());
        var urge = dispatch.urge();
        messages.send(new MemberMessageFacade.Command(
                session.systemId(),
                session.tenantId(),
                session.memberId(),
                urge.recipientId(),
                URGE_TEMPLATE,
                URGE_TITLE,
                urgeBody(dispatch),
                new AggregateRef("FLOW_INSTANCE", Long.toString(instanceId))
        ));
        var result = FlowViews.Urge.from(urge);
        idempotency.complete(id, 200, "OK", write(result));
        return result;
    }

    @Transactional
    public FlowViews.Comment comment(
            FlowSession session,
            long instanceId,
            FlowRequests.Comment request,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        var requestHash = requestHash(request, idempotencyKey);
        var scopeKey = scopeKey(session, instanceId, "comment");
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, FlowViews.Comment.class, "comment");
        }
        var id = begin(scopeKey, idempotencyKey, requestHash, "comment");
        var comment = services.forTenant(session.systemId(), session.tenantId())
                .comment(instanceId, session.memberId(), request == null ? null : request.body());
        var result = FlowViews.Comment.from(comment);
        idempotency.complete(id, 200, "OK", write(result));
        return result;
    }

    @Transactional
    public FlowViews.Copy copy(
            FlowSession session,
            long instanceId,
            FlowRequests.Copy request,
            String idempotencyKey
    ) {
        Objects.requireNonNull(session, "session");
        var requestHash = requestHash(request, idempotencyKey);
        var scopeKey = scopeKey(session, instanceId, "copy");
        var existing = idempotency.find(IDEMPOTENCY_SCOPE, scopeKey, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, FlowViews.Copy.class, "copy");
        }
        var id = begin(scopeKey, idempotencyKey, requestHash, "copy");
        var service = services.forTenant(session.systemId(), session.tenantId());
        var instance = service.instance(instanceId);
        var recipientId = activeCopyTarget(
                session,
                request == null ? null : request.targetMemberId()
        );
        var copy = service.copy(
                instance,
                session.memberId(),
                recipientId,
                request == null ? null : request.message()
        );
        messages.send(new MemberMessageFacade.Command(
                session.systemId(),
                session.tenantId(),
                session.memberId(),
                recipientId,
                COPY_TEMPLATE,
                COPY_TITLE,
                copyBody(instance.businessKey(), copy.message()),
                new AggregateRef("FLOW_INSTANCE", Long.toString(instanceId))
        ));
        var result = FlowViews.Copy.from(copy);
        idempotency.complete(id, 200, "OK", write(result));
        return result;
    }

    private String requestHash(Object request, String idempotencyKey) {
        requireKey(idempotencyKey);
        return sha256(write(request));
    }

    private long activeCopyTarget(FlowSession session, String value) {
        final long targetMemberId;
        try {
            targetMemberId = Long.parseLong(value);
            if (targetMemberId <= 0 || targetMemberId == session.memberId()) {
                throw new NumberFormatException();
            }
        } catch (RuntimeException exception) {
            throw copyTargetInvalid(
                    "targetMemberId must be a positive member-id string different from the actor"
            );
        }
        var active = activeMembers.lockActiveMember(
                session.systemId(),
                session.tenantId(),
                targetMemberId
        ).filter(member -> member.memberId() == targetMemberId);
        if (active.isEmpty()) {
            throw copyTargetInvalid(
                    "Copy target must be an active member in the current system and tenant"
            );
        }
        return targetMemberId;
    }

    private long begin(String scopeKey, String key, String requestHash, String action) {
        try {
            return idempotency.begin(
                    IDEMPOTENCY_SCOPE,
                    scopeKey,
                    key,
                    requestHash,
                    Duration.ofHours(24)
            );
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "REQUEST_IN_PROGRESS",
                    "The Flow " + action + " request is already being processed"
            );
        }
    }

    private <T> T replay(
            IdempotencyRecord record,
            String requestHash,
            Class<T> responseType,
            String action
    ) {
        if (!requestHash.equals(record.requestHash())) {
            throw conflict(
                    "IDEMPOTENCY_CONFLICT",
                    "The same idempotency key cannot be reused for a different Flow " + action + " request"
            );
        }
        if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
            throw conflict(
                    "REQUEST_IN_PROGRESS",
                    "The Flow " + action + " request is already being processed"
            );
        }
        try {
            return objectMapper.readValue(record.responseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read idempotent Flow interaction response", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Flow interaction payload must be JSON serializable", exception);
        }
    }

    private static String urgeBody(FlowInteractionService.UrgeDispatch dispatch) {
        var body = "流程实例 " + dispatch.businessKey() + " 已由发起人催办。";
        return dispatch.urge().message().isEmpty()
                ? body
                : body + "\n催办留言：" + dispatch.urge().message();
    }

    private static String copyBody(String businessKey, String message) {
        var body = "流程实例 " + businessKey + " 已抄送给你。";
        return message.isEmpty() ? body : body + "\n抄送留言：" + message;
    }

    private static ApprovalDomainException copyTargetInvalid(String message) {
        return new ApprovalDomainException(
                ApprovalDomainException.Code.COPY_TARGET_INVALID,
                message
        );
    }

    private static String scopeKey(FlowSession session, long instanceId, String action) {
        return session.systemId() + ":" + session.tenantId() + ":" + session.memberId()
                + ":" + instanceId + ":" + action;
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }
}
