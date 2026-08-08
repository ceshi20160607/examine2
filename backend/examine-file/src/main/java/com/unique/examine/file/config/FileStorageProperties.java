package com.unique.examine.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "examine.file.storage")
public final class FileStorageProperties {
    public static final long DEFAULT_MAX_SINGLE_UPLOAD_BYTES = 20L * 1024 * 1024;
    public static final long DEFAULT_MAX_MULTIPART_UPLOAD_BYTES = 100L * 1024 * 1024;
    public static final long DEFAULT_MAX_PART_BYTES = 5L * 1024 * 1024;

    private Mode mode = Mode.LOCAL;
    private long maxSingleUploadBytes = DEFAULT_MAX_SINGLE_UPLOAD_BYTES;
    private long maxMultipartUploadBytes = DEFAULT_MAX_MULTIPART_UPLOAD_BYTES;
    private long maxPartBytes = DEFAULT_MAX_PART_BYTES;
    private final Local local = new Local();
    private final S3 s3 = new S3();

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public long getMaxSingleUploadBytes() { return maxSingleUploadBytes; }
    public void setMaxSingleUploadBytes(long value) { this.maxSingleUploadBytes = value; }
    public long getMaxMultipartUploadBytes() { return maxMultipartUploadBytes; }
    public void setMaxMultipartUploadBytes(long value) { this.maxMultipartUploadBytes = value; }
    public long getMaxPartBytes() { return maxPartBytes; }
    public void setMaxPartBytes(long value) { this.maxPartBytes = value; }
    public Local getLocal() { return local; }
    public S3 getS3() { return s3; }

    public void validateLimits() {
        if (maxSingleUploadBytes < 1
                || maxMultipartUploadBytes < maxSingleUploadBytes
                || maxPartBytes < 1
                || maxPartBytes > maxMultipartUploadBytes) {
            throw new IllegalStateException("File upload limits are invalid");
        }
    }

    public enum Mode {
        LOCAL,
        S3
    }

    public static final class Local {
        private String root = "./data/files";

        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }

        @Override
        public String toString() {
            return "Local[root=[redacted]]";
        }
    }

    public static final class S3 {
        private String endpoint;
        private String region;
        private String bucket;
        private String prefix;
        private boolean pathStyle = true;
        private boolean allowLoopbackHttpForTesting;
        private String accessKey;
        private String secretKey;
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(15);

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public boolean isPathStyle() { return pathStyle; }
        public void setPathStyle(boolean pathStyle) { this.pathStyle = pathStyle; }
        public boolean isAllowLoopbackHttpForTesting() { return allowLoopbackHttpForTesting; }
        public void setAllowLoopbackHttpForTesting(boolean value) { this.allowLoopbackHttpForTesting = value; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration value) { this.connectTimeout = value; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration value) { this.readTimeout = value; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration value) { this.requestTimeout = value; }

        @Override
        public String toString() {
            return "S3[endpoint=[redacted], region=" + region
                    + ", bucket=" + bucket + ", prefix=" + prefix
                    + ", pathStyle=" + pathStyle
                    + ", allowLoopbackHttpForTesting=" + allowLoopbackHttpForTesting
                    + ", accessKey=[redacted], secretKey=[redacted]]";
        }
    }
}
