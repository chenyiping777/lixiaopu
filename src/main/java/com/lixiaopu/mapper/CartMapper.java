package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.Cart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.CartItem;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
* @author Mlpnk
*/
public interface CartMapper extends BaseMapper<Cart> {
    List<CartItem> getCartList(@Param("userId") Long userId);
}




