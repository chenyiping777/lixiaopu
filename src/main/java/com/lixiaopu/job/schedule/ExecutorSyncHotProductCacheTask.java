package com.lixiaopu.job.schedule;

import com.lixiaopu.infrastructure.redis.service.ProductRedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ExecutorSyncHotProductCacheTask {
    private final ProductRedisCacheService productRedisCacheService;

    //Spring 容器完全就绪、所有 Bean 加载完成后触发
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        refresh();
    }

    // 每12小时执行一次
    // zone 留空使用应用调度默认时区；可通过配置明确指定。
    @Scheduled(cron = "0 0 0/12 * * ?", zone = "${product.hot.schedule-zone:}")
    public void refresh() {
        try {
            productRedisCacheService.refreshHotProductCache();
        } catch (Exception failure) {
            log.error("热门商品榜单刷新失败，保留上一次 Redis 榜单", failure);
        }
    }
}
