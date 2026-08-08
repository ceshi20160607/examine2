package com.unique.examine.file.domain;

public record RuntimeRecordFile(
        FileAsset asset,
        FileReference reference
) {
    public RuntimeRecordFile {
        if (asset == null || reference == null) {
            throw new IllegalArgumentException("Runtime record file metadata is incomplete");
        }
        var persisted = asset.references().get(reference.target());
        if (!reference.equals(persisted)) {
            throw new IllegalArgumentException("Runtime record file reference is not attached to the asset");
        }
    }
}
