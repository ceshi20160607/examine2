package com.unique.examine.module.runtime.service;

import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecordRuntimeOpenApiCreateContractTest {

    @Test
    void publicEntryIsTransactionalAndApplicationScopesAreIsolated() throws Exception {
        var method = RecordRuntimeService.class.getMethod(
                "createOpenApi",
                RuntimeSession.class,
                String.class,
                long.class,
                String.class,
                Map.class,
                String.class,
                String.class,
                String.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();

        var session = new RuntimeSession(7, 11, 17, 13L, Set.of());
        var first = RecordRuntimeService.openApiCreateIdempotencyScope(session, "work_order", 101);
        var second = RecordRuntimeService.openApiCreateIdempotencyScope(session, "work_order", 102);

        assertThat(first).isEqualTo("11:13:17:101:work_order:openapi:create");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void openApiEntryUsesCanonicalPayloadAndDirectActiveCreateEmitsSingleFacts() throws Exception {
        var source = compact(Files.readString(sourcePath()));
        var entry = section(
                source,
                "public recordruntimeviews.recorddetail createopenapi(",
                "static string openapicreateidempotencyscope(");
        var create = section(
                source,
                "private recordruntimeviews.recorddetail createnow(",
                "@transactional public recordruntimeviews.recorddetail update(");

        assertThat(entry)
                .contains("new java.util.treemap<>(request.values())")
                .contains("openapicreateidempotencyscope(session, modulecode, applicationid)")
                .contains("\"openapi\"")
                .doesNotContain("activatenow(");
        assertThat(create)
                .contains("if (\"active\".equals(lifecyclestate)) { requireactivationvalues(")
                .contains("replaceuniquereservations(")
                .contains("auditaction = \"active\".equals(lifecyclestate) ? \"record_created\"")
                .contains("set.of(), auditsource")
                .contains("triggerevent.record_created");
        assertThat(count(create, "mutations.changed(")).isOne();
        assertThat(count(create, "publishrecordevent(")).isOne();
        assertThat(create.indexOf("publishrecordevent("))
                .isGreaterThan(create.indexOf("mutations.changed("));
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
