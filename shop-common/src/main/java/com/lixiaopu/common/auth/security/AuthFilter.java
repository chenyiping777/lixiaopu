package com.lixiaopu.common.auth.security;

import com.lixiaopu.common.auth.session.AuthContext;
import com.lixiaopu.common.auth.session.TokenService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.ResultCode;
import com.lixiaopu.common.result.UserInfo;
import com.lixiaopu.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private final DefaultSecurityManager securityManager;
    private final SysUserMapper users;
    private final ObjectMapper json;
    public AuthFilter(DefaultSecurityManager securityManager,SysUserMapper users,ObjectMapper json) {
        this.securityManager=securityManager;this.users=users;this.json=json;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String path=request.getRequestURI();
        boolean admin=path.startsWith("/api/admin/");
        boolean protectedBuyer=path.startsWith("/api/user/") || path.equals("/api/upload/image")
            || path.equals("/api/auth/logout") || path.equals("/api/auth/change-password");
        boolean publicAdmin=path.equals("/api/admin/auth/login") || path.equals("/api/admin/auth/refresh") || path.equals("/api/admin/auth/sms")
            || path.equals("/api/admin/auth/reset/verify") || path.equals("/api/admin/auth/reset/password");
        if ((!admin && !protectedBuyer) || (admin && publicAdmin)) { chain.doFilter(request,response);return; }
        try {
            String header=request.getHeader("Authorization");
            if (header==null || !header.startsWith("Bearer ")) throw new AuthException(ResultCode.NO_TOKEN);
            Subject subject=new Subject.Builder(securityManager).buildSubject();
            subject.login(new JwtAuthenticationToken(header.substring(7),admin?"ADMIN":"BUYER"));
            TokenService.Principal principal=(TokenService.Principal)subject.getPrincipal();
            String required=admin?"admin:access":"buyer:access";
            if (!subject.isPermitted(required)) throw new AuthException(ResultCode.FORBIDDEN);
            if (path.equals("/api/admin/users/administrators") && !subject.hasRole("SYS_ADMIN"))
                throw new AuthException(ResultCode.FORBIDDEN);
            if (path.equals("/api/admin/users/administrators") && !subject.isPermitted("admin:create"))
                throw new AuthException(ResultCode.FORBIDDEN);
            if (path.matches("/api/admin/users/[0-9]+/disable") && !subject.isPermitted("user:disable"))
                throw new AuthException(ResultCode.FORBIDDEN);
            AuthContext.set(principal);
            var user=users.selectById(principal.getUid());
            CurrentHolder.setCurrentUser(UserInfo.builder().id(user.getId()).nickname(user.getNickname())
                .avatar(user.getAvatar()).phone(user.getPhone()).openid(user.getOpenid()).build());
            chain.doFilter(request,response);
        } catch (AuthException ex) { error(response,ex.getCode(),ex.getMessage()); }
        catch (AuthenticationException ex) { error(response,ResultCode.UNAUTHORIZED,ResultCode.UNAUTHORIZED.getMessage()); }
        finally { CurrentHolder.remove();AuthContext.clear(); }
    }
    private void error(HttpServletResponse response,ResultCode code,String message) throws IOException {
        response.setStatus(code==ResultCode.FORBIDDEN?403:401);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(),Result.error(code,message));
    }
}
