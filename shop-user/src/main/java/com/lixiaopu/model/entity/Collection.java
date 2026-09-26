package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lixiaopu.common.constant.DatePatternConstants;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 栗小铺 collection
 * @TableName collection
 */
@TableName(value ="collection")
@Data
public class Collection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 收藏ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联用户ID（外键）
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 关联商品ID（外键）
     */
    @TableField(value = "product_id")
    private Long productId;

    /**
     * 收藏时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime createTime;
}