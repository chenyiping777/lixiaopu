package com.lixiaopu.security.handler;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.controller.user.ChatController;
import com.lixiaopu.controller.admin.AdminChatController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {ChatController.class, AdminChatController.class})
public class ChatErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Result.error(400, ex.getMessage()));
    }
}
