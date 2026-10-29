package com.lixiaopu.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 栗子商城聊天消息：0 文本、1 图片、2 商品卡片、3 语音。 */
@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long fromUserId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long toUserId;
    private String content;
    private Integer msgType;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;
    private Integer isRead;
    private LocalDateTime createTime;
}

