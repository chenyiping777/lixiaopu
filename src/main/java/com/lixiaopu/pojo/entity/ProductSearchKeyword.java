package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.pojo.enums.CommonStatus;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/** 对应 product_search_keyword；业务逻辑按模块逐步实现。 */
@Data
@TableName("product_search_keyword")
public class ProductSearchKeyword implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 搜索关键词，如：短袖T恤、无线耳机
     */
    @TableField("keyword")
    private String keyword;

    /**
     * 是否为热门推荐 0-否 1-是
     */
    @TableField("is_hot")
    private CommonStatus isHot;

    /**
     * 是否展示 0-隐藏 1-展示（违规词可隐藏）
     */
    @TableField("is_show")
    private CommonStatus isShow;

    /**
     * 首次搜索时间【仅创建时赋值，永不修改】
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime createTime;

    /**
     * 更新时间【创建+更新时自动赋值】
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime updateTime;
}

