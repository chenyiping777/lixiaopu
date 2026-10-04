package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.query.ProductQuery;
import com.lixiaopu.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private  ProductService productService;

    @GetMapping("/{id}")
    public Result getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PostMapping("/by-category")
    public Result getProductsByCategoryId(@RequestBody ProductQuery productQuery) {
        return productService.getProductsByCategoryId(productQuery);
    }

    @PostMapping("/search")
    public Result searchProducts(@RequestBody ProductQuery productQuery) {
        return productService.searchProducts(productQuery);
    }

    @GetMapping("/product/hot")
    public Result getHotProduct(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return productService.getHotProducts(limit);
    }
}
