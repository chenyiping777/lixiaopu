package com.lixiaopu.controller.tool;

import com.lixiaopu.common.context.AuthContext;
import com.lixiaopu.common.result.PageResult;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.properties.ChatProperties;
import com.lixiaopu.service.ChatService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** SpringMVC 的 HTTP 接口（跑在 buyer/user 或者 admin Spring 服务里），
 * 给前端页面拉会话列表、聊天历史、获取 ws 连接配置，是普通 HTTP 接口，走 Shiro AuthFilter 鉴权。 */
public abstract class ChatEndpoints {
    private final ChatService chat;
    private final ChatProperties properties;
    protected ChatEndpoints(ChatService chat, ChatProperties properties) { this.chat = chat; this.properties = properties; }

    @GetMapping("/config")
    public Result config() {
        var principal = AuthContext.get();
        return Result.success(Map.of("userId", Long.toString(principal.getUid()), "clientType", principal.getClientType(), "websocketPath", principal.getClientType().getChatPath(),
                "port", properties.getPort(), "publicUrl", properties.getPublicUrl(), "enabled", properties.isEnabled()));
    }
    @GetMapping("/sessions")
    public Result sessions() { return Result.success(chat.getSessionList(AuthContext.get().getUid())); }
    @GetMapping("/history/{contactId}")
    public Result history(@PathVariable Long contactId,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        var history = chat.getChatHistory(AuthContext.get().getUid(), contactId, page, size);
        return Result.success(PageResult.builder().list(history.getRecords()).total(history.getTotal()).pageNum(page).pageSize(size).build());
    }
    @PostMapping("/clearUnread/{contactId}")
    public Result clearUnread(@PathVariable Long contactId) {
        chat.clearUnread(AuthContext.get().getUid(), contactId); return Result.success();
    }
}
