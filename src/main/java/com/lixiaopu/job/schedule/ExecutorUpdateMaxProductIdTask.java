package com.lixiaopu.job.schedule;

import com.lixiaopu.infrastructure.redis.service.ProductRedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 最大 ID 是随机起点参考值，不用于分配主键，也不是查询上限。 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ExecutorUpdateMaxProductIdTask {
    private final ProductRedisCacheService productRedisCacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        refresh();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "${product.max-id.schedule-zone:}")
    public void refresh() {
        try {
            productRedisCacheService.initMaxProductId();
        } catch (Exception failure) {
            log.error("最大商品 ID 刷新失败，保留原缓存，下次整点重试", failure);
        }
    }
}
