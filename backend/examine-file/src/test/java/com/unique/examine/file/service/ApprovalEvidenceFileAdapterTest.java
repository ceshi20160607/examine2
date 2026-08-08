package com.unique.examine.file.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ApprovalEvidenceFileFacade;
import com.unique.examine.file.adapter.memory.InMemoryFileAssetRepository;
import com.unique.examine.file.adapter.memory.InMemoryFileContentStore;
import com.unique.examine.file.domain.FileActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalEvidenceFileAdapterTest {
    private InMemoryFileAssetRepository repository;
    private FileAssetService assets;
    private ApprovalEvidenceFileAdapter adapter;
    private FileActor uploader;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFileAssetRepository();
        assets = new FileAssetService(
                repository,
                new InMemoryFileContentStore(),
                Clock.fixed(
                        Instant.parse("2026-07-31T04:00:00Z"),
                        ZoneOffset.UTC)
        );
        adapter = new ApprovalEvidenceFileAdapter(assets);
        uploader = new FileActor(
                10, 20, 100,
                Set.of(
                        FileAssetService.CREATE,
                        FileAssetService.READ,
                        FileAssetService.REFERENCE
                )
        );
    }

    @Test
    void validatesCanonicalMetadataAndAttachesOneIdempotentReference() {
        var asset = assets.register(
                uploader, "approval.pdf", "application/pdf",
                new byte[]{1, 2, 3});
        var actor = actor(
                10, 20, 100, "file.read", "file.reference");

        assertThat(adapter.validateSelection(actor, List.of(asset.id())))
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.fileId()).isEqualTo(asset.id());
                    assertThat(snapshot.originalName())
                            .isEqualTo("approval.pdf");
                    assertThat(snapshot.contentType())
                            .isEqualTo("application/pdf");
                    assertThat(snapshot.size()).isEqualTo(3);
                    assertThat(snapshot.sha256()).hasSize(64);
                });

        adapter.attachEvidence(actor, 700L, List.of(asset.id()));
        adapter.attachEvidence(actor, 700L, List.of(asset.id()));

        var referenced = repository.findById(10, 20, asset.id())
                .orElseThrow();
        assertThat(referenced.references())
                .containsOnlyKeys(new AggregateRef(
                        ApprovalEvidenceFileFacade.AGGREGATE_TYPE, "700"));
    }

    @Test
    void rejectsForeignUnreadableAndNonReferenceEligibleFiles() {
        var asset = assets.register(
                uploader, "approval.pdf", "application/pdf",
                new byte[]{1});

        assertThatThrownBy(() -> adapter.validateSelection(
                actor(10, 21, 100, "file.read", "file.reference"),
                List.of(asset.id())))
                .isInstanceOfSatisfying(
                        ApprovalEvidenceFileFacade.EvidenceFileException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("EVIDENCE_FILE_NOT_FOUND"));
        assertThatThrownBy(() -> adapter.validateSelection(
                actor(10, 20, 101, "file.reference"),
                List.of(asset.id())))
                .isInstanceOfSatisfying(
                        ApprovalEvidenceFileFacade.EvidenceFileException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("EVIDENCE_FILE_FORBIDDEN"));
        assertThatThrownBy(() -> adapter.validateSelection(
                actor(10, 20, 100, "file.read"),
                List.of(asset.id())))
                .isInstanceOfSatisfying(
                        ApprovalEvidenceFileFacade.EvidenceFileException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("EVIDENCE_FILE_FORBIDDEN"));
        assertThat(repository.findById(10, 20, asset.id())
                .orElseThrow().references()).isEmpty();
    }

    @Test
    void rejectsDuplicateOrOversizedSelectionsBeforeMutation() {
        var asset = assets.register(
                uploader, "approval.pdf", "application/pdf",
                new byte[]{1});
        var actor = actor(
                10, 20, 100, "file.read", "file.reference");

        assertThatThrownBy(() -> adapter.attachEvidence(
                actor, 700L, List.of(asset.id(), asset.id())))
                .isInstanceOfSatisfying(
                        ApprovalEvidenceFileFacade.EvidenceFileException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(
                                        "EVIDENCE_FILE_SELECTION_INVALID"));
        assertThatThrownBy(() -> adapter.validateSelection(
                actor, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L)))
                .isInstanceOfSatisfying(
                        ApprovalEvidenceFileFacade.EvidenceFileException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(
                                        "EVIDENCE_FILE_SELECTION_INVALID"));
        assertThat(repository.findById(10, 20, asset.id())
                .orElseThrow().references()).isEmpty();
    }

    private static ApprovalEvidenceFileFacade.EvidenceActor actor(
            long systemId,
            long tenantId,
            long memberId,
            String... permissions
    ) {
        return new ApprovalEvidenceFileFacade.EvidenceActor(
                systemId, tenantId, memberId, Set.of(permissions));
    }
}
