package com.unique.examine.file.port;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileAssetPage;
import com.unique.examine.file.domain.RuntimeRecordFilePage;

import java.util.Optional;

public interface FileAssetRepository {
    long nextId();

    Optional<FileAsset> findById(long systemId, long tenantId, long id);

    default FileAssetPage findPage(
            long systemId,
            long tenantId,
            String keyword,
            String mediaType,
            int page,
            int size
    ) {
        throw new UnsupportedOperationException("File asset paging is not implemented");
    }

    RuntimeRecordFilePage findReferencePage(
            long systemId,
            long tenantId,
            AggregateRef target,
            int page,
            int size);

    FileAsset save(FileAsset asset);

    void delete(long systemId, long tenantId, long id, long expectedVersion);
}
