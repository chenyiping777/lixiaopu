package com.lixiaopu.infrastructure.rocketmq.seckill;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@org.springframework.context.annotation.Profile("user")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="seckill.mq.enabled", havingValue="true")
public class SeckillConfig {
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer seckillProducer(@Value("${seckill.mq.namesrv:${dev.rocketmq.name-server}}") String namesrv,
            @Value("${seckill.mq.producer-group:lixiaopu-seckill-producer}") String group) {
        DefaultMQProducer producer = new DefaultMQProducer(group);
        producer.setNamesrvAddr(namesrv);
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }
}
