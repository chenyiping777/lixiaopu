package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.OrderDTO;
import com.lixiaopu.pojo.dto.ScrollQueryDTO;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import com.lixiaopu.pojo.entity.Order;
import com.baomidou.mybatisplus.spring.service.IService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface OrderService extends IService<Order> {
    /** 事务提交后删除订单详情缓存，失败由 TTL 兜底。 */
    void evictAfterCommit(String orderNo);

    Result updateFulfillmentStatus(String orderNo, Integer status);

    Result insertOrder(@NotNull  OrderDTO orderDTO);

    Result getOrderList(ScrollQueryDTO query);

    /** 管理端按 Tab 查询所有用户的可见订单。 */
    Result getAdminOrderList(ScrollQueryDTO query);

    Result getOrderDesc(@NotNull String orderNo);

    Result cancelOrder(@NotBlank String orderNo,String cancelReason);

    Result confirmOrderReceipt(@NotBlank String orderNo);

    Result deleteOrder(@NotBlank String orderNo);

    Result getOrderFreight(@NotBlank String productIds, @NotBlank String addressId);

    Result getOrderLogistics(@NotBlank String orderNo);

    Result searchOrderByCondition(@NotBlank String SearchCondition);

    Boolean updateOrderStatus(Long orderId, String orderNo, @NotNull OrderStatusEnum orderStatusEnum);
}
