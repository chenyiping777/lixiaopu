package com.lixiaopu.model.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ProductQuery extends PageQuery{
    //分类id
    private Long categoryId;

}
