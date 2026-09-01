package com.unique.unexamine.backgroundjobs.manage;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "unexamine.jobs.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobSchedulingConfiguration {
}
