package com.lixiaopu.controller.user;


import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.query.ProductQuery;
import com.lixiaopu.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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


    /**
     * 获取热门商品
     */
    @GetMapping("/product/hot")
    public Result getHotProduct(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return productService.getHotProducts(limit);
    }
}
