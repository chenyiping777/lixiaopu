package com.lixiaopu.service;

import com.lixiaopu.common.result.CursorCommonEntity;
import com.lixiaopu.common.result.CursorCommonResult;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.SimpleCursorCommonResult;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.vo.SimpleProductVO;
import com.baomidou.mybatisplus.spring.service.IService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
* @author Mlpnk
* @description 针对表【product(商品，学习版每个商品只有一种规格)】的数据库操作Service
* @createDate 2026-09-22 11:17:08
*/
public interface ProductService extends IService<Product> {

    Long createProduct(Product product);

    Long updateProduct(Long id, Product product);

    void deleteProduct(Long id);

    List<ProductDocument> getHotProduct(Integer limit);

    Result getProductDetail(String productId, Long userId);

    CursorCommonResult searchProductList(CursorCommonEntity cursorCommonEntity ,String keyword);

    List<SimpleProductVO> getProductRelated(String productName, Integer limit);


    Result getBriefProduct(String productIds);


    SimpleCursorCommonResult getSimpleProductByScrollQuery(Long beginId , Integer querySize);

    CursorCommonResult getCategorySimpleProduct(@Valid @NotNull CursorCommonEntity cursorCommonEntity , Long categoryId , boolean isFirstCategoryId);

}
