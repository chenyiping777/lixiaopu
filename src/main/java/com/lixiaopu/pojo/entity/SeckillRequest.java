package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

@Data
@TableName("seckill_request")
public class SeckillRequest {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private String state;
    private String reason;
    private Integer attempts;
    
    private LocalDateTime sentAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime nextAttemptAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
