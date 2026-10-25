package com.lixiaopu.security.handler;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.controller.user.OrderController;
import com.lixiaopu.controller.admin.AdminOrderController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {OrderController.class, AdminOrderController.class})
public class OrderErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result> business(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Result.error(ex.getStatusCode().value(), ex.getReason()));
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Result> invalid(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Result.error(400, ex.getMessage()));
    }
}
