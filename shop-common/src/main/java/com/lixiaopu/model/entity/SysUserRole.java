package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 对应 sys_user_role；业务逻辑按模块逐步实现。 */
@Data
@TableName("sys_user_role")
public class SysUserRole {
    private Long userId;
    private Long roleId;
}

