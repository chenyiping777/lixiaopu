package com.lixiaopu.pojo.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
public class OrderItemDTO {
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须为正整数")
    private Long productId;
    @NotNull(message = "规格ID不能为空")
    @Positive(message = "规格ID必须为正整数")
    private Long specId;
    @NotNull(message = "购买数量不能为空")
    @Positive(message = "购买数量必须大于0")
    private Integer quantity;
    private String price;
    private String productName;
    private String productImage;
    private String specText;

}
