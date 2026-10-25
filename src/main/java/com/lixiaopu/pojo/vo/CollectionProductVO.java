package com.lixiaopu.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectionProductVO {
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
}
