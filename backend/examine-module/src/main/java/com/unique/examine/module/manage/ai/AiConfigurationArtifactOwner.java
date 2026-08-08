package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Authoritative prepare/confirm owner for bounded configuration artifacts. */
@Component
public class AiConfigurationArtifactOwner
        implements AiConfigurationArtifactFacade {
    static final Duration COMMAND_TTL = Duration.ofMinutes(15);

    private final ContextResolver contexts;
    private final MutationStore store;
    private final AiConfigurationArtifactCommandSealer sealer;
    private final AiConfigurationArtifactCommandCodec codec;
    private final AiConfigurationArtifactCanonicalizer canonicalizer;
    private final Clock clock;
    private final Duration commandTtl;

    @Autowired
    public AiConfigurationArtifactOwner(
            AiConfigurationArtifactContextReader contexts,
            AiConfigurationArtifactMutationStore store,
            AiConfigurationArtifactCommandSealer sealer,
            AiConfigurationArtifactCanonicalizer canonicalizer,
            ObjectMapper json) {
        this(new ContextResolver() {
                    @Override
                    public AiConfigurationArtifactContextReader.CommonSnapshot
                    common(AiConfigurationArtifactContextReader.Access access) {
                        return contexts.common(access);
                    }

                    @Override
                    public AiConfigurationArtifactContextReader.SelectionSnapshot
                    selection(
                            AiConfigurationArtifactContextReader.Access access,
                            String fieldCode, String dictionaryCode) {
                        return contexts.selection(
                                access, fieldCode, dictionaryCode);
                    }

                    @Override
                    public AiConfigurationArtifactContextReader.PageSnapshot page(
                            AiConfigurationArtifactContextReader.Access access,
                            String pageCode, Set<String> fieldCodes) {
                        return contexts.page(access, pageCode, fieldCodes);
                    }

                    @Override
                    public AiConfigurationArtifactContextReader.FilterScenarioSnapshot
                    filterScenario(
                            AiConfigurationArtifactContextReader.Access access,
                            String pageCode) {
                        return contexts.filterScenario(access, pageCode);
                    }

                    @Override
                    public AiConfigurationArtifactContextReader.FieldPermissionSnapshot
                    fieldPermission(
                            AiConfigurationArtifactContextReader.Access access,
                            String fieldCode) {
                        return contexts.fieldPermission(access, fieldCode);
                    }
                }, store::execute, sealer,
                new AiConfigurationArtifactCommandCodec(json),
                canonicalizer, Clock.systemUTC(), COMMAND_TTL);
    }

    AiConfigurationArtifactOwner(
            ContextResolver contexts,
            MutationStore store,
            AiConfigurationArtifactCommandSealer sealer,
            AiConfigurationArtifactCommandCodec codec,
            AiConfigurationArtifactCanonicalizer canonicalizer,
            Clock clock,
            Duration commandTtl) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.store = Objects.requireNonNull(store, "store");
        this.sealer = Objects.requireNonNull(sealer, "sealer");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.canonicalizer = canonicalizer;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commandTtl = Objects.requireNonNull(commandTtl, "commandTtl");
        if (commandTtl.isZero() || commandTtl.isNegative()
                || commandTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("commandTtl is invalid");
        }
    }

    AiConfigurationArtifactOwner(
            ContextResolver contexts,
            MutationStore store,
            AiConfigurationArtifactCommandSealer sealer,
            AiConfigurationArtifactCommandCodec codec,
            Clock clock,
            Duration commandTtl) {
        this(contexts, store, sealer, codec, null, clock, commandTtl);
    }

    @Override
    @Transactional(readOnly = true)
    public PreparedArtifact prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        SelectionFieldPreview selection = null;
        PageLayoutPreview page = null;
        FilterScenarioPreview filterScenario = null;
        FieldPermissionStagePreview fieldPermissionStage = null;
        AiConfigurationArtifactContextReader.CommonSnapshot common = null;
        var access = access(request);
        switch (request.operation()) {
            case CONFIG_SELECTION_FIELD_DRAFT -> {
                var draft = request.selectionField();
                var snapshot = contexts.selection(
                        access, draft.fieldCode(), draft.dictionaryCode());
                common = snapshot.common();
                if (snapshot.fieldCodeExists()) {
                    throw conflict(
                            "AI_CONFIG_FIELD_CODE_CONFLICT",
                            "The configuration field code already exists");
                }
                if (snapshot.dictionaryCodeExists()) {
                    throw conflict(
                            "AI_CONFIG_DICTIONARY_CODE_CONFLICT",
                            "The configuration dictionary code already exists");
                }
                selection = new SelectionFieldPreview(
                        snapshot.sortOrder(), draft);
            }
            case CONFIG_PAGE_LAYOUT_DRAFT -> {
                var draft = request.pageLayout();
                var snapshot = contexts.page(
                        access, draft.pageCode(), fields(draft.layout()));
                common = snapshot.common();
                if (snapshot.pageType() != draft.pageType()) {
                    throw conflict(
                            "AI_CONFIG_PAGE_TYPE_CONFLICT",
                            "The requested page type differs from the existing page");
                }
                page = new PageLayoutPreview(
                        Long.toString(snapshot.pageId()), snapshot.pageCode(),
                        snapshot.pageType(), snapshot.pageVersion(), draft.layout());
            }
            case CONFIG_FILTER_SCENARIO_DRAFT -> {
                var snapshot = contexts.filterScenario(
                        access, request.filterScenario().pageCode());
                common = snapshot.common();
                filterScenario = canonicalizer().filterScenario(
                        request.filterScenario(), snapshot);
            }
            case CONFIG_FIELD_PERMISSION_STAGE_DRAFT -> {
                var snapshot = contexts.fieldPermission(
                        access, request.fieldPermissionStage().fieldCode());
                common = snapshot.common();
                fieldPermissionStage = canonicalizer().fieldPermission(
                        common.moduleCode(), request.fieldPermissionStage(), snapshot);
            }
        }
        common = Objects.requireNonNull(common, "common");
        if (common.draftRevision() == Long.MAX_VALUE) throw stale();
        var expiresAt = clock.instant().plus(commandTtl);
        var command = codec.command(
                request, common.configRootId(), common.moduleId(),
                common.draftRevision(), selection, page, filterScenario,
                fieldPermissionStage, expiresAt);
        var plaintext = codec.encode(command);
        var sealed = sealer.seal(plaintext, binding(command));
        return new PreparedArtifact(
                new ArtifactPreview(
                        command.operation(), Long.toString(command.configRootId()),
                        Long.toString(command.moduleId()), command.moduleCode(),
                        command.expectedDraftRevision(),
                        command.expectedDraftRevision() + 1,
                        selection, page, filterScenario, fieldPermissionStage),
                expiresAt, sealed);
    }

    @Override
    @Transactional
    public ArtifactReadback execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        var plaintext = sealer.open(request.sealedCommand(), binding(request));
        if (!AiConfigurationArtifactCommandSealer.equal(
                AiConfigurationArtifactCommandSealer.sha256(plaintext),
                request.sealedCommand().commandSha256())) {
            throw AiConfigurationArtifactCommandCodec.invalid();
        }
        var command = codec.decode(plaintext);
        assertIdentity(request, command);
        if (!command.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(
                    "AI_CONFIG_ARTIFACT_COMMAND_EXPIRED",
                    "The configuration artifact command expired",
                    HttpStatus.CONFLICT);
        }
        var currentAccess = access(request);
        var live = contexts.common(currentAccess);
        if (live.configRootId() != command.configRootId()
                || live.moduleId() != command.moduleId()
                || !live.moduleCode().equals(command.moduleCode())) {
            throw stale();
        }
        return store.execute(
                command, currentAccess, request.idempotencyKey(),
                request.requestId(), request.traceId());
    }

    private static void assertIdentity(
            ExecuteRequest request,
            AiConfigurationArtifactCommandCodec.Command command) {
        if (!command.proposalId().equals(request.proposalId())
                || !command.sessionId().equals(request.sessionId())
                || !command.turnId().equals(request.turnId())
                || command.accountId() != request.accountId()
                || command.systemId() != request.systemId()
                || command.tenantId() != request.tenantId()
                || command.memberId() != request.memberId()
                || command.configRootId() != Long.parseLong(request.configRootId())
                || command.moduleId() != Long.parseLong(request.moduleId())
                || !command.moduleCode().equals(request.moduleCode())
                || command.expectedDraftRevision()
                != request.expectedDraftRevision()) {
            throw AiConfigurationArtifactCommandCodec.invalid();
        }
    }

    private static AiConfigurationArtifactContextReader.Access access(
            PrepareRequest request) {
        return new AiConfigurationArtifactContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.moduleCode());
    }

    private static AiConfigurationArtifactContextReader.Access access(
            ExecuteRequest request) {
        return new AiConfigurationArtifactContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.moduleCode());
    }

    private static Set<String> fields(PageLayout layout) {
        var result = new LinkedHashSet<String>();
        layout.sections().forEach(section -> result.addAll(section.fieldCodes()));
        return Set.copyOf(result);
    }

    private AiConfigurationArtifactCanonicalizer canonicalizer() {
        return Objects.requireNonNull(
                canonicalizer, "canonicalizer is required for this operation");
    }

    private static AiConfigurationArtifactCommandSealer.Binding binding(
            AiConfigurationArtifactCommandCodec.Command command) {
        return new AiConfigurationArtifactCommandSealer.Binding(
                command.accountId(), command.systemId(), command.tenantId(),
                command.memberId(), command.proposalId(), command.sessionId(),
                command.turnId(), command.configRootId(), command.moduleId(),
                command.moduleCode(), command.expectedDraftRevision());
    }

    private static AiConfigurationArtifactCommandSealer.Binding binding(
            ExecuteRequest request) {
        return new AiConfigurationArtifactCommandSealer.Binding(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.proposalId(), request.sessionId(),
                request.turnId(), Long.parseLong(request.configRootId()),
                Long.parseLong(request.moduleId()), request.moduleCode(),
                request.expectedDraftRevision());
    }

    private static BusinessException stale() {
        return conflict(
                "AI_CONFIG_DRAFT_STALE",
                "The configuration draft changed before confirmation");
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    interface ContextResolver {
        AiConfigurationArtifactContextReader.CommonSnapshot common(
                AiConfigurationArtifactContextReader.Access access);

        AiConfigurationArtifactContextReader.SelectionSnapshot selection(
                AiConfigurationArtifactContextReader.Access access,
                String fieldCode,
                String dictionaryCode);

        AiConfigurationArtifactContextReader.PageSnapshot page(
                AiConfigurationArtifactContextReader.Access access,
                String pageCode,
                Set<String> fieldCodes);

        default AiConfigurationArtifactContextReader.FilterScenarioSnapshot
        filterScenario(
                AiConfigurationArtifactContextReader.Access access,
                String pageCode) {
            throw new UnsupportedOperationException();
        }

        default AiConfigurationArtifactContextReader.FieldPermissionSnapshot
        fieldPermission(
                AiConfigurationArtifactContextReader.Access access,
                String fieldCode) {
            throw new UnsupportedOperationException();
        }
    }

    @FunctionalInterface
    interface MutationStore {
        ArtifactReadback execute(
                AiConfigurationArtifactCommandCodec.Command command,
                AiConfigurationArtifactContextReader.Access currentAccess,
                String idempotencyKey,
                String requestId,
                String traceId);
    }
}
