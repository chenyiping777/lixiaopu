package com.lixiaopu.pojo.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductQuery extends PageQuery {
    private Long categoryId;
    private String keyword;
}
