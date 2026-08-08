package com.unique.examine.web;

import com.unique.examine.file.domain.FileActor;
import com.unique.examine.file.service.FileAssetService;
import com.unique.examine.module.runtime.printing.PrintAssetReader;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class FilePrintAssetReaderAdapter implements PrintAssetReader {
    private final FileAssetService files;

    public FilePrintAssetReaderAdapter(FileAssetService files) {
        this.files = files;
    }

    @Override
    public AssetImage thumbnail(long systemId, long tenantId, long memberId,
                                Set<String> permissions, long fileId) {
        var mapped = new LinkedHashSet<String>();
        if (permissions.contains("file.read") || permissions.contains(FileAssetService.READ)) {
            mapped.add(FileAssetService.READ);
        }
        var actor = new FileActor(systemId, tenantId, memberId, Set.copyOf(mapped));
        var asset = files.get(actor, fileId);
        var thumbnail = files.thumbnail(actor, fileId, 360, 240);
        return new AssetImage("image/png", thumbnail.content(), asset.sha256());
    }
}
