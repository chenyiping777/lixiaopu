package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.mapper.OrderMapper;
import com.lixiaopu.pojo.entity.Order;
import com.lixiaopu.service.OrderService;
import org.springframework.stereotype.Service;

/**
* @author Mlpnk
* @description 针对表【shop_order(订单，收货信息独立保存，不依赖当前地址)】的数据库操作Service实现
* @createDate 2026-09-22 11:17:13
*/
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order>
    implements OrderService {

}




