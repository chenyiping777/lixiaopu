package com.lixiaopu.controller;


import com.lixiaopu.common.result.Result;
import com.lixiaopu.model.query.ProductQuery;
import com.lixiaopu.model.vo.PageVO;
import com.lixiaopu.model.vo.ProductVO;
import com.lixiaopu.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;



    // 根据商品id获取商品详情
    @GetMapping("/{id}")
    public Result getProductById(@PathVariable Long id) {

        return  productService.getProductById(id);
    }

    // 获取商品分类下的商品列表
    @PostMapping("/by-category")//分页，多条件查询用Post
    public Result getProductsByCategoryId(@RequestBody ProductQuery productQuery) {
        return productService.getProductsByCategoryId(productQuery);
    }

    // 按照商品关键字搜索商品
    @PostMapping("/search")
    public Result searchProducts(@RequestBody ProductQuery productQuery) {
        return productService.searchProducts(productQuery);
    }
}
