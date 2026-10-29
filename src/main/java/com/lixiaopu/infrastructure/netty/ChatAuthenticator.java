package com.lixiaopu.infrastructure.netty;

import com.lixiaopu.security.ClientType;

import com.lixiaopu.security.token.JwtAuthenticationToken;
import com.lixiaopu.security.token.TokenService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Component;

/** Netty 线程不能依赖 HTTP ThreadLocal；显式复用当前 Shiro/JWT/Redis 认证链。 */
@Component
@RequiredArgsConstructor
public class ChatAuthenticator {
    private final DefaultSecurityManager securityManager;

    public TokenService.Principal authenticate(String token, ClientType clientType) {
        if (token == null || token.isBlank() || clientType == null)
            throw new IllegalArgumentException("登录凭证无效");
        var subject = new Subject.Builder(securityManager).buildSubject();
        subject.login(new JwtAuthenticationToken(token, clientType));
        if (!subject.isPermitted(clientType.getAccessPermission()))
            throw new org.apache.shiro.authz.AuthorizationException("无客服访问权限");
        return (TokenService.Principal) subject.getPrincipal();
    }
}
