package com.lixiaopu.pojo.query;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductQuery extends PageQuery {
    //可以查关键词，分类，价格区间
    private Long categoryId;
    private boolean firstCategoryId;
    private String keyword;
    @DecimalMin("0.0")
    private BigDecimal minPrice;
    @DecimalMin("0.0")
    private BigDecimal maxPrice;

    private String sortType;
}
