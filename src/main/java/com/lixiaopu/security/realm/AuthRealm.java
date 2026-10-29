package com.lixiaopu.security.realm;

import com.lixiaopu.security.ClientAccessPolicy;

import com.lixiaopu.security.token.JwtAuthenticationToken;

import com.lixiaopu.security.token.TokenService;

import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.pojo.entity.SysPermission;
import com.lixiaopu.pojo.entity.SysRole;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Collectors;
import java.util.Objects;

/**
 * 前端请求接口，请求头带上accessToken，shiro过滤器拦截请求，拿到token，解析jwt
 * 把解析的jwt包装成AuthenticationInfo，然后触发doGetAuthenticationInfo
 * */
public class AuthRealm extends AuthorizingRealm {
    @Autowired
    private TokenService tokenService;
    @Autowired
    private AuthMapper authMapper;
    public AuthRealm() {
        // 快照只供本次 Subject 使用；下一次 HTTP 请求或 WebSocket 复核必须重新读取。
        setCachingEnabled(false);
    }

    //这个realm只处理JwtAuthenticationToken这样类型的token
    @Override
    public boolean supports(AuthenticationToken token) {
        //instanceof判断这个对象（token）是不是JwtAuthenticationToken的实例
        return token instanceof JwtAuthenticationToken;
    }

    //获取身份认证信息
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        //1.强转token，使用自定义的JwtAuthenticationToken还有里面的方法
        // 包装对象携带 JWT 与期望业务端；JWT 载荷本身不含 clientType。
        JwtAuthenticationToken jwt = (JwtAuthenticationToken) token;
        //2.校验jwt令牌合法性
        //2.1.校验jwt签名是否合法，有没有过期，解析载荷封装principle对象
        TokenService.Principal principal = tokenService.verifyAccess(jwt.getJwt(), jwt.getClientType());
        var identity = principal.getIdentity();
        if (identity == null || identity.userId() != principal.getUid()
                || principal.getClientType() != jwt.getClientType()) {
            throw new AuthenticationException("身份不匹配");
        }
        var roles = authMapper.roles(principal.getUid()).stream().map(SysRole::getRoleCode)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!ClientAccessPolicy.allowsType(identity.userType(), jwt.getClientType(), roles)) {
            throw new AuthenticationException("身份不匹配");
        }
        var permissions = authMapper.permissions(principal.getUid()).stream().map(SysPermission::getPermCode)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var authenticated = new TokenService.Principal(principal.getUid(), principal.getSid(),
                principal.getClientType(), identity.withAuthorization(roles, permissions));
        //参数：principle主体，凭证，当前realm的名字
        //SimpleAuthenticationInfo实现了AuthenticationInfo接口，用于封装认证成功后的用户主体信息
        return new SimpleAuthenticationInfo(authenticated, jwt.getJwt(), getName());
    }

    // 授权检查复用本次认证已读取的角色和权限，不再访问数据库。
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        //认证阶段拿到的AuthenticationInfo   principal(id,sid,expectedClientType)
        TokenService.Principal principal = (TokenService.Principal) principals.getPrimaryPrincipal();
        var identity = principal.getIdentity();
        if (identity == null) throw new AuthorizationException("缺少认证快照");
        // 重复 isPermitted/hasRole 仅复用本次认证快照，不再查询数据库。
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setRoles(identity.roles());
        info.setStringPermissions(identity.permissions());
        return info;//把封装好的角色+权限信息返回给shiro框架，shiro用来校验接口上的权限注解
    }
}
