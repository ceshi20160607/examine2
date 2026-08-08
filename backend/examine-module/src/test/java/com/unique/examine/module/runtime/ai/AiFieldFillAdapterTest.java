package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import com.unique.examine.module.runtime.service.RecordMutationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFieldFillAdapterTest {
    private static final String SOURCE_HASH = "a".repeat(64);
    private static final Set<String> PERMISSIONS = Set.of(
            "system.runtime.access", "ai.agent.use",
            "module.work_order.view", "module.work_order.update");
    private static final AiFieldFillFacade.Provenance PROVENANCE =
            new AiFieldFillFacade.Provenance("7", 2, "governed-model", "prompt-v1", "9");

    @Test
    void snapshotAndPrepareAreReadOnlyAndSealAnExactTypedPreview() {
        var owner = new FakeOwner(resolved(AiFieldFillFacade.OverwriteMode.CONFIRM, null));
        var adapter = adapter(owner);

        var snapshot = adapter.sourceSnapshot(source());
        var prepared = adapter.prepare(prepare("{\"value\":\"Generated\",\"confidence\":0.90}"));

        assertThat(snapshot.sources()).extracting(AiFieldFillFacade.SourceValue::fieldCode)
                .containsExactly("description");
        assertThat(prepared.preview().afterDisplayValue()).isEqualTo("Generated");
        assertThat(prepared.preview().confidence()).isEqualTo(0.90d);
        assertThat(prepared.preview().overwrite()).isFalse();
        assertThat(prepared.sealedCommand().ciphertext()).doesNotContain("Generated");
        assertThat(owner.materializeCalls).isZero();
        assertThat(owner.rejectCalls).isZero();
    }

    @Test
    void rejectsMalformedUnknownDuplicateWrongTypeAndLowConfidenceBeforeWrite() {
        var owner = new FakeOwner(resolved(AiFieldFillFacade.OverwriteMode.CONFIRM, null));
        var adapter = adapter(owner);

        assertCode("AI_FILL_RESULT_INVALID", () -> adapter.prepare(prepare(
                "{\"value\":12,\"confidence\":0.90}")));
        assertCode("AI_FILL_RESULT_INVALID", () -> adapter.prepare(prepare(
                "{\"value\":\"x\",\"confidence\":0.90,\"unknown\":true}")));
        assertCode("AI_FILL_RESULT_INVALID", () -> adapter.prepare(prepare(
                "{\"value\":\"x\",\"value\":\"y\",\"confidence\":0.90}")));
        assertCode("AI_FILL_CONFIDENCE_TOO_LOW", () -> adapter.prepare(prepare(
                "{\"value\":\"x\",\"confidence\":0.79}")));
        assertThat(owner.materializeCalls).isZero();
    }

    @Test
    void neverOverwriteIsEnforcedAtPrepareAndConfirmation() {
        var current = new AiFieldFillFacade.CurrentValue("Old", 3, 0.91, PROVENANCE);
        var owner = new FakeOwner(resolved(AiFieldFillFacade.OverwriteMode.NEVER, current));
        var adapter = adapter(owner);

        assertCode("AI_FILL_OVERWRITE_DENIED", () -> adapter.prepare(prepare(
                "{\"value\":\"New\",\"confidence\":0.90}")));

        owner.resolved = resolved(AiFieldFillFacade.OverwriteMode.CONFIRM, null);
        var prepared = adapter.prepare(prepare("{\"value\":\"New\",\"confidence\":0.90}"));
        owner.resolved = resolved(AiFieldFillFacade.OverwriteMode.NEVER, current);
        assertCode("AI_FILL_CONTRACT_STALE", () -> adapter.execute(execute(prepared.sealedCommand(), "idem-1")));
        assertThat(owner.materializeCalls).isZero();
    }

    @Test
    void confirmationRechecksLiveSourceAndAadThenMaterializesOnlyOncePerIdempotencyKey() {
        var owner = new FakeOwner(resolved(AiFieldFillFacade.OverwriteMode.CONFIRM, null));
        var adapter = adapter(owner);
        var prepared = adapter.prepare(prepare("{\"value\":\"Generated\",\"confidence\":0.90}"));

        owner.resolved = resolvedWithHash("b".repeat(64));
        assertCode("AI_FILL_SOURCE_STALE", () -> adapter.execute(execute(prepared.sealedCommand(), "stale")));
        owner.resolved = resolved(AiFieldFillFacade.OverwriteMode.CONFIRM, null);

        var wrongActor = new AiFieldFillFacade.ExecuteRequest(
                "proposal-1", 11, 13, 18, 5, PERMISSIONS, "work_order", "42", "summary",
                prepared.sealedCommand(), "aad", "request-1", "trace-1");
        assertCode("AI_FILL_COMMAND_INVALID", () -> adapter.execute(wrongActor));

        var first = adapter.execute(execute(prepared.sealedCommand(), "same-key"));
        var replay = adapter.execute(execute(prepared.sealedCommand(), "same-key"));
        assertThat(first).isEqualTo(replay);
        assertThat(first.displayValue()).isEqualTo("Generated");
        assertThat(first.provenance()).isEqualTo(PROVENANCE);
        assertThat(owner.materializeCalls).isOne();
    }

    @Test
    void rejectionIsAuthenticatedPermissionRecheckedAndImmutable() {
        var owner = new FakeOwner(resolved(AiFieldFillFacade.OverwriteMode.CONFIRM, null));
        var adapter = adapter(owner);
        var prepared = adapter.prepare(prepare("{\"value\":\"Generated\",\"confidence\":0.90}"));

        var rejected = adapter.reject(reject(prepared.sealedCommand(), "reject-key"));
        var replay = adapter.reject(reject(prepared.sealedCommand(), "reject-key"));

        assertThat(rejected).isEqualTo(replay);
        assertThat(rejected.outcome()).isEqualTo("REJECTED");
        assertThat(owner.rejectCalls).isOne();
        assertThat(owner.materializeCalls).isZero();
    }

    @Test
    void publicBoundaryRemainsSpringProxyableWithExplicitTransactionModes() throws Exception {
        assertThat(java.lang.reflect.Modifier.isFinal(AiFieldFillAdapter.class.getModifiers())).isFalse();
        assertThat(AiFieldFillAdapter.class.getMethod(
                        "sourceSnapshot", AiFieldFillFacade.SourceRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(AiFieldFillAdapter.class.getMethod(
                        "prepare", AiFieldFillFacade.PrepareRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(AiFieldFillAdapter.class.getMethod(
                        "execute", AiFieldFillFacade.ExecuteRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
        assertThat(AiFieldFillAdapter.class.getMethod(
                        "reject", AiFieldFillFacade.RejectRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    private static AiFieldFillAdapter adapter(FakeOwner owner) {
        var keys = (SensitiveKeyProvider) () -> Optional.of(new SensitiveKeyProvider.KeyRing(
                "enc-v1", "hash-v1",
                Map.of("enc-v1", "0123456789abcdef".getBytes(StandardCharsets.UTF_8)),
                Map.of("hash-v1", "abcdef0123456789".getBytes(StandardCharsets.UTF_8)),
                List.of("hash-v1")));
        var random = new SecureRandom() {
            @Override public void nextBytes(byte[] bytes) { java.util.Arrays.fill(bytes, (byte) 7); }
        };
        return new AiFieldFillAdapter(
                context -> owner.resolved, owner::materialize, owner::reject,
                new AiFieldFillCommandSealer(keys, random), new AiFieldFillCommandCodec(),
                new FakeIdempotency());
    }

    private static AiFieldFillFacade.SourceRequest source() {
        return new AiFieldFillFacade.SourceRequest(
                11, 13, 17, 5, PERMISSIONS, "work_order", "42", "summary", "request-1", "trace-1");
    }

    private static AiFieldFillFacade.PrepareRequest prepare(String result) {
        return new AiFieldFillFacade.PrepareRequest(
                "proposal-1", 11, 13, 17, 5, PERMISSIONS, "work_order", "42", "summary",
                "101", 3, SOURCE_HASH, result, PROVENANCE, "request-1", "trace-1");
    }

    private static AiFieldFillFacade.ExecuteRequest execute(
            AiFieldFillFacade.SealedCommand command, String key) {
        return new AiFieldFillFacade.ExecuteRequest(
                "proposal-1", 11, 13, 17, 5, PERMISSIONS, "work_order", "42", "summary",
                command, key, "request-1", "trace-1");
    }

    private static AiFieldFillFacade.RejectRequest reject(
            AiFieldFillFacade.SealedCommand command, String key) {
        return new AiFieldFillFacade.RejectRequest(
                "proposal-1", 11, 13, 17, 5, PERMISSIONS, "work_order", "42", "summary",
                command, key, "request-1", "trace-1");
    }

    private static AiFieldFillRuntimeReader.Resolved resolved(
            AiFieldFillFacade.OverwriteMode mode, AiFieldFillFacade.CurrentValue current) {
        var hash = current == null ? null : "c".repeat(64);
        var version = current == null ? null : current.materializationVersion();
        return new AiFieldFillRuntimeReader.Resolved(snapshot(mode, current, SOURCE_HASH),
                1001, 2001, 201, hash, version);
    }

    private static AiFieldFillRuntimeReader.Resolved resolvedWithHash(String hash) {
        return new AiFieldFillRuntimeReader.Resolved(
                snapshot(AiFieldFillFacade.OverwriteMode.CONFIRM, null, hash),
                1001, 2001, 201, null, null);
    }

    private static AiFieldFillFacade.SourceSnapshot snapshot(
            AiFieldFillFacade.OverwriteMode mode,
            AiFieldFillFacade.CurrentValue current,
            String sourceHash
    ) {
        return new AiFieldFillFacade.SourceSnapshot(
                "work_order", "42", 3, "101",
                new AiFieldFillFacade.FieldContract(
                        "201", "summary", "Summary", AiFieldFillFacade.ResultSchema.STRING,
                        List.of("101"), "Summarize", "SYSTEM_DEFAULT", 0.80, mode),
                sourceHash,
                List.of(new AiFieldFillFacade.SourceValue(
                        "101", "description", "Description", "TEXT", "Visible source")),
                current);
    }

    private static void assertCode(String code, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code()).isEqualTo(code);
    }

    private static final class FakeOwner {
        private AiFieldFillRuntimeReader.Resolved resolved;
        private int materializeCalls;
        private int rejectCalls;

        private FakeOwner(AiFieldFillRuntimeReader.Resolved resolved) { this.resolved = resolved; }

        private AiFieldFillFacade.FillReadback materialize(AiFieldFillMaterializationStore.StoreRequest request) {
            materializeCalls++;
            var command = request.command();
            return new AiFieldFillFacade.FillReadback(
                    "900", request.moduleCode(), request.recordId(), command.recordVersion(),
                    command.schemaVersionId(), Long.toString(command.logicalFieldId()), command.fieldCode(),
                    command.resultSchema(), command.displayValue(), command.confidence(), 0,
                    "CREATED", command.provenance());
        }

        private AiFieldFillFacade.RejectionReadback reject(AiFieldFillMaterializationStore.StoreRequest request) {
            rejectCalls++;
            return new AiFieldFillFacade.RejectionReadback(
                    "901", request.command().proposalId(), request.recordId(),
                    request.command().fieldCode(), "REJECTED");
        }
    }

    private static final class FakeIdempotency implements AiFieldFillAdapter.IdempotentMutation {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public <T> T run(
                String scope, String key, Object request, Class<T> responseType,
                int successStatus, RecordMutationSupport.Mutation<T> mutation
        ) {
            return responseType.cast(values.computeIfAbsent(scope + ":" + key, ignored -> mutation.run()));
        }
    }
}
