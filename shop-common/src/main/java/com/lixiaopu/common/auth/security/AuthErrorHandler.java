package com.lixiaopu.common.auth.security;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthErrorHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Result> auth(AuthException ex) {
        int status=switch (ex.getCode()) {
            case FORBIDDEN -> 403;
            case UNAUTHORIZED,NO_TOKEN,ACCESS_TOKEN_EXPIRED,REFRESH_TOKEN_EXPIRED,LOGIN_ERROR -> 401;
            default -> 400;
        };
        return ResponseEntity.status(status).body(Result.error(ex.getCode(),ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> invalid() {
        return ResponseEntity.badRequest().body(Result.error(ResultCode.VALIDATE_FAILED));
    }
}
