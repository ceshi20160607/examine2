package com.unique.examine.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.domain.AiFillProposal;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiFillResultParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.ai.repository.AiRepository;
import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.unique.examine.ai.AiSupport.conflict;
import static com.unique.examine.ai.AiSupport.invalid;

@Service
public final class AiFillFacade {
    private final AiRepository repository;
    private final AiFieldFillFacade owner;
    private final AiProviderClient providers;
    private final AiFillResultParser parser;
    private final IdService ids;
    private final Clock clock;
    private final ObjectMapper json;

    public AiFillFacade(
            AiRepository repository,
            AiFieldFillFacade owner,
            AiProviderClient providers,
            AiFillResultParser parser,
            IdService ids,
            Clock clock,
            ObjectMapper json
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.json = Objects.requireNonNull(json, "json");
    }

    public ProposalView propose(
            AiActor actor, String moduleCode, String recordId, String fieldCode,
            long expectedRecordVersion, String idempotencyKey) {
        requireRuntime(actor);
        moduleCode = code(moduleCode, "moduleCode");
        recordId = positiveId(recordId, "recordId");
        fieldCode = code(fieldCode, "fieldCode");
        if (expectedRecordVersion < 0) {
            throw invalid("AI_FILL_VERSION_INVALID",
                    "AI fill expectedRecordVersion must not be negative");
        }
        idempotencyKey = key(idempotencyKey);
        var requestHash = AiSupport.sha256(moduleCode + ":" + recordId + ":"
                + fieldCode + ":" + expectedRecordVersion);
        var replay = repository.fillAttempt(actor.systemId(), actor.tenantId(),
                actor.memberId(), "PROPOSE", idempotencyKey);
        if (replay.isPresent()) {
            return proposalReplay(actor, moduleCode, recordId, fieldCode,
                    requestHash, replay.get());
        }
        var proposalId = ids.nextId();
        var now = clock.instant();
        var runningAttempt = new AiFillProposal.Attempt(
                ids.nextId(), actor.systemId(), actor.tenantId(), actor.memberId(),
                proposalId, "PROPOSE", idempotencyKey, requestHash,
                AiFillProposal.State.EXECUTING, "AI_FILL_PROPOSING", now, null);
        if (!repository.reserveFillAttempt(runningAttempt)) {
            return proposalReplay(actor, moduleCode, recordId, fieldCode,
                    requestHash, repository.fillAttempt(
                            actor.systemId(), actor.tenantId(), actor.memberId(),
                            "PROPOSE", idempotencyKey).orElseThrow());
        }
        try {
            var policy = fillPolicy(actor, moduleCode, fieldCode);
            var provider = repository.provider(
                            actor.systemId(), actor.tenantId(), policy.providerId())
                    .filter(value -> value.enabled()
                            && value.version() == policy.providerVersion())
                    .orElseThrow(() -> AiSupport.unavailable(
                            "AI_PROVIDER_UNAVAILABLE", "AI provider is unavailable"));
            var snapshot = owner.sourceSnapshot(new AiFieldFillFacade.SourceRequest(
                    actor.systemId(), actor.tenantId(), actor.memberId(),
                    actor.authorizationEpoch(), actor.effectivePermissions(),
                    moduleCode, recordId, fieldCode,
                    actor.requestId(), actor.traceId()));
            if (snapshot.recordVersion() != expectedRecordVersion) {
                throw conflict("AI_FILL_RECORD_VERSION_CONFLICT",
                        "AI fill target record version changed");
            }
            assertSnapshot(moduleCode, recordId, fieldCode, snapshot);
            var completion = providers.complete(provider, new AiProviderClient.Request(
                    AiProviderClient.Phase.PLAN, systemPrompt(snapshot.contract(), policy),
                    sourceContent(snapshot), 1_024));
            var parsed = parser.parse(completion.content(),
                    snapshot.contract().resultSchema(),
                    snapshot.contract().minimumConfidence());
            final AiFieldFillFacade.PreparedFill prepared;
            if (parsed.confirmable()) {
                prepared = owner.prepare(new AiFieldFillFacade.PrepareRequest(
                        Long.toString(proposalId), actor.systemId(), actor.tenantId(),
                        actor.memberId(), actor.authorizationEpoch(),
                        actor.effectivePermissions(), moduleCode, recordId, fieldCode,
                        snapshot.schemaVersionId(), snapshot.recordVersion(),
                        snapshot.sourceVersionHash(), parsed.canonicalOwnerResultJson(),
                        new AiFieldFillFacade.Provenance(
                                Long.toString(provider.id()), provider.version(),
                                provider.model(), policy.promptVersion(),
                                Long.toString(policy.id())),
                        actor.requestId(), actor.traceId()));
                assertPrepared(snapshot, parsed, prepared.preview());
            } else {
                prepared = null;
            }
            var proposal = proposal(actor, proposalId, policy, provider.id(),
                    provider.version(), provider.model(), snapshot, parsed, prepared,
                    completion, now);
            var completedAttempt = attempt(runningAttempt,
                    AiFillProposal.State.SUCCEEDED, "OK", clock.instant());
            repository.insertFillProposal(proposal, completedAttempt,
                    event(actor, proposal, "PROPOSED", null, proposal.state(),
                            proposal.resultCode(), clock.instant()));
            return view(proposal);
        } catch (RuntimeException failure) {
            var error = safeCode(failure);
            repository.completeFillAttempt(runningAttempt, attempt(
                    runningAttempt, AiFillProposal.State.FAILED, error,
                    clock.instant()));
            throw failure;
        }
    }

