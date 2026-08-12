package com.unique.examine.file.port;

import com.unique.examine.file.domain.FileStorageStatus;

@FunctionalInterface
public interface FileStorageStatusProvider {
    FileStorageStatus status();
}
