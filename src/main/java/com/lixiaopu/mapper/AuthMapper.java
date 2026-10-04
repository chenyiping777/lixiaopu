package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.SysPermission;
import com.lixiaopu.pojo.entity.SysRole;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface AuthMapper {
    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{uid} AND r.is_enable=1")
    List<SysRole> roles(long uid);

    @Select("SELECT DISTINCT p.* FROM sys_permission p JOIN sys_role_permission rp ON rp.perm_id=p.id JOIN sys_role r ON r.id=rp.role_id JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{uid} AND r.is_enable=1 AND p.is_enable=1")
    List<SysPermission> permissions(long uid);

    @Select("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='SYS_ADMIN'")
    long systemAdminCount();
}
