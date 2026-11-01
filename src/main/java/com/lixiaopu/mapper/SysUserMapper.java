package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;


public interface SysUserMapper extends BaseMapper<SysUser> {
    /** 只向买家公开具备管理端准入权限的在用客服姓名与编号。 */
    @org.apache.ibatis.annotations.Select("""
        SELECT DISTINCT u.id, u.nickname FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id
        JOIN sys_role r ON r.id = ur.role_id AND r.is_enable = 1
        WHERE u.is_enable = 1
          AND ((u.user_type = 1 AND r.role_code = 'SYS_ADMIN')
            OR (u.user_type = 2 AND r.role_code = 'ADMIN'))
          AND EXISTS (SELECT 1 FROM sys_user_role upr
            JOIN sys_role pr ON pr.id = upr.role_id AND pr.is_enable = 1
            JOIN sys_role_permission rp ON rp.role_id = pr.id
            JOIN sys_permission p ON p.id = rp.perm_id AND p.is_enable = 1
            WHERE upr.user_id = u.id AND p.perm_code IN ('admin:access', 'admin:*', '*'))
        ORDER BY u.id LIMIT 20
        """)
    java.util.List<SysUser> selectServiceContacts();
}




