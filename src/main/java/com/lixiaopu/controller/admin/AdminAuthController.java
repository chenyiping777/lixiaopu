package com.lixiaopu.controller.admin;

import com.lixiaopu.pojo.dto.AuthDtos;
import com.lixiaopu.service.AuthService;
import com.lixiaopu.common.context.AuthContext;

import com.lixiaopu.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    @Autowired
    private  AuthService authService;

    @PostMapping("/login")
    public Result login(@Valid @RequestBody AuthDtos.Login request) {
        return Result.success(authService.login(request,"ADMIN"));
    }
    @PostMapping("/sms")
    public Result sms(@Valid @RequestBody AuthDtos.Sms request) {
        if (!"RESET_PASSWORD".equals(request.getPurpose())) {
            return Result.error("不支持该用途");
        }
        return  authService.sendSms(request.getPhone(),request.getPurpose());
    }
    @PostMapping("/reset/verify")
    public Result verify(@Valid @RequestBody AuthDtos.ResetVerify request) {
        String ticket = authService.verifyReset(request.getPhone(),request.getCode());
        Map<String, Object> result = Map.of("ticket", ticket);
        return Result.success(result);
    }
    @PostMapping("/reset/password")
    public Result reset(@Valid @RequestBody AuthDtos.ResetPassword request) {
        authService.resetPassword(request.getTicket(),request.getPassword());
        return Result.success();
    }
    @PostMapping("/change-password")
    public Result change(@Valid @RequestBody AuthDtos.ChangePassword request) {
        long uid = AuthContext.get().getUid();
        authService.changePassword(uid, request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }
    @PostMapping("/refresh")
    public Result refresh(@Valid @RequestBody AuthDtos.Refresh request) {
       return authService.refresh(request.getRefreshToken(),"ADMIN");

    }
    @PostMapping("/logout")
    public Result logout() {
        authService.logout();return Result.success();
    }
}
