package com.lixiaopu.security.realm;

import com.lixiaopu.security.config.ShiroAuthConfig;
import com.lixiaopu.security.token.JwtAuthenticationToken;

import com.lixiaopu.security.token.TokenService;

import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.pojo.entity.SysPermission;
import com.lixiaopu.pojo.entity.SysRole;
import com.lixiaopu.pojo.entity.SysUser;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRealmTest {
    @Test void buyerCannotAuthenticateAtAdminEntryOrGainAdminPermission() {
        TokenService tokens=mock(TokenService.class);AuthMapper mapper=mock(AuthMapper.class);SysUserMapper users=mock(SysUserMapper.class);
        var principal=new TokenService.Principal(7,"sid","BUYER");
        when(tokens.verifyAccess("access","ADMIN")).thenReturn(principal);
        SysUser user=new SysUser();user.setId(7L);user.setUserType((byte)3);
        when(users.selectById(7L)).thenReturn(user);
        SysRole buyer=new SysRole();buyer.setRoleCode("BUYER");when(mapper.roles(7)).thenReturn(List.of(buyer));
        SysPermission access=new SysPermission();access.setPermCode("buyer:access");when(mapper.permissions(7)).thenReturn(List.of(access));
        AuthRealm realm=new AuthRealm();
        ReflectionTestUtils.setField(realm,"tokenService",tokens);
        ReflectionTestUtils.setField(realm,"authMapper",mapper);
        ReflectionTestUtils.setField(realm,"userMapper",users);
        assertThrows(AuthenticationException.class,()->realm.doGetAuthenticationInfo(new JwtAuthenticationToken("access","ADMIN")));
        var grants=realm.doGetAuthorizationInfo(new SimplePrincipalCollection(principal,realm.getName()));
        assertTrue(grants.getStringPermissions().contains("buyer:access"));
        assertFalse(grants.getStringPermissions().contains("admin:create"));
        var manager=new ShiroAuthConfig().securityManager(realm);
        when(tokens.verifyAccess("access","BUYER")).thenReturn(principal);
        Subject subject=new Subject.Builder(manager).buildSubject();
        subject.login(new JwtAuthenticationToken("access","BUYER"));
        assertTrue(subject.isPermitted("buyer:access"));
        assertFalse(subject.isPermitted("admin:create"));
        assertNull(subject.getSession(false));
    }
}
