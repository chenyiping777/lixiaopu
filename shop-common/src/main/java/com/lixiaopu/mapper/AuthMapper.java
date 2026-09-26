package com.lixiaopu.mapper;

import com.lixiaopu.model.entity.SysPermission;
import com.lixiaopu.model.entity.SysRole;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface AuthMapper {
    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{uid} AND r.is_enable=1")
    List<SysRole> roles(long uid);

    @Select("SELECT DISTINCT p.* FROM sys_permission p JOIN sys_role_permission rp ON rp.perm_id=p.id JOIN sys_role r ON r.id=rp.role_id JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{uid} AND r.is_enable=1 AND p.is_enable=1")
    List<SysPermission> permissions(long uid);

    @Select("SELECT id FROM sys_role WHERE role_code=#{code} AND is_enable=1")
    Long roleId(String code);

    @Insert("INSERT INTO sys_user_role(user_id,role_id) VALUES(#{uid},#{roleId})")
    int grantRole(@Param("uid") long uid, @Param("roleId") long roleId);

    @Select("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='SYS_ADMIN'")
    long systemAdminCount();

    @Update("UPDATE sys_user SET password=#{hash}, auth_version=auth_version+1 WHERE id=#{uid}")
    int replacePassword(@Param("uid") long uid,@Param("hash") String hash);
}
