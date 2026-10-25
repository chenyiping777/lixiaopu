package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 商品事务同步事件。未设置的字段使用数据库默认值。 */
@Data
@TableName("product_sync_outbox")
public class ProductSyncEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
