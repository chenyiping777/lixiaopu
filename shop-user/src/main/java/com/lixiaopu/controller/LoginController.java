package com.lixiaopu.controller;

import com.lixiaopu.common.auth.dto.AuthDtos;
import com.lixiaopu.common.auth.service.AuthService;
import com.lixiaopu.common.auth.session.AuthContext;

import com.lixiaopu.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class LoginController {
    @Autowired
    private  AuthService authService;

    //发送验证码
    @PostMapping("/sms")
    public Result sms(@Valid @RequestBody AuthDtos.Sms request) {
        String code=authService.sendSms(request.getPhone(),request.getPurpose());
        return Result.success(code==null?null:java.util.Map.of("localTestCode",code));
    }
    //注册
    @PostMapping("/register")
    public Result register(@Valid @RequestBody AuthDtos.Register request) {
        authService.register(request);
        return Result.success();
    }

    //登录
    @PostMapping("/login")
    public Result login(@Valid @RequestBody AuthDtos.Login request) {
        return Result.success(authService.login(request,"BUYER"));
    }

    //微信登录
    @PostMapping("/wechat")
    public Result wechat(@Valid @RequestBody AuthDtos.Wechat request) {
        return Result.success(authService.wechat(request.getCode()));
    }

    //验证重置密码验证码
    @PostMapping("/reset/verify")
    public Result verify(@Valid @RequestBody AuthDtos.ResetVerify request) {
        return Result.success(java.util.Map.of("ticket",authService.verifyReset(request.getPhone(),request.getCode())));
    }
    //重置密码
    @PostMapping("/reset/password")
    public Result reset(@Valid @RequestBody AuthDtos.ResetPassword request) {
        authService.resetPassword(request.getTicket(),request.getPassword());return Result.success();
    }
    //修改密码
    @PostMapping("/change-password")
    public Result change(@Valid @RequestBody AuthDtos.ChangePassword request) {
        authService.changePassword(AuthContext.get().getUid(),request.getOldPassword(),request.getNewPassword());return Result.success();
    }
    //刷新令牌
    @PostMapping("/refresh")
    public Result refresh(@Valid @RequestBody AuthDtos.Refresh request) {
        return Result.success(authService.refresh(request.getRefreshToken(),"BUYER"));
    }

    //退出登录
    @PostMapping("/logout")
    public Result logout() { authService.logout();return Result.success(); }
}
