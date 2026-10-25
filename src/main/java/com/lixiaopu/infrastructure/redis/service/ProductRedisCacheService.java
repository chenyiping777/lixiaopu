package com.lixiaopu.infrastructure.redis.service;

import com.lixiaopu.infrastructure.es.document.ProductDocument;

import java.util.List;

public interface ProductRedisCacheService {
    /** 从 ES 按销量刷新有序 Redis 榜单。 */
    void refreshHotProductCache();

    /**
     * 获取热门商品
     * @return 商品文档列表
     */
    List<ProductDocument> getHotProduct();


    /**
     * 获取es最大商品id
     * @return 最大商品id
     */
    Long getMaxProductId();


    /**
     * 初始化最大商品id es -> redis
     */
    void initMaxProductId();
 }
