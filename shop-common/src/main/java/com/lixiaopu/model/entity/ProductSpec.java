package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 product_spec；业务逻辑按模块逐步实现。 */
@Data
@TableName("product_spec")
public class ProductSpec {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String specText;
    private BigDecimal price;
    private BigDecimal enterprisePrice;
    private Integer stock;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

