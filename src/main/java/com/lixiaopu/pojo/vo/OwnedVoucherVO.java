package com.lixiaopu.pojo.vo;

import com.lixiaopu.pojo.entity.VoucherOrder;
import java.time.LocalDateTime;

/** 不向客户端暴露微信交易流水；长 ID 使用字符串。 */
public record OwnedVoucherVO(String id, String voucherId, Long payValue, Long actualValue,
        String state, LocalDateTime validFrom, LocalDateTime validUntil, LocalDateTime payExpireTime) {
    public static OwnedVoucherVO from(VoucherOrder order, LocalDateTime now) {
        String state = Integer.valueOf(2).equals(order.getStatus()) ? order.getUseStatus()
                : Integer.valueOf(1).equals(order.getStatus()) ? "UNPAID"
                : Integer.valueOf(3).equals(order.getStatus()) ? "CANCELLED" : "UNAVAILABLE";
        if ("AVAILABLE".equals(state) && order.getValidUntil() != null && !order.getValidUntil().isAfter(now))
            state = "EXPIRED";
        if ("UNPAID".equals(state) && order.getPayExpireTime() != null && !order.getPayExpireTime().isAfter(now))
            state = "PAY_EXPIRED";
        return new OwnedVoucherVO(order.getId().toString(), order.getVoucherId().toString(),
                order.getPayValue(), order.getActualValue(), state, order.getValidFrom(),
                order.getValidUntil(), order.getPayExpireTime());
    }
}
