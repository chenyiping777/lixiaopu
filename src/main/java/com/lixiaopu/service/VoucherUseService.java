package com.lixiaopu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.mapper.VoucherOrderMapper;
import com.lixiaopu.pojo.entity.Order;
import com.lixiaopu.pojo.entity.VoucherOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 券与商品订单共用本地事务；不另加 Redis 锁或异步券状态。 */
@Service
@RequiredArgsConstructor
public class VoucherUseService {
    public static final int UNPAID = 1;
    public static final int PAID = 2;
    /** 超时未支付被定时任务关闭，不可再支付；仅秒杀券回补库存。 */
    public static final int CANCELLED = 3;
    public static final String AVAILABLE = "AVAILABLE";
    public static final String LOCKED = "LOCKED";
    public static final String USED = "USED";
    private final VoucherOrderMapper vouchers;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BigDecimal lock(long voucherId, Order order) {
        LocalDateTime now = LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);
        int changed = vouchers.update(null, Wrappers.<VoucherOrder>lambdaUpdate()
                .eq(VoucherOrder::getId, voucherId).eq(VoucherOrder::getUserId, order.getUserId())
                .eq(VoucherOrder::getStatus, PAID).eq(VoucherOrder::getUseStatus, AVAILABLE)
                .isNull(VoucherOrder::getBoundOrderId)
                .le(VoucherOrder::getValidFrom, now).gt(VoucherOrder::getValidUntil, now)
                .set(VoucherOrder::getUseStatus, LOCKED).set(VoucherOrder::getBoundOrderId, order.getId()));
        if (changed != 1) throw conflict("优惠券不存在、未生效、已过期或已被占用，请重新选择");
        VoucherOrder voucher = vouchers.selectById(voucherId);
        if (voucher.getActualValue() == null || voucher.getActualValue() <= 0)
            throw new IllegalStateException("优惠券面额无效");
        BigDecimal discount = BigDecimal.valueOf(voucher.getActualValue(), 2);
        if (order.getTotalGoodsAmount().compareTo(discount)<0)
            throw conflict("商品金额不足券面额，优惠券不抵运费、不找零");
        return discount;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void consume(Order order, LocalDateTime paidAt) {
        if (order.getVoucherOrderId() == null) return;
        int changed = vouchers.update(null, bound(order).eq(VoucherOrder::getUseStatus, LOCKED)
                .set(VoucherOrder::getUseStatus, USED).set(VoucherOrder::getUseTime, paidAt));
        if (changed != 1) throw new IllegalStateException("订单锁定优惠券状态不一致，不能确认支付");
    }

    /** 调用方仅在订单首次取消/退款成功时调用；过期由 validUntil 计算，不另存状态。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void release(Order order, boolean refunded) {
        if (order.getVoucherOrderId() == null) return;
        int changed = vouchers.update(null, bound(order)
                .eq(VoucherOrder::getUseStatus, refunded ? USED : LOCKED)
                .set(VoucherOrder::getUseStatus, AVAILABLE).set(VoucherOrder::getBoundOrderId, null)
                .set(VoucherOrder::getUseTime, null));
        if (changed != 1) throw new IllegalStateException("订单绑定优惠券状态不一致，不能释放其他订单的券");
    }

    private LambdaUpdateWrapper<VoucherOrder> bound(Order order) {
        return Wrappers.<VoucherOrder>lambdaUpdate().eq(VoucherOrder::getId, order.getVoucherOrderId())
                .eq(VoucherOrder::getUserId, order.getUserId()).eq(VoucherOrder::getStatus, PAID)
                .eq(VoucherOrder::getBoundOrderId, order.getId());
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
