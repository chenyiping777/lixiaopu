package com.lixiaopu.infrastructure.rocketmq.seckill;

import com.lixiaopu.service.VoucherOrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("user")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="seckill.mq.enabled", havingValue="true")
public class SeckillMessaging {
    private static final Logger log = LoggerFactory.getLogger(SeckillMessaging.class);
    private final VoucherOrderService orders;
    private final DefaultMQPushConsumer consumer;

    public SeckillMessaging(VoucherOrderService orders,
            @Value("${seckill.mq.topic:lixiaopu-seckill}") String topic, @Value("${seckill.mq.namesrv:${dev.rocketmq.name-server}}") String namesrv,
            @Value("${seckill.mq.consumer-group:lixiaopu-seckill-consumer}") String group,
            @Value("${seckill.mq.consumer-min-threads:4}") int minThreads,
            @Value("${seckill.mq.consumer-max-threads:16}") int maxThreads,
            @Value("${seckill.mq.max-retries:16}") int maxRetries) throws Exception {
        this.orders = orders;
        consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(namesrv);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(minThreads);
        consumer.setConsumeThreadMax(maxThreads);
        consumer.setMaxReconsumeTimes(maxRetries);
        consumer.subscribe(topic, "create");
    }
    @PostConstruct
    public void start() throws Exception {
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (var message : messages) {
                var result = handle(message);
                if (result == ConsumeConcurrentlyStatus.RECONSUME_LATER) return result;
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    public ConsumeConcurrentlyStatus handle(MessageExt message) {
        try {
            orders.consume(SeckillMessageCodec.decode(message.getBody()));
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception failure) {
            log.error("消费失败，等待 RocketMQ 重试，messageId={}", message.getMsgId(), failure);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    @PreDestroy public void stop() { consumer.shutdown();}

}
