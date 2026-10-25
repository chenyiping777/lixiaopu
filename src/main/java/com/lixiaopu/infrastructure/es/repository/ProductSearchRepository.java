package com.lixiaopu.infrastructure.es.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.infrastructure.es.query.ProductSearchOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 唯一的 ES 仓储：发送读写请求，检查执行结果，将 _source 转回文档。 */
@Repository
public class ProductSearchRepository {

    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;
    private final ProductSearchRequestFactory requestFactory = new ProductSearchRequestFactory();

    public ProductSearchRepository(
            ElasticsearchClient elasticsearchClient,
            @Value("${lixiaopu.elasticsearch.product-index:product_index}") String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
    }

    /** 按 ES _id 实时读取，文档不存在时返回 null。 */
    public ProductDocument getById(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return elasticsearchClient.get(request -> request
                    .index(indexName)
                    .id(id.toString()), ProductDocument.class).source();
        } catch (IOException exception) {
            throw new UncheckedIOException("读取商品 ES 文档失败", exception);
        }
    }

    public List<ProductDocument> getByIdList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> documentIds = ids.stream().map(Object::toString).toList();
            var response = elasticsearchClient.mget(request -> request
                    .index(indexName)
                    .ids(documentIds), ProductDocument.class);

            List<ProductDocument> documents = new ArrayList<>();
            for (var item : response.docs()) {
                if (item.isFailure()) {
                    throw new IllegalStateException("批量读取失败: " + item.failure().error().reason());
                }
                if (item.result().found() && item.result().source() != null) {
                    documents.add(item.result().source());
                }
            }
            return documents;
        } catch (IOException exception) {
            throw new UncheckedIOException("批量读取商品 ES 文档失败", exception);
        }
    }

    public void save(ProductDocument document) {
        Objects.requireNonNull(document, "商品文档必须存在");
        batchSave(List.of(document));
    }

    /** 固定 _id 覆盖写入；等待搜索可见，并逐项检查 Bulk 失败。 */
    public void batchSave(List<ProductDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        BulkRequest.Builder request = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        for (ProductDocument document : documents) {
            if (document == null || document.getId() == null) {
                throw new IllegalArgumentException("商品 ID 必须存在");
            }
            request.operations(operation -> operation.index(index -> index
                    .index(indexName)
                    .id(document.getId().toString())
                    .document(document)));
        }
        try {
            var response = elasticsearchClient.bulk(request.build());
            if (response.errors()) {
                var failures = response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.id() + ": " + item.error().reason())
                        .toList();
                throw new IllegalStateException("商品批量写入部分失败: " + failures);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("写入商品 ES 文档失败", exception);
        }
    }

    public void delete(Long id) {
        Objects.requireNonNull(id, "商品 ID 必须存在");
        try {
            elasticsearchClient.delete(request -> request
                    .index(indexName)
                    .id(id.toString())
                    .refresh(Refresh.WaitFor));
        } catch (IOException exception) {
            throw new UncheckedIOException("删除商品 ES 文档失败", exception);
        }
    }

    public record Page(long total, List<ProductDocument> list) {
    }

    /** 页码搜索，额外返回准确的匹配总数。 */
    public Page searchPage(ProductSearchOptions options) {
        // 1. 请工厂构造请求，相当于写好 Console 查询。
        SearchRequest request = requestFactory.createPageRequest(indexName, options);
        // 2. 发送给 ES，相当于点击执行按钮。
        SearchResponse<ProductDocument> response = executeSearch(request);
        // 3. 提取总数和商品，交给上层。
        return new Page(response.hits().total().value(), extractDocuments(response));
    }

    /** 首次列表查询或 search_after 查询。 */
    public List<ProductDocument> search(ProductSearchOptions options) {
        // 1. 请工厂构造请求，相当于写好 Console 查询。
        SearchRequest request = requestFactory.createCursorRequest(indexName, options);
        // 2. 发送给 ES，相当于点击执行按钮。
        SearchResponse<ProductDocument> response = executeSearch(request);
        // 3. 提取商品，交给上层。
        return extractDocuments(response);
    }

    private SearchResponse<ProductDocument> executeSearch(SearchRequest request) {
        try {
            var response = elasticsearchClient.search(request, ProductDocument.class);
            if (response.timedOut() || response.shards().failed().intValue() > 0) {
                throw new IllegalStateException("商品搜索未完整完成");
            }
            return response;
        } catch (IOException exception) {
            throw new UncheckedIOException("商品搜索失败", exception);
        }
    }

    private List<ProductDocument> extractDocuments(SearchResponse<ProductDocument> response) {
        List<ProductDocument> documents = new ArrayList<>();
        for (var hit : response.hits().hits()) {
            ProductDocument document = hit.source();
            if (document != null) {
                document.setSortValues(hit.sort());
                documents.add(document);
            }
        }
        return documents;
    }
}