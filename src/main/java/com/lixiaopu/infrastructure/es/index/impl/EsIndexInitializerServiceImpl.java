package com.lixiaopu.infrastructure.es.index.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.util.ObjectBuilder;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.infrastructure.es.index.EsIndexInitializerService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.Function;

/**
 * 初始化 es 索引 , 只为启动类提供服务
 *
 *          项目启动
 *    ↓
 * 获取索引名字 product_index
 *    ↓
 * 问 ES：这个索引存在吗？
 *    ↓
 *  ┌───────────┐
 *  │           │
 * 存在         不存在
 *  │           │
 * 跳过       创建索引
 *              ↓
 *          设置字段类型
 *              ↓
 *         创建 product_index
 */
@Slf4j
@Service
public class EsIndexInitializerServiceImpl implements EsIndexInitializerService {

    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Value("${lixiaopu.elasticsearch.product-index:product_index}")
    private String productIndex;

    /**
     * Spring 容器启动成功之后，业务接口还没对外提供服务之前，只执行这一次。
     * 1. 看 product_index 存不存在
     * 2. 如果存在 → 什么都不做
     * 3. 如果不存在 → 创建 product_index，并规定每个字段是什么类型
     * */
    @Override
    @PostConstruct// Bean 完成依赖注入之后，自动执行这个方法。
    public synchronized void initProductIndex() {
        String indexName = productIndex;
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(e -> e.index(indexName))
                    .value();

            if (exists) {
                //ES里面存在这个索引
                log.info("ES索引[{}]已存在，跳过初始化", indexName);
                return;
            }

            /**
             * 创建一个叫 product_index 的索引；
             *
             *     设置：
             *         分片 = 1    一个大仓库里面划分出来的“存储区域”。
             *         副本 = 0    复制一份索引数据，提高查询速度。
             *
             *     设置字段：
             *         id          -> long
             *         categoryId  -> long
             *         name        -> text
             *         image       -> keyword
             *         sellPoint   -> text
             *         price       -> scaled_float
             *         status      -> integer
             *         viewCount   -> long
             *         salesCount  -> long
             *         createTime  -> date
             *         updateTime  -> date
            * */

            Function<CreateIndexRequest.Builder,
                    ObjectBuilder<CreateIndexRequest>> request =
                    builder -> builder.index(indexName)
                            .settings(IndexSettings.of(s -> s
                                    .numberOfShards("1")// 分片数,只有一个主分片
                                    .numberOfReplicas("0")// 副本数 ,这个主分片不做副本。
                            ))
                            // 设置字段
                            .mappings(m -> m
                                    .properties(ProductDocument.Fields.id, p -> p.long_(l -> l))
                                    .properties(ProductDocument.Fields.categoryId, p -> p.long_(l -> l))
                                    .properties(ProductDocument.Fields.name, p -> p.text(t -> t
                                            .analyzer("ik_max_word")
                                            .searchAnalyzer("ik_smart")
                                            .fields("keyword", k -> k.keyword(kw -> kw.ignoreAbove(256)))
                                    ))
                                    .properties(ProductDocument.Fields.image, p -> p.keyword(k -> k))
                                    .properties(ProductDocument.Fields.sellPoint, p -> p.text(t -> t
                                            .analyzer("ik_max_word")
                                            .searchAnalyzer("ik_smart")
                                    ))
                                    .properties(ProductDocument.Fields.price, p -> p.scaledFloat(f -> f.scalingFactor(100.0)))
                                    // 商品状态：枚举 0/1/2
                                    .properties(ProductDocument.Fields.status, p -> p.integer(i -> i))
                                    // 浏览量：排序、筛选
                                    .properties(ProductDocument.Fields.viewCount, p -> p.long_(l -> l))
                                    // 销量：排序、筛选
                                    .properties(ProductDocument.Fields.salesCount, p -> p.long_(l -> l))
                                    .properties(ProductDocument.Fields.createTime, p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
                                    .properties(ProductDocument.Fields.updateTime, p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
                            );

            try {
                elasticsearchClient.indices().create(request);
            } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException concurrentCreate) {
                // 用户端和管理端可能同时首次启动。
                if (!"resource_already_exists_exception".equals(concurrentCreate.error().type())) throw concurrentCreate;
                log.info("ES索引[{}]已由另一个进程创建", indexName);
            }
            /*
            *1. `.indices()`：拿到索引相关操作的 API 分组（专门管索引：创建、删除、判断是否存在、更新 mapping 等）
            *2. `.create(...)`：索引创建方法，用来向 ES 服务发送创建索引的 HTTP 请求
            *3. `request`：创建索引请求对象（CreateIndexRequest），里面封装了你要创建索引的全部参数：
                 - 索引名称
                 - settings：分片数、副本数、刷新间隔等索引配置
                 - mappings：字段类型定义（mapping，比如字段是 text、keyword、LocalDateTime 等）
            * */
            log.info("ES索引[{}]初始化成功", indexName);

        } catch (IOException e) {
            log.error("ES索引[{}]初始化失败", indexName, e);
            throw new RuntimeException("商品 ES文档初始化异常", e);
        }
    }
}
