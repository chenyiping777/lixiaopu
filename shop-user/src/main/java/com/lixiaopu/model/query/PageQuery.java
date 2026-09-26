package com.lixiaopu.model.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageQuery {
    //传页码和总页条数
    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNo = 1;
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;
    private String sortBy;//排序字段
    private Boolean asc;//排序升降
}
