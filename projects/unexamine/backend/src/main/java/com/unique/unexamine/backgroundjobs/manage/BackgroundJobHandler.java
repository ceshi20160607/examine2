package com.unique.unexamine.backgroundjobs.manage;

import java.util.Map;
import java.util.Set;

public interface BackgroundJobHandler {
    Set<String> jobTypes();

    String name(String jobType);

    String description(String jobType);

    long estimateTotal(String jobType, Map<String, Object> parameters);

    Map<String, Object> execute(String jobType, Map<String, Object> parameters,
                                BackgroundJobExecution execution) throws Exception;
}
