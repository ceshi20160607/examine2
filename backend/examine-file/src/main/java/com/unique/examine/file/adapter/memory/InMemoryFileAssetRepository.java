package com.unique.examine.file.adapter.memory;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.RuntimeRecordFile;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import com.unique.examine.file.port.FileAssetRepository;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryFileAssetRepository implements FileAssetRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, FileAsset> assets = new ConcurrentHashMap<>();

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public Optional<FileAsset> findById(long systemId, long tenantId, long id) {
        return Optional.ofNullable(assets.get(id))
                .filter(asset -> asset.systemId() == systemId && asset.tenantId() == tenantId);
    }

    @Override
    public RuntimeRecordFilePage findReferencePage(
            long systemId,
            long tenantId,
            AggregateRef target,
            int page,
            int size
    ) {
        var matches = assets.values().stream()
                .filter(asset -> asset.systemId() == systemId && asset.tenantId() == tenantId)
                .filter(asset -> asset.references().containsKey(target))
                .map(asset -> new RuntimeRecordFile(asset, asset.references().get(target)))
                .sorted(Comparator
                        .<RuntimeRecordFile, java.time.Instant>comparing(
                                item -> item.reference().createdAt())
                        .reversed()
                        .thenComparing(Comparator.comparingLong(
                                (RuntimeRecordFile item) -> item.asset().id()).reversed()))
                .toList();
        var offset = (long) (page - 1) * size;
        var items = matches.stream().skip(offset).limit(size).toList();
        return new RuntimeRecordFilePage(items, page, size, matches.size());
    }

    @Override
    public FileAsset save(FileAsset asset) {
        assets.compute(asset.id(), (id, current) -> {
            if (current != null && asset.version() != current.version() + 1) {
                throw conflict();
            }
            if (current == null && asset.version() != 1) {
                throw conflict();
            }
            return asset;
        });
        return asset;
    }

    @Override
    public void delete(long systemId, long tenantId, long id, long expectedVersion) {
        assets.compute(id, (ignored, current) -> {
            if (current == null
                    || current.systemId() != systemId
                    || current.tenantId() != tenantId
                    || current.version() != expectedVersion) {
                throw conflict();
            }
            return null;
        });
    }

    private static FileDomainException conflict() {
        return new FileDomainException("FILE_VERSION_CONFLICT", "File metadata version is stale");
    }
}
