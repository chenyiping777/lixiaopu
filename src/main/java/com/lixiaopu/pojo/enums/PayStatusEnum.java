package com.lixiaopu.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 支付状态独立于发货、收货等订单履约状态。 */
@Getter
@RequiredArgsConstructor
public enum PayStatusEnum {
    UNPAID(0, "unpaid", "未支付"),
    PAID(1, "paid", "已支付"),
    REFUND_REQUESTED(2, "refundRequested", "申请退款"),
    REFUNDING(3, "refunding", "退款中"),
    REFUND_SUCCESS(4, "refundSuccess", "退款成功"),
    REFUND_REJECTED(5, "refundRejected", "申请失败");

    @EnumValue private final int code;
    @JsonValue private final String value;
    private final String desc;

    public boolean blocksFulfillment() {
        return this == REFUND_REQUESTED || this == REFUNDING || this == REFUND_SUCCESS;
    }
}
