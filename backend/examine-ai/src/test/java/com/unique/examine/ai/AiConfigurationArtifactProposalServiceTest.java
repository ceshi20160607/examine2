package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiConfigurationArtifactProposal;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiConfigurationArtifactPlanParser;
import com.unique.examine.ai.service.AiConfigurationArtifactProposalService;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void selectionPrepareIsZeroWriteAndConfirmationReplayIsSingularAndRedacted() {
        var repository = repository();
        var owner = new Owner(null);
        var service = service(repository, owner);
        var prepared = service.prepare(actor(), repository.turnValues.get(71L),
                policy(), parser().parse(selectionJson(), policy()));
        repository.insertConfigurationArtifactProposal(
                prepared.storedProposal(), prepared.event());

        assertThat(prepared.liveProposal().preview().selectionField().fieldName())
                .isEqualTo("Priority");
        assertThat(prepared.liveProposal().expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(owner.prepareCalls).isOne();
        assertThat(owner.executeCalls).isZero();
        var persisted = repository.configurationArtifactProposals.values().toString()
                + repository.configurationArtifactEvents;
        assertThat(persisted).doesNotContain("Priority")
                .doesNotContain("Order priority")
                .doesNotContain("Medium")
                .doesNotContain("raw-owner-command")
                .contains("[name:redacted]")
                .contains("[label:redacted]");

        var succeeded = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "artifact-1");
        var replay = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "artifact-1");
        assertThat(succeeded.state()).isEqualTo(
                AiConfigurationArtifactProposal.State.SUCCEEDED);
        assertThat(succeeded.result().selectionField().dictionary().dictionaryId())
                .isEqualTo("601");
        assertThat(succeeded.result().selectionField().options())
                .extracting(AiConfigurationArtifactProposal.OptionResult::label)
                .containsOnly("[label:redacted]");
        assertThat(replay).isEqualTo(succeeded);
        assertThat(owner.executeCalls).isOne();
        assertThat(repository.configurationArtifactEvents)
                .extracting(AiConfigurationArtifactProposal.Event::eventType)
                .containsExactly("PROPOSED", "CONFIRMING", "SUCCEEDED");
        assertThat(owner.lastExecute.sessionId()).isEqualTo("70");
        assertThat(owner.lastExecute.turnId()).isEqualTo("71");
        assertThatThrownBy(() -> service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 1, "artifact-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_ARTIFACT_REPLAY_CONFLICT");
    }

    @Test
    void pageSectionsAreNeverPersistedAndStaleExecutionIsTerminal() {
        var repository = repository();
        var owner = new Owner("MODULE_CONFIG_STALE");
        var service = service(repository, owner);
        var prepared = service.prepare(actor(), repository.turnValues.get(71L),
                policy(), parser().parse(pageJson(), policy()));
        repository.insertConfigurationArtifactProposal(
                prepared.storedProposal(), prepared.event());

        var storedLayout = prepared.storedProposal().preview().pageLayout().layout();
        assertThat(storedLayout.redacted()).isTrue();
        assertThat(storedLayout.sections()).isEmpty();
        assertThat(storedLayout.sectionCount()).isEqualTo(2);
        assertThat(storedLayout.fieldCount()).isEqualTo(3);
        assertThat(repository.configurationArtifactProposals.values().toString())
                .doesNotContain("Main")
                .doesNotContain("Details")
                .doesNotContain("customer")
                .doesNotContain("amount");

        var failed = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "page-stale");
        var replay = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "page-stale");
        assertThat(failed.state()).isEqualTo(
                AiConfigurationArtifactProposal.State.FAILED);
        assertThat(failed.resultCode()).isEqualTo("MODULE_CONFIG_STALE");
        assertThat(failed.result()).isNull();
        assertThat(replay).isEqualTo(failed);
        assertThat(owner.executeCalls).isOne();
    }

    @Test
    void successfulPageReadbackIsSummaryOnlyAndPolicyChangeBlocksExecution() {
        var repository = repository();
        var owner = new Owner(null);
        var service = service(repository, owner);
        var prepared = service.prepare(actor(), repository.turnValues.get(71L),
                policy(), parser().parse(pageJson(), policy()));
        repository.insertConfigurationArtifactProposal(
                prepared.storedProposal(), prepared.event());
        var succeeded = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "page-ok");
        assertThat(succeeded.result().pageLayout().layout().redacted()).isTrue();
        assertThat(succeeded.result().pageLayout().layout().sections()).isEmpty();
        assertThat(succeeded.result().pageLayout().version()).isEqualTo(5);

        var changedRepository = repository();
        var changed = service(changedRepository, owner);
        var pending = changed.prepare(actor(), changedRepository.turnValues.get(71L),
                policy(), parser().parse(selectionJson(), policy()));
        changedRepository.insertConfigurationArtifactProposal(
                pending.storedProposal(), pending.event());
        var readOnly = readOnlyPolicy();
        changedRepository.versions.put(readOnly.id(), readOnly);
        changedRepository.drafts.put("1:2", draft(readOnly.id(),
                Set.of("RECORD_QUERY"), 2));
        assertThatThrownBy(() -> changed.confirm(actor(), "70",
                Long.toString(pending.liveProposal().id()), 0, "changed"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_ARTIFACT_POLICY_CHANGED");
    }

    @Test
    void clarificationAndRejectNeverExecuteOwner() {
        var clarificationRepository = repository();
        var owner = new Owner(null);
        var service = service(clarificationRepository, owner);
        var plan = parser().parse("""
                {"operation":"CONFIG_SELECTION_FIELD_DRAFT","moduleCode":null,
                 "fieldCode":null,"fieldName":null,"fieldType":null,
                 "required":null,"dictionaryCode":null,"dictionaryName":null,
                 "options":null,"maxSelections":null,"confidence":0.4,
                 "clarification":"Which options should be available?"}
                """, policy());
        var clarification = service.prepare(actor(),
                clarificationRepository.turnValues.get(71L), policy(), plan);
        clarificationRepository.insertConfigurationArtifactProposal(
                clarification.storedProposal(), clarification.event());
        assertThat(clarification.liveProposal().state()).isEqualTo(
                AiConfigurationArtifactProposal.State.CLARIFICATION_REQUIRED);
        assertThat(clarification.storedProposal().clarification())
                .isEqualTo("[clarification:redacted]");
        assertThat(owner.prepareCalls).isZero();

        var rejectRepository = repository();
        var rejectService = service(rejectRepository, owner);
        var pending = rejectService.prepare(actor(),
                rejectRepository.turnValues.get(71L), policy(),
                parser().parse(selectionJson(), policy()));
        rejectRepository.insertConfigurationArtifactProposal(
                pending.storedProposal(), pending.event());
        var rejected = rejectService.reject(actor(), "70",
                Long.toString(pending.liveProposal().id()), 0);
        assertThat(rejected.state()).isEqualTo(
                AiConfigurationArtifactProposal.State.REJECTED);
        assertThat(owner.executeCalls).isZero();
    }

    @Test
    void filterScenarioUsesOwnerResolvedPreviewAndRedactsStoredFreeText() {
        var repository = repository();
        var owner = new Owner(null);
        var service = service(repository, owner);
        var prepared = service.prepare(actor(), repository.turnValues.get(71L),
                policy(), parser().parse(filterScenarioJson(), policy()));
        repository.insertConfigurationArtifactProposal(
                prepared.storedProposal(), prepared.event());

        assertThat(prepared.liveProposal().artifactKind()).isEqualTo(
                AiConfigurationArtifactProposal.ArtifactKind.FILTER_SCENARIO);
        assertThat(prepared.liveProposal().preview().filterScenario()
                .resolvedState().defaultFilterScenarioCode())
                .isEqualTo("open_items");
        assertThat(prepared.liveProposal().preview().filterScenario().scenario()
                .filter().path("value").asText()).isEqualTo("OPEN CUSTOMER");
        var persisted = repository.configurationArtifactProposals.values().toString();
        assertThat(persisted)
                .doesNotContain("Open customer items")
                .doesNotContain("OPEN CUSTOMER")
                .doesNotContain("SECRET EXISTING")
                .contains("[name:redacted]")
                .contains("[value:redacted]");

        var succeeded = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "filter-ok");
        assertThat(succeeded.result().filterScenario().version()).isEqualTo(5);
        assertThat(succeeded.result().filterScenario().state().filterScenarios())
                .extracting(AiConfigurationArtifactProposal.FilterScenario::name)
                .containsOnly("[name:redacted]");
        assertThat(succeeded.result().filterScenario().state().filterScenarios()
                .getLast().filter().path("value").asText())
                .isEqualTo("[value:redacted]");
        assertThat(owner.executeCalls).isOne();
    }

    @Test
    void fieldPermissionStageReturnsOnlyOwnerDerivedModesAndCodes() {
        var repository = repository();
        var owner = new Owner(null);
        var service = service(repository, owner);
        var prepared = service.prepare(actor(), repository.turnValues.get(71L),
                policy(), parser().parse(fieldPermissionStageJson(), policy()));
        repository.insertConfigurationArtifactProposal(
                prepared.storedProposal(), prepared.event());

        var preview = prepared.liveProposal().preview().fieldPermissionStage();
        assertThat(prepared.liveProposal().artifactKind()).isEqualTo(
                AiConfigurationArtifactProposal.ArtifactKind.FIELD_PERMISSION_STAGE);
        assertThat(preview.expectedReadPermissionMode()).isEqualTo("INHERIT");
        assertThat(preview.readPermissionMode()).isEqualTo("STAGED");
        assertThat(preview.readPermissionCode())
                .isEqualTo("module.orders.field.status.read");
        assertThat(prepared.storedProposal().preview().fieldPermissionStage()
                .fieldName()).isEqualTo("[name:redacted]");

        var succeeded = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "permission-ok");
        var result = succeeded.result().fieldPermissionStage();
        assertThat(result.readPermissionMode()).isEqualTo("STAGED");
        assertThat(result.writePermissionMode()).isEqualTo("INHERIT");
        assertThat(result.readPermissionCode())
                .isEqualTo("module.orders.field.status.read");
        assertThat(result.fieldName()).isEqualTo("[name:redacted]");
        assertThat(owner.executeCalls).isOne();
    }

    private static AiConfigurationArtifactProposalService service(
            MemoryAiRepository repository, Owner owner) {
        return new AiConfigurationArtifactProposalService(
                repository, owner, new SequenceIdService(5_000),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiConfigurationArtifactPlanParser parser() {
        return new AiConfigurationArtifactPlanParser();
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        var policy = policy();
        repository.versions.put(policy.id(), policy);
        repository.drafts.put("1:2", draft(policy.id(), Set.of(
                "RECORD_QUERY", "CONFIG_SELECTION_FIELD_DRAFT",
                "CONFIG_PAGE_LAYOUT_DRAFT", "CONFIG_FILTER_SCENARIO_DRAFT",
                "CONFIG_FIELD_PERMISSION_STAGE_DRAFT"), 1));
        repository.insertSession(new AiConversation.Session(
                70, 1, 2, 7, policy.id(), 80, 3, "gpt-write", "v3", 41,
                AiConversation.SessionStatus.ACTIVE, "[title:redacted]", NOW, NOW));
        repository.insertTurn(new AiConversation.Turn(
                71, 1, 2, 70, policy.id(), 80, 3, 41,
                AiConversation.TurnStatus.RUNNING, "[request:redacted]",
                "a".repeat(64), null, null, null, 0, "AI_TURN_RUNNING", false,
                0, "request-1", "trace-1", NOW, null));
        return repository;
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")), Set.of(
                "RECORD_QUERY", "CONFIG_SELECTION_FIELD_DRAFT",
                "CONFIG_PAGE_LAYOUT_DRAFT", "CONFIG_FILTER_SCENARIO_DRAFT",
                "CONFIG_FIELD_PERMISSION_STAGE_DRAFT"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v3", "b".repeat(64), NOW, 7);
    }

    private static AiPolicy.Version readOnlyPolicy() {
        return new AiPolicy.Version(
                92, 1, 2, 90, 2, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")), Set.of("RECORD_QUERY"),
                10, true, AiPolicy.RedactionMode.STRICT, "v3",
                "c".repeat(64), NOW, 7);
    }

    private static AiPolicy.Draft draft(
            long activeVersion, Set<String> operations, long revision) {
        return new AiPolicy.Draft(
                90, 1, 2, revision, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")), operations,
                Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v3", "d".repeat(64),
                activeVersion, NOW, 7);
    }

    private static AiActor actor() {
        return new AiActor(5, 1, 2, 7, Set.of(
                "system.runtime.access", "system.admin.access", "ai.agent.use",
                "module.config.manage"),
                41, "request-1", "trace-1");
    }

    private static String selectionJson() {
        return """
                {"operation":"CONFIG_SELECTION_FIELD_DRAFT","moduleCode":"orders",
                 "fieldCode":"priority","fieldName":"Priority","fieldType":"RADIO",
                 "required":true,"dictionaryCode":"order_priority",
                 "dictionaryName":"Order priority","options":[
                  {"code":"low","label":"Low","semanticKey":"LOW","color":null,"default":false},
                  {"code":"medium","label":"Medium","semanticKey":"MEDIUM","color":null,"default":true}],
                 "maxSelections":null,"confidence":0.98,"clarification":null}
                """;
    }

    private static String pageJson() {
        return """
                {"operation":"CONFIG_PAGE_LAYOUT_DRAFT","moduleCode":"orders",
                 "pageCode":"order_form","pageType":"FORM","layout":{
                  "columns":1,"gap":16,"labelPosition":"TOP","density":"DEFAULT",
                  "stickyActions":true,"pageSize":null,"searchEnabled":null,
                  "filterEnabled":null,"sections":[
                   {"code":"main","title":"Main","fieldCodes":["customer"]},
                   {"code":"details","title":"Details","fieldCodes":["amount","status"]}]},
                 "confidence":0.96,"clarification":null}
                """;
    }

    private static String filterScenarioJson() {
        return """
                {"operation":"CONFIG_FILTER_SCENARIO_DRAFT","moduleCode":"orders",
                 "pageCode":"order_list","scenario":{"code":"open_items",
                 "name":"Open customer items","filter":{"kind":"PREDICATE",
                 "fieldCode":"status","operator":"EQ","value":"OPEN CUSTOMER"},
                 "sort":[{"fieldCode":"created_at","direction":"DESC","nulls":"LAST"}]},
                 "makeDefault":true,"confidence":0.97,"clarification":null}
                """;
    }

    private static String fieldPermissionStageJson() {
        return """
                {"operation":"CONFIG_FIELD_PERMISSION_STAGE_DRAFT","moduleCode":"orders",
                 "fieldCode":"status","stageRead":true,"stageWrite":false,
                 "confidence":0.99,"clarification":null}
                """;
    }

    private static final class Owner implements AiConfigurationArtifactFacade {
        private final String failureCode;
        private int prepareCalls;
        private int executeCalls;
        private PrepareRequest lastPrepare;
        private ExecuteRequest lastExecute;

        private Owner(String failureCode) { this.failureCode = failureCode; }

        @Override
        public PreparedArtifact prepare(PrepareRequest request) {
            prepareCalls++;
            lastPrepare = request;
            var preview = switch (request.operation()) {
                case CONFIG_SELECTION_FIELD_DRAFT -> new ArtifactPreview(
                        request.operation(), "301", "401",
                        request.moduleCode(), 8, 9,
                        new SelectionFieldPreview(30, request.selectionField()),
                        null, null, null);
                case CONFIG_PAGE_LAYOUT_DRAFT -> new ArtifactPreview(
                        request.operation(), "301", "401", request.moduleCode(),
                        8, 9, null,
                        new PageLayoutPreview("501", request.pageLayout().pageCode(),
                                request.pageLayout().pageType(), 4,
                                request.pageLayout().layout()), null, null);
                case CONFIG_FILTER_SCENARIO_DRAFT -> filterScenarioPreview(request);
                case CONFIG_FIELD_PERMISSION_STAGE_DRAFT -> new ArtifactPreview(
                        request.operation(), "301", "401", request.moduleCode(),
                        8, 9, null, null, null,
                        new FieldPermissionStagePreview(
                                "801", request.fieldPermissionStage().fieldCode(),
                                "Status", 4,
                                request.fieldPermissionStage().stageRead(),
                                request.fieldPermissionStage().stageWrite(),
                                FieldPermissionMode.INHERIT,
                                FieldPermissionMode.INHERIT,
                                FieldPermissionMode.STAGED,
                                FieldPermissionMode.INHERIT,
                                "module.orders.field.status.read",
                                "module.orders.field.status.write"));
            };
            return new PreparedArtifact(preview, NOW.plusSeconds(900),
                    new SealedCommand("sealed_artifact_command", "key-v1",
                            "9".repeat(64)));
        }

        @Override
        public ArtifactReadback execute(ExecuteRequest request) {
            executeCalls++;
            lastExecute = request;
            if (failureCode != null) throw new BusinessException(
                    failureCode, "Owner rejected artifact",
                    org.springframework.http.HttpStatus.CONFLICT);
            var proposal = request.proposalId();
            if (proposal != null && executeCalls > 0
                    && request.sealedCommand().ciphertext().contains("artifact")) {
                // Test owner chooses result by the proposal currently prepared.
            }
            if (lastPrepare.operation() == Operation.CONFIG_PAGE_LAYOUT_DRAFT) {
                var layout = new PageLayout(1, 16, LabelPosition.TOP,
                        Density.DEFAULT, true, null, null, null, List.of(
                        new PageSection("main", "Main", List.of("customer")),
                        new PageSection("details", "Details",
                                List.of("amount", "status"))));
                return new ArtifactReadback(Operation.CONFIG_PAGE_LAYOUT_DRAFT,
                        "301", "401", "orders", 9, null,
                        new PageLayoutReadback("501", "order_form",
                                PageType.FORM, 5, layout));
            }
            if (lastPrepare.operation() == Operation.CONFIG_FILTER_SCENARIO_DRAFT) {
                var preview = lastPrepare.filterScenario();
                return new ArtifactReadback(Operation.CONFIG_FILTER_SCENARIO_DRAFT,
                        "301", "401", "orders", 9, null, null,
                        new FilterScenarioReadback("501", preview.pageCode(), 5,
                                filterScenarioState(preview)), null);
            }
            if (lastPrepare.operation()
                    == Operation.CONFIG_FIELD_PERMISSION_STAGE_DRAFT) {
                return new ArtifactReadback(
                        Operation.CONFIG_FIELD_PERMISSION_STAGE_DRAFT,
                        "301", "401", "orders", 9, null, null, null,
                        new FieldPermissionStageReadback(
                                "801", "status", "Status", 5,
                                FieldPermissionMode.STAGED,
                                FieldPermissionMode.INHERIT,
                                "module.orders.field.status.read",
                                "module.orders.field.status.write"));
            }
            var dictionary = new DictionaryView(
                    "601", "order_priority", "Order priority", 0);
            var options = List.of(
                    new OptionView("701", "low", "Low", "LOW", null,
                            false, 0, 0),
                    new OptionView("702", "medium", "Medium", "MEDIUM", null,
                            true, 10, 0));
            var field = new SelectionFieldView(
                    "801", "priority", "Priority", SelectionType.RADIO,
                    true, "601", 30, null, 0);
            return new ArtifactReadback(Operation.CONFIG_SELECTION_FIELD_DRAFT,
                    "301", "401", "orders", 9,
                    new SelectionFieldReadback(dictionary, options, field), null);
        }

        private static ArtifactPreview filterScenarioPreview(PrepareRequest request) {
            var draft = request.filterScenario();
            var state = filterScenarioState(draft);
            var layout = new ObjectMapper().valueToTree(state);
            return new ArtifactPreview(request.operation(), "301", "401",
                    request.moduleCode(), 8, 9, null, null,
                    new FilterScenarioPreview("501", draft.pageCode(), 4,
                            draft.makeDefault(), draft.scenario(), state, layout),
                    null);
        }

        private static FilterScenarioState filterScenarioState(
                FilterScenarioDraft draft) {
            var existingFilter = new ObjectMapper().createObjectNode()
                    .put("kind", "PREDICATE")
                    .put("fieldCode", "owner")
                    .put("operator", "EQ")
                    .put("value", "SECRET EXISTING");
            var existing = new FilterScenario("mine", "My private scenario",
                    existingFilter, new ObjectMapper().createArrayNode());
            return new FilterScenarioState(List.of(existing, draft.scenario()),
                    draft.makeDefault() ? draft.scenario().code() : null);
        }
    }
}
