package com.unique.examine.module.runtime.printing;

import java.util.Set;

/** Executable-app boundary for embedding bounded file-field imagery in immutable print snapshots. */
public interface PrintAssetReader {
    AssetImage thumbnail(long systemId, long tenantId, long memberId, Set<String> permissions, long fileId);

    record AssetImage(String mediaType, byte[] content, String sha256) {
        public AssetImage {
            content = content == null ? new byte[0] : content.clone();
        }
        @Override public byte[] content() { return content.clone(); }
    }
}
