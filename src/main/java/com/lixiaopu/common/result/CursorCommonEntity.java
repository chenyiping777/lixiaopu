package com.lixiaopu.common.result;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
/**
 * 通用游标分页对象，
 * 用来记录“按什么顺序查询、上次查到哪里、这次查多少条”，适合商品列表下拉加载等场景
 * */
public class CursorCommonEntity {

    /**
     * 排序
     */
    @NotNull
    private String sortType;

    /**
     * 末尾查询值
     * 后端查完后，把最后一件商品的价格和 ID 返回给前端；前端下次带上它们，后端就从这个位置继续查。
     */
    private String sortValue ;

    /**
     * 末尾查询 id
     */
    private Long sortId;

    /**
     * 查询数量，对应PageQuery的pageSize
     */
    private Integer querySize = 20;
}
