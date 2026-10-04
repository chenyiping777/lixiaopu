package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 product_comment；业务逻辑按模块逐步实现。 */
@Data
@TableName("product_comment")
public class ProductComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long productSpecId;
    private String productSpecText;
    private String orderNo;
    private Long userId;
    private String userNickname;
    private String userAvatar;
    private Long parentId;
    private Long replyUserId;
    private Integer isBuyer;
    private Integer isAppendComment;
    private Integer isAnonymous;
    private Integer isGoodReview;
    private String replyUserNickname;
    private Integer rating;
    private String content;
    private String imageUrls;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updatedTime;
}

