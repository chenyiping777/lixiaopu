package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import jakarta.validation.Valid;
import java.util.List;

@Data
public class OrderDTO {
    /** 已支付购券订单的 ID；不传则不用券。 */
    @Positive
    private Long voucherOrderId;
    /** CART：购物车勾选结算；DIRECT：直接购买。旧客户端未传时按直接购买处理。 */
    @NotNull(message = "下单来源不能为空")
    @Pattern(regexp = "CART|DIRECT", message = "下单来源只能为 CART 或 DIRECT")
    private String source = "DIRECT";
    @Pattern(regexp = "[1-9][0-9]*", message = "地址ID必须为正整数")
    private String addressId;
    @Size(max = 255, message = "订单备注不能超过255字")
    private String remark;
    @DecimalMin(value = "0.00", message = "运费不能为负数")
    @Digits(integer = 10, fraction = 2, message = "运费最多10位整数和2位小数")
    private String freight;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<@NotNull(message = "订单明细项不能为空") OrderItemDTO> orderItems;
}
