package com.lixiaopu.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderVO {
    // 订单ID
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long orderId;
    // 订单号
    private String orderNo;
    // 支付金额
    private BigDecimal payAmount;
    private BigDecimal totalGoodsAmount;
    private BigDecimal freight;
    private BigDecimal voucherDiscountAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long voucherOrderId;
    // 支付截止时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime paymentDeadline;

    /** 购物车下单提交后是否完成清理；直接购买为 null。 */
    private Boolean cartCleared;

    /** 零元订单已在创建事务中结算，前端无需调支付接口。 */
    public boolean isPaymentRequired() {
        return payAmount != null && payAmount.signum() > 0;
    }

}
