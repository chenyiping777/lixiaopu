package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.model.entity.Product;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.model.query.ProductQuery;
import com.lixiaopu.model.vo.PageVO;
import com.lixiaopu.model.vo.ProductVO;

import java.util.List;

/**
* @author Mlpnk
* @description 针对表【product(商品，学习版每个商品只有一种规格)】的数据库操作Service
* @createDate 2026-09-22 11:17:08
*/
public interface ProductService extends IService<Product> {

    //根据商品id查看商品详情
    Result getProductById(Long id);

    //根据分类id查看某个分类下所有的商品，分页展示
    Result getProductsByCategoryId(ProductQuery productQuery);

    Result searchProducts(ProductQuery productQuery);
}
