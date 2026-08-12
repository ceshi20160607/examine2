package com.unique.examine.module.runtime.ai;

import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.service.RecordMutationSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;

/** Governed source, preview and explicit materialization owner adapter. */
@Component
public class AiFieldFillAdapter implements AiFieldFillFacade {
    private final RuntimeReader runtime;
    private final RoutedFillStore store;
    private final AiFieldFillCommandSealer sealer;
    private final AiFieldFillCommandCodec codec;
    private final IdempotentMutation idempotency;

    @Autowired
    public AiFieldFillAdapter(
            AiFieldFillRuntimeReader runtime,
            AiFieldFillMaterializationStore store,
            AiFieldFillCommandSealer sealer,
            RecordMutationSupport idempotency
    ) {
        this(runtime::resolve, store::materialize, store::reject, sealer,
                new AiFieldFillCommandCodec(), idempotency::idempotent);
    }

    AiFieldFillAdapter(
            RuntimeReader runtime,
            FillStore materialize,
            FillStore reject,
            AiFieldFillCommandSealer sealer,
            AiFieldFillCommandCodec codec,
            IdempotentMutation idempotency
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.store = new RoutedStore(
                Objects.requireNonNull(materialize, "materialize"),
                Objects.requireNonNull(reject, "reject"));
        this.sealer = Objects.requireNonNull(sealer, "sealer");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
    }

    @Override
    @Transactional(readOnly = true)
    public SourceSnapshot sourceSnapshot(SourceRequest request) {
        Objects.requireNonNull(request, "request");
        return resolve(request.systemId(), request.tenantId(), request.memberId(),
                request.authorizationEpoch(), request.effectivePermissions(), request.moduleCode(),
                request.recordId(), request.fieldCode()).snapshot();
    }

