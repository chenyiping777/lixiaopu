package com.lixiaopu.common.result;

import lombok.Data;

@Data
public class Result {
    private Boolean success;      // 是否成功
    private Integer code;         // 状态码
    private String message;       // 描述信息
    private Object data;          // 数据，把T改成Object

    private Result() {}

    //链式setter
    public Result code(Integer code) {
        this.code = code;
        return this;
    }

    public Result message(String message) {
        this.message = message;
        return this;
    }

    public Result data(Object data) {
        this.data = data;
        return this;
    }

    public Result success(Boolean success) {
        this.success = success;
        return this;
    }

    public static Result success() {
        return build(ResultCode.SUCCESS, true, null);
    }

    public static Result success(Object data) {
        return build(ResultCode.SUCCESS, true, data);
    }

    //1.默认错误--系统异常
    public static Result error() {
        return build(ResultCode.ERROR, false, null);
    }

    //2.只传message，错误码不变，只想改提示信息
    public static Result error(String message) {
        return build(ResultCode.ERROR, false, message, null);
    }

    //3.指定不同的业务状态码
    public static Result error(ResultCode resultCode) {
        return build(resultCode, false, null);
    }

    //4.指定不同的业务状态码和提示信息
    public static Result error(ResultCode resultCode, String message) {
        return build(resultCode, false, message, null);
    }

    private static Result build(ResultCode rc, boolean success, Object data) {
        Result result = new Result();
        result.success = success;
        result.code = rc.getCode();
        result.message = rc.getMessage();
        result.data = data;
        return result;
    }

    private static Result build(ResultCode rc, boolean success, String customMessage, Object data) {
        Result result = new Result();
        result.success = success;
        result.code = rc.getCode();
        result.message = customMessage != null ? customMessage : rc.getMessage();
        result.data = data;
        return result;
    }
}
