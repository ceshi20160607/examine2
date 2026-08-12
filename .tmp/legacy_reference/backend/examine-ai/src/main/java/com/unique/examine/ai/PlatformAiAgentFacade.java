package com.unique.examine.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.ai.domain.PlatformAiTaskProposal;
import com.unique.examine.ai.plan.PlatformAiPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.ai.provider.PlatformAiProviderClient;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.ai.service.PlatformAiTaskProposalService;
import com.unique.examine.core.ai.PlatformAuthorizedSystemFacade;
import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class PlatformAiAgentFacade {
    private static final int RESERVED_TOKENS = 10_000;
    private static final int MAXIMUM_USER_CHARS = 4_000;
    private static final int MAXIMUM_ANSWER_CHARS = 16_000;

    private final PlatformAiRepository repository;
    private final PlatformAuthorizedSystemFacade directory;
    private final PlatformOperationsQueryFacade operationsOwner;
    private final PlatformAiProviderClient providers;
    private final PlatformAiPlanParser parser;
    private final PlatformAiTaskProposalService taskProposals;
    private final IdService ids;
    private final Clock clock;
    private final ObjectMapper json;

    public PlatformAiAgentFacade(
            PlatformAiRepository repository,
            PlatformAuthorizedSystemFacade directory,
            PlatformOperationsQueryFacade operationsOwner,
            PlatformAiProviderClient providers,
            PlatformAiPlanParser parser,
            PlatformAiTaskProposalService taskProposals,
            IdService ids,
            Clock clock,
            ObjectMapper json) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.operationsOwner = Objects.requireNonNull(
                operationsOwner, "operationsOwner");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.taskProposals = Objects.requireNonNull(
                taskProposals, "taskProposals");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.json = Objects.requireNonNull(json, "json");
    }

    public CapabilityView capability(PlatformAiActor actor) {
        requireRuntime(actor);
        var value = PlatformAiAdminFacade.capability(repository);
        return new CapabilityView(
                value.available(), value.reason(), value.policyVersion());
    }

    public SessionPage sessions(PlatformAiActor actor, int page, int size) {
        requireRuntime(actor);
        if (page < 1 || size < 1 || size > 100) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_PAGE_INVALID",
                    "Platform AI page or size is invalid");
        }
        var values = repository.sessions(
                actor.accountId(), Math.multiplyExact(page - 1, size), size + 1);
        var hasMore = values.size() > size;
        return new SessionPage(values.stream().limit(size)
                .map(PlatformAiAgentFacade::view).toList(), page, size, hasMore);
    }

    public SessionView createSession(
            PlatformAiActor actor, CreateSession command) {
        requireRuntime(actor);
        var title = bounded(command == null ? null : command.title(),
                "title", 120);
        var now = clock.instant();
        var session = new PlatformAiConversation.Session(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), AiSupport.redactedSummary("title", title),
                PlatformAiConversation.SessionStatus.ACTIVE, now, now);
        repository.insertSession(session, event(
                actor, "SESSION", session.id(), "SESSION_CREATED", "OK", now));
        return view(session);
    }

    public DetailView detail(PlatformAiActor actor, String sessionId) {
        requireRuntime(actor);
        var session = session(actor, sessionId);
        var messages = repository.messages(
                actor.accountId(), session.id(), 200).stream()
                .map(PlatformAiAgentFacade::view).toList();
        var turns = repository.turns(actor.accountId(), session.id(), 100).stream()
                .map(turn -> stored(actor, turn)).toList();
        return new DetailView(view(session), messages, turns);
    }

    public TaskProposalView proposal(
            PlatformAiActor actor, String sessionId, String proposalId) {
        requireRuntime(actor);
        return taskView(taskProposals.get(actor, sessionId, proposalId),
                actor.requestId(), actor.traceId());
    }

    public TaskProposalView confirmTask(
            PlatformAiActor actor,
            String sessionId,
            String proposalId,
            ConfirmTaskProposal command,
            String idempotencyKey) {
        requireRuntime(actor);
        if (command == null) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_REQUEST_INVALID",
                    "Platform AI task confirmation body is required");
        }
        return taskView(taskProposals.confirm(
                actor, sessionId, proposalId, command.expectedRevision(),
                idempotencyKey), actor.requestId(), actor.traceId());
    }

    public TaskProposalView rejectTask(
            PlatformAiActor actor,
            String sessionId,
            String proposalId,
            RejectTaskProposal command) {
        requireRuntime(actor);
        if (command == null) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_REQUEST_INVALID",
                    "Platform AI task rejection body is required");
        }
        return taskView(taskProposals.reject(
                actor, sessionId, proposalId, command.expectedRevision()),
                actor.requestId(), actor.traceId());
    }

    public TurnView submit(
            PlatformAiActor actor, String sessionId, SubmitMessage command) {
        requireRuntime(actor);
        var content = bounded(command == null ? null : command.content(),
                "content", MAXIMUM_USER_CHARS);
        var session = session(actor, sessionId);
        if (session.status() != PlatformAiConversation.SessionStatus.ACTIVE) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_SESSION_CLOSED",
                    "Platform AI session is not active");
        }
        var policy = availablePolicy();
        var provider = currentProvider(policy);
        var now = clock.instant();
        var turnId = ids.nextId();
        var running = new PlatformAiConversation.Turn(
                turnId, PlatformAiConversation.Scope.PLATFORM, actor.accountId(),
                session.id(), policy.id(), provider.id(), provider.version(),
                actor.authorizationEpoch(), "UNRESOLVED",
                PlatformAiConversation.TurnStatus.RUNNING,
                AiSupport.redactedSummary("request", content),
                AiSupport.sha256(content), null, null, null, 0,
                "PLATFORM_AI_RUNNING", false, RESERVED_TOKENS, 0,
                actor.requestId(), actor.traceId(), now, null);
        var userMessage = new PlatformAiConversation.Message(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), session.id(), turnId,
                PlatformAiConversation.Role.USER,
                AiSupport.redactedSummary("user", content),
                AiSupport.sha256(content),
                content.codePointCount(0, content.length()), now);
        var touched = new PlatformAiConversation.Session(
                session.id(), session.scope(), session.accountId(),
                session.titleSummary(), session.status(), session.createdAt(), now);
        var settings = policy.settings();
        var admission = repository.admitTurn(
                session, touched, userMessage, running,
                new PlatformAiConversation.QuotaReservation(
                        actor.accountId(), policy.id(), day(now),
                        settings.dailyRequestQuota(), settings.dailyTokenQuota(),
                        settings.maxConcurrency(), RESERVED_TOKENS),
                event(actor, "TURN", turnId, "TURN_STARTED",
                        "PLATFORM_AI_RUNNING", now));
        requireAdmission(admission);
        return execute(actor, running, content, policy, provider);
    }

    private TurnView execute(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            String content,
            PlatformAiPolicy.Version policy,
            PlatformAiProvider provider) {
        var usage = new UsageTotals();
        PlatformAiPlanParser.Plan plan = null;
        try {
            var planned = providers.complete(provider, new AiProviderClient.Request(
                    AiProviderClient.Phase.PLAN, planPrompt(policy), content, 1_024));
            usage.add(planned);
            ensureUsage(running, usage);
            plan = parser.parse(planned.content());
            if (!policy.settings().allowedOperations()
                    .contains(plan.operation().name())) {
                throw AiSupport.invalid(
                        "PLATFORM_AI_OPERATION_DENIED",
                        "Platform AI plan exceeds the active policy");
            }
            recheckCurrent(policy, provider);
            return switch (plan.operation()) {
                case AUTHORIZED_SYSTEMS_QUERY -> query(
                        actor, running, policy, provider, plan,
                        authorizedSystems(actor, policy), usage);
                case SYSTEM_SWITCH_GUIDANCE -> guidance(
                        actor, running, plan, authorizedSystems(actor, policy), usage);
                case PLATFORM_TASK_DRAFT -> task(
                        actor, running, policy, plan, usage);
                case PLATFORM_OPERATIONS_QUERY -> operations(
                        actor, running, policy, provider, plan, usage);
            };
        } catch (AiProviderClient.ProviderFailure failure) {
            if ("PLATFORM_AI_PROVIDER_USAGE_INVALID".equals(failure.code())) {
                usage.capTo(running.reservedTokens());
            }
            return failed(actor, running, plan, failure.code(),
                    failure.retryable(), usage);
        } catch (RuntimeException failure) {
            return failed(actor, running, plan,
                    failure instanceof BusinessException business
                            ? business.code() : "PLATFORM_AI_FAILED",
                    false, usage);
        }
    }

    private TurnView query(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            PlatformAiPolicy.Version policy,
            PlatformAiProvider provider,
            PlatformAiPlanParser.Plan plan,
            List<AuthorizedSystemView> systems,
            UsageTotals usage) {
        var projection = projection("AUTHORIZED_SYSTEMS", systems, null);
        var summarized = providers.complete(provider, new AiProviderClient.Request(
                AiProviderClient.Phase.SUMMARY, summaryPrompt(policy),
                projection, 2_048));
        usage.add(summarized);
        ensureUsage(running, usage);
        var answer = "Based only on your current authorized-system directory: "
                + bounded(summarized.content(), "answer", MAXIMUM_ANSWER_CHARS);
        return succeed(actor, running, plan, answer, systems, null,
                "AUTHORIZED_SYSTEMS", projection, usage);
    }

    private TurnView guidance(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            PlatformAiPlanParser.Plan plan,
            List<AuthorizedSystemView> systems,
            UsageTotals usage) {
        var selected = plan.requestedSystemCode() == null ? null
                : systems.stream().filter(value -> value.systemCode().equals(
                plan.requestedSystemCode())).findFirst().orElse(null);
        var guidance = selected == null
                ? new SwitchGuidance(
                plan.requestedSystemCode(),
                "Choose an authorized system and switch context before asking about business records.",
                null)
                : new SwitchGuidance(
                plan.requestedSystemCode(),
                "Switch to " + selected.systemName()
                        + " before asking about its business records.",
                selected.switchTarget());
        var projection = projection("SWITCH_GUIDANCE", systems, guidance);
        return succeed(actor, running, plan, guidance.message(), systems,
                guidance, "SWITCH_GUIDANCE", projection, usage);
    }

    private TurnView task(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            PlatformAiPolicy.Version policy,
            PlatformAiPlanParser.Plan plan,
            UsageTotals usage) {
        var prepared = taskProposals.prepare(actor, running, policy, plan);
        var proposal = prepared.proposal();
        var answer = proposal.state()
                == PlatformAiTaskProposal.State.CLARIFICATION_REQUIRED
                ? proposal.clarification()
                : "Review the personal platform task draft and confirm it explicitly.";
        var now = clock.instant();
        var terminal = terminal(
                running, plan.operation().name(),
                PlatformAiConversation.TurnStatus.SUCCEEDED, plan.planHash(),
                AiSupport.redactedSummary("answer", answer),
                AiSupport.sha256(answer), 0, "OK", false, now);
        var assistant = new PlatformAiConversation.Message(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), running.sessionId(), running.id(),
                PlatformAiConversation.Role.ASSISTANT,
                AiSupport.redactedSummary("assistant", answer),
                AiSupport.sha256(answer),
                answer.codePointCount(0, answer.length()), now);
        if (!repository.finishTurnWithTaskProposal(
                running, terminal, assistant, usage(running, usage, now),
                proposal, prepared.event(), event(
                        actor, "TURN", running.id(), "TURN_SUCCEEDED", "OK", now))) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_TURN_CONFLICT",
                    "Platform AI turn was already finished");
        }
        return new TurnView(
                Long.toString(running.id()), "SUCCEEDED", plan.operation().name(),
                answer, null, false, List.of(), null,
                taskView(proposal, actor.requestId(), actor.traceId()),
                null,
                actor.requestId(), actor.traceId());
    }

    private TurnView operations(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            PlatformAiPolicy.Version policy,
            PlatformAiProvider provider,
            PlatformAiPlanParser.Plan plan,
            UsageTotals usage) {
        var query = Objects.requireNonNull(
                plan.operationsQuery(), "operationsQuery");
        if (!query.actionable()) {
            var view = new OperationsView(
                    null, query.confidence(), query.clarification(),
                    List.of(), null, null, List.of());
            return operationsSucceed(
                    actor, running, plan, view, query.clarification(),
                    "OPERATIONS_CLARIFICATION", usage);
        }
        var kind = PlatformOperationsQueryFacade.QueryKind.valueOf(
                query.queryKind().name());
        var result = operationsOwner.query(
                new PlatformOperationsQueryFacade.Request(
                        actor.accountId(), actor.authorizationEpoch(), kind,
                        query.limit()));
        if (result.queryKind() != kind) {
            throw new IllegalStateException(
                    "Platform operations owner returned a mismatched projection");
        }
        recheckCurrent(policy, provider);
        var view = operationsView(result, policy, query.confidence(), query.limit());
        var answer = switch (kind) {
            case PERSONAL_TASKS -> "Found " + view.personalTasks().size()
                    + " personal platform tasks.";
            case AI_QUOTA -> "Current platform AI quota is available.";
            case SERVICE_HEALTH -> "Current coarse service health is "
                    + view.serviceHealth().status() + ".";
            case AGENT_ACTIVITY -> "Found " + view.agentActivity().size()
                    + " recent personal Agent activity events.";
        };
        return operationsSucceed(
                actor, running, plan, view, answer, kind.name(), usage);
    }

    private TurnView operationsSucceed(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            PlatformAiPlanParser.Plan plan,
            OperationsView operations,
            String answer,
            String evidenceType,
            UsageTotals usage) {
        var now = clock.instant();
        var projection = operationsProjection(operations, true);
        var terminal = terminal(
                running, plan.operation().name(),
                PlatformAiConversation.TurnStatus.SUCCEEDED, plan.planHash(),
                AiSupport.redactedSummary("answer", answer),
                AiSupport.sha256(answer), 0, "OK", false, now);
        var assistant = new PlatformAiConversation.Message(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), running.sessionId(), running.id(),
                PlatformAiConversation.Role.ASSISTANT,
                AiSupport.redactedSummary("assistant", answer),
                AiSupport.sha256(answer),
                answer.codePointCount(0, answer.length()), now);
        var evidence = new PlatformAiConversation.Evidence(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), running.id(), evidenceType,
                plan.planHash(), projection, AiSupport.sha256(projection),
                0, "OK", now);
        finish(running, terminal, assistant, usage(running, usage, now),
                evidence, event(actor, "TURN", running.id(),
                        "TURN_SUCCEEDED", "OK", now));
        return new TurnView(
                Long.toString(running.id()), "SUCCEEDED", plan.operation().name(),
                answer, null, false, List.of(), null, null, operations,
                actor.requestId(), actor.traceId());
    }

    private static OperationsView operationsView(
            PlatformOperationsQueryFacade.Result result,
            PlatformAiPolicy.Version policy,
            double confidence,
            int requestedLimit) {
        return switch (result) {
            case PlatformOperationsQueryFacade.PersonalTasksResult value -> {
                if (value.tasks().size() > requestedLimit) {
                    throw new IllegalStateException(
                            "Platform operations owner exceeded the requested limit");
                }
                yield new OperationsView(
                        result.queryKind().name(), confidence, null,
                        value.tasks().stream().map(task -> new PersonalTaskView(
                                task.taskId(), task.title(), task.dueAt(),
                                task.priority().name(), task.status().name(),
                                task.source().name(), task.createdAt())).toList(),
                        null, null, List.of());
            }
            case PlatformOperationsQueryFacade.AiQuotaResult value ->
                    new OperationsView(
                            result.queryKind().name(), confidence, null, List.of(),
                            new QuotaView(
                                    Long.toString(policy.id()), value.bucketStart(),
                                    value.bucketEnd(), value.requestLimit(),
                                    value.requestCount(), value.remainingRequests(),
                                    value.tokenLimit(), value.usedTokens(),
                                    value.reservedTokens(), value.remainingTokens(),
                                    value.concurrencyLimit(), value.runningCount(),
                                    value.remainingConcurrency()),
                            null, List.of());
            case PlatformOperationsQueryFacade.ServiceHealthResult value -> {
                var status = value.services().stream().anyMatch(item ->
                        item.state()
                                == PlatformOperationsQueryFacade.HealthState.DEGRADED)
                        ? "DEGRADED" : value.services().stream().anyMatch(item ->
                        item.state()
                                == PlatformOperationsQueryFacade.HealthState.UNKNOWN)
                        ? "UNKNOWN" : "UP";
                yield new OperationsView(
                        result.queryKind().name(), confidence, null, List.of(),
                        null, new ServiceHealthView(
                        status, value.services().stream().map(item ->
                                new HealthComponentView(
                                        item.service().name(), item.state().name()))
                                .toList(), value.observedAt()), List.of());
            }
            case PlatformOperationsQueryFacade.AgentActivityResult value -> {
                if (value.activities().size() > requestedLimit) {
                    throw new IllegalStateException(
                            "Platform operations owner exceeded the requested limit");
                }
                yield new OperationsView(
                        result.queryKind().name(), confidence, null, List.of(),
                        null, null, value.activities().stream().map(item ->
                        new AgentActivityView(
                                item.event(), item.occurredAt(), item.operation(),
                                item.resultCode(), item.requestId(), item.traceId()))
                                .toList());
            }
        };
    }

    private TurnView succeed(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            PlatformAiPlanParser.Plan plan,
            String answer,
            List<AuthorizedSystemView> systems,
            SwitchGuidance guidance,
            String evidenceType,
            String projection,
            UsageTotals usage) {
        var now = clock.instant();
        var terminal = terminal(
                running, plan.operation().name(),
                PlatformAiConversation.TurnStatus.SUCCEEDED, plan.planHash(),
                AiSupport.redactedSummary("answer", answer),
                AiSupport.sha256(answer), systems.size(), "OK", false, now);
        var assistant = new PlatformAiConversation.Message(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), running.sessionId(), running.id(),
                PlatformAiConversation.Role.ASSISTANT,
                AiSupport.redactedSummary("assistant", answer),
                AiSupport.sha256(answer),
                answer.codePointCount(0, answer.length()), now);
        var evidence = new PlatformAiConversation.Evidence(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), running.id(), evidenceType,
                plan.planHash(), projection, AiSupport.sha256(projection),
                systems.size(), "OK", now);
        finish(running, terminal, assistant, usage(running, usage, now),
                evidence, event(actor, "TURN", running.id(),
                        "TURN_SUCCEEDED", "OK", now));
        return new TurnView(
                Long.toString(running.id()), "SUCCEEDED", plan.operation().name(),
                answer, null, false, systems, guidance,
                null, null,
                actor.requestId(), actor.traceId());
    }

    private TurnView failed(
            PlatformAiActor actor,
            PlatformAiConversation.Turn running,
            PlatformAiPlanParser.Plan plan,
            String resultCode,
            boolean retryable,
            UsageTotals usage) {
        var now = clock.instant();
        var status = retryable
                ? PlatformAiConversation.TurnStatus.RETRYABLE
                : PlatformAiConversation.TurnStatus.FAILED;
        var operation = plan == null ? "UNRESOLVED" : plan.operation().name();
        var terminal = terminal(
                running, operation, status,
                plan == null ? null : plan.planHash(), null, null,
                0, safeCode(resultCode), retryable, now);
        finish(running, terminal, null, usage(running, usage, now), null,
                event(actor, "TURN", running.id(), "TURN_FAILED",
                        terminal.resultCode(), now));
        return new TurnView(
                Long.toString(running.id()), status.name(),
                plan == null ? null : plan.operation().name(), null,
                terminal.resultCode(), retryable, List.of(), null,
                null, null,
                actor.requestId(), actor.traceId());
    }

    private void finish(
            PlatformAiConversation.Turn expected,
            PlatformAiConversation.Turn terminal,
            PlatformAiConversation.Message assistant,
            PlatformAiConversation.Usage usage,
            PlatformAiConversation.Evidence evidence,
            PlatformAiConversation.AuditEvent event) {
        if (!repository.finishTurn(
                expected, terminal, assistant, usage, evidence, event)) {
            throw AiSupport.conflict(
                    "PLATFORM_AI_TURN_CONFLICT",
                    "Platform AI turn was already finished");
        }
    }

    private PlatformAiConversation.Turn terminal(
            PlatformAiConversation.Turn running,
            String operation,
            PlatformAiConversation.TurnStatus status,
            String planHash,
            String responseSummary,
            String responseHash,
            int returnedSystems,
            String resultCode,
            boolean retryable,
            Instant now) {
        return new PlatformAiConversation.Turn(
                running.id(), running.scope(), running.accountId(),
                running.sessionId(), running.policyVersionId(),
                running.providerId(), running.providerVersion(),
                running.authorizationEpoch(), operation, status,
                running.requestSummary(), running.requestHash(), planHash,
                responseSummary, responseHash, returnedSystems, resultCode,
                retryable, running.reservedTokens(), Math.max(0,
                Duration.between(running.createdAt(), now).toMillis()),
                running.requestId(), running.traceId(), running.createdAt(), now);
    }

    private PlatformAiConversation.Usage usage(
            PlatformAiConversation.Turn turn, UsageTotals value, Instant now) {
        return new PlatformAiConversation.Usage(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                turn.accountId(), turn.id(), turn.policyVersionId(),
                turn.providerId(), value.calls, value.promptTokens,
                value.completionTokens,
                Math.addExact(value.promptTokens, value.completionTokens),
                value.latencyMs, now);
    }

    private StoredTurnView stored(
            PlatformAiActor actor, PlatformAiConversation.Turn value) {
        var evidence = repository.evidence(
                actor.accountId(), value.id(), 1).stream()
                .findFirst().map(this::view).orElse(null);
        var proposal = repository.taskProposalByTurn(
                actor.accountId(), value.id()).map(item -> taskView(
                item, value.requestId(), value.traceId())).orElse(null);
        return new StoredTurnView(
                Long.toString(value.id()), value.status().name(),
                "UNRESOLVED".equals(value.operation()) ? null : value.operation(),
                value.responseSummary(), "OK".equals(value.resultCode())
                || "PLATFORM_AI_RUNNING".equals(value.resultCode())
                ? null : value.resultCode(), value.retryable(),
                value.returnedSystems(), evidence, proposal,
                evidence == null ? null : evidence.operations(), value.createdAt(),
                value.finishedAt());
    }

    private EvidenceView view(PlatformAiConversation.Evidence value) {
        try {
            var root = json.readTree(value.projectionJson());
            var systems = new ArrayList<AuthorizedSystemView>();
            root.path("systems").forEach(node -> systems.add(system(node)));
            SwitchGuidance guidance = null;
            if (root.has("guidance") && root.get("guidance").isObject()) {
                var node = root.get("guidance");
                guidance = new SwitchGuidance(
                        nullableText(node, "requestedSystemCode"),
                        node.path("message").textValue(),
                        nullableText(node, "switchTarget"));
            }
            OperationsView operations = root.has("operations")
                    && root.get("operations").isObject()
                    ? operations(root.get("operations")) : null;
            return new EvidenceView(
                    value.evidenceType(), systems, guidance, operations);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Cannot decode platform AI safe evidence", failure);
        }
    }

    private String operationsProjection(
            OperationsView value, boolean redactTaskTitles) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("scope", "PLATFORM_OPERATIONS");
        var node = root.putObject("operations");
        if (value.queryKind() == null) node.putNull("queryKind");
        else node.put("queryKind", value.queryKind());
        node.put("confidence", value.confidence());
        if (value.clarification() == null) node.putNull("clarification");
        else node.put("clarification", "[clarification:redacted]");
        var tasks = node.putArray("personalTasks");
        value.personalTasks().forEach(task -> {
            var item = tasks.addObject();
            item.put("taskId", task.taskId());
            item.put("title", redactTaskTitles ? "[title:redacted]" : task.title());
            if (task.dueAt() == null) item.putNull("dueAt");
            else item.put("dueAt", task.dueAt().toString());
            item.put("priority", task.priority());
            item.put("status", task.status());
            item.put("source", task.source());
            item.put("createdAt", task.createdAt().toString());
        });
        if (value.quota() == null) node.putNull("quota");
        else {
            var quota = node.putObject("quota");
            quota.put("policyVersion", value.quota().policyVersion());
            quota.put("periodStart", value.quota().periodStart().toString());
            quota.put("periodEnd", value.quota().periodEnd().toString());
            quota.put("requestLimit", value.quota().requestLimit());
            quota.put("requestCount", value.quota().requestCount());
            quota.put("remainingRequests", value.quota().remainingRequests());
            quota.put("tokenLimit", value.quota().tokenLimit());
            quota.put("usedTokens", value.quota().usedTokens());
            quota.put("reservedTokens", value.quota().reservedTokens());
            quota.put("remainingTokens", value.quota().remainingTokens());
            quota.put("concurrencyLimit", value.quota().concurrencyLimit());
            quota.put("runningCount", value.quota().runningCount());
            quota.put("remainingConcurrency", value.quota().remainingConcurrency());
        }
        if (value.serviceHealth() == null) node.putNull("serviceHealth");
        else {
            var health = node.putObject("serviceHealth");
            health.put("status", value.serviceHealth().status());
            health.put("checkedAt", value.serviceHealth().checkedAt().toString());
            var components = health.putArray("components");
            value.serviceHealth().components().forEach(component -> {
                var item = components.addObject();
                item.put("component", component.component());
                item.put("status", component.status());
            });
        }
        var activities = node.putArray("agentActivity");
        value.agentActivity().forEach(activity -> {
            var item = activities.addObject();
            item.put("event", activity.event());
            item.put("time", activity.time().toString());
            item.put("operation", activity.operation());
            item.put("resultCode", activity.resultCode());
            item.put("requestId", activity.requestId());
            item.put("traceId", activity.traceId());
        });
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode platform AI operations evidence", failure);
        }
    }

    private static OperationsView operations(JsonNode value) {
        var tasks = new ArrayList<PersonalTaskView>();
        value.path("personalTasks").forEach(item -> tasks.add(
                new PersonalTaskView(
                        item.path("taskId").textValue(),
                        item.path("title").textValue(),
                        nullableInstant(item, "dueAt"),
                        item.path("priority").textValue(),
                        item.path("status").textValue(),
                        item.path("source").textValue(),
                        Instant.parse(item.path("createdAt").textValue()))));
        QuotaView quota = null;
        if (value.has("quota") && value.get("quota").isObject()) {
            var item = value.get("quota");
            quota = new QuotaView(
                    item.path("policyVersion").textValue(),
                    Instant.parse(item.path("periodStart").textValue()),
                    Instant.parse(item.path("periodEnd").textValue()),
                    item.path("requestLimit").intValue(),
                    item.path("requestCount").intValue(),
                    item.path("remainingRequests").intValue(),
                    item.path("tokenLimit").longValue(),
                    item.path("usedTokens").longValue(),
                    item.path("reservedTokens").longValue(),
                    item.path("remainingTokens").longValue(),
                    item.path("concurrencyLimit").intValue(),
                    item.path("runningCount").intValue(),
                    item.path("remainingConcurrency").intValue());
        }
        ServiceHealthView health = null;
        if (value.has("serviceHealth")
                && value.get("serviceHealth").isObject()) {
            var item = value.get("serviceHealth");
            var components = new ArrayList<HealthComponentView>();
            item.path("components").forEach(component -> components.add(
                    new HealthComponentView(
                            component.path("component").textValue(),
                            component.path("status").textValue())));
            health = new ServiceHealthView(
                    item.path("status").textValue(), components,
                    Instant.parse(item.path("checkedAt").textValue()));
        }
        var activities = new ArrayList<AgentActivityView>();
        value.path("agentActivity").forEach(item -> activities.add(
                new AgentActivityView(
                        item.path("event").textValue(),
                        Instant.parse(item.path("time").textValue()),
                        item.path("operation").textValue(),
                        item.path("resultCode").textValue(),
                        item.path("requestId").textValue(),
                        item.path("traceId").textValue())));
        return new OperationsView(
                nullableText(value, "queryKind"), value.path("confidence").doubleValue(),
                nullableText(value, "clarification"), tasks, quota, health,
                activities);
    }

    private String projection(
            String scope,
            List<AuthorizedSystemView> systems,
            SwitchGuidance guidance) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("scope", scope);
        var values = root.putArray("systems");
        systems.forEach(value -> system(values.addObject(), value));
        if (guidance != null) {
            var node = root.putObject("guidance");
            if (guidance.requestedSystemCode() == null) {
                node.putNull("requestedSystemCode");
            } else node.put("requestedSystemCode", guidance.requestedSystemCode());
            node.put("message", guidance.message());
            if (guidance.switchTarget() == null) node.putNull("switchTarget");
            else node.put("switchTarget", guidance.switchTarget());
        }
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode platform AI safe evidence", failure);
        }
    }

    private static void system(
            com.fasterxml.jackson.databind.node.ObjectNode node,
            AuthorizedSystemView value) {
        node.put("systemId", value.systemId());
        node.put("systemCode", value.systemCode());
        node.put("systemName", value.systemName());
        node.put("status", value.status());
        node.put("membershipState", value.membershipState());
        node.put("accessState", value.accessState());
        node.put("switchTarget", value.switchTarget());
    }

    private static AuthorizedSystemView system(
            PlatformAuthorizedSystemFacade.SystemAccess value) {
        return new AuthorizedSystemView(
                value.systemId(), value.systemCode(), value.systemName(),
                value.status(), value.membershipState(), value.accessState(),
                value.switchTarget());
    }

    private List<AuthorizedSystemView> authorizedSystems(
            PlatformAiActor actor, PlatformAiPolicy.Version policy) {
        return directory.authorizedSystems(
                new PlatformAuthorizedSystemFacade.Request(
                        actor.accountId(), actor.authorizationEpoch(),
                        actor.effectivePermissions(), actor.requestId(),
                        actor.traceId())).systems().stream()
                .limit(policy.settings().maxSystems())
                .map(PlatformAiAgentFacade::system).toList();
    }

    private static AuthorizedSystemView system(JsonNode value) {
        return new AuthorizedSystemView(
                value.path("systemId").textValue(),
                value.path("systemCode").textValue(),
                value.path("systemName").textValue(),
                value.path("status").textValue(),
                value.path("membershipState").textValue(),
                value.path("accessState").textValue(),
                value.path("switchTarget").textValue());
    }

    private void recheckCurrent(
            PlatformAiPolicy.Version policy, PlatformAiProvider provider) {
        var current = repository.activePolicy().filter(value ->
                value.id() == policy.id() && value.settings().enabled())
                .orElseThrow(() -> AiSupport.unavailable(
                        "PLATFORM_AI_POLICY_CHANGED",
                        "Platform AI policy changed during execution"));
        var currentProvider = repository.provider(current.providerId())
                .filter(value -> value.enabled()
                        && value.id() == provider.id()
                        && value.version() == provider.version())
                .orElseThrow(() -> AiSupport.unavailable(
                        "PLATFORM_AI_PROVIDER_UNAVAILABLE",
                        "Platform AI provider changed during execution"));
        if (!current.model().equals(currentProvider.model())) {
            throw AiSupport.unavailable(
                    "PLATFORM_AI_PROVIDER_UNAVAILABLE",
                    "Platform AI provider model changed during execution");
        }
    }

    private PlatformAiPolicy.Version availablePolicy() {
        return repository.activePolicy().filter(
                value -> value.settings().enabled()).orElseThrow(() ->
                AiSupport.unavailable(
                        "PLATFORM_AI_CAPABILITY_UNAVAILABLE",
                        "Platform AI policy is not published or enabled"));
    }

    private PlatformAiProvider currentProvider(PlatformAiPolicy.Version policy) {
        return repository.provider(policy.providerId()).filter(
                value -> value.enabled()
                        && value.version() == policy.providerVersion())
                .orElseThrow(() -> AiSupport.unavailable(
                        "PLATFORM_AI_PROVIDER_UNAVAILABLE",
                        "Platform AI provider is unavailable"));
    }

    private PlatformAiConversation.Session session(
            PlatformAiActor actor, String id) {
        return repository.session(actor.accountId(), positiveId(id, "sessionId"))
                .orElseThrow(() -> AiSupport.notFound(
                        "Platform AI session does not exist"));
    }

    private PlatformAiConversation.AuditEvent event(
            PlatformAiActor actor, String aggregateType, long aggregateId,
            String eventType, String resultCode, Instant now) {
        return new PlatformAiConversation.AuditEvent(
                ids.nextId(), PlatformAiConversation.Scope.PLATFORM,
                actor.accountId(), aggregateType, aggregateId, eventType,
                safeCode(resultCode), actor.requestId(), actor.traceId(),
                AiSupport.sha256(aggregateType + ":" + aggregateId + ":"
                        + eventType + ":" + safeCode(resultCode)), now);
    }

    private static Instant day(Instant value) {
        return value.atZone(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static void requireRuntime(PlatformAiActor actor) {
        actor.require("platform.runtime.access");
        actor.require("platform.ai.agent.use");
    }

    private static void requireAdmission(
            PlatformAiRepository.AdmissionResult value) {
        if (value == PlatformAiRepository.AdmissionResult.ADMITTED) return;
        var code = switch (value) {
            case SESSION_CONFLICT -> "PLATFORM_AI_SESSION_CONFLICT";
            case REQUEST_QUOTA_EXCEEDED -> "PLATFORM_AI_REQUEST_QUOTA_EXCEEDED";
            case TOKEN_QUOTA_EXCEEDED -> "PLATFORM_AI_TOKEN_QUOTA_EXCEEDED";
            case CONCURRENCY_EXCEEDED -> "PLATFORM_AI_CONCURRENCY_EXCEEDED";
            case ADMITTED -> throw new IllegalStateException();
        };
        throw new BusinessException(
                code, "Platform AI execution admission was rejected",
                value == PlatformAiRepository.AdmissionResult.SESSION_CONFLICT
                        ? HttpStatus.CONFLICT : HttpStatus.TOO_MANY_REQUESTS);
    }

    private static String planPrompt(PlatformAiPolicy.Version policy) {
        return "Return one JSON object only. To list systems, return exact keys "
                + "operation with value AUTHORIZED_SYSTEMS_QUERY. For any request "
                + "for one personal platform follow-up task, use exact keys "
                + "operation,title,description,dueAt,priority,confidence,clarification "
                + "with operation PLATFORM_TASK_DRAFT. title is at most 200 chars; "
                + "description and clarification are null or at most 200 chars; "
                + "dueAt is an ISO instant or null; priority is LOW, NORMAL, HIGH or "
                + "URGENT; confidence is 0 through 1. If clarification is needed, "
                + "title,description,dueAt,priority must all be null. Never assign "
                + "another account. For personal platform tasks, current AI quota, "
                + "coarse service health or recent personal Agent activity, use exact "
                + "keys operation,queryKind,limit,confidence,clarification with "
                + "operation PLATFORM_OPERATIONS_QUERY; queryKind is PERSONAL_TASKS, "
                + "AI_QUOTA, SERVICE_HEALTH or AGENT_ACTIVITY; limit is 1 through 50. "
                + "For clarification, queryKind and limit must be null. Never emit "
                + "SQL, filters, account/system/tenant/member/module/field/record ids, "
                + "raw logs, hosts, secrets or task descriptions. For any request "
                + "about a system business record, "
                + "module, field, report, system task or write, return exact keys "
                + "operation and "
                + "requestedSystemCode, with operation SYSTEM_SWITCH_GUIDANCE and "
                + "requestedSystemCode as a system code or null. No other keys, "
                + "tools, SQL, URLs, business payloads or actions. Authorized "
                + "operations: " + policy.settings().allowedOperations()
                + ". Prompt version: " + policy.settings().promptVersion() + ".";
    }

    private static String summaryPrompt(PlatformAiPolicy.Version policy) {
        return "Summarize only the supplied current authorized-system directory. "
                + "State that it is permission-projected. Do not infer modules, "
                + "records, tenant business data, credentials or write actions. "
                + "Prompt version: " + policy.settings().promptVersion() + ".";
    }

    private static String bounded(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_REQUEST_INVALID",
                    "Platform AI " + field + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_REQUEST_INVALID",
                    "Platform AI " + field + " is too long");
        }
        return value;
    }

    private static long positiveId(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException failure) {
            throw AiSupport.invalid(
                    "PLATFORM_AI_ID_INVALID",
                    field + " must be a positive id");
        }
    }

    private static String safeCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]{1,63}$")
                ? value : "PLATFORM_AI_FAILED";
    }

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.textValue();
    }

    private static Instant nullableInstant(JsonNode node, String field) {
        var value = nullableText(node, field);
        return value == null ? null : Instant.parse(value);
    }

    private static void ensureUsage(
            PlatformAiConversation.Turn turn, UsageTotals usage) {
        if (usage.totalTokens() > turn.reservedTokens()) {
            throw new AiProviderClient.ProviderFailure(
                    "PLATFORM_AI_PROVIDER_USAGE_INVALID", false);
        }
    }

    private static SessionView view(PlatformAiConversation.Session value) {
        return new SessionView(
                Long.toString(value.id()), value.titleSummary(),
                value.status().name(), value.createdAt(), value.updatedAt());
    }

    private static MessageView view(PlatformAiConversation.Message value) {
        return new MessageView(
                Long.toString(value.id()), value.role().name(), "REDACTED",
                value.redactedSummary(), value.createdAt());
    }

    private static TaskProposalView taskView(
            PlatformAiTaskProposal value, String requestId, String traceId) {
        TaskPreviewView preview = value.preview() == null ? null
                : new TaskPreviewView(
                value.preview().title(), value.preview().description(),
                value.preview().dueAt(), value.preview().priority().name(), true);
        TaskResultView result = value.result() == null ? null
                : new TaskResultView(
                value.result().taskId(), value.result().title(),
                value.result().description(), value.result().dueAt(),
                value.result().priority().name(), value.result().status(),
                value.result().source(), value.result().createdAt());
        var error = switch (value.state()) {
            case FAILED, EXPIRED -> value.resultCode();
            default -> null;
        };
        return new TaskProposalView(
                Long.toString(value.id()), value.state().name(), value.revision(),
                preview, value.confidence(), value.clarification(), value.expiresAt(),
                result, error, requestId, traceId);
    }

    public record CapabilityView(
            boolean available, String reason, String policyVersion) { }

    public record CreateSession(String title) { }

    public record SubmitMessage(String content) { }

    public record ConfirmTaskProposal(long expectedRevision) { }

    public record RejectTaskProposal(long expectedRevision) { }

    public record SessionView(
            String id, String title, String status,
            Instant createdAt, Instant updatedAt) { }

    public record SessionPage(
            List<SessionView> rows, int page, int size, boolean hasMore) {
        public SessionPage { rows = List.copyOf(rows); }
    }

    public record DetailView(
            SessionView session,
            List<MessageView> messages,
            List<StoredTurnView> turns) {
        public DetailView {
            messages = List.copyOf(messages);
            turns = List.copyOf(turns);
        }
    }

    public record MessageView(
            String id, String role, String status,
            String content, Instant createdAt) { }

    public record StoredTurnView(
            String id,
            String status,
            String operation,
            String responseSummary,
            String errorCode,
            boolean retryable,
            int returnedSystems,
            EvidenceView evidence,
            TaskProposalView proposal,
            OperationsView operations,
            Instant createdAt,
            Instant finishedAt) { }

    public record TurnView(
            String id,
            String status,
            String operation,
            String answer,
            String errorCode,
            boolean retryable,
            List<AuthorizedSystemView> systems,
            SwitchGuidance guidance,
            TaskProposalView proposal,
            OperationsView operations,
            String requestId,
            String traceId) {
        public TurnView { systems = List.copyOf(systems); }
    }

    public record TaskProposalView(
            String id,
            String state,
            long revision,
            TaskPreviewView preview,
            double confidence,
            String clarification,
            Instant expiresAt,
            TaskResultView result,
            String errorCode,
            String requestId,
            String traceId) { }

    public record TaskPreviewView(
            String title,
            String description,
            Instant dueAt,
            String priority,
            boolean selfAssigned) { }

    public record TaskResultView(
            String taskId,
            String title,
            String description,
            Instant dueAt,
            String priority,
            String status,
            String source,
            Instant createdAt) { }

    public record OperationsView(
            String queryKind,
            double confidence,
            String clarification,
            List<PersonalTaskView> personalTasks,
            QuotaView quota,
            ServiceHealthView serviceHealth,
            List<AgentActivityView> agentActivity) {
        public OperationsView {
            personalTasks = List.copyOf(personalTasks);
            agentActivity = List.copyOf(agentActivity);
        }
    }

    public record PersonalTaskView(
            String taskId,
            String title,
            Instant dueAt,
            String priority,
            String status,
            String source,
            Instant createdAt) { }

    public record QuotaView(
            String policyVersion,
            Instant periodStart,
            Instant periodEnd,
            int requestLimit,
            int requestCount,
            int remainingRequests,
            long tokenLimit,
            long usedTokens,
            long reservedTokens,
            long remainingTokens,
            int concurrencyLimit,
            int runningCount,
            int remainingConcurrency) { }

    public record ServiceHealthView(
            String status,
            List<HealthComponentView> components,
            Instant checkedAt) {
        public ServiceHealthView { components = List.copyOf(components); }
    }

    public record HealthComponentView(String component, String status) { }

    public record AgentActivityView(
            String event,
            Instant time,
            String operation,
            String resultCode,
            String requestId,
            String traceId) { }

    public record AuthorizedSystemView(
            String systemId,
            String systemCode,
            String systemName,
            String status,
            String membershipState,
            String accessState,
            String switchTarget) { }

    public record SwitchGuidance(
            String requestedSystemCode,
            String message,
            String switchTarget) { }

    public record EvidenceView(
            String type,
            List<AuthorizedSystemView> systems,
            SwitchGuidance guidance,
            OperationsView operations) {
        public EvidenceView { systems = List.copyOf(systems); }
    }

    private static final class UsageTotals {
        private int calls;
        private int promptTokens;
        private int completionTokens;
        private long latencyMs;

        private void add(AiProviderClient.Completion value) {
            calls++;
            promptTokens = Math.addExact(promptTokens, value.promptTokens());
            completionTokens = Math.addExact(
                    completionTokens, value.completionTokens());
            latencyMs = Math.addExact(latencyMs, value.latencyMs());
        }

        private int totalTokens() {
            return Math.addExact(promptTokens, completionTokens);
        }

        private void capTo(int reservedTokens) {
            promptTokens = reservedTokens;
            completionTokens = 0;
        }
    }
}
