package com.lixiaopu.job;

import com.lixiaopu.infrastructure.rocketmq.product.ProductSyncOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 商品事件只由用户端周期投递，两端仍可记录事件和处理消费。 */
@Component
@Profile("user")
@RequiredArgsConstructor
public class ProductSyncDispatchJob {
    private final ProductSyncOutbox outbox;

    @Scheduled(fixedDelayString = "${product.sync.poll-ms:5000}")
    public void dispatch() {
        outbox.dispatch();
    }
}
