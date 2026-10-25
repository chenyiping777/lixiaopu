package com.lixiaopu.infrastructure.rocketmq.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.beans.factory.ObjectProvider;
import com.lixiaopu.mapper.ProductSyncEventMapper;
import com.lixiaopu.pojo.entity.ProductSyncEvent;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 商品事务内记账；未完成事件重复投递，只有消费执行成功才能标记完成。 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ProductSyncOutbox {
    //Outbox 事务消息模式（发件箱模式），用来解决：数据库事务和 MQ 消息发送的一致性问题。
    //商品主表、图片、规格在事务里提交的时候，不直接发 MQ，而是在同一个数据库事务里，往 `product_sync_outbox`（事件发件箱表）插入一条事件记录。
    //事务提交成功之后，再由定时任务扫描这张 outbox 表，把消息投递到 RocketMQ；消费端处理完成，才把这条事件标记为已完成。
    //就算 MQ 发送失败、服务重启，定时任务会自动重试这条事件。
    public static final String DESTINATION = "product-topic:product-change-sync-tag"; // MQ主题+标签
    private final ProductSyncEventMapper productSyncEventMapper;
    private final ProductSyncService productSyncService;// 商品同步服务（同步/失效缓存）
    private final ObjectProvider<RocketMQTemplate> mq;// MQ生产者，ObjectProvider方便MQ可选（没有MQ时走本地consume）
    private final PlatformTransactionManager transactions; // 事务管理器

    /**
     * 记录商品同步事件。
     * TransactionSynchronizationManager.registerSynchronization()只负责注册；`afterCommit()` 是 Spring 事务框架自动调用
    * */
    public void record(Long productId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            //判断当前线程有没有活跃事务
            throw new IllegalStateException("商品同步事件必须与商品写入处于同一事务");
        }
        var event = new ProductSyncEvent();//就是product_sync_outbox表的实体类
        event.setProductId(productId);
        if (productSyncEventMapper.insert(event) != 1) {
            throw new IllegalStateException("商品同步事件记录失败");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // 商品事务已提交，失效缓存
                    productSyncService.invalidate(productId);
                } catch (Exception failure) {
                    // 商品事务已提交，不能将缓存异常伪装成保存失败；事件会重试清理。
                    log.warn("商品 {} 提交后缓存失效失败，等待事件重试", productId, failure);
                }
            }
        });
    }

    /**
     * 把待处理的事件分发出去。
     * - 查到待处理事件 ID 列表；
     * - 有 MQ：把 eventId 发送给 RocketMQ，分发给消费者；
     * - 无 MQ：直接本地调用 consume，相当于 “就地分发执行”。
     * 所以方法名叫 dispatch，就是事件分发器。
     * */
    //`@Scheduled` 是 Spring 框架启动之后,单独创建的定时任务线程池，和 Tomcat 的 HTTP 线程池互相隔离.
    // 定时任务线程只有 1 个（或者少量几个）用来扫描数据库、做 dispatch 分发；但 dispatch 只负责 “发通知”，
    // 真正干活的消费线程是另一套独立线程池，而且多服务实例部署会放大这个现象。
    @Scheduled(fixedDelayString = "${product.sync.poll-ms:5000}")//上一次任务执行完成之后，再等待固定时长，才启动下一次
    //定这个事件分发器的时，每5秒执行一次，每次都去数据库查询待处理事件 ID 列表，并进行处理。
    public void dispatch() {
        //查询 `product_sync_outbox` 表，取出待投递事件 ID 列表。
        var ids = productSyncEventMapper.selectPendingIds();
        //遍历待投递事件 ID 列表。
        for (Long id : ids) {
            try {
                //获取 MQ 生产者，如果不存在则使用本地消费
                var producer = mq.getIfAvailable();
                if (producer == null) {
                    //使用本地消费
                    consume(id);
                } else {
                    //这里发送 MQ 消息，只是把事件 id 投递到 MQ。
                    //`syncSend`：同步发送消息，阻塞等待 RocketMQ broker 返回发送结果；
                    var result = producer.syncSend(DESTINATION, id);
                    if (result.getSendStatus() != SendStatus.SEND_OK) {
                        //如果发送状态不是 SEND_OK，抛出异常，进入 catch 块
                        throw new IllegalStateException("MQ 未确认商品事件写入成功");
                    }
                }
            } catch (Exception failure) {
                log.error("商品同步事件 {} 失败，将自动重试", id, failure);
            } finally {
                //不管成功还是失败，都更新事件的重试信息
                productSyncEventMapper.scheduleNextAttempt(id);
                //1. 尝试次数 `attempts` +1；
                //2. 把下一次可重试时间往后推迟 30 秒。
            }
        }
    }

    /**
     * - 本地模式：`dispatch` 定时任务线程直接调用 consume (eventId)，跳过 MQ 中间件。
     * - MQ 模式：`dispatch` 定时任务发送消息到 RocketMQ，MQ 消费者线程收到消息后再调用 consume (eventId)。
     * 同一个线程
     * → 开启事务 A → 处理事件1 → 提交 A，事务 A 结束
     * → 开启事务 B → 处理事件2 → 提交 B，事务 B 结束
    * */
    public void consume(Long eventId) {
        // 新建一个独立事务，PROPAGATION_REQUIRES_NEW：开启全新事务，挂起外层事务（如果有的话）
        var transaction = new TransactionTemplate(transactions);//编程式事务（不是注解`@Transactional`，代码手动开事务）
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        transaction.executeWithoutResult(status -> {
            // 1. select ... FOR UPDATE 行锁查询这条事件记录
            var productId = productSyncEventMapper.selectPendingProductIdForUpdate(eventId);
            // 如果事件已经完成（completed_at不为null），拿到null，直接返回，什么都不做（幂等）
            if (productId == null) {
                return;
            }
            // 2. 真正的业务：同步商品（删缓存、读MySQL、写ES）
            productSyncService.syncProduct(productId);
            // 3. ✅业务同步成功，标记事件为已完成
            productSyncEventMapper.markCompleted(eventId);
        });
    }
}
