package com.lixiaopu.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductVO {

    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private String coverImage;
    private BigDecimal price;
    private Long stock;
    private Integer status;
}
