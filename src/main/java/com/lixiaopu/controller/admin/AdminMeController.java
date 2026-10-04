package com.lixiaopu.controller.admin;

import com.lixiaopu.common.context.AuthContext;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class AdminMeController {
    private final AuthMapper roles;
    private final SysUserMapper users;
    public AdminMeController(AuthMapper roles,SysUserMapper users) { this.roles=roles;this.users=users; }
    @GetMapping("/api/admin/me") public Result me() {
        long uid=AuthContext.get().getUid();
        var user=users.selectById(uid);
        return Result.success(Map.of("id",uid,"username",user.getUsername(),
            "roles",roles.roles(uid).stream().map(r->r.getRoleCode()).toList(),
            "permissions",roles.permissions(uid).stream().map(p->p.getPermCode()).toList()));
    }
}
