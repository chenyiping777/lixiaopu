package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lixiaopu.common.constant.DatePatternConstants;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/** 对应 product_spec；业务逻辑按模块逐步实现。 */
@Data
@TableName("product_spec")
public class  ProductSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规格ID（主键）
     */
    @TableId(type = IdType.AUTO) // 对应数据库 auto_increment 自增主键
    private Long id;

    /**
     * 关联商品ID（外键）
     */
    @TableField(value = "product_id")
    private Long productId;

    /**
     * 规格描述（如：1.2m×0.8m/原木色）
     */
    @TableField(value = "spec_text")
    private String specText;

    /**
     * 规格单价
     */
    @TableField(value = "price")
    private BigDecimal price;

    /**
     * 企业批量价格（有值则表示启用）
     */
    @TableField(value = "enterprise_price")
    private BigDecimal enterprisePrice;

    /**
     * 规格库存
     */
    @TableField(value = "stock")
    private Integer stock;

    /**
     * 创建时间
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 插入时自动填充
    private Date createTime;

    /**
     * 更新时间
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 插入和更新时自动填充
    private Date updateTime;
}

