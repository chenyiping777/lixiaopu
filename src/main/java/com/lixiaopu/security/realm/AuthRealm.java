package com.lixiaopu.security.realm;

import com.lixiaopu.security.token.JwtAuthenticationToken;

import com.lixiaopu.security.token.TokenService;

import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.pojo.entity.SysPermission;
import com.lixiaopu.pojo.entity.SysRole;
import com.lixiaopu.pojo.entity.SysUser;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前端请求接口，请求头带上accessToken，shiro过滤器拦截请求，拿到token，解析jwt
 * 把解析的jwt包装成AuthenticationInfo，然后触发doGetAuthenticationInfo
 * */
public class AuthRealm extends AuthorizingRealm {
    @Autowired
    private TokenService tokenService;
    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private SysUserMapper userMapper;

    //这个realm只处理JwtAuthenticationToken这样类型的token
    @Override
    public boolean supports(AuthenticationToken token) {
        //instanceof判断这个对象（token）是不是JwtAuthenticationToken的实例
        return token instanceof JwtAuthenticationToken;
    }

    //校验用户类型和访问端设备和用户角色三者是否匹配
    private boolean matchesDevice(
            byte userType,
            String device,
            Set<String> roleCodes) {

        /*
         * 电商用户类型 1-系统管理员 2-普通管理员 3-普通买家
         */
        if ("BUYER".equals(device)) {
            return userType == 3 && roleCodes.contains("BUYER");
        }
        if ("ADMIN".equals(device)) {
            return (userType == 1 && roleCodes.contains("SYS_ADMIN"))
                    || (userType == 2 && roleCodes.contains("ADMIN"));
        }
        return false;
    }

    //获取身份认证信息
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        //1.强转token，使用自定义的JwtAuthenticationToken还有里面的方法
        //jwt里不止有jwt，还有device
        JwtAuthenticationToken jwt = (JwtAuthenticationToken) token;
        //2.校验jwt令牌合法性
        //2.1.校验jwt签名是否合法，有没有过期，解析载荷封装principle对象
        TokenService.Principal principal = tokenService.verifyAccess(jwt.getJwt(), jwt.getDevice());
        //Principal(id,sid,expectedDevice)
        SysUser user = userMapper.selectById(principal.getUid());
        if (user == null || user.getUserType() == null) {
            throw new AuthenticationException("身份不匹配");
        }
        //从sys_role连接sys_user_role表里查这个用户对应的角色集合
        Set<String> roleCodes = authMapper
                .roles(principal.getUid())
                .stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet());

        //校验用户类型和访问端设备和用户角色三者是否匹配
        if (!matchesDevice(user.getUserType(), jwt.getDevice(), roleCodes)) {
            throw new AuthenticationException("身份不匹配");
        }
        //参数：principle主体，凭证，当前realm的名字
        //SimpleAuthenticationInfo实现了AuthenticationInfo接口，用于封装认证成功后的用户主体信息
        return new SimpleAuthenticationInfo(principal, jwt.getJwt(), getName());
    }

    //获取授权信息，如果接口有权限注解，就会触发doGetAuthorizationInfo
    //查询当前登录用户拥有的角色，权限，交给Shiro做权限比对
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        //认证阶段拿到的AuthenticationInfo   principal(id,sid,expectedDevice)
        TokenService.Principal principal = (TokenService.Principal) principals.getPrimaryPrincipal();
        //创建授权信息对象，用来存放角色和权限集合
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        info.setRoles(
                authMapper.roles(principal.getUid())
                        .stream()
                        .map(SysRole::getRoleCode)
                        .collect(Collectors.toSet()));//角色集合

        info.setStringPermissions(authMapper
                .permissions(principal.getUid())
                .stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toSet()));//权限集合
        return info;//把封装好的角色+权限信息返回给shiro框架，shiro用来校验接口上的权限注解
    }
}
