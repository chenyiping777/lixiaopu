package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 chat_session；业务逻辑按模块逐步实现。 */
@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long contactId;
    private String lastMsgContent;
    private LocalDateTime lastMsgTime;
    private Integer unreadCountA;
    private Integer unreadCountB;
    private LocalDateTime updateTime;
}

