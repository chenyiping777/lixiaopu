package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.mapper.CartMapper;
import com.lixiaopu.model.entity.Cart;
import com.lixiaopu.service.CartService;
import org.springframework.stereotype.Service;

/**
* @author Mlpnk
* @description 针对表【cart_item(购物车条目，同一用户的同一商品合并数量)】的数据库操作Service实现
* @createDate 2026-09-22 11:16:54
*/
@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart>
    implements CartService {

}




