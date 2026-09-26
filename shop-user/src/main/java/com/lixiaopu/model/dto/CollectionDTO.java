package com.lixiaopu.model.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectionDTO {
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
}
