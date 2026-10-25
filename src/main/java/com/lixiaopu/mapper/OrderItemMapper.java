package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.OrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


public interface OrderItemMapper extends BaseMapper<OrderItem> {
    @Select("SELECT COUNT(*) FROM order_item oi JOIN `order` o ON o.id = oi.order_id " +
            "WHERE oi.product_id = #{productId} AND o.status = 1")
    long countPendingProduct(@Param("productId") Long productId);

    @Select("SELECT COUNT(*) FROM order_item oi JOIN `order` o ON o.id = oi.order_id " +
            "WHERE oi.spec_id = #{specId} AND o.status = 1")
    long countPendingSpec(@Param("specId") Long specId);

    List<OrderItem> selectByOrderIdList(@Param("orderIdList") List<Long> orderIdList);

}




