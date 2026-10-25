package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value ="voucher_order")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoucherOrder {
    
    @TableId
    private Long id;

    private Long userId;

    private Long voucherId;

    private Integer payType;

    private Integer status;

    /** 购买金额、面额和使用期限均为抢购时的快照。金额单位：分。 */
    private Long payValue;
    private Long actualValue;
    /** 仅秒杀订单关联活动，普通券为 null；用于保留秒杀一人一单约束。 */
    private Long seckillVoucherId;
    private Integer validityDays;
    private String transactionId;
    private LocalDateTime payExpireTime;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String useStatus;
    private Long boundOrderId;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime useTime;

    private LocalDateTime refundTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
