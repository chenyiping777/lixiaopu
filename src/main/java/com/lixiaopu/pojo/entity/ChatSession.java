package com.lixiaopu.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 栗子商城会话：userId 为较小 ID(A)，contactId 为较大 ID(B)。 */
@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long contactId;
    private String lastMsgContent;
    private LocalDateTime lastMsgTime;
    private Integer unreadCountA;
    private Integer unreadCountB;
    private LocalDateTime updateTime;
}

