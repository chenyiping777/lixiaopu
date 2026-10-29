package com.lixiaopu.controller.admin;

import com.lixiaopu.controller.tool.ChatEndpoints;
import com.lixiaopu.properties.ChatProperties;
import com.lixiaopu.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/chat")
public class AdminChatController extends ChatEndpoints {
    public AdminChatController(ChatService chat, ChatProperties properties) { super(chat, properties); }
}
