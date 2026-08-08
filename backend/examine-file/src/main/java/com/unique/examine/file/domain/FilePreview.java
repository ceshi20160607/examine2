package com.unique.examine.file.domain;

public record FilePreview(
        String originalName,
        String mediaType,
        byte[] content
) {
    public FilePreview {
        if (originalName == null || originalName.isBlank()
                || mediaType == null || mediaType.isBlank() || content == null) {
            throw new IllegalArgumentException("File preview is incomplete");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
