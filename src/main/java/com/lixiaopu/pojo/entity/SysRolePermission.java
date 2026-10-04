package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 对应 sys_role_permission；业务逻辑按模块逐步实现。 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {
    private Long roleId;
    private Long permId;
}

