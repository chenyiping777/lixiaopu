package com.lixiaopu.infrastructure.rocketmq.product;

import com.lixiaopu.common.util.BloomFilterUtils;
import com.lixiaopu.infrastructure.es.mapstruct.EsCopyMapper;
import com.lixiaopu.infrastructure.es.repository.ProductSearchRepository;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.infrastructure.redis.service.ProductRedisCacheService;
import com.lixiaopu.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/** 所有增量同步均按 ID 重读数据库，同一商品串行执行，允许安全重试。 */
@Service
@RequiredArgsConstructor
public class ProductSyncService {
    private final ProductMapper productMapper;
    private final ProductSearchRepository productSearchRepository;
    private final EsCopyMapper esCopyMapper;
    private final ProductRedisCacheService productRedisCacheService;
    private final BloomFilterUtils bloomFilterUtils;
    private final RedissonClient redissonClient;

    public void invalidate(Long id) {
        RedisConnector.delete(RedisKeyGenerator.productDetail(id));
        RedisConnector.delete(RedisKeyGenerator.productCollection(id));
    }

    /**
     * 1. outbox 表的 FOR UPDATE 悲观行锁：保护 `product_sync_outbox` 事件记录，防止同一条事件被多个消费线程同时进入 consume 方法。
     * 2. Redisson 分布式锁 product:sync:lock:{id}：保护同一个商品的同步业务逻辑，防止同一个商品多次并发读写 ES、Redis。
     * 为什么两层锁？
     * outbox 行锁只能控制同一条事件；但可能存在多条不同事件，对应同一个商品 id（商品连续多次修改，产生多条 outbox 事件）。
     * 多条不同事件同时触发同步，outbox 行锁互不冲突，会并发进入 syncProduct，这时就需要商品级分布式锁来串行。
     * | 锁 | 保护范围 |
     * |---|---|
     * | `product:sync:lock:商品ID` | 同一个商品的同步流程 |
     * | `product:hot:refresh:lock` | 所有商品共用的热榜刷新流程 |
     * */
    public void syncProduct(Long id) {
        //同一个商品，任何时刻只能有一个线程执行这段同步逻辑
        var lock = redissonClient.getLock("product:sync:lock:" + id);
        lock.lock();
        try {
            invalidate(id);  // 第一次删缓存
            var product = productMapper.selectById(id); // 读MySQL最新商品数据
            if (product == null) {
                productSearchRepository.delete(id); // MySQL商品不存在 → 删除ES文档
            } else {
                bloomFilterUtils.add(id); // 商品id加入布隆过滤器
                productSearchRepository.save(esCopyMapper.toDocument(product)); // 写入ES
            }
            invalidate(id); // 第二次删缓存
            productRedisCacheService.refreshHotProductCache(); // 刷新热点商品全局缓存
            productRedisCacheService.initMaxProductId(); // 更新全局最大商品ID缓存
        } finally {
            lock.unlock();
        }
    }
}
