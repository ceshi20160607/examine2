package com.unique.examine.core.job;

/** Publishes a best-effort wake-up signal after the durable job fact commits. */
public interface JobSignalPublisher {
    void publish(long jobId, String jobType);
}
