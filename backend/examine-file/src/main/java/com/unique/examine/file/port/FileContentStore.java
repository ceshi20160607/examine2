package com.unique.examine.file.port;

import java.util.Optional;

public interface FileContentStore {
    void put(String objectKey, byte[] content);

    Optional<byte[]> read(String objectKey);

    void delete(String objectKey);
}
