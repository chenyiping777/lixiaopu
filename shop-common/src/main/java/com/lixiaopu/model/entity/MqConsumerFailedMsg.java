package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 mq_consumer_failed_msg；业务逻辑按模块逐步实现。 */
@Data
@TableName("mq_consumer_failed_msg")
public class MqConsumerFailedMsg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String msgId;
    private String bizId;
    private String topic;
    private String tag;
    private String body;
    private Integer retryCount;
    private String errorMsg;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

