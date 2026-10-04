package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 coupon_user；业务逻辑按模块逐步实现。 */
@Data
@TableName("coupon_user")
public class CouponUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer useStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

