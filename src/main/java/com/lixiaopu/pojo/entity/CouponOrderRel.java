package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 coupon_order_rel；业务逻辑按模块逐步实现。 */
@Data
@TableName("coupon_order_rel")
public class CouponOrderRel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long userCouponId;
    private Long activityId;
    private BigDecimal discountAmount;
    private Integer relStatus;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
}

