package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 product_comment_append；业务逻辑按模块逐步实现。 */
@Data
@TableName("product_comment_append")
public class ProductCommentAppend {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long commentId;
    private Long productId;
    private Long productSpecId;
    private String orderNo;
    private Long userId;
    private String content;
    private String imageUrls;
    private Integer status;
    private LocalDateTime createTime;
}

