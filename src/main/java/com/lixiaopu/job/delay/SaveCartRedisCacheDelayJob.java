package com.lixiaopu.job.delay;

import com.lixiaopu.infrastructure.redis.properties.RedisCacheTtlProperties;
import com.lixiaopu.service.CartService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
public class SaveCartRedisCacheDelayJob {

    /*
     * 1. 用户调用购物车接口：add /clear/deleteCartProduct /mergeCart
     * 2. Controller 方法打上 `@SaveCartRedisCacheToMysqlAnnotation`
     * 3. 接口执行成功，返回 `Result.success`；AOP 的 `@AfterReturning` 触发
     * 4. AOP 调用 `saveCartRedisCacheDelayJob.setUserIdToDelayedQueue(userId)`
     * 5. Job 内部：删除该用户在延迟队列中已存在的旧任务，重新把 userId 放入延迟队列，重新开始倒计时
     * 6. 👉 如果用户继续操作购物车：重复第 4、5 步，倒计时不断重置
     * 7. 用户不再操作购物车，等待延迟时间到期
     * 8. Redisson 自动把到期的 userId 转移到阻塞队列，消费线程 `take()` 获取 userId
     * 9. 执行 `cartService.syncCartToMysql(userId)`，Redis 购物车数据同步到 MySQL
     * 10. 任务完成，消费线程继续阻塞等待下一个到期任务
     */



    /**
     * 1
     * Job 泛指不在接口主线程同步执行，而是后台异步跑的任务，比如定时任务、延迟任务、异步消费任务。
     * 用户操作购物车（增 / 删 / 改 / 合并）
     * 并且接口成功返回 → AOP 切面把 userId 交给这个 Job → Job先删掉这个用户旧的延迟任务，
     * 再重新放入延迟队列（防抖重置倒计时）。
     * 等到延迟时间到期 → 后台线程取出 userId → 调用 `cartService.syncCartToMysql(userId)`，
     * 把 Redis 里该用户购物车全量写入 MySQL。
     * 2
     * 核心亮点：防抖重置倒计时
     * 用户短时间连续点加入购物车，每次都会移除旧任务，重新计时。
     * 比如延迟 30 分钟，用户每隔 1 分钟加一次商品，倒计时会不断重置；
     * 只有用户停止操作，等待设定的延迟时间后，才会执行一次同步 DB。避免频繁写库。
    * */

    //怎么解决依赖循环，common里的job包里的SaveCartRedisCacheDelayJob依赖 service，
    //common里的aspect文件里又注入了当前这个文件SaveCartRedisCacheDelayJob，也就是common依赖user？
    //可是user本来就依赖common，这不就是依赖循环了吗


    //- `@PostConstruct`：Bean 被 Spring 实例化完成之后自动执行一次。
    // 项目启动时，就初始化队列，并且开启一个后台线程，持续监听任务。
    //- Redisson 的 `RDelayedQueue`
    // 原理：消息先放到延迟队列，到期之后才会转移到 `RBlockingQueue`；
    // `take()` 阻塞拿元素，没有到期任务就卡住等待。

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisCacheTtlProperties redisKeyTtlProperties;

    @Autowired
    private CartService cartService;

    @Autowired
    @Qualifier("saveCartRedisCacheToMysqlThreadPool")
    //`@Qualifier` 的作用：按 Bean 名称精确匹配。
    //`"saveCartRedisCacheToMysqlThreadPool"` 就是在配置类里定义线程池 Bean 时写的名字。
    private Executor threadPool;

    private RBlockingQueue<String> blockingQueue;
    private RDelayedQueue<String> delayedQueue;

    private static final String BLOCKING_QUEUE_NAME = "saveCartRedisCacheBlockingQueue";

    @PostConstruct
    //当前这个 Bean 的所有属性全部注入完成之后，自动执行这个 init 方法
    private void init() {
        // 1. 初始化队列
        this.blockingQueue = redissonClient.getBlockingQueue(BLOCKING_QUEUE_NAME);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

        // 2. 启动后台消费线程
        startConsumer();
    }

    /**
     * 将用户ID加入延迟队列
     * 逻辑：如果任务已存在，先移除再添加，实现倒计时重置（防抖）
     */
    public void setUserIdToDelayedQueue(long userId) {
        // 1. 防抖逻辑：移除已存在的旧任务
        delayedQueue.remove(String.valueOf(userId));

        // 2. 计算延迟时间（比缓存过期时间早 1 小时，确保同步时 Redis 还有数据）
        // 假设 redisKeyTtlProperties.getCartTtl() 单位是秒
        long delayInSeconds = redisKeyTtlProperties.getCartTtl() - 3600;

        // 安全检查，如果 TTL 设置过短，至少延迟 10 秒
        if (delayInSeconds < 0) {
            delayInSeconds = 10;
        }

        // 3. 添加新任务
        delayedQueue.offer(String.valueOf(userId), delayInSeconds, TimeUnit.SECONDS);
        log.info("用户 {} 的购物车同步任务已加入延迟队列，将在 {} 秒后执行", userId, delayInSeconds);
    }

    /**
     * 后台消费逻辑
     */
    private void startConsumer() {
        /*
        *把里面的 while 死循环丢到线程池里新开一个独立后台线程跑，
        *不和 Tomcat 接口线程抢资源。这个线程专门阻塞等着延迟队列消息，程序启动后就一直在后台活着。
        * */
        threadPool.execute(() -> {
            log.info("购物车延迟同步消费者已启动...");
            while (!Thread.currentThread().isInterrupted()) {
                //获取当前正在执行这行代码的线程对象,读取这个线程的ya中断标记（中断状态），返回布尔值
                //确保项目停止时，线程能正常退出，不会成为僵尸线程
                try {
                    // take() 是阻塞的，会一直等到有任务到期
                    long userId = Long.parseLong(blockingQueue.take());
                    log.info("收到购物车同步任务，用户ID: {}", userId);

                    // 执行同步逻辑
                    cartService.syncCartToMysql(userId);

                } catch (InterruptedException e) {
                    log.warn("购物车同步消费者线程被中断，停止运行");
                    Thread.currentThread().interrupt();
                    /*
                    * 1. 项目准备关闭，Spring 给这个消费者线程调用 `interrupt()`，打上中断标记 = true
                    * 2. 线程此时大概率卡在 `blockingQueue.take()` 阻塞等待任务
                    * 3. take 检测到中断信号，抛出`InterruptedException`
                    * 4. 进入异常 catch，打印日志，执行 `Thread.currentThread().interrupt()`（恢复中断标记，因为抛出这个异常会自动清空标记），然后 break 跳出 try
                    * 5. 回到 while 判断：`!isInterrupted()` → `!true` → false，循环终止
                    * 6. 线程执行结束，JVM 可以正常退出，不会残留后台线程
                    * */
                    break;
                } catch (Exception e) {
                    log.error("处理购物车同步任务时发生异常: ", e);
                }
            }
        });
    }
}
