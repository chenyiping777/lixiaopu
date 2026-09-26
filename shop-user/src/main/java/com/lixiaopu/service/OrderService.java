package com.lixiaopu.service;

import com.lixiaopu.model.entity.Order;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author Mlpnk
* @description 针对表【shop_order(订单，收货信息独立保存，不依赖当前地址)】的数据库操作Service
* @createDate 2026-09-22 11:17:13
*/
public interface OrderService extends IService<Order> {

}
