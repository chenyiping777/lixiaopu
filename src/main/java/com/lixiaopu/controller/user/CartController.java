package com.lixiaopu.controller.user;

import com.lixiaopu.aop.annotation.SaveCartRedisCacheToMysqlAnnotation;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.CartDTO;
import com.lixiaopu.pojo.dto.CartProductDTO;
import com.lixiaopu.service.CartService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/cart")
@RestController
public class CartController {
    @Autowired
    private CartService cartService;

    /**
     * 获取购物车列表
     */
    @GetMapping("/list")
    public Result getCartList() {

        return cartService.getCartList();
    }


    /**
     * 购物车操作（添加商品、清空、删除购物车商品、合并购物车）先操作 Redis，
     * 不立刻同步 MySQL；Service 执行成功之后，AOP 切面捕获成功返回结果，往延迟队列丢一个用户 ID，
     * 过一段时间再异步把该用户 Redis 里的购物车数据批量写入数据库。
     * Service 方法上的注解给 AOP 做切点标记。
     * 添加商品到购物车
     */
    @PostMapping("/add")
    public Result addProductToCart(@RequestBody @Valid CartProductDTO cartProductDTO) {
        return cartService.addProductToCart(cartProductDTO);
    }

    /**
     * 清空购物车
     *冗余接口(暂定)
     */
    @DeleteMapping("/clear")
    public Result clearCart() {
        return cartService.clearCart();
    }

    /**
     * 批量删除购物车商品(单个+批量)
     * DELETE /api/cart/products?productIds=100&specIds=201
     * 批量删除：
     * DELETE /api/cart/products?productIds=100,101&specIds=201,203
     */
    @DeleteMapping("/products")
    public Result deleteCartProduct(@RequestParam("productIds") String productIds,
                                    @RequestParam("specIds") String specIds) {
        return cartService.deleteCartProduct(productIds, specIds);
    }

    /**
     * 将前端的购物车数据(List)更新到数据库
     * 修改购物车内容，点击保存触发
     *
     * @param cartDTO
     * @return
     */
    @PutMapping("/update")
    public Result mergeCart(@RequestBody @Valid CartDTO cartDTO) {
        return cartService.mergeCart(cartDTO);
    }

}
