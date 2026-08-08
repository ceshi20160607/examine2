package com.unique.examine.web;

import com.unique.examine.core.job.JobSignalPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisJobSignalPublisher implements JobSignalPublisher {
    public static final String STREAM_KEY = "examine:jobs:v1";

    private static final Logger log = LoggerFactory.getLogger(RedisJobSignalPublisher.class);
    private final StringRedisTemplate redis;

    public RedisJobSignalPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void publish(long jobId, String jobType) {
        try {
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofMap(Map.of("jobId", Long.toString(jobId), "jobType", jobType))
                    .withStreamKey(STREAM_KEY);
            redis.opsForStream().add(record);
        } catch (RuntimeException exception) {
            // The database is authoritative; the recovery scanner will still claim it.
            log.warn("Unable to publish durable job wake-up signal jobId={} type={}", jobId, jobType, exception);
        }
    }
}
