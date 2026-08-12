package com.unique.examine.module.runtime.service;

import com.unique.examine.core.runtime.RuntimeRecordFlowTriggerFacade;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFlowEventWiringContractTest {

    @Test
    void serviceDependsOnTheLazyPublisherAndActivationRemainsTransactional() throws Exception {
        assertThat(RecordRuntimeService.class.getDeclaredField("flowTriggers").getType())
                .isEqualTo(RecordFlowEventPublisher.class);
        assertThat(RecordFlowEventPublisher.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> {
                    assertThat(constructor.getParameterTypes()).containsExactly(ObjectProvider.class);
                    assertThat(constructor.getGenericParameterTypes()[0].getTypeName())
                            .contains(RuntimeRecordFlowTriggerFacade.class.getName());
                });

        var activate = RecordRuntimeService.class.getMethod(
                "activate",
                RuntimeSession.class,
                String.class,
                long.class,
                RecordRuntimeViews.VersionCommandRequest.class,
                String.class,
                String.class,
                String.class);
        assertThat(activate.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void publisherRunsAfterMutationFactsInsideTheIdempotentActivateSupplier() throws Exception {
        var source = compact(Files.readString(sourcePath()));
        var publicActivate = source.indexOf(
                "public recordruntimeviews.recorddetail activate(");
        var activateNow = source.indexOf(
                "private recordruntimeviews.recorddetail activatenow(");
        var nextMethod = source.indexOf(
                "@transactional public recordruntimeviews.recorddetail archive(", activateNow);
        var publicBody = source.substring(publicActivate, activateNow);
        var privateBody = source.substring(activateNow, nextMethod);

        assertThat(publicBody)
                .contains("return mutations.idempotent(")
                .contains("() -> activatenow(")
                .doesNotContain("publishrecordevent(");
        assertThat(privateBody.indexOf("mutations.changed("))
                .isGreaterThanOrEqualTo(0);
        assertThat(privateBody.indexOf("publishrecordevent("))
                .isGreaterThan(privateBody.indexOf("mutations.changed("));
        assertThat(privateBody.indexOf("return response;"))
                .isGreaterThan(privateBody.indexOf("publishrecordevent("));
        assertThat(count(privateBody, "publishrecordevent(")).isOne();
        assertThat(privateBody)
                .contains("triggerevent.record_activated");
    }

    @Test
    void frozenMutationPointsPublishExactlyOneEventAndAutosaveRemainsExcluded() throws Exception {
        var source = compact(Files.readString(sourcePath()));
        var create = section(source,
                "private recordruntimeviews.recorddetail createnow(",
                "@transactional public recordruntimeviews.recorddetail update(");
        var write = section(source,
                "private writeresult writenow(",
                "@transactional public recordruntimeviews.recorddetail activate(");
        var batchEdit = section(source,
                "private recordruntimeviews.batchmutationresponse batcheditnow(",
                "private recordruntimeviews.batchmutationresponse batchlifecycle(");
        var lifecycle = section(source,
                "private recordruntimeviews.recorddetail applylifecycletransition(",
                "private void publishrecordevent(");

        assertThat(create)
                .contains("triggerevent.record_created")
                .contains("mutations.changed(")
                .contains("publishrecordevent(");
        assertThat(create.indexOf("publishrecordevent("))
                .isGreaterThan(create.indexOf("mutations.changed("));
        assertThat(write)
                .contains("triggerevent.record_updated")
                .contains("if (!autosave) { publishrecordevent(");
        assertThat(count(write, "publishrecordevent(")).isOne();
        assertThat(batchEdit)
                .contains("triggerevent.record_updated")
                .contains("publishrecordevent(");
        assertThat(count(batchEdit, "publishrecordevent(")).isOne();
        assertThat(lifecycle)
                .contains("command.triggerevent()")
                .contains("publishrecordevent(");
        assertThat(count(lifecycle, "publishrecordevent(")).isOne();
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

    private static int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
