package com.lixiaopu.security.handler;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.controller.user.VoucherController;
import com.lixiaopu.controller.user.VoucherOrderController;
import com.lixiaopu.controller.admin.AdminVoucherController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes={VoucherController.class, VoucherOrderController.class, AdminVoucherController.class})
public class VoucherErrorHandler {
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Result> validation(jakarta.validation.ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(Result.error(400, "请求参数不合法"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result> business(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Result.error(ex.getStatusCode().value(), ex.getReason()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Result.error(400, ex.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result> unavailable(IllegalStateException ex) {
        return ResponseEntity.status(503).body(Result.error(503, ex.getMessage()));
    }
}
