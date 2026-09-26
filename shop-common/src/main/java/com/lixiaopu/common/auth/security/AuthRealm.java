package com.lixiaopu.common.auth.security;

import com.lixiaopu.common.auth.session.TokenService;

import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.model.entity.SysRole;
import com.lixiaopu.model.entity.SysUser;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

public class AuthRealm extends AuthorizingRealm {
    @Autowired
    private TokenService tokenService;
    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private SysUserMapper userMapper;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtAuthenticationToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        JwtAuthenticationToken jwt = (JwtAuthenticationToken) token;
        TokenService.Principal principal = tokenService.verifyAccess(jwt.getJwt(), jwt.getDevice());
        SysUser user = userMapper.selectById(principal.getUid());
        if (user == null || user.getUserType() == null) {
            throw new AuthenticationException("身份不匹配");
        }

        Set<String> roleCodes = authMapper.roles(principal.getUid()).stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet());
        if (!matchesDevice(user.getUserType(), jwt.getDevice(), roleCodes)) {
            throw new AuthenticationException("身份不匹配");
        }
        return new SimpleAuthenticationInfo(principal, jwt.getJwt(), getName());
    }

    private boolean matchesDevice(byte userType, String device, Set<String> roleCodes) {
        if ("BUYER".equals(device)) {
            return userType == 3 && roleCodes.contains("BUYER");
        }
        if ("ADMIN".equals(device)) {
            return (userType == 1 && roleCodes.contains("SYS_ADMIN"))
                    || (userType == 2 && roleCodes.contains("ADMIN"));
        }
        return false;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        TokenService.Principal principal = (TokenService.Principal) principals.getPrimaryPrincipal();
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setRoles(authMapper.roles(principal.getUid()).stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet()));
        info.setStringPermissions(authMapper.permissions(principal.getUid()).stream()
                .map(permission -> permission.getPermCode())
                .collect(Collectors.toSet()));
        return info;
    }
}
