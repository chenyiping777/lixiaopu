package com.lixiaopu.controller.user;

import com.lixiaopu.pojo.dto.AuthDtos;
import com.lixiaopu.service.AuthService;
import com.lixiaopu.common.context.AuthContext;
import com.lixiaopu.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class LoginController {
    @Autowired
    private  AuthService authService;

    /**
     * 1. 前端输入手机号 + 短信验证码，调用这个接口
     * 2. `sendSms` 发送短信验证码
     * 3. 返回结果
     **/
    //发送验证码
    @PostMapping("/sms")
    public Result sms(@Valid @RequestBody AuthDtos.Sms request) {
        return authService.sendSms(request.getPhone(),request.getPurpose());

    }
    /**
     * 1. 前端输入用户名 + 密码 + 验证码，调用这个接口
     * 2. `register` 注册用户
     * 3. 返回结果
     **/
    //注册
    @PostMapping("/register")
    public Result register(@Valid @RequestBody AuthDtos.Register request) {
        //传入用户名，手机号，密码，验证码
        return authService.register(request);
    }
    /**
     * 1. 前端输入手机号 + 密码，调用这个接口
     * 2. `login` 登录
     * 3. 返回结果
     **/
    //登录
    @PostMapping("/login")
    public Result login(@Valid @RequestBody AuthDtos.Login request) {
        return authService.login(request,"BUYER");
    }
    /**
     * 1. 前端输入微信 code，调用这个接口
     * 2. `wechat` 登录
     * 3. 返回结果
     **/

    //微信登录
    @PostMapping("/wechat")
    public Result wechat(@Valid @RequestBody AuthDtos.Wechat request) {
        return Result.success(authService.wechat(request.getCode()));
    }


    /**
     * 忘记密码
     * 1. 前端输入手机号 + 短信验证码，调用这个接口
     * 2. `verifyReset` 校验手机号 + 短信验证码是否正确
     * 3. 校验成功 → 生成一次性临时 ticket（短时有效）
     * 4. 返回给前端这个 ticket
     * 5. 前端拿着 ticket + 新密码 调用下一个接口修改密码
     * */
    @PostMapping("/reset/verify")
    public Result verify(@Valid @RequestBody AuthDtos.ResetVerify request) {
        //`Map.of` 创建一个只有一个键值对的 Map, 超过 10 个键值对：用 Map.ofEntries
        // Map<String, Object> map = Map.ofEntries(
        //        Map.entry("ticket", "abc123"),
        //        Map.entry("phone", "13800138000"),
        //        Map.entry("name", "test"),
        //        Map.entry("uid", 10001L)
        //        // 可以继续加，没有10条限制
        //);
        Map<String, Object> result = Map.of("ticket",authService.verifyReset(request.getPhone(),request.getCode()));
        return Result.success(result);
    }

    /**
     * 1. 前端输入 ticket + 新密码，调用这个接口
     * 2. `resetPassword` 校验 ticket 是否正确，并修改密码
     * 3. 返回结果
     * */
    @PostMapping("/reset/password")
    public Result reset(@Valid @RequestBody AuthDtos.ResetPassword request) {

        return authService.resetPassword(request.getTicket(),request.getPassword());
    }

    /**
     * 修改密码
     * 1. 前端输入 旧密码 + 新密码，调用这个接口
     * 2. `changePassword` 校验旧密码是否正确，并修改密码
     * 3. 返回结果
     *      */
    @PostMapping("/change-password")
    public Result change(@Valid @RequestBody AuthDtos.ChangePassword request) {
        return authService.changePassword(
                AuthContext.get().getUid(),
                request.getOldPassword(),
                request.getNewPassword());

    }

    /**
     * 1. 前端输入 refreshToken，调用这个接口
     * 2. `refresh` 校验 refreshToken 是否正确，并生成新的 accessToken 和 refreshToken
     * 3. 返回结果
     *      */
    @PostMapping("/refresh")
    public Result refresh(@Valid @RequestBody AuthDtos.Refresh request) {
        return authService.refresh(request.getRefreshToken(),"BUYER");
    }

    /**
     * 1. 前端调用这个接口
     * 2. `logout` 删除用户会话
     * 3. 返回结果
     *      */
    //退出登录
    @PostMapping("/logout")
    public Result logout() {

        return  authService.logout();
    }
}
