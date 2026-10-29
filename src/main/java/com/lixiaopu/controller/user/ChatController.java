package com.lixiaopu.controller.user;

import com.lixiaopu.controller.tool.ChatEndpoints;
import com.lixiaopu.properties.ChatProperties;
import com.lixiaopu.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController extends ChatEndpoints {
    public ChatController(ChatService chat, ChatProperties properties) { super(chat, properties); }
}
