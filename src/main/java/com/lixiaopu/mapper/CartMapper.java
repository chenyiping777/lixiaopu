package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.Cart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.CartItem;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
* @author Mlpnk
* @description 针对表【cart(栗小铺 cart)】的数据库操作Mapper
* @createDate 2026-09-23 17:02:22
* @Entity com.lixiaopu.pojo.entity.Cart
*/
public interface CartMapper extends BaseMapper<Cart> {
    @Select("""
            SELECT c.id, c.user_id, c.product_id, c.spec_id, c.quantity, c.checked,
                   c.create_time, c.update_time, p.name AS product_name,
                   p.cover_image AS product_image, ps.spec_text, ps.price, ps.stock
            FROM cart c
            LEFT JOIN product p ON p.id = c.product_id
            LEFT JOIN product_spec ps ON ps.id = c.spec_id
            WHERE c.user_id = #{userId}
            """)
    List<CartItem> getCartList(@Param("userId") Long userId);
}




