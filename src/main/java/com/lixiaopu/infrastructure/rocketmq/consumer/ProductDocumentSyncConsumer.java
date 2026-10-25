package com.lixiaopu.infrastructure.rocketmq.consumer;

import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.infrastructure.rocketmq.product.ProductSyncService;
import com.lixiaopu.infrastructure.rocketmq.constant.MqProductConstant;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqProductConstant.TOPIC_PRODUCT,
        selectorExpression = MqProductConstant.TAG_PRODUCT_DOCUMENT_SYNC,
        consumerGroup = MqProductConstant.CONSUMER_GROUP_PRODUCT_DOCUMENT_SYNC,
        maxReconsumeTimes = 3)
public class ProductDocumentSyncConsumer implements RocketMQListener<List<ProductDocument>> {
    private final ProductSyncService sync;
    @Override
    public void onMessage(List<ProductDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        // 兼容旧补缺消息，但只使用 ID，避免旧消息覆盖新值或复活已删除商品。
        documents.stream().map(ProductDocument::getId).distinct().forEach(sync::syncProduct);
    }
}
