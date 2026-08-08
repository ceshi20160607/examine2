package com.unique.examine.file.support;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileAssetPage;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.RuntimeRecordFile;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import com.unique.examine.file.port.FileAssetRepository;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TestFileAssetRepository implements FileAssetRepository {
    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentHashMap<Long, FileAsset> assets = new ConcurrentHashMap<>();

    @Override
    public long nextId() {
        return ids.incrementAndGet();
    }

    @Override
    public Optional<FileAsset> findById(long systemId, long tenantId, long id) {
        return Optional.ofNullable(assets.get(id))
                .filter(value -> value.systemId() == systemId && value.tenantId() == tenantId);
    }

    @Override
    public FileAssetPage findPage(long systemId, long tenantId, String keyword, String mediaType,
                                  int page, int size) {
        var normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        var values = assets.values().stream()
                .filter(value -> value.systemId() == systemId && value.tenantId() == tenantId)
                .filter(value -> normalizedKeyword == null
                        || value.originalName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .filter(value -> matchesMediaType(value.mediaType(), mediaType))
                .sorted(Comparator.comparing(FileAsset::createdAt).reversed()
                        .thenComparing(Comparator.comparingLong(FileAsset::id).reversed()))
                .toList();
        var offset = (long) (page - 1) * size;
        return new FileAssetPage(values.stream().skip(offset).limit(size).toList(),
                page, size, values.size());
    }

    @Override
    public RuntimeRecordFilePage findReferencePage(long systemId, long tenantId, AggregateRef target,
                                                   int page, int size) {
        var values = assets.values().stream()
                .filter(value -> value.systemId() == systemId && value.tenantId() == tenantId)
                .filter(value -> value.references().containsKey(target))
                .map(value -> new RuntimeRecordFile(value, value.references().get(target)))
                .toList();
        return new RuntimeRecordFilePage(values, page, size, values.size());
    }

    @Override
    public FileAsset save(FileAsset asset) {
        assets.compute(asset.id(), (id, current) -> {
            if (current == null && asset.version() == 1
                    || current != null && asset.version() == current.version() + 1) {
                return asset;
            }
            throw conflict();
        });
        return asset;
    }

    @Override
    public void delete(long systemId, long tenantId, long id, long expectedVersion) {
        assets.compute(id, (ignored, current) -> {
            if (current == null || current.systemId() != systemId || current.tenantId() != tenantId
                    || current.version() != expectedVersion) throw conflict();
            return null;
        });
    }

    private static boolean matchesMediaType(String actual, String filter) {
        if (filter == null) return true;
        var normalized = actual.toLowerCase(Locale.ROOT);
        return filter.endsWith("/*")
                ? normalized.startsWith(filter.substring(0, filter.length() - 1))
                : normalized.equals(filter);
    }

    private static FileDomainException conflict() {
        return new FileDomainException("FILE_VERSION_CONFLICT", "File metadata version is stale");
    }
}
