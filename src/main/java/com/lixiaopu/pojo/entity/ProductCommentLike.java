package com.lixiaopu.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * 栗小铺 product_comment_like
 * @TableName product_comment_like
 */
@TableName(value ="product_comment_like")
@Data
public class ProductCommentLike {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("comment_id")
    private Long commentId;

    @TableField("user_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    @TableField("status")
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}