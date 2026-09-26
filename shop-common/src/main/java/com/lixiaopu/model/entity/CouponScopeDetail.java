package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 coupon_scope_detail；业务逻辑按模块逐步实现。 */
@Data
@TableName("coupon_scope_detail")
public class CouponScopeDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponId;
    private Integer scopeType;
    private Long targetId;
    private LocalDateTime createTime;
}

