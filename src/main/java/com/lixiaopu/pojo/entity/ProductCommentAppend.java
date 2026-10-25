package com.lixiaopu.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 栗小铺 product_comment_append
 * @TableName product_comment_append
 */
@TableName(value ="product_comment_append")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class ProductCommentAppend {
    /**
     * 追评唯一ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联原评论ID（对应product_comment.id）
     */
    @TableField(value = "comment_id")
    private Long commentId;

    /**
     * 商品ID（冗余，关联商品表）
     */
    @TableField(value = "product_id")
    private Long productId;

    /**
     * 商品规格ID（冗余）
     */
    @TableField(value = "product_spec_id")
    private Long productSpecId;

    /**
     * 订单单号（冗余，关联订单表）
     */
    @TableField(value = "order_no")
    private String orderNo;

    /**
     * 追评人用户 ID
     */
    @TableField(value = "user_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 追评内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 追评图片，JSON数组格式：["url1","url2"]
     */
    @TableField(value = "image_urls")
    private String imageUrls;

    /**
     * 审核状态：0=待审核，1=已通过，2=已驳回（同主评论表）
     */
    @TableField(value = "status")
    private Integer status = 0;

    /**
     * 追评创建时间
     */
    @TableField(value = "create_time",fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}