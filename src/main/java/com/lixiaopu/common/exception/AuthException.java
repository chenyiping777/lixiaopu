package com.lixiaopu.common.exception;

import com.lixiaopu.common.result.ResultCode;

public class AuthException extends RuntimeException {
    private final ResultCode code;
    public AuthException(ResultCode code) { super(code.getMessage()); this.code=code; }
    public AuthException(ResultCode code, String message) { super(message); this.code=code; }
    public ResultCode getCode() { return code; }
}
