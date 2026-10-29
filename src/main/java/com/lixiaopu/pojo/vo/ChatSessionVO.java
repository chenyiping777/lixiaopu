package com.lixiaopu.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatSessionVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long contactId;
    private String contactNickname;
    private String contactAvatar;
    private String lastMsgContent;
    private LocalDateTime lastMsgTime;
    private Integer unreadCount;
}
