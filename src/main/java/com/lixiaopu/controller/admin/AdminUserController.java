package com.lixiaopu.controller.admin;

import com.lixiaopu.pojo.dto.AuthDtos;
import com.lixiaopu.service.AuthService;
import com.lixiaopu.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AuthService auth;
    public AdminUserController(AuthService auth) { this.auth=auth; }
    @PostMapping("/administrators")
    public Result create(@Valid @RequestBody AuthDtos.CreateAdmin request) {
        auth.createAdmin(request);return Result.success();
    }
    // 禁用
    @PostMapping("/{id}/disable")
    public Result disable(@PathVariable long id) {
        auth.disable(id);return Result.success();
    }
}
