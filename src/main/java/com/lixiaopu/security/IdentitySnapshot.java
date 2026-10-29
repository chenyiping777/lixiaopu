package com.lixiaopu.security;

import com.lixiaopu.common.result.UserInfo;
import com.lixiaopu.pojo.entity.SysUser;
import java.util.Set;

/** 仅属于一次认证调用的不可变快照，不保存用户实体或密码哈希，不跨请求缓存。 */
public record IdentitySnapshot(long userId, Byte userType, Long authVersion,
                               String nickname, String avatar, String phone, String openid,
                               Set<String> roles, Set<String> permissions) {
    public IdentitySnapshot {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public static IdentitySnapshot from(SysUser user) {
        return new IdentitySnapshot(user.getId(), user.getUserType(), user.getAuthVersion(),
                user.getNickname(), user.getAvatar(), user.getPhone(), user.getOpenid(), Set.of(), Set.of());
    }

    public IdentitySnapshot withAuthorization(Set<String> roles, Set<String> permissions) {
        return new IdentitySnapshot(userId, userType, authVersion, nickname, avatar, phone, openid,
                roles, permissions);
    }

    public UserInfo toUserInfo() {
        return UserInfo.builder().id(userId).nickname(nickname).avatar(avatar).phone(phone).openid(openid).build();
    }
}
