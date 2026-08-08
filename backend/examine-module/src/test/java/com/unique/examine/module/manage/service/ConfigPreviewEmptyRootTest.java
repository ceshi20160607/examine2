package com.unique.examine.module.manage.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigPreviewEmptyRootTest {

    @Test
    void treatsABrandNewSystemAsRevisionZeroWithoutAnActiveVersion() {
        var root = ConfigPreviewService.previewRoot(List.of());

        assertThat(root.draftRevision()).isEqualTo("0");
        assertThat(root.activeVersionId()).isNull();
    }

    @Test
    void preservesPersistedDraftAndActiveVersionIdentifiers() {
        var root = ConfigPreviewService.previewRoot(List.of(Map.of(
                "draft_revision", 7L,
                "active_version_id", 12L)));

        assertThat(root.draftRevision()).isEqualTo("7");
        assertThat(root.activeVersionId()).isEqualTo("12");
    }
}
