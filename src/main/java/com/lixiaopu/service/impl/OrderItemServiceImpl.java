package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.mapper.OrderItemMapper;
import com.lixiaopu.pojo.entity.OrderItem;
import com.lixiaopu.service.OrderItemService;
import org.springframework.stereotype.Service;

/**
* @author Mlpnk
* @description 针对表【order_item(订单明细)】的数据库操作Service实现
* @createDate 2026-09-22 11:17:01
*/
@Service
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem>
    implements OrderItemService{

}