    public ProposalView proposal(
            AiActor actor, String moduleCode, String recordId,
            String fieldCode, String proposalId) {
        requireRuntime(actor);
        var value = owned(actor, moduleCode, recordId, fieldCode, proposalId);
        if ((value.state() == AiFillProposal.State.PENDING
                || value.state() == AiFillProposal.State.CLARIFICATION_REQUIRED)
                && !clock.instant().isBefore(value.expiresAt())) {
            value = expire(actor, value);
        }
        return view(value);
    }

    public ProposalView confirm(
            AiActor actor, String moduleCode, String recordId, String fieldCode,
            String proposalId, long expectedVersion, String idempotencyKey) {
        requireRuntime(actor);
        return view(act(actor, moduleCode, recordId, fieldCode, proposalId,
                expectedVersion, idempotencyKey, "CONFIRM"));
    }

    public ProposalView reject(
            AiActor actor, String moduleCode, String recordId, String fieldCode,
            String proposalId, long expectedVersion, String idempotencyKey) {
        requireRuntime(actor);
        return view(act(actor, moduleCode, recordId, fieldCode, proposalId,
                expectedVersion, idempotencyKey, "REJECT"));
    }

    private AiFillProposal act(
            AiActor actor, String moduleCode, String recordId, String fieldCode,
            String proposalId, long expectedVersion, String idempotencyKey,
            String action) {
        idempotencyKey = key(idempotencyKey);
        var value = owned(actor, moduleCode, recordId, fieldCode, proposalId);
        var requestHash = AiSupport.sha256(
                value.id() + ":" + expectedVersion + ":" + action);
        var replay = repository.fillAttempt(actor.systemId(), actor.tenantId(),
                actor.memberId(), action, idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)
                    || replay.get().proposalId() != value.id()) {
                throw conflict("AI_FILL_REPLAY_CONFLICT",
                        "AI fill key was used for another request");
            }
            if (value.state() != AiFillProposal.State.EXECUTING) return value;
            return execute(actor, value, replay.get(), action);
        }
        if ((value.state() == AiFillProposal.State.PENDING
                || value.state() == AiFillProposal.State.CLARIFICATION_REQUIRED)
                && !clock.instant().isBefore(value.expiresAt())) {
            expire(actor, value);
            throw conflict("AI_FILL_PROPOSAL_EXPIRED", "AI fill proposal expired");
        }
        if (value.revision() != expectedVersion) {
            throw conflict("AI_FILL_VERSION_CONFLICT", "AI fill proposal version is stale");
        }
        if (value.state() != AiFillProposal.State.PENDING) {
            throw conflict("AI_FILL_STATE_CONFLICT", "AI fill proposal is not pending");
        }
        fillPolicy(actor, value.moduleCode(), value.fieldCode());
        var now = clock.instant();
        var executing = executing(value, actor, now);
        var attempt = new AiFillProposal.Attempt(
                ids.nextId(), value.systemId(), value.tenantId(), value.memberId(),
                value.id(), action, idempotencyKey, requestHash,
                AiFillProposal.State.EXECUTING, "AI_FILL_EXECUTING", now, null);
        var claim = repository.claimFillProposal(value, executing, attempt,
                event(actor, executing, action + "ING", value.state(),
                        executing.state(), executing.resultCode(), now));
        if (claim != AiRepository.ClaimResult.CLAIMED) {
            var concurrent = repository.fillAttempt(actor.systemId(), actor.tenantId(),
                    actor.memberId(), action, idempotencyKey);
            if (concurrent.isPresent()) {
                if (!concurrent.get().requestHash().equals(requestHash)) {
                    throw conflict("AI_FILL_REPLAY_CONFLICT",
                            "AI fill key was used for another request");
                }
                return execute(actor, owned(actor, moduleCode, recordId,
                        fieldCode, proposalId), concurrent.get(), action);
            }
            throw conflict("AI_FILL_VERSION_CONFLICT",
                    "AI fill proposal version changed");
        }
        return execute(actor, executing, attempt, action);
    }

    private AiFillProposal execute(
            AiActor actor, AiFillProposal executing,
            AiFillProposal.Attempt attempt, String action) {
        if (executing.state() != AiFillProposal.State.EXECUTING
                || attempt.status() != AiFillProposal.State.EXECUTING) return executing;
        var now = clock.instant();
        try {
            fillPolicy(actor, executing.moduleCode(), executing.fieldCode());
            if ("CONFIRM".equals(action)) {
                var readback = owner.execute(new AiFieldFillFacade.ExecuteRequest(
                        Long.toString(executing.id()), actor.systemId(), actor.tenantId(),
                        actor.memberId(), actor.authorizationEpoch(),
                        actor.effectivePermissions(), executing.moduleCode(),
                        executing.recordId(), executing.fieldCode(),
                        executing.sealedCommand(), attempt.requestKey(),
                        actor.requestId(), actor.traceId()));
                assertReadback(executing, readback);
                var result = result(readback, now);
                var succeeded = terminal(executing, AiFillProposal.State.SUCCEEDED,
                        actor.memberId(), result, "OK", actor, now);
                return finish(executing, succeeded, attempt, actor, "SUCCEEDED", now);
            }
            owner.reject(new AiFieldFillFacade.RejectRequest(
                    Long.toString(executing.id()), actor.systemId(), actor.tenantId(),
                    actor.memberId(), actor.authorizationEpoch(),
                    actor.effectivePermissions(), executing.moduleCode(),
                    executing.recordId(), executing.fieldCode(),
                    executing.sealedCommand(), attempt.requestKey(),
                    actor.requestId(), actor.traceId()));
            var rejected = terminal(executing, AiFillProposal.State.REJECTED,
                    actor.memberId(), null, "AI_FILL_REJECTED", actor, now);
            return finish(executing, rejected, attempt, actor, "REJECTED", now);
        } catch (RuntimeException failure) {
            var failed = terminal(executing, AiFillProposal.State.FAILED,
                    actor.memberId(), null, safeCode(failure), actor, now);
            return finish(executing, failed, attempt, actor, "FAILED", now);
        }
    }

    private AiFillProposal finish(
            AiFillProposal expected, AiFillProposal terminal,
            AiFillProposal.Attempt attempt, AiActor actor,
            String eventType, Instant now) {
        var terminalAttempt = attempt(attempt, terminal.state(),
                terminal.resultCode(), now);
        if (repository.finishFillProposal(expected, terminal, terminalAttempt,
                event(actor, terminal, eventType, expected.state(), terminal.state(),
                        terminal.resultCode(), now))) return terminal;
        return repository.fillProposal(expected.systemId(), expected.tenantId(),
                expected.memberId(), expected.moduleCode(), expected.recordId(),
                expected.fieldCode(), expected.id()).orElseThrow();
    }

    private AiFillProposal proposal(
            AiActor actor, long id, AiPolicy.Version policy,
            long providerId, long providerVersion, String model,
            AiFieldFillFacade.SourceSnapshot snapshot,
            AiFillResultParser.Result parsed,
            AiFieldFillFacade.PreparedFill prepared,
            AiProviderClient.Completion completion,
            Instant now) {
        var preview = prepared == null ? null : prepared.preview();
        return new AiFillProposal(
                id, actor.systemId(), actor.tenantId(), actor.memberId(),
                snapshot.moduleCode(), snapshot.recordId(), snapshot.contract().fieldId(),
                snapshot.contract().fieldCode(), snapshot.contract().fieldName(),
                snapshot.contract().resultSchema(), snapshot.recordVersion(),
                snapshot.schemaVersionId(), snapshot.sourceVersionHash(), policy.id(),
                providerId, providerVersion, model, policy.promptVersion(),
                actor.authorizationEpoch(), snapshot.sources().stream().map(source ->
                        new AiFillProposal.SourceSummary(
                                source.fieldId(), source.fieldCode(), source.fieldName(),
                                source.fieldType(), AiSupport.redactedSummary(
                                "source", source.displayValue()))).toList(),
                preview == null
                        ? snapshot.currentValue() == null ? null
                        : snapshot.currentValue().displayValue()
                        : preview.beforeDisplayValue(),
                preview == null ? null : preview.afterDisplayValue(),
                parsed.confidence(), parsed.clarification() == null ? null
                        : "Additional clarification is required",
                preview != null && preview.overwrite(), parsed.resultHash(),
                prepared == null ? null : prepared.sealedCommand(),
                prepared == null ? AiFillProposal.State.CLARIFICATION_REQUIRED
                        : AiFillProposal.State.PENDING,
                0, now.plus(policy.confirmationExpiresSeconds(), ChronoUnit.SECONDS),
                null, null, prepared == null ? "AI_FILL_CLARIFICATION_REQUIRED"
                        : "AI_FILL_PENDING", null, null,
                completion.promptTokens(), completion.completionTokens(),
                completion.latencyMs(), now, now, null);
    }

    private AiPolicy.Version fillPolicy(
            AiActor actor, String moduleCode, String fieldCode) {
        var policy = repository.activePolicy(actor.systemId(), actor.tenantId())
                .filter(AiPolicy.Version::enabled)
                .orElseThrow(() -> conflict(
                        "AI_POLICY_CHANGED", "AI policy is unavailable"));
        if (!policy.allowedOperations().contains("AI_FILL")
                || !policy.fillFields().getOrDefault(moduleCode, Set.of())
                .contains(fieldCode)) {
            throw conflict("AI_POLICY_CHANGED",
                    "AI fill field exceeds the current active policy");
        }
        return policy;
    }

    private ProposalView proposalReplay(
            AiActor actor, String moduleCode, String recordId, String fieldCode,
            String requestHash, AiFillProposal.Attempt attempt) {
        if (!attempt.requestHash().equals(requestHash)) {
            throw conflict("AI_FILL_REPLAY_CONFLICT",
                    "AI fill key was used for another request");
        }
        var proposal = repository.fillProposal(
                actor.systemId(), actor.tenantId(), actor.memberId(), moduleCode,
                recordId, fieldCode, attempt.proposalId());
        if (proposal.isPresent()) return view(proposal.get());
        if (attempt.status() == AiFillProposal.State.FAILED) {
            throw AiSupport.unavailable(attempt.resultCode(),
                    "AI fill proposal creation previously failed");
        }
        throw conflict("AI_FILL_PROPOSAL_IN_PROGRESS",
                "AI fill proposal creation is in progress");
    }

    private AiFillProposal owned(
            AiActor actor, String moduleCode, String recordId,
            String fieldCode, String proposalId) {
        return repository.fillProposal(
                        actor.systemId(), actor.tenantId(), actor.memberId(),
                        code(moduleCode, "moduleCode"), positiveId(recordId, "recordId"),
                        code(fieldCode, "fieldCode"),
                        Long.parseLong(positiveId(proposalId, "proposalId")))
                .orElseThrow(() -> AiSupport.notFound(
                        "AI fill proposal does not exist"));
    }

    private AiFillProposal expire(AiActor actor, AiFillProposal value) {
        var now = clock.instant();
        var expired = terminal(value, AiFillProposal.State.EXPIRED, null, null,
                "AI_FILL_EXPIRED", actor, now);
        if (repository.expireFillProposal(value, expired,
                event(actor, expired, "EXPIRED", value.state(), expired.state(),
                        expired.resultCode(), now))) return expired;
        return owned(actor, value.moduleCode(), value.recordId(), value.fieldCode(),
                Long.toString(value.id()));
    }

    private static AiFillProposal executing(
            AiFillProposal value, AiActor actor, Instant now) {
        return copy(value, AiFillProposal.State.EXECUTING, value.revision() + 1,
                actor.memberId(), null, "AI_FILL_EXECUTING", actor.requestId(),
                actor.traceId(), now, null);
    }

    private static AiFillProposal terminal(
            AiFillProposal value, AiFillProposal.State state, Long actorId,
            AiFillProposal.FillResult result, String resultCode,
            AiActor actor, Instant now) {
        return copy(value, state, value.revision() + 1, actorId, result, resultCode,
                actor.requestId(), actor.traceId(), now, now);
    }

    private static AiFillProposal copy(
            AiFillProposal v, AiFillProposal.State state, long revision,
            Long actedBy, AiFillProposal.FillResult result, String resultCode,
            String requestId, String traceId, Instant updatedAt, Instant finishedAt) {
        return new AiFillProposal(
                v.id(), v.systemId(), v.tenantId(), v.memberId(), v.moduleCode(),
                v.recordId(), v.fieldId(), v.fieldCode(), v.fieldName(),
                v.resultSchema(), v.expectedRecordVersion(), v.schemaVersionId(),
                v.sourceVersionHash(), v.policyVersionId(), v.providerId(),
                v.providerVersion(), v.model(), v.promptVersion(),
                v.authorizationEpoch(), v.sources(), v.beforeDisplayValue(),
                v.afterDisplayValue(), v.confidence(), v.clarificationSummary(),
                v.overwrite(), v.resultHash(), v.sealedCommand(), state, revision,
                v.expiresAt(), actedBy, result, resultCode, requestId, traceId,
                v.promptTokens(), v.completionTokens(), v.providerLatencyMs(),
                v.createdAt(), updatedAt, finishedAt);
    }

    private AiFillProposal.Event event(
            AiActor actor, AiFillProposal value, String type,
            AiFillProposal.State from, AiFillProposal.State to,
            String resultCode, Instant now) {
        return new AiFillProposal.Event(
                ids.nextId(), value.systemId(), value.tenantId(), value.id(), type,
                from, to, value.revision(), actor.memberId(), actor.requestId(),
                actor.traceId(), resultCode, AiSupport.sha256(value.id() + ":"
                + value.revision() + ":" + type + ":" + resultCode), now);
    }

    private static AiFillProposal.Attempt attempt(
            AiFillProposal.Attempt value, AiFillProposal.State state,
            String resultCode, Instant now) {
        return new AiFillProposal.Attempt(
                value.id(), value.systemId(), value.tenantId(), value.memberId(),
                value.proposalId(), value.action(), value.requestKey(),
                value.requestHash(), state, resultCode, value.createdAt(), now);
    }

    private static AiFillProposal.FillResult result(
            AiFieldFillFacade.FillReadback value, Instant now) {
        return new AiFillProposal.FillResult(
                value.historyId(), value.recordId(), value.recordVersion(),
                value.schemaVersionId(), value.fieldId(), value.fieldCode(),
                value.resultSchema(), value.displayValue(), value.confidence(),
                value.materializationVersion(), value.outcome(), now);
    }

    private static void assertSnapshot(
            String moduleCode, String recordId, String fieldCode,
            AiFieldFillFacade.SourceSnapshot value) {
        if (!moduleCode.equals(value.moduleCode())
                || !recordId.equals(value.recordId())
                || !fieldCode.equals(value.contract().fieldCode())) {
            throw new IllegalStateException("AI fill owner returned mismatched source scope");
        }
        var returnedSourceIds = new LinkedHashSet<String>();
        var distinctSources = value.sources().stream()
                .allMatch(source -> returnedSourceIds.add(source.fieldId()));
        if (value.sources().size() < 1 || value.sources().size() > 16
                || !distinctSources || !returnedSourceIds.equals(
                Set.copyOf(value.contract().sourceFieldIds()))) {
            throw new IllegalStateException(
                    "AI fill owner returned sources outside the field contract");
        }
    }

    private static void assertPrepared(
            AiFieldFillFacade.SourceSnapshot source,
            AiFillResultParser.Result parsed,
            AiFieldFillFacade.FillPreview preview) {
        if (!source.moduleCode().equals(preview.moduleCode())
                || !source.recordId().equals(preview.recordId())
                || !source.contract().fieldCode().equals(preview.fieldCode())
                || !source.contract().fieldId().equals(preview.fieldId())
                || source.contract().resultSchema() != preview.resultSchema()
                || Double.compare(parsed.confidence(), preview.confidence()) != 0
                || source.recordVersion() != preview.recordVersion()
                || !source.schemaVersionId().equals(preview.schemaVersionId())
                || !source.sourceVersionHash().equals(preview.sourceVersionHash())) {
            throw new IllegalStateException("AI fill owner returned mismatched preview");
        }
    }

    private static void assertReadback(
            AiFillProposal proposal, AiFieldFillFacade.FillReadback value) {
        var provenance = value.provenance();
        if (!proposal.moduleCode().equals(value.moduleCode())
                || !proposal.recordId().equals(value.recordId())
                || !proposal.schemaVersionId().equals(value.schemaVersionId())
                || !proposal.fieldId().equals(value.fieldId())
                || !proposal.fieldCode().equals(value.fieldCode())
                || proposal.resultSchema() != value.resultSchema()
                || Double.compare(proposal.confidence(), value.confidence()) != 0
                || !Long.toString(proposal.providerId()).equals(
                provenance.providerId())
                || proposal.providerVersion() != provenance.providerVersion()
                || !proposal.model().equals(provenance.model())
                || !proposal.promptVersion().equals(provenance.promptVersion())
                || !Long.toString(proposal.policyVersionId()).equals(
                provenance.policyVersionId())) {
            throw new IllegalStateException("AI fill owner returned mismatched readback");
        }
    }

    private String sourceContent(AiFieldFillFacade.SourceSnapshot snapshot) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("recordId", snapshot.recordId());
        root.put("fieldCode", snapshot.contract().fieldCode());
        var sources = root.putArray("sources");
        snapshot.sources().forEach(source -> {
            var row = sources.addObject();
            row.put("fieldName", source.fieldName());
            row.put("fieldType", source.fieldType());
            if (source.displayValue() == null) row.putNull("value");
            else row.put("value", source.displayValue());
        });
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot render AI fill sources", failure);
        }
    }

    private static String systemPrompt(
            AiFieldFillFacade.FieldContract contract, AiPolicy.Version policy) {
        return "Return one JSON object with exact keys value,confidence,clarification. "
                + "The value must match result schema " + contract.resultSchema()
                + "; confidence must be 0..1; clarification must be null or bounded text. "
                + "Do not return source values in clarification. Field prompt: "
                + contract.promptTemplate() + ". Policy prompt version: "
                + policy.promptVersion() + ".";
    }

    private static ProposalView view(AiFillProposal value) {
        var sources = value.sources().stream().map(source -> new SourcePreview(
                source.fieldCode(), source.fieldName(), source.displaySummary(), true))
                .toList();
        ResultView result = value.result() == null ? null : new ResultView(
                value.result().historyId(), value.result().recordId(),
                value.result().recordVersion(), value.result().fieldCode(),
                value.result().displayValue(), value.result().confidence());
        return new ProposalView(
                Long.toString(value.id()), value.state().name(), value.moduleCode(),
                value.recordId(), value.fieldCode(), value.fieldName(),
                value.resultSchema().name(), sources, value.beforeDisplayValue(),
                value.afterDisplayValue(), value.confidence(),
                value.clarificationSummary(), value.overwrite(), value.expiresAt(),
                value.revision(), result,
                Set.of("OK", "AI_FILL_PENDING", "AI_FILL_CLARIFICATION_REQUIRED",
                        "AI_FILL_EXECUTING").contains(value.resultCode())
                        ? null : value.resultCode());
    }

    private static String safeCode(RuntimeException failure) {
        if (failure instanceof BusinessException business
                && business.code().matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            return business.code();
        }
        if (failure instanceof AiProviderClient.ProviderFailure provider) {
            return provider.code();
        }
        return "AI_FILL_FAILED";
    }

    private static void requireRuntime(AiActor actor) {
        actor.require("system.runtime.access");
        actor.require("ai.agent.use");
    }

    private static String key(String value) {
        if (value == null || !value.strip().matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw invalid("AI_IDEMPOTENCY_KEY_INVALID",
                    "AI fill Idempotency-Key is invalid");
        }
        return value.strip();
    }

    private static String code(String value, String name) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("AI_FILL_REQUEST_INVALID", "AI fill " + name + " is invalid");
        }
        return value;
    }

    private static String positiveId(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
            return value;
        } catch (RuntimeException failure) {
            throw invalid("AI_FILL_REQUEST_INVALID",
                    "AI fill " + name + " must be a positive id");
        }
    }

    public record ProposalView(
            String id, String state, String moduleCode, String recordId,
            String fieldCode, String fieldName, String resultSchema,
            List<SourcePreview> sources, String beforeDisplayValue,
            String afterDisplayValue, double confidence, String clarification,
            boolean overwrite, Instant expiresAt, long version,
            ResultView result, String errorCode
    ) {
        public ProposalView { sources = List.copyOf(sources); }
    }

    public record SourcePreview(
            String fieldCode, String fieldName, String displayValue, boolean masked) { }

    public record ResultView(
            String materializationId, String recordId, long recordVersion,
            String fieldCode, String displayValue, double confidence) { }
}
