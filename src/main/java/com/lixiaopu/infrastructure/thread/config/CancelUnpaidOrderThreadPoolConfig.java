package com.lixiaopu.infrastructure.thread.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CancelUnpaidOrderThreadPoolConfig {
    @Bean(name = "cancelUnpaidOrderExecutor", destroyMethod = "shutdownNow")
    public ExecutorService cancelUnpaidOrderExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "cancel-unpaid-order");
            thread.setDaemon(true);
            return thread;
        });
    }
}
