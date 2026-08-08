package com.unique.examine.file.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ApprovalEvidenceFileFacade;
import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileDomainException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * File-side implementation of the narrow Flow evidence bridge.
 */
@Transactional
public class ApprovalEvidenceFileAdapter
        implements ApprovalEvidenceFileFacade {
    private final FileAssetService assets;

    public ApprovalEvidenceFileAdapter(FileAssetService assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileMetadata> validateSelection(
            EvidenceActor actor,
            List<Long> fileIds
    ) {
        try {
            var ids = requireFileIds(fileIds);
            if (ids.isEmpty()) {
                return List.of();
            }
            var fileActor = fileActor(actor);
            requireReferencePermission(fileActor);
            return ids.stream()
                    .map(fileId -> snapshot(assets.get(fileActor, fileId)))
                    .toList();
        } catch (FileDomainException failure) {
            throw translate(failure);
        }
    }

    @Override
    public void attachEvidence(
            EvidenceActor actor,
            long evidenceId,
            List<Long> fileIds
    ) {
        try {
            if (evidenceId <= 0) {
                throw new IllegalArgumentException(
                        "Approval evidence id must be positive");
            }
            var ids = requireFileIds(fileIds);
            if (ids.isEmpty()) {
                return;
            }
            var fileActor = fileActor(actor);
            requireReferencePermission(fileActor);
            var target = new AggregateRef(
                    AGGREGATE_TYPE, Long.toString(evidenceId));
            // Revalidate immediately before mutation so ACTIVE/scope/readability
            // cannot be bypassed between preview and the transactional attach.
            ids.forEach(fileId -> assets.get(fileActor, fileId));
            var newlyAttached = new ArrayList<Long>();
            for (var fileId : ids) {
                try {
                    var before = assets.get(fileActor, fileId);
                    var existed = before.references().containsKey(target);
                    assets.addReference(fileActor, fileId, target);
                    if (!existed) {
                        newlyAttached.add(fileId);
                    }
                } catch (RuntimeException failure) {
                    compensate(fileActor, target, newlyAttached, failure);
                    throw failure;
                }
            }
        } catch (FileDomainException failure) {
            throw translate(failure);
        }
    }

    private void compensate(
            FileActor actor,
            AggregateRef target,
            List<Long> newlyAttached,
            RuntimeException failure
    ) {
        for (var index = newlyAttached.size() - 1; index >= 0; index--) {
            try {
                assets.removeReference(actor, newlyAttached.get(index), target);
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
        }
    }

    private static List<Long> requireFileIds(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > MAX_DISTINCT_FILES
                || values.stream().anyMatch(value ->
                        value == null || value <= 0)
                || new LinkedHashSet<>(values).size() != values.size()) {
            throw new EvidenceFileException(
                    "EVIDENCE_FILE_SELECTION_INVALID",
                    "Approval evidence accepts at most 6 distinct positive file ids"
            );
        }
        return List.copyOf(values);
    }

    private static FileActor fileActor(EvidenceActor actor) {
        Objects.requireNonNull(actor, "actor");
        var permissions = new LinkedHashSet<String>();
        map(actor.permissions(), permissions, "file.read", FileAssetService.READ);
        map(actor.permissions(), permissions,
                "file.reference", FileAssetService.REFERENCE);
        map(actor.permissions(), permissions,
                "file.manage", FileAssetService.MANAGE);
        return new FileActor(
                actor.systemId(), actor.tenantId(), actor.memberId(),
                Set.copyOf(permissions));
    }

    private static void map(
            Set<String> source,
            Set<String> target,
            String external,
            String internal
    ) {
        if (source.contains(external) || source.contains(internal)) {
            target.add(internal);
        }
    }

    private static void requireReferencePermission(FileActor actor) {
        if (!actor.has(FileAssetService.REFERENCE)) {
            throw new FileDomainException(
                    "FILE_FORBIDDEN",
                    "Missing permission: " + FileAssetService.REFERENCE);
        }
    }

    private static FileMetadata snapshot(FileAsset asset) {
        return new FileMetadata(
                asset.id(),
                asset.originalName(),
                asset.mediaType(),
                asset.size(),
                asset.sha256()
        );
    }

    private static EvidenceFileException translate(
            FileDomainException failure
    ) {
        var code = switch (failure.code()) {
            case "FILE_NOT_FOUND" -> "EVIDENCE_FILE_NOT_FOUND";
            case "FILE_FORBIDDEN" -> "EVIDENCE_FILE_FORBIDDEN";
            case "FILE_EVIDENCE_SELECTION_INVALID" ->
                    "EVIDENCE_FILE_SELECTION_INVALID";
            default -> "EVIDENCE_FILE_INVALID";
        };
        return new EvidenceFileException(code, failure.getMessage());
    }
}
