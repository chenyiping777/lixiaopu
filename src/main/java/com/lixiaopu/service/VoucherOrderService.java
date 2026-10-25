package com.lixiaopu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.pojo.entity.VoucherOrder;
import com.lixiaopu.infrastructure.rocketmq.seckill.SeckillMessage;
import com.lixiaopu.pojo.vo.Ticket;

public interface VoucherOrderService extends IService<VoucherOrder> {
    Ticket submit(long voucherId, long userId);
    Ticket result(long orderId, long userId);
    void consume(SeckillMessage message);
    void recoverReservations(long voucherId);
    void rebuildCache(long voucherId);
    /** 扫描已过支付截止时间仍待支付的购券订单，调微信关单并回补秒杀库存。 */
    void scanTimeoutOrders();
}