    @Override
    @Transactional(readOnly = true)
    public PreparedFill prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        var resolved = resolve(request.systemId(), request.tenantId(), request.memberId(),
                request.authorizationEpoch(), request.effectivePermissions(), request.moduleCode(),
                request.recordId(), request.fieldCode());
        assertExpected(
                request.expectedSchemaVersionId(), request.expectedRecordVersion(),
                request.expectedSourceVersionHash(), resolved.snapshot());
        var contract = resolved.snapshot().contract();
        var result = codec.result(
                request.canonicalResultJson(), contract.resultSchema(), contract.minimumConfidence());
        var current = resolved.snapshot().currentValue();
        if (current != null && contract.overwriteMode() == OverwriteMode.NEVER) {
            throw conflict(
                    "AI_FILL_OVERWRITE_DENIED",
                    "The published AI_FILL contract forbids overwrite");
        }
        var command = new AiFieldFillCommandCodec.FillCommand(
                request.proposalId(), resolved.snapshot().schemaVersionId(),
                resolved.snapshot().recordVersion(), resolved.snapshot().sourceVersionHash(),
                resolved.moduleSnapshotId(), resolved.fieldSnapshotId(), resolved.logicalFieldId(),
                contract.fieldCode(), contract.resultSchema(), result.canonicalValue(),
                result.displayValue(), result.confidence(), contract.overwriteMode(),
                resolved.currentMaterializationVersion(), resolved.currentValueHash(), request.provenance());
        var canonical = codec.command(command);
        var sealed = sealer.seal(canonical, binding(
                request.systemId(), request.tenantId(), request.memberId(), request.proposalId(),
                request.moduleCode(), request.recordId(), request.fieldCode()));
        return new PreparedFill(
                new FillPreview(
                        request.moduleCode(), request.recordId(), resolved.snapshot().recordVersion(),
                        resolved.snapshot().schemaVersionId(), contract.fieldId(), contract.fieldCode(),
                        contract.resultSchema(), resolved.snapshot().sourceVersionHash(),
                        current == null ? null : current.displayValue(), result.displayValue(),
                        result.confidence(), current != null),
                sealed);
    }

    @Override
    @Transactional
    public FillReadback execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        var command = command(
                request.sealedCommand(), binding(
                        request.systemId(), request.tenantId(), request.memberId(), request.proposalId(),
                        request.moduleCode(), request.recordId(), request.fieldCode()));
        assertCommandIdentity(
                command, request.proposalId(), request.fieldCode());
        var resolved = resolve(request.systemId(), request.tenantId(), request.memberId(),
                request.authorizationEpoch(), request.effectivePermissions(), request.moduleCode(),
                request.recordId(), request.fieldCode());
        assertLive(command, resolved);
        var storeRequest = storeRequest(
                request.systemId(), request.tenantId(), request.memberId(), request.moduleCode(),
                request.recordId(), command, request.idempotencyKey(), request.requestId(), request.traceId());
        return idempotency.run(
                scope(request.systemId(), request.tenantId(), request.recordId(), request.fieldCode(), "execute"),
                request.idempotencyKey(), command, FillReadback.class, 200,
                () -> store.materialize(storeRequest));
    }

    @Override
    @Transactional
    public RejectionReadback reject(RejectRequest request) {
        Objects.requireNonNull(request, "request");
        var command = command(
                request.sealedCommand(), binding(
                        request.systemId(), request.tenantId(), request.memberId(), request.proposalId(),
                        request.moduleCode(), request.recordId(), request.fieldCode()));
        assertCommandIdentity(command, request.proposalId(), request.fieldCode());
        resolve(request.systemId(), request.tenantId(), request.memberId(),
                request.authorizationEpoch(), request.effectivePermissions(), request.moduleCode(),
                request.recordId(), request.fieldCode());
        var storeRequest = storeRequest(
                request.systemId(), request.tenantId(), request.memberId(), request.moduleCode(),
                request.recordId(), command, request.idempotencyKey(), request.requestId(), request.traceId());
        return idempotency.run(
                scope(request.systemId(), request.tenantId(), request.recordId(), request.fieldCode(), "reject"),
                request.idempotencyKey(), command, RejectionReadback.class, 200,
                () -> store.reject(storeRequest));
    }

    private AiFieldFillCommandCodec.FillCommand command(
            SealedCommand sealed,
            AiFieldFillCommandSealer.Binding binding
    ) {
        var canonical = sealer.open(sealed, binding);
        var actualHash = AiFieldFillCommandSealer.sha256(canonical);
        if (!MessageDigest.isEqual(
                sealed.commandSha256().getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII))) {
            throw conflict("AI_FILL_COMMAND_INVALID", "AI_FILL command hash verification failed");
        }
        return codec.open(canonical);
    }

    private static void assertExpected(
            String schemaVersion,
            long recordVersion,
            String sourceHash,
            SourceSnapshot snapshot
    ) {
        if (!snapshot.schemaVersionId().equals(schemaVersion)) {
            throw conflict("RECORD_SCHEMA_STALE", "AI_FILL schema changed before prepare");
        }
        if (snapshot.recordVersion() != recordVersion) {
            throw conflict("RECORD_VERSION_CONFLICT", "AI_FILL record changed before prepare");
        }
        if (!snapshot.sourceVersionHash().equals(sourceHash)) {
            throw conflict("AI_FILL_SOURCE_STALE", "AI_FILL sources changed before prepare");
        }
    }

    private static void assertCommandIdentity(
            AiFieldFillCommandCodec.FillCommand command,
            String proposalId,
            String fieldCode
    ) {
        if (!command.proposalId().equals(proposalId) || !command.fieldCode().equals(fieldCode)) {
            throw conflict("AI_FILL_COMMAND_INVALID", "AI_FILL command identity is invalid");
        }
    }

    private static void assertLive(
            AiFieldFillCommandCodec.FillCommand command,
            AiFieldFillRuntimeReader.Resolved resolved
    ) {
        var snapshot = resolved.snapshot();
        var contract = snapshot.contract();
        if (!command.schemaVersionId().equals(snapshot.schemaVersionId())) {
            throw conflict("RECORD_SCHEMA_STALE", "AI_FILL schema changed before confirmation");
        }
        if (command.recordVersion() != snapshot.recordVersion()) {
            throw conflict("RECORD_VERSION_CONFLICT", "AI_FILL record changed before confirmation");
        }
        if (!command.sourceVersionHash().equals(snapshot.sourceVersionHash())) {
            throw conflict("AI_FILL_SOURCE_STALE", "AI_FILL sources changed before confirmation");
        }
        if (command.moduleSnapshotId() != resolved.moduleSnapshotId()
                || command.fieldSnapshotId() != resolved.fieldSnapshotId()
                || command.logicalFieldId() != resolved.logicalFieldId()
                || command.resultSchema() != contract.resultSchema()
                || command.overwriteMode() != contract.overwriteMode()
                || command.confidence() < contract.minimumConfidence()) {
            throw conflict("AI_FILL_CONTRACT_STALE", "AI_FILL field contract changed");
        }
        if (!Objects.equals(command.currentValueHash(), resolved.currentValueHash())
                || !Objects.equals(command.currentMaterializationVersion(),
                resolved.currentMaterializationVersion())) {
            throw conflict("AI_FILL_VALUE_STALE", "AI_FILL current value changed");
        }
        if (resolved.currentValueHash() != null
                && contract.overwriteMode() == OverwriteMode.NEVER) {
            throw conflict("AI_FILL_OVERWRITE_DENIED", "AI_FILL overwrite is forbidden");
        }
    }

    private AiFieldFillRuntimeReader.Resolved resolve(
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> permissions,
            String moduleCode,
            String recordId,
            String fieldCode
    ) {
        return runtime.resolve(new AiFieldFillRuntimeReader.AccessContext(
                systemId, tenantId, memberId, authorizationEpoch,
                permissions, moduleCode, recordId, fieldCode));
    }

    private static AiFieldFillMaterializationStore.StoreRequest storeRequest(
            long systemId,
            long tenantId,
            long memberId,
            String moduleCode,
            String recordId,
            AiFieldFillCommandCodec.FillCommand command,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return new AiFieldFillMaterializationStore.StoreRequest(
                systemId, tenantId, memberId, moduleCode, recordId,
                command, idempotencyKey, requestId, traceId);
    }

    private static AiFieldFillCommandSealer.Binding binding(
            long systemId,
            long tenantId,
            long memberId,
            String proposalId,
            String moduleCode,
            String recordId,
            String fieldCode
    ) {
        return new AiFieldFillCommandSealer.Binding(
                systemId, tenantId, memberId, proposalId, moduleCode, recordId, fieldCode);
    }

    private static String scope(
            long systemId,
            long tenantId,
            String recordId,
            String fieldCode,
            String action
    ) {
        return "aif:" + AiFieldFillCommandSealer.sha256(
                systemId + ":" + tenantId + ":" + recordId + ":" + fieldCode + ":" + action);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    @FunctionalInterface
    interface RuntimeReader {
        AiFieldFillRuntimeReader.Resolved resolve(AiFieldFillRuntimeReader.AccessContext context);
    }

    @FunctionalInterface
    interface FillStore {
        Object apply(AiFieldFillMaterializationStore.StoreRequest request);
    }

    @FunctionalInterface
    interface IdempotentMutation {
        <T> T run(
                String scope,
                String key,
                Object request,
                Class<T> responseType,
                int successStatus,
                RecordMutationSupport.Mutation<T> mutation);
    }

    private interface RoutedFillStore {
        FillReadback materialize(AiFieldFillMaterializationStore.StoreRequest request);

        RejectionReadback reject(AiFieldFillMaterializationStore.StoreRequest request);
    }

    private record RoutedStore(FillStore materializer, FillStore rejecter) implements RoutedFillStore {
        @Override
        public FillReadback materialize(AiFieldFillMaterializationStore.StoreRequest request) {
            return (FillReadback) materializer.apply(request);
        }

        @Override
        public RejectionReadback reject(AiFieldFillMaterializationStore.StoreRequest request) {
            return (RejectionReadback) rejecter.apply(request);
        }
    }
}
