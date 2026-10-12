package com.handyai.build.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background work for {@code @Async} methods: bookkeeping a request should not wait for.
 *
 * <p>Small on purpose, for a 0.1 CPU host. When the queue is full the caller runs the task itself,
 * so a burst slows a few requests down instead of dropping data or failing them.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String TRACKING_EXECUTOR = "trackingExecutor";

    @Bean(name = TRACKING_EXECUTOR)
    public ThreadPoolTaskExecutor trackingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("handyai-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Finish queued clicks on shutdown rather than losing them.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
