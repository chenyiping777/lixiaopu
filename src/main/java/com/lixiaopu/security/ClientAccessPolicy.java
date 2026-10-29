package com.lixiaopu.security;

import com.lixiaopu.pojo.entity.SysRole;
import com.lixiaopu.pojo.entity.SysUser;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 登录签发与每次令牌认证共用准入规则；调用方仍须各自检查当前账号、会话和权限。 */
public final class ClientAccessPolicy {
    private ClientAccessPolicy() {}

    public static boolean allows(SysUser user, ClientType clientType, Collection<SysRole> roles) {
        if (user == null || roles == null) return false;
        return allowsType(user.getUserType(), clientType, roles.stream().filter(Objects::nonNull)
                .map(SysRole::getRoleCode).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    public static boolean allowsType(Byte userType, ClientType clientType, Set<String> roles) {
        if (userType == null || clientType == null || roles == null) return false;
        return switch (clientType) {
            case BUYER -> userType == 3 && roles.contains("BUYER");
            case ADMIN -> (userType == 1 && roles.contains("SYS_ADMIN"))
                    || (userType == 2 && roles.contains("ADMIN"));
        };
    }
}
