package com.lixiaopu.infrastructure.redis.service.impl;

import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.service.ProductDocumentService;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.infrastructure.redis.properties.RedisCacheCountProperties;
import com.lixiaopu.infrastructure.redis.service.ProductRedisCacheService;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ProductRedisCacheServiceImpl implements ProductRedisCacheService {
    @Autowired
    private ProductDocumentService productDocumentService;
    @Autowired
    private RedisCacheCountProperties redisCacheCountProperties;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolExecutor;
    @Autowired
    private RedissonClient redissonClient;

    // 一个有序快照通过 Redis SET 原子替换，商品与排名永远属于同一轮刷新。
    private String snapshotKey() {
        return RedisKeyGenerator.hotProductKey() + ":snapshot";
    }

    // 获取热榜商品
    @Override
    @SuppressWarnings("unchecked")
    public List<ProductDocument> getHotProduct() {
        List<ProductDocument> snapshot = (List<ProductDocument>) RedisConnector.opsForValue().get(snapshotKey());
        return snapshot == null ? List.of() : snapshot;
    }

    // 刷新热榜商品缓存
    @Override
    public void refreshHotProductCache() {
        // 所有实例的定时和事件刷新使用同一锁，避免旧快照晚写覆盖新榜单。
        var lock = redissonClient.getLock("product:hot:refresh:lock");
        lock.lock();
        try {
        // 从 Elasticsearch 查询指定数量的热门商品
        List<ProductDocument> products =
                productDocumentService.searchLimitHotProductDocument(
                        redisCacheCountProperties.getHotProductCacheSize()
                );

        // 将整个商品列表作为一个 Value 写入 Redis
        RedisConnector.opsForValue().set(
                snapshotKey(),
                new ArrayList<>(products)
        );
        } finally {
            lock.unlock();
        }
    }

    // 获取最大商品ID
    // 最大商品 ID，目前用于商品滚动列表首次查询时，生成一个随机起点，让每次进入页面看到的商品有所变化。
    @Override
    public Long getMaxProductId() {
        Object value = RedisConnector.opsForValue().get(RedisKeyGenerator.maxProductId());
        if (value == null) {
            Long maxId = productDocumentService.getMaxProductDocumentId();
            try {
                threadPoolExecutor.execute(() -> {
                    try {
                        initMaxProductId();
                    } catch (Exception failure) {
                        log.warn("异步补充最大商品 ID 缓存失败", failure);
                    }
                });
            } catch (org.springframework.core.task.TaskRejectedException rejected) {
                log.warn("最大商品 ID 缓存补充任务被拒绝，下次请求或整点刷新重试", rejected);
            }
            return maxId;
        }
        return Long.valueOf(value.toString());
    }

    // 无 TTL；可能陈旧，启动、整点或缓存缺失时刷新。ES 同步延迟也会影响该值。
    @Override
    public void initMaxProductId() {
        RedisConnector.opsForValue().set(RedisKeyGenerator.maxProductId(),
                productDocumentService.getMaxProductDocumentId());
    }
}
