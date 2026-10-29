package com.lixiaopu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixiaopu.pojo.entity.ChatMessage;
import com.lixiaopu.pojo.vo.ChatSessionVO;
import java.util.List;

public interface ChatService {
    ChatMessage saveAndGetMessage(Long fromUserId, Long toUserId, String content, Integer msgType, Long productId);
    List<ChatSessionVO> getSessionList(Long userId);
    Page<ChatMessage> getChatHistory(Long userId, Long contactId, Integer page, Integer size);
    void clearUnread(Long userId, Long contactId);
}
