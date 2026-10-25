package com.lixiaopu.service.impl;

import com.lixiaopu.aop.annotation.ParamCheckAnnotation;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.infrastructure.es.query.ProductSearchOptions;
import com.lixiaopu.infrastructure.es.repository.ProductSearchRepository;
import com.lixiaopu.service.ProductDocumentService;
import com.lixiaopu.pojo.enums.ProductSortTypeEnum;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 已有商品业务的文档服务：处理分类和排序含义，直接调用唯一 ES 仓储。 */
@Service
@RequiredArgsConstructor
public class ProductDocumentServiceImpl implements ProductDocumentService {

    @Autowired
    private  ProductSearchRepository productSearchRepository;
    @Autowired
    private com.lixiaopu.service.CategoryService categoryService;

    @Override
    public ProductDocument getProductDocumentById(Long id) {
        return productSearchRepository.getById(id);
    }

    @Override
    public List<ProductDocument> getProductDocumentById(Long id, Long... ids) {
        List<Long> productIds = new ArrayList<>();
        if (id != null) {
            productIds.add(id);
        }
        if (ids != null) {
            productIds.addAll(Arrays.asList(ids));
        }
        return productSearchRepository.getByIdList(productIds);
    }

    @Override
    public List<ProductDocument> getProductDocumentByIdList(List<Long> ids) {
        return productSearchRepository.getByIdList(ids);
    }

    @Override
    public List<ProductDocument> getProductDocumentByProductNameKeyword(String keyword) {
        return getProductDocumentByProductNameKeyword(keyword, 100);
    }

    @Override
    public List<ProductDocument> getProductDocumentByProductNameKeyword(String keyword, Integer limit) {
        if (StringUtils.isBlank(keyword) || limit == null) {
            return Collections.emptyList();
        }
        return productSearchRepository.search(ProductSearchOptions.builder()
                .keyword(keyword)
                .pageSize(limit)
                .build());
    }

    @Override
    public Long getMaxProductDocumentId() {
        var documents = productSearchRepository.search(ProductSearchOptions.builder()
                .activeOnly(true)
                .ascending(false)
                .sortField("id")
                .pageSize(1)
                .build());
        return documents.isEmpty() ? 0L : documents.get(0).getId();
    }

    @Override
    public void saveProductDocument(ProductDocument document) {
        productSearchRepository.save(document);
    }

    @Override
    public void batchSaveProductDocument(List<ProductDocument> documents) {
        productSearchRepository.batchSave(documents);
    }

    @Override
    @ParamCheckAnnotation
    public List<ProductDocument> searchLimitAfterProductId(Integer limit, Long productId) {
        return productSearchRepository.search(ProductSearchOptions.builder()
                .pageSize(limit)
                .sortField("id")
                .ascending(true)
                .afterId(productId)
                .build());
    }

    @Override
    public List<ProductDocument> searchByCursorByCategoryId(
            Integer limit, ProductSortTypeEnum sort, String sortValue,
            Long productId, Long categoryId, Boolean isFirstCategoryId) {
        if (isFirstCategoryId == null) {
            throw new IllegalArgumentException("必须指定是否为一级分类");
        }
        List<Long> categoryIds = resolveCategoryIds(categoryId, isFirstCategoryId);
        return productSearchRepository.search(sortedOptions(limit, sort)
                .categoryIds(categoryIds)
                .cursorValue(sortValue)
                .cursorId(productId)
                .build());
    }

    @Override
    public List<ProductDocument> searchByCursorByName(
            Integer limit, ProductSortTypeEnum sort, String sortValue,
            Long productId, String keyword) {
        return productSearchRepository.search(sortedOptions(limit, sort)
                .keyword(keyword)
                .cursorValue(sortValue)
                .cursorId(productId)
                .build());//最后调用 `.build()`，执行对象构建，返回 `ProductSearchOptions`
    }

    @Override
    public List<ProductDocument> searchLimitHotProductDocument(Integer limit) {
        return productSearchRepository.search(ProductSearchOptions.builder()
                .activeOnly(true)
                .ascending(false)
                .pageSize(limit)
                .sortField("salesCount")
                .build());
    }

    private ProductSearchOptions.ProductSearchOptionsBuilder sortedOptions(
            int limit, ProductSortTypeEnum sort) {
        return ProductSearchOptions.builder()
                .pageSize(limit)
                .sortField(sort.getSortField())//排序字段
                .ascending(sort.getCommonSortTypeEnum().isAsc());//排序规则
    }

    private List<Long> resolveCategoryIds(Long categoryId, boolean firstCategory) {
        return categoryService.resolveProductCategoryIds(categoryId, firstCategory);
    }
}
