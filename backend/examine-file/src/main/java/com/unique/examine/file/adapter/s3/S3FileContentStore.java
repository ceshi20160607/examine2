package com.unique.examine.file.adapter.s3;

import com.unique.examine.file.port.FileContentStore;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/** S3-compatible content adapter with a deployment-fixed bucket and prefix. */
public final class S3FileContentStore implements FileContentStore {
    private final S3Client client;
    private final String bucket;
    private final String prefix;

    public S3FileContentStore(S3Client client, String bucket, String prefix) {
        if (client == null) {
            throw new IllegalArgumentException("S3 client is required");
        }
        this.client = client;
        this.bucket = requireBucket(bucket);
        this.prefix = requirePrefix(prefix);
    }

    @Override
    public void put(String objectKey, byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("File content is required");
        }
        var resolvedKey = key(objectKey);
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(resolvedKey).build(),
                    RequestBody.fromBytes(content));
        } catch (RuntimeException failure) {
            throw storageFailure("write", failure);
        }
    }

    @Override
    public Optional<byte[]> read(String objectKey) {
        var resolvedKey = key(objectKey);
        try {
            var response = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(resolvedKey).build());
            return Optional.of(response.asByteArray());
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) {
                return Optional.empty();
            }
            throw storageFailure("read", failure);
        } catch (RuntimeException failure) {
            throw storageFailure("read", failure);
        }
    }

    @Override
    public void delete(String objectKey) {
        var resolvedKey = key(objectKey);
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket).key(resolvedKey).build());
        } catch (RuntimeException failure) {
            throw storageFailure("delete", failure);
        }
    }

    public boolean available() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public String location() {
        return bucket + "/" + prefix;
    }

    private String key(String objectKey) {
        return prefix + "/" + safeObjectKey(objectKey);
    }

    private static String safeObjectKey(String value) {
        if (value == null || value.isBlank() || value.length() > 700
                || value.startsWith("/") || value.endsWith("/")
                || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Object key is invalid");
        }
        for (var segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Object key is invalid");
            }
            for (int index = 0; index < segment.length(); index++) {
                if (Character.isISOControl(segment.charAt(index))) {
                    throw new IllegalArgumentException("Object key is invalid");
                }
            }
        }
        return value;
    }

    private static String requireBucket(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
            throw new IllegalArgumentException("S3 bucket is invalid");
        }
        return value;
    }

    private static String requirePrefix(String value) {
        if (value == null) {
            throw new IllegalArgumentException("S3 prefix is required");
        }
        var normalized = value.strip();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isBlank() || normalized.length() > 300) {
            throw new IllegalArgumentException("S3 prefix is invalid");
        }
        safeObjectKey(normalized);
        return normalized;
    }

    private static IllegalStateException storageFailure(String operation, RuntimeException cause) {
        return new IllegalStateException("Unable to " + operation + " S3 file content", cause);
    }
}
