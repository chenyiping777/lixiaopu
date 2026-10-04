package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 order_tracking；业务逻辑按模块逐步实现。 */
@Data
@TableName("order_tracking")
public class OrderTracking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String logisticsNo;
    private Long orderId;
    private Integer logisticsStatus;
    private String location;
    private String description;
    private LocalDateTime createTime;
}

