package com.unique.examine.module.runtime.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRecordAccessAdapterTest {
    private static final Set<String> VIEW_PERMISSIONS = Set.of(
            "system.runtime.access",
            "module.work_order.view");

    @Test
    void delegatesToTheCanonicalRecordViewAndReturnsOnlyAccessMetadata() {
        var seenSession = new AtomicReference<RuntimeSession>();
        var seenModule = new AtomicReference<String>();
        var seenRecord = new AtomicReference<Long>();
        var adapter = new RuntimeRecordAccessAdapter(
                (session, moduleCode, recordId) -> {
                    seenSession.set(session);
                    seenModule.set(moduleCode);
                    seenRecord.set(recordId);
                    return record("9001", 7L);
                },
                (session, moduleCode) -> definition(true));

        var access = adapter.requireView(request(VIEW_PERMISSIONS));

        assertThat(access).isEqualTo(
                new RuntimeRecordAccessFacade.RuntimeRecordAccess("9001", 7L, true));
        assertThat(seenSession.get().systemId()).isEqualTo(11L);
        assertThat(seenSession.get().tenantId()).isEqualTo(22L);
        assertThat(seenSession.get().memberId()).isEqualTo(33L);
        assertThat(seenSession.get().permissions()).isEqualTo(VIEW_PERMISSIONS);
        assertThat(seenModule.get()).isEqualTo("work_order");
        assertThat(seenRecord.get()).isEqualTo(44L);
    }

    @Test
    void deniesMissingShellOrModuleViewBeforeReadingTheRecord() {
        var recordRead = new AtomicBoolean();
        var adapter = new RuntimeRecordAccessAdapter(
                (session, moduleCode, recordId) -> {
                    recordRead.set(true);
                    return record("44", 1L);
                },
                (session, moduleCode) -> definition(true));

        assertDenied(() -> adapter.requireView(request(Set.of("module.work_order.view"))));
        assertDenied(() -> adapter.requireView(request(Set.of("system.runtime.access"))));
        assertThat(recordRead).isFalse();
    }

    @Test
    void preservesCanonicalNotFoundSemanticsAndDoesNotReadModuleMetadata() {
        var definitionRead = new AtomicBoolean();
        var notFound = new BusinessException(
                "RECORD_NOT_FOUND",
                "Record is missing or outside VIEW scope",
                HttpStatus.NOT_FOUND);
        var adapter = new RuntimeRecordAccessAdapter(
                (session, moduleCode, recordId) -> {
                    throw notFound;
                },
                (session, moduleCode) -> {
                    definitionRead.set(true);
                    return definition(true);
                });

        assertThatThrownBy(() -> adapter.requireView(request(VIEW_PERMISSIONS)))
                .isSameAs(notFound);
        assertThat(definitionRead).isFalse();
    }

    @Test
    void readsDisabledAndNumericCommentFlagsFromThePublishedDefinition() {
        var recordReader = (RuntimeRecordAccessAdapter.RecordViewReader)
                (session, moduleCode, recordId) -> record("44", 1L);
        var disabled = new RuntimeRecordAccessAdapter(
                recordReader,
                (session, moduleCode) -> definition(false));
        var numeric = new RuntimeRecordAccessAdapter(
                recordReader,
                (session, moduleCode) -> definition(1));

        assertThat(disabled.requireView(request(VIEW_PERMISSIONS)).allowComments()).isFalse();
        assertThat(numeric.requireView(request(VIEW_PERMISSIONS)).allowComments()).isTrue();
    }

    private static RuntimeRecordAccessFacade.RuntimeRecordAccessRequest request(Set<String> permissions) {
        return new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                11L, 22L, 33L, permissions, "work_order", 44L);
    }

    private static RecordRuntimeViews.RecordDetail record(String recordId, long version) {
        return new RecordRuntimeViews.RecordDetail(
                recordId, "WO-1", version, "ACTIVE", "Work order", "101", List.of(), List.of());
    }

    private static RuntimeViews.Definition definition(boolean allowComments) {
        var mapper = new ObjectMapper();
        var module = mapper.createObjectNode().put("allow_comments", allowComments);
        var empty = mapper.createArrayNode();
        return new RuntimeViews.Definition(
                "101", "1", module, empty, empty, empty, empty, empty, empty, empty, true);
    }

    private static RuntimeViews.Definition definition(int allowComments) {
        var mapper = new ObjectMapper();
        var module = mapper.createObjectNode().put("allow_comments", allowComments);
        var empty = mapper.createArrayNode();
        return new RuntimeViews.Definition(
                "101", "1", module, empty, empty, empty, empty, empty, empty, empty, true);
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("PERMISSION_DENIED");
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }
}
