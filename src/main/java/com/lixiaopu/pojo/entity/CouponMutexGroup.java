package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 coupon_mutex_group；业务逻辑按模块逐步实现。 */
@Data
@TableName("coupon_mutex_group")
public class CouponMutexGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupName;
    private Long groupCode;
    private String remark;
    private LocalDateTime createTime;
}

