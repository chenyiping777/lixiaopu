package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import com.lixiaopu.common.result.UserInfo;
import com.lixiaopu.model.enums.CommonStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 栗小铺 sys_user
 * @TableName sys_user
 */
@TableName(value ="sys_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SysUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO) // 对应数据库的 auto_increment 自增策略
    private Long id;

    /**
     * 登录名
     */
    private String username;

    /**
     * 密码（JBCrypt加密，固定60位）
     */
    private String password;


    /**
     * 用户昵称
     */
    @TableField(value = "nickname")
    private String nickname;

    /**
     * 用户头像 URL
     */
    @TableField(value = "avatar")
    private String avatar;


    /**
     * 微信openid（小程序唯一标识，唯一）
     */
    private String openid;

    /**
     * 手机号
     */
    private String phone;


    /**
     * 电商用户类型 1-系统管理员 2-普通管理员 3-普通买家
     */
    @TableField(value = "user_type")
    private Byte userType;


    /**
     * 是否启用 1-启用 0-禁用（禁用后无法登录）
     */
    @TableField(value = "is_enable")
    private CommonStatus isEnable;

    /**
     * 授权版本号
     * 用来批量作废用户已下发的所有token的版本标记
     * 1. 下发 Token 的时候，会把当前用户的 authVersion 值写入到 JWT / Token 载荷里面；
     * 2. 用户表保存一份最新的 `authVersion`；
     * 3. 每次接口鉴权解析 Token 时：
     *    - 取出 Token 内部携带的 version
     *    - 查询数据库拿到该用户最新 `authVersion`
     *    - 两个值不一致 → 直接拒绝访问，Token 失效
     * > 只要修改 authVersion，这个用户历史所有 Token 全部一次性作废。
     */
    private Long authVersion;

    /**
     * 角色列表
     */
    @TableField(exist = false)
    private List<SysRole> sysRoleList;


    /**
     * 权限列表
     */
    @TableField(exist = false)
    private List<SysPermission> sysPermissionList;

    /**
     * 用户简单登录信息
     */
    @TableField(exist = false)
    private UserInfo userInfo;

    /**
     * 首次登录时间
     */
    private LocalDateTime firstLoginTime;

    /**
     * 最近登录时间
     */
    private LocalDateTime lastLoginTime;


    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 插入时自动填充
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 插入和更新时自动填充
    private LocalDateTime updateTime;

}
