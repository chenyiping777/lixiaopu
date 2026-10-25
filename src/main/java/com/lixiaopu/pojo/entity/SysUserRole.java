package com.lixiaopu.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 对应 sys_user_role；业务逻辑按模块逐步实现。 */
@Data
@TableName("sys_user_role")
public class SysUserRole {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    private Long roleId;
}

