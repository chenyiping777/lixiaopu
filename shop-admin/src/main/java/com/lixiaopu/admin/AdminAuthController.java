package com.lixiaopu.admin;

import com.lixiaopu.common.auth.dto.AuthDtos;
import com.lixiaopu.common.auth.service.AuthService;
import com.lixiaopu.common.auth.session.AuthContext;

import com.lixiaopu.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AuthService auth;
    public AdminAuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/login") public Result login(@Valid @RequestBody AuthDtos.Login request) {
        return Result.success(auth.login(request,"ADMIN"));
    }
    @PostMapping("/sms") public Result sms(@Valid @RequestBody AuthDtos.Sms request) {
        if (!"RESET_PASSWORD".equals(request.getPurpose())) return Result.error("不支持该用途");
        String code=auth.sendSms(request.getPhone(),request.getPurpose());
        return Result.success(code==null?null:Map.of("localTestCode",code));
    }
    @PostMapping("/reset/verify") public Result verify(@Valid @RequestBody AuthDtos.ResetVerify request) {
        return Result.success(Map.of("ticket",auth.verifyReset(request.getPhone(),request.getCode())));
    }
    @PostMapping("/reset/password") public Result reset(@Valid @RequestBody AuthDtos.ResetPassword request) {
        auth.resetPassword(request.getTicket(),request.getPassword());return Result.success();
    }
    @PostMapping("/change-password") public Result change(@Valid @RequestBody AuthDtos.ChangePassword request) {
        auth.changePassword(AuthContext.get().getUid(),request.getOldPassword(),request.getNewPassword());return Result.success();
    }
    @PostMapping("/refresh") public Result refresh(@Valid @RequestBody AuthDtos.Refresh request) {
        return Result.success(auth.refresh(request.getRefreshToken(),"ADMIN"));
    }
    @PostMapping("/logout") public Result logout() { auth.logout();return Result.success(); }
}
