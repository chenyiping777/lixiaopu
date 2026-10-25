package com.lixiaopu.service.admin;

import com.lixiaopu.infrastructure.es.mapstruct.EsCopyMapper;
import com.lixiaopu.infrastructure.es.repository.ProductSearchRepository;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.pojo.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 分批读取 Product → 统一转换 → 批量写入 ES。 */
@Service
@RequiredArgsConstructor
public class ProductIndexSyncService {

    private static final int BATCH_SIZE = 200;

    private final ProductMapper productMapper;
    private final EsCopyMapper esCopyMapper;
    private final ProductSearchRepository productSearchRepository;

    public long importAll() {
        long afterId = 0;
        long importedCount = 0;

        while (true) {
            List<Product> products = productMapper.selectIndexSyncBatch(afterId, BATCH_SIZE);
            if (products.isEmpty()) {
                return importedCount;
            }

            var documents = products.stream()
                    .map(esCopyMapper::toDocument)
                    .toList();

            productSearchRepository.batchSave(documents);
            importedCount += products.size();
            afterId = products.get(products.size() - 1).getId();
        }
    }
}