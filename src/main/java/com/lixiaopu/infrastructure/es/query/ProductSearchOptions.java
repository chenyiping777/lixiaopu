package com.lixiaopu.infrastructure.es.query;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** ES 查询条件：用具名字段替代长串位置参数。 */
@Getter
@Builder
public class ProductSearchOptions {

    private String keyword;
    private List<Long> categoryIds;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    @Builder.Default
    private boolean activeOnly = true;

    @Builder.Default
    private int pageNo = 1;

    @Builder.Default
    private int pageSize = 10;

    @Builder.Default
    private String sortField = "_score";

    private boolean ascending;

    // 上一页最后一条结果的排序值。
    private String cursorValue;
    private Long cursorId;

    // 用于按 ID 批次遍历，不是 search_after 游标。
    private Long afterId;
}
