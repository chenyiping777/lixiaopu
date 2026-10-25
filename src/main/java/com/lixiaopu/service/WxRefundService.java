package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.wechat.pay.java.service.refund.model.Refund;

/**
 * 微信退款领域服务：围绕订单支付状态的一条状态线
 * PAID -> REFUND_REQUESTED -> REFUNDING -> REFUND_SUCCESS
 *                  \-> REFUND_REJECTED（商家拒绝后可重新申请）
 * 订单业务状态（待发货/待收货/已完成…）不卡在后端，前端按钮按 payStatus 控制显隐。
 */
public interface WxRefundService {

    /** 买家申请退款：PAID / REFUND_REJECTED -> REFUND_REQUESTED */
    Result applyRefund(String orderNo, String reason);

    /** 商家审核退款：REFUND_REQUESTED -> REFUNDING（同意，事务提交后调微信）/ REFUND_REJECTED */
    Result reviewRefund(String orderNo, boolean approved, String reason);

    /** 商家同意后调用微信退款失败，手动重试 */
    Result retryRefund(String orderNo);

    /** 微信退款回调结果落库（仅由 WxPayService 验签后调用，无客户端入口） */
    void recordRefund(Refund refund);
}
