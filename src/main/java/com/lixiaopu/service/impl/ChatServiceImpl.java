package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixiaopu.mapper.ChatMessageMapper;
import com.lixiaopu.mapper.ChatSessionMapper;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.pojo.entity.ChatMessage;
import com.lixiaopu.pojo.entity.ChatSession;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.vo.ChatSessionVO;
import com.lixiaopu.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 迁移栗子商城的双人会话和 A/B 未读计数模型。 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ChatMessageMapper messages;
    private final ChatSessionMapper sessions;
    private final SysUserMapper users;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage saveAndGetMessage(Long from, Long to, String content, Integer msgType, Long productId) {
        validatePair(from, to);
        if (content == null || content.isBlank() || content.length() > 4000)
            throw new IllegalArgumentException("消息内容须为 1～4000 个字符");
        if (msgType == null || msgType < 0 || msgType > 3)
            throw new IllegalArgumentException("消息类型须为 0～3");
        if (productId != null && productId <= 0) throw new IllegalArgumentException("商品 ID 无效");
        var target = users.selectById(to);
        if (target == null || target.getIsEnable() != CommonStatus.ACTIVE)
            throw new IllegalArgumentException("联系人不存在或已停用");
        long a = Math.min(from, to), b = Math.max(from, to);
        // 先串行更新该会话，再写消息；失败时两者一起回滚，返回后方可推送及 ACK。
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        sessions.recordMessage(a, b, content.substring(0, Math.min(content.length(), 500)), now,
                to == a ? 1 : 0, to == b ? 1 : 0);
        var message = new ChatMessage();
        message.setFromUserId(from); message.setToUserId(to); message.setContent(content);
        message.setMsgType(msgType); message.setProductId(productId); message.setIsRead(0); message.setCreateTime(now);
        if (messages.insert(message) != 1) throw new IllegalStateException("消息保存失败");
        return message;
    }

    @Override public List<ChatSessionVO> getSessionList(Long userId) { return sessions.selectSessionList(userId); }

    @Override
    public Page<ChatMessage> getChatHistory(Long userId, Long contactId, Integer page, Integer size) {
        validatePair(userId, contactId);
        if (page == null || page < 1 || page > 100000 || size == null || size < 1 || size > 100)
            throw new IllegalArgumentException("page 须为 1～100000，size 须为 1～100");
        return messages.selectPage(new Page<>(page, size), Wrappers.<ChatMessage>lambdaQuery()
                .and(q -> q.eq(ChatMessage::getFromUserId, userId).eq(ChatMessage::getToUserId, contactId)
                        .or().eq(ChatMessage::getFromUserId, contactId).eq(ChatMessage::getToUserId, userId))
                .orderByDesc(ChatMessage::getCreateTime).orderByDesc(ChatMessage::getId));
    }

    @Override
    public void clearUnread(Long userId, Long contactId) {
        validatePair(userId, contactId);
        long a = Math.min(userId, contactId), b = Math.max(userId, contactId);
        var update = Wrappers.<ChatSession>lambdaUpdate().eq(ChatSession::getUserId, a).eq(ChatSession::getContactId, b);
        if (userId == a) update.set(ChatSession::getUnreadCountA, 0);
        else update.set(ChatSession::getUnreadCountB, 0);
        sessions.update(null, update);
        // 保留原逻辑：只清当前一侧的会话未读数，不修改 chat_message.is_read。
    }

    private void validatePair(Long from, Long to) {
        if (from == null || to == null || from <= 0 || to <= 0 || from.equals(to))
            throw new IllegalArgumentException("联系人 ID 无效，不能与自己聊天");
    }
}
