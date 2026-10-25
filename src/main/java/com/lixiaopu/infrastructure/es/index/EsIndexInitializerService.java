package com.lixiaopu.infrastructure.es.index;


/**
 * 以下接口为 启动类一次性初始化使用
 */
public interface EsIndexInitializerService {
    /**
     * 初始化商品文档索引
     */
    void initProductIndex();

}
