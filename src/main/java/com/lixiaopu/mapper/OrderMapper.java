package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按 Tab 状态集合、ID 游标查询订单主表，不关联明细
     */
    List<Order> getOrderBatch(@Param("userId") Long userId, @Param("statuses") List<Integer> statuses,
                              @Param("beginId") Long beginId, @Param("limit") int limit);


    Order getOrderDesc(String orderNo);

    Order getOrderLogistics(String orderNo);

    List<Order> searchOrderByCondition(String orderNo, String logisticsNo, String productName, Long userId);



}



