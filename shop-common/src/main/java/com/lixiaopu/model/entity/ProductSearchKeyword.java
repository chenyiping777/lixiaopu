package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 product_search_keyword；业务逻辑按模块逐步实现。 */
@Data
@TableName("product_search_keyword")
public class ProductSearchKeyword {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private Integer isHot;
    private Integer isShow;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

