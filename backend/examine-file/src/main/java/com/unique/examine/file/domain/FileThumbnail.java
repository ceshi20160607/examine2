package com.unique.examine.file.domain;

public record FileThumbnail(
        String originalName,
        String mediaType,
        int width,
        int height,
        byte[] content
) {
    public FileThumbnail {
        if (originalName == null || originalName.isBlank()
                || mediaType == null || mediaType.isBlank()
                || width < 1 || height < 1 || content == null || content.length == 0) {
            throw new IllegalArgumentException("File thumbnail is incomplete");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
