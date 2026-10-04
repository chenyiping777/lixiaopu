package com.lixiaopu.service;

import com.lixiaopu.pojo.entity.Cart;
import com.lixiaopu.pojo.entity.CartItem;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.CartDTO;
import com.lixiaopu.pojo.dto.CartProductDTO;

/**
* @author Mlpnk
* @description 针对表【cart_item(购物车条目，同一用户的同一商品合并数量)】的数据库操作Service
* @createDate 2026-09-22 11:16:54
*/
public interface CartService extends IService<Cart> {
    Result getCartList();

    Result addProductToCart(CartProductDTO item);

    Result clearCart();

    Result deleteCartProduct(String productIds, String specIds);

    Result mergeCart(CartDTO cart);

    void syncCartToMysql(Long userId);
}
