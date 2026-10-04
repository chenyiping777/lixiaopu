package com.lixiaopu.security.filter;

import com.lixiaopu.common.exception.AuthException;
import com.lixiaopu.security.token.JwtAuthenticationToken;

import com.lixiaopu.common.context.AuthContext;
import com.lixiaopu.security.token.TokenService;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {
    @Autowired
    private  DefaultSecurityManager securityManager;
    @Autowired
    private  SysUserMapper userMapper;
    @Autowired
    private  ObjectMapper jsonMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String path=request.getRequestURI();
        boolean admin=path.startsWith("/api/admin/");
        boolean protectedBuyer=path.startsWith("/api/user/") || path.equals("/api/cart") || path.startsWith("/api/cart/")
            || path.equals("/api/addresses") || path.startsWith("/api/addresses/")
            || path.equals("/api/address") || path.startsWith("/api/address/")
            || path.equals("/api/upload/image")
            || path.equals("/api/auth/logout") || path.equals("/api/auth/change-password");
        boolean publicAdmin=path.equals("/api/admin/auth/login") || path.equals("/api/admin/auth/refresh") || path.equals("/api/admin/auth/sms")
            || path.equals("/api/admin/auth/reset/verify") || path.equals("/api/admin/auth/reset/password");
        if ((!admin && !protectedBuyer) || (admin && publicAdmin)) { chain.doFilter(request,response);return; }
        try {
            String header=request.getHeader("Authorization");
            if (header==null || !header.startsWith("Bearer "))
                throw new AuthException(ResultCode.NO_TOKEN);

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
            var user=userMapper.selectById(principal.getUid());

            CurrentHolder.setCurrentUser(UserInfo.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatar())
                    .phone(user.getPhone())
                    .openid(user.getOpenid())
                    .build());
            chain.doFilter(request,response);
        } catch (AuthException ex) {
            error(response,ex.getCode(),ex.getMessage());
        } catch (AuthenticationException ex) {
            error(response,ResultCode.UNAUTHORIZED,ResultCode.UNAUTHORIZED.getMessage());
        } finally {
            CurrentHolder.remove();AuthContext.clear();
        }
    }
    private void error(HttpServletResponse response,ResultCode code,String message) throws IOException {
        response.setStatus(code==ResultCode.FORBIDDEN?403:401);
        response.setContentType("application/json;charset=UTF-8");
        jsonMapper.writeValue(response.getWriter(),Result.error(code,message));
    }
}
