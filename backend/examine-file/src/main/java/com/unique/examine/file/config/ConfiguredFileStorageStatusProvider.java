package com.unique.examine.file.config;

import com.unique.examine.file.domain.FileStorageStatus;
import com.unique.examine.file.port.FileStorageStatusProvider;

import java.util.function.BooleanSupplier;

/** Produces a bounded, credential-free deployment storage descriptor. */
public final class ConfiguredFileStorageStatusProvider implements FileStorageStatusProvider {
    private final FileStorageStatus.Mode mode;
    private final String location;
    private final long maxSingleUploadBytes;
    private final long maxMultipartUploadBytes;
    private final long maxPartBytes;
    private final BooleanSupplier health;

    public ConfiguredFileStorageStatusProvider(
            FileStorageStatus.Mode mode,
            String location,
            long maxSingleUploadBytes,
            long maxMultipartUploadBytes,
            long maxPartBytes,
            BooleanSupplier health
    ) {
        if (mode == null || location == null || location.isBlank() || health == null) {
            throw new IllegalArgumentException("File storage descriptor is incomplete");
        }
        this.mode = mode;
        this.location = location.strip();
        this.maxSingleUploadBytes = maxSingleUploadBytes;
        this.maxMultipartUploadBytes = maxMultipartUploadBytes;
        this.maxPartBytes = maxPartBytes;
        this.health = health;
    }

    @Override
    public FileStorageStatus status() {
        boolean available;
        try {
            available = health.getAsBoolean();
        } catch (RuntimeException failure) {
            available = false;
        }
        return new FileStorageStatus(
                mode, location, maxSingleUploadBytes,
                maxMultipartUploadBytes, maxPartBytes, available);
    }
}
