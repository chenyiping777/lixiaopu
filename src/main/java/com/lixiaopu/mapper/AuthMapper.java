package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.SysPermission;
import com.lixiaopu.pojo.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface AuthMapper {
    List<SysRole> roles(@Param("uid") long uid);

    List<SysPermission> permissions(@Param("uid") long uid);

    long systemAdminCount();
}
