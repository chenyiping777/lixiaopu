package com.lixiaopu.job.delay;

import com.lixiaopu.service.impl.OrderServiceImpl;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import com.lixiaopu.pojo.entity.Order;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import java.time.LocalDateTime;
import java.time.ZoneId;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CancelUnpaidOrderDelayJob {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    @Qualifier("cancelUnpaidOrderExecutor")
    private Executor threadPool;

    @Autowired
    private ApplicationContext applicationContext;


    private RBlockingQueue<String> blockingQueue;
    private RDelayedQueue<String> delayedQueue;

    private static final String BLOCKING_QUEUE_NAME = "cancelUnpaidOrderBlockingQueue";
    private static final String ORDER_CANCEL_REASON = "订单超时未支付，自动取消";


    @PostConstruct
    private void init() {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(BLOCKING_QUEUE_NAME);
        RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        this.blockingQueue = blockingQueue;
        this.delayedQueue = delayedQueue;
        startConsumer();

    }

    public void setUnpaidOrderNoToDelayQueue(String orderNO, LocalDateTime payExpireTime) {
        log.info("order:{},添加延迟任务成功", orderNO);
        //1. 先删掉队列里已经存在的同一个 orderNo 的延迟任务，防止同一个订单重复投递多条超时任务，避免超时的时候多次触发取消订单。
        delayedQueue.remove(orderNO);
        // 按下单时固化的截止时间计算，配置调整或任务重投不能延长旧订单的有效期。
        long remainingMillis = Math.max(0, java.time.Duration.between(
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")), payExpireTime).toMillis());
        delayedQueue.offer(orderNO, remainingMillis, TimeUnit.MILLISECONDS);

    }

    /** 扫描兜底：进程重启或微信关单暂时失败后，下次扫描继续重试。 */
    @Scheduled(fixedDelayString = "${order.timeout-scan-ms:60000}")
    public void retryExpiredOrders() {
        OrderServiceImpl service = applicationContext.getBean(OrderServiceImpl.class);
        for (Order order : service.lambdaQuery().eq(Order::getStatus, OrderStatusEnum.PENDING_PAYMENT)
                .le(Order::getPayExpireTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai"))).list()) {
            try { service.cancelOrderCommon(order.getOrderNo(), ORDER_CANCEL_REASON); }
            catch (RuntimeException ex) { log.warn("超时订单关单失败，下次扫描重试: {}", order.getOrderNo(), ex); }
        }
    }

    /**
     * 往线程池提交一个异步任务，
     * 在另外一条线程里，无限循环执行 blockingQueue 的阻塞 take 拉取消息；
     * 是异步执行的，不会卡住主线程。
    * */
    private void startConsumer() {
        threadPool.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String orderNo = blockingQueue.take();
                    /*子线程停在这里不动，休眠等待消息
                     - 有消息来了 → take 返回拿到 orderNo，继续往下执行业务
                     - 没有消息 → 一直阻塞在这里*/
                    applicationContext.getBean("orderServiceImpl", OrderServiceImpl.class).cancelOrderCommon(orderNo, ORDER_CANCEL_REASON);

                } catch (InterruptedException e) {
                    log.warn("处理订单支付超时消费者线程被中断，停止运行");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("处理订单支付超时任务时发生异常: ", e);
                }
            }
        });

    }
}
