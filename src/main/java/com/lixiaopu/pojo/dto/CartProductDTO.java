package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartProductDTO {
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]*")
    private String productId;// 商品ID
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]*")
    private String specId;// 规格ID
    @NotNull
    @Min(1)
    private Integer quantity;// 数量
}
