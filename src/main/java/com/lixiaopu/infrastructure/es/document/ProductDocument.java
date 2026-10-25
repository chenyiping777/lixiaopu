package com.lixiaopu.infrastructure.es.document;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/**
 * 商品索引
 */
@Data
@FieldNameConstants
public class ProductDocument {
    /** 查询返回的实际排序值，仅用于构造下一页游标，不写入 _source。 */
    @JsonIgnore//接口返回给前端时，这个字段不会出现在响应体里
    private List<FieldValue> sortValues;
    //`sortValues` 就是上一条记录的排序值，传给下一次搜索的 `searchAfter`，实现游标分页。

/**
 * 数据库 Product → ProductDocument → 写入 ES
 * ES 查询 → ProductDocument → SimpleProductVO → 返回前端
* */
    @NotNull(message = "商品 ID 不能为空")
    private Long id;

    @NotNull(message = "分类 ID 不能为空")
    private Long categoryId;

    @NotNull(message = "商品名称不能为空")
    private String name;

    @NotNull(message = "商品封面图 URL 不能为空")
    private String image;

    @NotNull(message = "商品卖点不能为空")
    private String sellPoint;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    @NotNull(message = "商品状态不能为空")
    private Integer status;

    /**
     * 浏览量
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    //浏览量是后端数据库 / ES 统计出来的，前端不能自己传值修改，但是查询列表的时候，需要返回给前端展示。
    private Long viewCount = 0L;

    /**
     * 销量
     */
    private Long salesCount = 0L;

    /**
     * 商品创建时间
     */
    @NotNull(message = "商品创建时间不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createTime;

    /**
     * 商品更新时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updateTime;

}
