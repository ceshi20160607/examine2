package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordMutationAdapterTest {
    private static final Set<String> PERMISSIONS = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.create",
            "module.work_order.update");
    private static final Set<String> POLICY_FIELDS = Set.of(
            "name", "secret_note", "customer", "lines");

    @Test
    void prepareIsReadOnlyAndBuildsPermissionProjectedScalarRelationAndSubtablePreview() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);

        var result = adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                createCommand(), POLICY_FIELDS));

        assertThat(owner.createCalls).isZero();
        assertThat(owner.updateCalls).isZero();
        assertThat(owner.businessWrites).isZero();
        assertThat(owner.schemaCalls).isEqualTo(1);
        assertThat(owner.detailCalls).isZero();
        assertThat(result.preview().recordId()).isNull();
        assertThat(result.preview().changes())
                .extracting(AiRecordMutationFacade.FieldChange::fieldCode)
                .containsExactly("customer", "lines", "name", "secret_note");
        assertThat(change(result, "name"))
                .extracting(AiRecordMutationFacade.FieldChange::afterDisplayValue,
                        AiRecordMutationFacade.FieldChange::masked)
                .containsExactly("New order", false);
        assertThat(change(result, "secret_note").afterDisplayValue()).isEqualTo("******");
        assertThat(change(result, "customer").afterDisplayValue()).isEqualTo("1 target(s)");
        assertThat(change(result, "lines").afterDisplayValue()).isEqualTo("1 row(s)");
        assertThat(result.sealedCommand().commandSha256()).matches("^[0-9a-f]{64}$");
        assertThat(result.sealedCommand().ciphertext()).doesNotContain("New order", "hidden-value");
    }

    @Test
    void sealedCommandRejectsCiphertextHashAndAadTamperingBeforeAnyWrite() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);
        var prepared = adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                createCommand(), POLICY_FIELDS));

        assertSealedInvalid(() -> adapter.execute(execute(
                "another-confirmation", AiRecordMutationFacade.Operation.RECORD_CREATE,
                prepared.sealedCommand(), "idem-1")));

        var ciphertext = prepared.sealedCommand().ciphertext();
        var replacement = ciphertext.endsWith("A") ? "B" : "A";
        var tamperedCiphertext = new AiRecordMutationFacade.SealedCommand(
                ciphertext.substring(0, ciphertext.length() - 1) + replacement,
                prepared.sealedCommand().encryptionKeyVersion(),
                prepared.sealedCommand().commandSha256());
        assertSealedInvalid(() -> adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_CREATE,
                tamperedCiphertext, "idem-2")));

        var tamperedHash = new AiRecordMutationFacade.SealedCommand(
                prepared.sealedCommand().ciphertext(),
                prepared.sealedCommand().encryptionKeyVersion(),
                "0".repeat(64));
        assertSealedInvalid(() -> adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_CREATE,
                tamperedHash, "idem-3")));
        assertThat(owner.businessWrites).isZero();
        assertThat(owner.createCalls).isZero();
    }

    @Test
    void policyOrLiveFieldPermissionDenialNeverDelegates() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);

        assertCode("AI_POLICY_FIELD_DENIED", () -> adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                createCommand(), Set.of("name", "customer", "lines"))));

        owner.schema = schema(List.of(
                field("name", "TEXT", true, false),
                field("secret_note", "SECRET", false, true),
                field("customer", "RELATION", true, false),
                field("lines", "SUBTABLE", true, false)));
        assertCode("AI_FIELD_NOT_WRITABLE", () -> adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                createCommand(), POLICY_FIELDS)));
        assertThat(owner.createCalls).isZero();
        assertThat(owner.updateCalls).isZero();
        assertThat(owner.businessWrites).isZero();
    }

    @Test
    void strictOwnerCommandRejectsUnknownDuplicateAndTrailingContentBeforeOwnerReads() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);
        var unknown = createCommand().replace(
                "\"schemaVersionId\":\"101\",",
                "\"schemaVersionId\":\"101\",\"unknown\":true,");
        var duplicate = createCommand().replace(
                "\"schemaVersionId\":\"101\",",
                "\"schemaVersionId\":\"101\",\"schemaVersionId\":\"101\",");

        assertCode("AI_RECORD_MUTATION_INVALID", () -> adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                unknown, POLICY_FIELDS)));
        assertCode("AI_RECORD_MUTATION_INVALID", () -> adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                duplicate, POLICY_FIELDS)));
        assertCode("AI_RECORD_MUTATION_INVALID", () -> adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                createCommand() + "{}", POLICY_FIELDS)));
        assertThat(owner.schemaCalls).isZero();
        assertThat(owner.businessWrites).isZero();
    }

    @Test
    void permissionEpochRowAndVersionAreRecheckedAtConfirmation() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);

        var missingUpdate = new AiRecordMutationFacade.PrepareRequest(
                "confirmation-1", 11, 13, 17, 5,
                Set.of("system.runtime.access", "module.work_order.view"),
                "work_order", AiRecordMutationFacade.Operation.RECORD_UPDATE,
                updateCommand(), POLICY_FIELDS, "request-1", "trace-1");
        assertCode("PERMISSION_DENIED", () -> adapter.prepare(missingUpdate));
        assertThat(owner.schemaCalls).isZero();

        var prepared = adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_UPDATE,
                updateCommand(), POLICY_FIELDS));
        owner.schema = schemaWithEpoch(6);
        assertCode("AI_AUTHORIZATION_STALE", () -> adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_UPDATE,
                prepared.sealedCommand(), "idem-epoch")));

        owner.schema = schemaWithEpoch(5);
        owner.denyDetail = true;
        assertCode("RECORD_NOT_FOUND", () -> adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_UPDATE,
                prepared.sealedCommand(), "idem-row")));

        owner.denyDetail = false;
        owner.detail = detail(4, "Old order");
        assertCode("RECORD_VERSION_CONFLICT", () -> adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_UPDATE,
                prepared.sealedCommand(), "idem-version")));
        assertThat(owner.updateCalls).isZero();
        assertThat(owner.businessWrites).isZero();
    }

    @Test
    void createDelegatesTheCompleteNativeCommandAndOwnerIdempotencyRemainsSingular() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);
        var prepared = adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_CREATE,
                createCommand(), POLICY_FIELDS));

        var first = adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_CREATE,
                prepared.sealedCommand(), "same-key"));
        var replay = adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_CREATE,
                prepared.sealedCommand(), "same-key"));

        assertThat(first).isEqualTo(replay);
        assertThat(owner.createCalls).isEqualTo(2);
        assertThat(owner.businessWrites).isEqualTo(1);
        assertThat(owner.lastCreate.schemaVersionId()).isEqualTo("101");
        assertThat(owner.lastCreate.values()).containsKeys("name", "secret_note");
        assertThat(owner.lastCreate.relations()).singleElement().satisfies(relation -> {
            assertThat(relation.fieldCode()).isEqualTo("customer");
            assertThat(relation.targets()).singleElement().satisfies(target ->
                    assertThat(target.targetRecordId()).isEqualTo("88"));
        });
        assertThat(owner.lastCreate.subtables()).singleElement().satisfies(subtable -> {
            assertThat(subtable.fieldCode()).isEqualTo("lines");
            assertThat(subtable.rows()).singleElement().satisfies(row ->
                    assertThat(row.values()).containsKey("description"));
        });
        assertThat(first.values()).extracting(AiRecordMutationFacade.DisplayValue::fieldCode)
                .containsExactly("name", "secret_note");
        assertThat(first.toString()).doesNotContain("raw-name", "raw-secret");
    }

    @Test
    void updateDelegatesExpectedVersionAndPropagatesNativeValidationFailureUnchanged() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);
        var prepared = adapter.prepare(prepare(
                AiRecordMutationFacade.Operation.RECORD_UPDATE,
                updateCommand(), POLICY_FIELDS));

        var result = adapter.execute(execute(
                "confirmation-1", AiRecordMutationFacade.Operation.RECORD_UPDATE,
                prepared.sealedCommand(), "update-key"));

        assertThat(owner.updateCalls).isEqualTo(1);
        assertThat(owner.businessWrites).isEqualTo(1);
        assertThat(owner.lastRecordId).isEqualTo(42);
        assertThat(owner.lastUpdate.expectedVersion()).isEqualTo(3);
        assertThat(result.version()).isEqualTo(4);

        var nativeFailure = new BusinessException(
                "RECORD_VALIDATION_FAILED", "required/unique/relation/subtable", HttpStatus.UNPROCESSABLE_ENTITY);
        owner.updateFailure = nativeFailure;
        var second = adapter.prepare(new AiRecordMutationFacade.PrepareRequest(
                "confirmation-2", 11, 13, 17, 5, PERMISSIONS, "work_order",
                AiRecordMutationFacade.Operation.RECORD_UPDATE, updateCommand(), POLICY_FIELDS,
                "request-2", "trace-2"));
        assertThatThrownBy(() -> adapter.execute(new AiRecordMutationFacade.ExecuteRequest(
                "confirmation-2", 11, 13, 17, 5, PERMISSIONS, "work_order",
                AiRecordMutationFacade.Operation.RECORD_UPDATE, second.sealedCommand(),
                "failure-key", "request-2", "trace-2")))
                .isSameAs(nativeFailure);
    }

    @Test
    void publicAdapterMethodsRemainSpringProxyableWithCorrectTransactionModes() throws Exception {
        assertThat(java.lang.reflect.Modifier.isFinal(AiRecordMutationAdapter.class.getModifiers())).isFalse();
        assertThat(AiRecordMutationAdapter.class.getMethod(
                        "prepare", AiRecordMutationFacade.PrepareRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(AiRecordMutationAdapter.class.getMethod(
                        "execute", AiRecordMutationFacade.ExecuteRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    private static AiRecordMutationAdapter adapter(FakeOwner owner) {
        var keys = (SensitiveKeyProvider) () -> Optional.of(new SensitiveKeyProvider.KeyRing(
                "enc-v1", "hash-v1",
                Map.of("enc-v1", "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Map.of("hash-v1", "abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                List.of("hash-v1")));
        var random = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 7);
            }
        };
        return new AiRecordMutationAdapter(
                owner,
                new AiMutationCommandSealer(keys, random),
                new AiRecordMutationCommandCodec());
    }

    private static AiRecordMutationFacade.PrepareRequest prepare(
            AiRecordMutationFacade.Operation operation,
            String command,
            Set<String> fields
    ) {
        return new AiRecordMutationFacade.PrepareRequest(
                "confirmation-1", 11, 13, 17, 5, PERMISSIONS,
                "work_order", operation, command, fields, "request-1", "trace-1");
    }

    private static AiRecordMutationFacade.ExecuteRequest execute(
            String confirmationId,
            AiRecordMutationFacade.Operation operation,
            AiRecordMutationFacade.SealedCommand command,
            String idempotencyKey
    ) {
        return new AiRecordMutationFacade.ExecuteRequest(
                confirmationId, 11, 13, 17, 5, PERMISSIONS,
                "work_order", operation, command, idempotencyKey, "request-1", "trace-1");
    }

    private static String createCommand() {
        return """
                {
                  "schemaVersionId":"101",
                  "recordId":null,
                  "expectedVersion":null,
                  "title":"New order",
                  "values":{"name":"New order","secret_note":"hidden-value"},
                  "relations":[{"fieldCode":"customer","targets":[
                    {"targetRecordId":"88","targetExpectedVersion":2,"ordinal":0}
                  ]}],
                  "subtables":[{"fieldCode":"lines","rows":[
                    {"clientRowKey":"row-1","rowId":null,"expectedVersion":null,"ordinal":0,
                     "values":{"description":"Line one"}}
                  ]}]
                }
                """;
    }

    private static String updateCommand() {
        return """
                {
                  "schemaVersionId":"101",
                  "recordId":"42",
                  "expectedVersion":3,
                  "title":"Updated order",
                  "values":{"name":"Updated order"},
                  "relations":[],
                  "subtables":[]
                }
                """;
    }

    private static AiRecordMutationFacade.FieldChange change(
            AiRecordMutationFacade.PreparedMutation result,
            String code
    ) {
        return result.preview().changes().stream()
                .filter(change -> code.equals(change.fieldCode())).findFirst().orElseThrow();
    }

    private static RecordRuntimeViews.RecordSchema schemaWithEpoch(long epoch) {
        return new RecordRuntimeViews.RecordSchema(
                "101", "31", "31", "checksum", "READY", null, epoch,
                List.of(
                        field("name", "TEXT", true, false),
                        field("secret_note", "SECRET", true, true),
                        field("customer", "RELATION", true, false),
                        field("lines", "SUBTABLE", true, false),
                        field("hidden", "TEXT", false, false, false)),
                List.of("CREATE"), new RecordRuntimeViews.QueryLimits(20, 200, 3));
    }

    private static RecordRuntimeViews.RecordSchema schema(
            List<RecordRuntimeViews.FieldCapability> fields
    ) {
        return new RecordRuntimeViews.RecordSchema(
                "101", "31", "31", "checksum", "READY", null, 5,
                fields, List.of("CREATE"), new RecordRuntimeViews.QueryLimits(20, 200, 3));
    }

    private static RecordRuntimeViews.FieldCapability field(
            String code,
            String type,
            boolean writable,
            boolean masked
    ) {
        return field(code, type, writable, masked, true);
    }

    private static RecordRuntimeViews.FieldCapability field(
            String code,
            String type,
            boolean writable,
            boolean masked,
            boolean readable
    ) {
        return new RecordRuntimeViews.FieldCapability(
                code, code + " name", "1", type, writable ? "WRITABLE" : "READONLY",
                readable, writable, !masked, false, masked,
                List.of(), false, true, true, List.of(), null);
    }

    private static RecordRuntimeViews.RecordDetail detail(long version, String title) {
        return new RecordRuntimeViews.RecordDetail(
                "42", "WO-42", version, "ACTIVE", title, "101",
                List.of(
                        new RecordRuntimeViews.FieldValue(
                                "name", "name name", "TEXT", "raw-name", title),
                        new RecordRuntimeViews.FieldValue(
                                "secret_note", "secret_note name", "SECRET", "raw-secret", "******"),
                        new RecordRuntimeViews.FieldValue(
                                "hidden", "hidden name", "TEXT", "raw-hidden", "Hidden")),
                List.of("UPDATE"));
    }

    private static void assertCode(
            String code,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        assertThatThrownBy(action).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static void assertSealedInvalid(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        assertCode("AI_MUTATION_COMMAND_INVALID", action);
    }

    private static final class FakeOwner implements AiRecordMutationAdapter.RecordOwner {
        private RecordRuntimeViews.RecordSchema schema = schemaWithEpoch(5);
        private RecordRuntimeViews.RecordDetail detail = AiRecordMutationAdapterTest.detail(3, "Old order");
        private final Map<String, RecordRuntimeViews.RecordDetail> idempotent = new LinkedHashMap<>();
        private int schemaCalls;
        private int detailCalls;
        private int createCalls;
        private int updateCalls;
        private int businessWrites;
        private boolean denyDetail;
        private BusinessException updateFailure;
        private RecordRuntimeViews.CreateRecordRequest lastCreate;
        private RecordRuntimeViews.UpdateRecordRequest lastUpdate;
        private long lastRecordId;

        @Override
        public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
            schemaCalls++;
            return schema;
        }

        @Override
        public RecordRuntimeViews.RecordDetail detail(RuntimeSession session, String moduleCode, long recordId) {
            detailCalls++;
            if (denyDetail) {
                throw new BusinessException("RECORD_NOT_FOUND", "row scope", HttpStatus.NOT_FOUND);
            }
            return detail;
        }

        @Override
        public RecordRuntimeViews.RecordDetail create(
                RuntimeSession session,
                String moduleCode,
                RecordRuntimeViews.CreateRecordRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            createCalls++;
            lastCreate = request;
            return idempotent.computeIfAbsent("create:" + idempotencyKey, ignored -> {
                businessWrites++;
                return AiRecordMutationAdapterTest.detail(0, request.title());
            });
        }

        @Override
        public RecordRuntimeViews.RecordDetail update(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                RecordRuntimeViews.UpdateRecordRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            updateCalls++;
            lastRecordId = recordId;
            lastUpdate = request;
            if (updateFailure != null) {
                throw updateFailure;
            }
            return idempotent.computeIfAbsent("update:" + idempotencyKey, ignored -> {
                businessWrites++;
                return AiRecordMutationAdapterTest.detail(request.expectedVersion() + 1, request.title());
            });
        }
    }
}
