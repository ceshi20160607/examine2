package com.unique.examine.module.runtime.service;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecordRuntimeOpenApiMutationContractTest {

    @Test
    void ownerEntriesAreTransactionalAndApplicationMutationScopesAreIsolated() throws Exception {
        assertThat(RecordRuntimeService.class.getMethod(
                "updateOpenApi", RuntimeSession.class, String.class, long.class, long.class, long.class,
                Map.class, String.class, String.class, String.class).getAnnotation(Transactional.class))
                .isNotNull();
        assertThat(RecordRuntimeService.class.getMethod(
                "lifecycleOpenApi", RuntimeSession.class, String.class, long.class, long.class, String.class,
                long.class, String.class, String.class, String.class).getAnnotation(Transactional.class))
                .isNotNull();
        assertThat(RecordRuntimeService.class.getMethod(
                "mutateRelation", RuntimeSession.class, String.class, long.class, String.class,
                long.class, RecordRuntimeViews.RelationMutationRequest.class,
                String.class, String.class, String.class).getAnnotation(Transactional.class))
                .isNotNull();
        assertThat(RecordRuntimeService.class.getMethod(
                "mutateSubtable", RuntimeSession.class, String.class, long.class, String.class,
                long.class, RecordRuntimeViews.SubtableMutationRequest.class,
                String.class, String.class, String.class).getAnnotation(Transactional.class))
                .isNotNull();

        var session = new RuntimeSession(7, 11, 17, 13L, Set.of());
        var first = RecordRuntimeService.openApiRecordMutationScope(session, "work_order", 23, 101);
        var second = RecordRuntimeService.openApiRecordMutationScope(session, "work_order", 23, 102);
        var anotherRecord = RecordRuntimeService.openApiRecordMutationScope(session, "work_order", 24, 101);

        assertThat(first).isEqualTo("11:13:17:101:work_order:23:openapi:mutation");
        assertThat(second).isNotEqualTo(first);
        assertThat(anotherRecord).isNotEqualTo(first);
    }

    @Test
    void compositionMutationScopesSeparateApplicationKindFieldAndRecord() {
        var session = new RuntimeSession(7, 11, 17, 13L, Set.of());
        var relation = RecordRuntimeService.openApiCompositionMutationScope(
                session, "work_order", 23L, 101L,
                "relation", "related_orders");
        var anotherApplication = RecordRuntimeService.openApiCompositionMutationScope(
                session, "work_order", 23L, 102L,
                "relation", "related_orders");
        var anotherKind = RecordRuntimeService.openApiCompositionMutationScope(
                session, "work_order", 23L, 101L,
                "subtable", "related_orders");
        var anotherField = RecordRuntimeService.openApiCompositionMutationScope(
                session, "work_order", 23L, 101L,
                "relation", "parent_order");
        var anotherRecord = RecordRuntimeService.openApiCompositionMutationScope(
                session, "work_order", 24L, 101L,
                "relation", "related_orders");

        assertThat(relation)
                .startsWith("oac:")
                .hasSize(68);
        assertThat(Set.of(
                relation, anotherApplication, anotherKind,
                anotherField, anotherRecord)).hasSize(5);

        var maximum = RecordRuntimeService.openApiCompositionMutationScope(
                new RuntimeSession(
                        Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                        Long.MAX_VALUE, Set.of()),
                "m".repeat(64), Long.MAX_VALUE, Long.MAX_VALUE,
                "subtable", "f".repeat(64));
        assertThat(maximum).hasSize(68);
    }

    @Test
    void compositionOpenApiWrappersReuseOneMutationChainWithOpenApiAuditSource() throws Exception {
        var source = compact(Files.readString(sourcePath()));
        var relation = section(
                source,
                "public recordruntimeviews.recordmutationresponse mutaterelation( runtimesession session, string modulecode, long recordid, string fieldcode, long applicationid",
                "private recordruntimeviews.recordmutationresponse mutaterelationnow(");
        var subtable = section(
                source,
                "public recordruntimeviews.recordmutationresponse mutatesubtable( runtimesession session, string modulecode, long recordid, string fieldcode, long applicationid",
                "private recordruntimeviews.recordmutationresponse mutatesubtablenow(");

        assertThat(relation)
                .contains("openapicompositionmutationscope(")
                .contains("\"relation\", fieldcode")
                .contains("request, \"openapi\", requestid, traceid");
        assertThat(subtable)
                .contains("openapicompositionmutationscope(")
                .contains("\"subtable\", fieldcode")
                .contains("request, \"openapi\", requestid, traceid");
    }

    @Test
    void updateCanonicalizesValuesChecksCurrentScopeAndEmitsOneOpenApiFactPair() throws Exception {
        var source = compact(Files.readString(sourcePath()));
        var entry = section(
                source,
                "public recordruntimeviews.recorddetail updateopenapi(",
                "@transactional public recordruntimeviews.autosaverecordresponse autosave(");
        var write = section(
                source,
                "private writeresult writenow(",
                "@transactional public recordruntimeviews.recorddetail activate(");

        assertThat(entry)
                .contains("requireopenapimutationaccess(session, modulecode, recordid, \"update\")")
                .contains("new java.util.treemap<>(supplied)")
                .contains("new openapimutationidentity( \"update\", expectedversion")
                .contains("openapirecordmutationscope(session, modulecode, recordid, applicationid)")
                .contains("writenow(session, modulecode, recordid, input, false, false, \"openapi\"");
        assertThat(count(write, "mutations.changed(")).isOne();
        assertThat(count(write, "publishrecordevent(")).isOne();
        assertThat(write)
                .contains("forcedmaskedchanges, auditsource")
                .contains("triggerevent.record_updated");
    }

    @Test
    void lifecycleUsesActionInCommonFingerprintAndEachTransitionEmitsOneOpenApiFactPair() throws Exception {
        var source = compact(Files.readString(sourcePath()));
        var entry = section(
                source,
                "public recordruntimeviews.recorddetail lifecycleopenapi(",
                "private recordruntimeviews.recorddetail activatenow(");
        var activate = section(
                source,
                "private recordruntimeviews.recorddetail activatenow(",
                "@transactional public recordruntimeviews.recorddetail archive(");
        var transition = section(
                source,
                "private recordruntimeviews.recorddetail applylifecycletransition(",
                "private void publishrecordevent(");

        assertThat(entry)
                .contains("new openapimutationidentity(normalizedaction, expectedversion, map.of())")
                .contains("requireopenapimutationaccess(session, modulecode, recordid, permissionverb)")
                .contains("openapirecordmutationscope(session, modulecode, recordid, applicationid)")
                .contains("activatenow(session, modulecode, recordid, request, \"openapi\"")
                .contains("lifecyclenow(session, modulecode, recordid, request, lifecyclecommand, \"openapi\"");
        assertThat(count(activate, "mutations.changed(")).isOne();
        assertThat(count(activate, "publishrecordevent(")).isOne();
        assertThat(activate).contains("set.of(), auditsource");
        assertThat(count(transition, "mutations.changed(")).isOne();
        assertThat(count(transition, "publishrecordevent(")).isOne();
        assertThat(transition).contains("set.of(), auditsource");
    }

    private static Path sourcePath() throws Exception {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "backend/examine-module/src/main/java/com/unique/examine/module/runtime/service/"
                            + "RecordRuntimeService.java");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            candidate = current.resolve(
                    "src/main/java/com/unique/examine/module/runtime/service/RecordRuntimeService.java");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate RecordRuntimeService.java");
    }

    private static String compact(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ");
    }

    private static String section(String source, String start, String end) {
        var startIndex = source.indexOf(start);
        var endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("section start " + start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("section end " + end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static int count(String value, String token) {
        var result = 0;
        var offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            result++;
            offset += token.length();
        }
        return result;
    }
}
