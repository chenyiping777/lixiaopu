package com.lixiaopu.infrastructure.rocketmq.consumer;

import com.lixiaopu.infrastructure.rocketmq.product.ProductSyncOutbox;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@RocketMQMessageListener(topic = "product-topic", selectorExpression = "product-change-sync-tag",
        consumerGroup = "product-change-sync-consumer", maxReconsumeTimes = 3)
public class ProductChangeSyncConsumer implements RocketMQListener<Long> {
    private final ProductSyncOutbox outbox;
    @Override public void onMessage(Long eventId) {
        outbox.consume(eventId);
    }
}
