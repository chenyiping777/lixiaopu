package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.mapper.VoucherOrderMapper;
import com.lixiaopu.pojo.entity.VoucherOrder;
import com.lixiaopu.properties.WxPayProperties;
import com.lixiaopu.service.VoucherUseService;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 购券支付使用 VC 前缀，共用 /api/pay/notify 验签入口，不混用商品订单表。 */
@Service
@RequiredArgsConstructor
public class VoucherPaymentService {
    public static final String PREFIX = "VC";
    private final VoucherOrderMapper orders;
    private final WxPayProperties properties;
    private final ObjectProvider<JsapiServiceExtension> jsapi;

    @Transactional(rollbackFor = Exception.class)
    public PrepayWithRequestPaymentResponse prepay(long orderId) {
        if (!properties.isEnabled()) throw new IllegalStateException("微信支付尚未启用");
        VoucherOrder order = lock(orderId);
        var user = CurrentHolder.getCurrentUser();
        if (user == null || !Objects.equals(user.getId(), order.getUserId()))
            throw new IllegalArgumentException("购券订单不存在或无权操作");
        if (!Objects.equals(order.getStatus(), VoucherUseService.UNPAID)
                || order.getPayExpireTime() == null
                || !order.getPayExpireTime().isAfter(LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID)))
            throw new IllegalArgumentException("购券订单已过期或状态不允许支付");
        if (!StringUtils.hasText(user.getOpenid())) throw new IllegalArgumentException("请先使用微信登录");
        if (order.getPayValue() == null || order.getPayValue() <= 0)
            throw new IllegalStateException("购券金额快照无效");
        PrepayRequest request = new PrepayRequest();
        request.setAppid(properties.getAppId());
        request.setMchid(properties.getMerchantId());
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setOutTradeNo(PREFIX + orderId);
        request.setDescription("栗小铺购券-" + orderId);
        request.setTimeExpire(order.getPayExpireTime().atZone(DatePatternConstants.SYSTEM_ZONE_ID)
                .toOffsetDateTime().toString());
        Amount amount = new Amount();
        amount.setTotal(Math.toIntExact(order.getPayValue()));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(user.getOpenid());
        request.setPayer(payer);
        return jsapi.getObject().prepayWithRequestPayment(request);
    }

    /** 调用方已经验签、核对商户和交易成功状态；本方法只处理购券事务。 */
    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public void accept(Transaction transaction) {
        String no = transaction.getOutTradeNo();
        if (no == null || !no.matches("VC[1-9][0-9]*")) throw new IllegalArgumentException("购券支付单号无效");
        VoucherOrder order = lock(Long.parseLong(no.substring(PREFIX.length())));
        if (!StringUtils.hasText(transaction.getTransactionId()) || transaction.getAmount() == null
                || order.getPayValue() == null
                || !Objects.equals(transaction.getAmount().getTotal(), Math.toIntExact(order.getPayValue()))
                || !"CNY".equals(transaction.getAmount().getCurrency()))
            throw new IllegalArgumentException("购券支付金额、币种或流水不匹配");
        if (order.getTransactionId() != null) {
            if (!Objects.equals(order.getTransactionId(), transaction.getTransactionId())
                    || !Objects.equals(order.getStatus(), VoucherUseService.PAID))
                throw new IllegalStateException("购券支付流水或状态不匹配");
            return;
        }
        if (!Objects.equals(order.getStatus(), VoucherUseService.UNPAID)
                || order.getActualValue() == null || order.getActualValue() <= 0
                || order.getValidityDays() == null || order.getValidityDays() <= 0)
            throw new IllegalStateException("购券订单状态或权益快照无效");
        LocalDateTime paidAt = OffsetDateTime.parse(transaction.getSuccessTime())
                .atZoneSameInstant(DatePatternConstants.SYSTEM_ZONE_ID).toLocalDateTime();
        int changed = orders.update(null, Wrappers.<VoucherOrder>lambdaUpdate()
                .eq(VoucherOrder::getId, order.getId()).eq(VoucherOrder::getStatus, VoucherUseService.UNPAID)
                .isNull(VoucherOrder::getTransactionId)
                .set(VoucherOrder::getStatus, VoucherUseService.PAID)
                .set(VoucherOrder::getTransactionId, transaction.getTransactionId())
                .set(VoucherOrder::getPayTime, paidAt).set(VoucherOrder::getPayType, 1)
                .set(VoucherOrder::getValidFrom, paidAt)
                .set(VoucherOrder::getValidUntil, paidAt.plusDays(order.getValidityDays()))
                .set(VoucherOrder::getUseStatus, VoucherUseService.AVAILABLE));
        if (changed != 1) throw new IllegalStateException("购券支付状态更新失败");
    }

    private VoucherOrder lock(long id) {
        VoucherOrder order = orders.selectOne(Wrappers.<VoucherOrder>lambdaQuery()
                .eq(VoucherOrder::getId, id).last("FOR UPDATE"));
        if (order == null) throw new IllegalArgumentException("购券订单不存在");
        return order;
    }
}
