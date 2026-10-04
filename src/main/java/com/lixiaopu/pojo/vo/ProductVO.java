package com.lixiaopu.pojo.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
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
