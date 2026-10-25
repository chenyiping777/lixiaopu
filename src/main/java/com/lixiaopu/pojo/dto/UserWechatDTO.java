package com.lixiaopu.pojo.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 使用于用户微信快捷登录
 */
@Data
public class UserWechatDTO {
    //微信登录 code
    @NotBlank(message = "微信登录 code 不能为空")
    private String code;
    //微信昵称
    private String nickName;
    //微信头像链接
    private String avatarUrl;
}
