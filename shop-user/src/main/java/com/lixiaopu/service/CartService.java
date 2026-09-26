package com.lixiaopu.service;

import com.lixiaopu.model.entity.Cart;
import com.lixiaopu.model.entity.CartItem;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author Mlpnk
* @description 针对表【cart_item(购物车条目，同一用户的同一商品合并数量)】的数据库操作Service
* @createDate 2026-09-22 11:16:54
*/
public interface CartService extends IService<Cart> {

}
