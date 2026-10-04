package com.lixiaopu.service;

import com.lixiaopu.pojo.dto.AuthDtos;
import com.lixiaopu.common.exception.AuthException;
import com.lixiaopu.security.token.TokenService;
import com.lixiaopu.infrastructure.sms.SmsService;
import com.lixiaopu.infrastructure.wechat.WechatGateway;

import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.mapper.SysRoleMapper;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.mapper.SysUserRoleMapper;
import com.lixiaopu.pojo.entity.SysUser;
import com.lixiaopu.pojo.entity.SysRole;
import com.lixiaopu.pojo.entity.SysUserRole;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final SysUserMapper users=mock(SysUserMapper.class);
    private final AuthMapper roles=mock(AuthMapper.class);
    private final SysRoleMapper roleMapper=mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoleMapper=mock(SysUserRoleMapper.class);
    private final TokenService tokens=mock(TokenService.class);
    private final SmsService sms=mock(SmsService.class);
    private final WechatGateway wechat=mock(WechatGateway.class);
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    private AuthService auth;
    @BeforeEach void setup() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
        auth = new AuthService();
        ReflectionTestUtils.setField(auth, "baseMapper", users);
        ReflectionTestUtils.setField(auth, "authMapper", roles);
        ReflectionTestUtils.setField(auth, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(auth, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(auth, "tokenService", tokens);
        ReflectionTestUtils.setField(auth, "smsService", sms);
        ReflectionTestUtils.setField(auth, "wechatGateway", wechat);
        ReflectionTestUtils.setField(auth, "stringRedisTemplate", redis);
    }
    @Test void changePasswordRevokesAllSessions() {
        SysUser user=new SysUser();user.setId(7L);user.setPassword(BCrypt.hashpw("old-password",BCrypt.gensalt(4)));
        when(users.selectById(7L)).thenReturn(user);
        when(users.update(isNull(), any())).thenReturn(1);
        auth.changePassword(7,"old-password","new-password");
        verify(tokens).revokeAll(7);
        verify(users).update(isNull(), any());
        assertThrows(AuthException.class,()->auth.changePassword(7,"wrong-password","new-password"));
    }
    @Test void resetTicketIsConsumedOnceWithoutLogin() {
        when(redis.execute(any(RedisScript.class),anyList())).thenReturn("7",null);
        when(users.update(isNull(), any())).thenReturn(1);
        auth.resetPassword("single-use-ticket","new-password");
        verify(tokens).revokeAll(7);
        verify(tokens,never()).issue(anyLong(),anyString(),anyLong());
        assertThrows(AuthException.class,()->auth.resetPassword("single-use-ticket","new-password"));
    }

    @Test void registrationBindsBuyerRole() {
        SysRole buyerRole = new SysRole();
        buyerRole.setId(3L);
        when(roleMapper.selectOne(any())).thenReturn(buyerRole);
        when(users.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        });

        auth.register(new AuthDtos.Register("buyer_1", "safe-password", "13800000000", "123456"));

        verify(sms).consume("13800000000", "REGISTER", "123456");
        verify(userRoleMapper).insert(argThat((SysUserRole userRole) ->
                userRole.getUserId() == 7L && userRole.getRoleId() == 3L));
        verify(users).insert(argThat((SysUser user) ->
                user.getPassword().startsWith("$2") && user.getUserType() == 3));
    }

    @Test void buyerPasswordCannotOpenAdminSession() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUserType((byte) 3);
        user.setIsEnable(CommonStatus.ACTIVE);
        user.setAuthVersion(0L);
        user.setPassword(BCrypt.hashpw("safe-password", BCrypt.gensalt(4)));
        when(users.selectOne(any())).thenReturn(user);
        SysRole role = new SysRole();
        role.setRoleCode("BUYER");
        when(roles.roles(7L)).thenReturn(List.of(role));
        AuthDtos.Login request = new AuthDtos.Login("13800000000", "safe-password", null);

        assertThrows(AuthException.class, () -> auth.login(request, "ADMIN"));
        verify(tokens, never()).issue(anyLong(), anyString(), anyLong());
    }
}
