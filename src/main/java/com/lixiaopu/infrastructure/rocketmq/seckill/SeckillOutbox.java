package com.lixiaopu.infrastructure.rocketmq.seckill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.mapper.SeckillRequestMapper;
import com.lixiaopu.pojo.entity.SeckillRequest;
import com.lixiaopu.service.VoucherOrderService;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.lixiaopu.common.constant.SeckillState.PENDING;

/** 请求表也是可靠发件箱。终态由消费者推进，未完成记录持续重投。 */
@Component @Slf4j
public class SeckillOutbox {
    private final SeckillRequestMapper requests;
    private final ObjectProvider<DefaultMQProducer> producer;
    private final ObjectProvider<VoucherOrderService> orders;
    private final String topic;
    public SeckillOutbox(SeckillRequestMapper requests,
            @Qualifier("seckillProducer") ObjectProvider<DefaultMQProducer> producer,
            ObjectProvider<VoucherOrderService> orders,
            @Value("${seckill.mq.topic:lixiaopu-seckill}") String topic) {
        this.requests=requests; this.producer=producer; this.orders=orders; this.topic=topic;
    }

    public void publish(SeckillMessage message) {
        // 无 MQ 时，HTTP 只记受理；由用户端后台任务异步落单。
        var sender = producer.getIfAvailable();
        if (sender == null) return;
        try {
            var result = sender.send(new Message(topic, "create", Long.toString(message.getOrderId()),
                    SeckillMessageCodec.encode(message)));
            if (result.getSendStatus() != SendStatus.SEND_OK) throw new IllegalStateException("Broker 未确认消息");
            requests.update(null, Wrappers.<SeckillRequest>lambdaUpdate().eq(SeckillRequest::getId, message.getOrderId())
                    .set(SeckillRequest::getSentAt, LocalDateTime.now()));
        } catch (Exception failure) {
            log.error("秒杀消息待重试，orderId={}", message.getOrderId(), failure);
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally { schedule(message.getOrderId()); }
    }

    public void dispatch() {
        var pending = requests.selectList(Wrappers.<SeckillRequest>lambdaQuery()
                .eq(SeckillRequest::getState, PENDING).le(SeckillRequest::getNextAttemptAt, LocalDateTime.now())
                .orderByAsc(SeckillRequest::getNextAttemptAt).orderByAsc(SeckillRequest::getId).last("LIMIT 100"));
        for (var request : pending) {
            var message = new SeckillMessage(request.getId(), request.getUserId(), request.getVoucherId());
            if (producer.getIfAvailable() != null) { publish(message); continue; }
            try { orders.getObject().consume(message); }
            catch (Exception failure) { log.error("本地秒杀任务待重试，orderId={}", request.getId(), failure); }
            finally { schedule(request.getId()); }
        }
    }

    private void schedule(long id) {
        requests.update(null, Wrappers.<SeckillRequest>lambdaUpdate().eq(SeckillRequest::getId, id)
                .setSql("attempts = attempts + 1")
                .set(SeckillRequest::getNextAttemptAt, LocalDateTime.now().plusSeconds(30)));
    }
}
