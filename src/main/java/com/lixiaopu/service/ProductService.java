package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.entity.Product;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.pojo.query.ProductQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author Mlpnk
* @description 针对表【product(商品，学习版每个商品只有一种规格)】的数据库操作Service
* @createDate 2026-09-22 11:17:08
*/
public interface ProductService extends IService<Product> {

    List<ProductDocument> getHotProduct(Integer limit);

    Result getProductDetail(String productId, String userId);

    CursorCommonResult searchProductList(CursorCommonEntity cursorCommonEntity ,String keyword);

    List<ProductDocument> getProductRelated(String productName, Integer limit);

    Result getProductSpecPrice(String productId, String specId);

    Result<List<SimpleProductVO>> getBriefProduct(String productIds);

    Result getCategoryProductList(@NotBlank String categoryId, String beginProductId, String sortType);

    SimpleCursorCommonResult getSimpleProductByScrollQuery(Long beginId , Integer querySize);

    Map<Long, Product> getProductDetailByProductIdSet(Set<Long> productIdSet);

    CursorCommonResult getCategorySimpleProduct(@Valid @NotNull CursorCommonEntity cursorCommonEntity , Long categoryId , boolean isFirstCategoryId);

}
