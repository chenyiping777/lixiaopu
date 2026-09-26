package com.lixiaopu.common.auth.service;

import com.lixiaopu.common.auth.dto.AuthDtos;
import com.lixiaopu.common.auth.security.AuthException;
import com.lixiaopu.common.auth.session.AuthContext;
import com.lixiaopu.common.auth.session.TokenService;
import com.lixiaopu.common.auth.sms.SmsService;
import com.lixiaopu.common.auth.wechat.WechatGateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixiaopu.common.result.ResultCode;
import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.model.entity.SysRole;
import com.lixiaopu.model.entity.SysUser;
import com.lixiaopu.model.enums.CommonStatus;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private static final byte SYS_ADMIN_TYPE = 1;
    private static final byte ADMIN_TYPE = 2;
    private static final byte BUYER_TYPE = 3;

    private static final String SYS_ADMIN_ROLE = "SYS_ADMIN";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String BUYER_ROLE = "BUYER";

    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private SmsService smsService;
    @Autowired
    private WechatGateway wechatGateway;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

    private SysUser findByPhone(String phone) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getPhone, phone);
        return userMapper.selectOne(query);
    }

    private SysUser findByOpenid(String openid) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getOpenid, openid);
        return userMapper.selectOne(query);
    }

    private boolean matchesPassword(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || !passwordHash.startsWith("$2")) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, passwordHash);
        } catch (IllegalArgumentException ex) {
            // 旧密码或损坏的散列不能按明文回退验证。
            return false;
        }
    }

    private boolean hasRole(long userId, String roleCode) {
        List<SysRole> roles = authMapper.roles(userId);
        for (SysRole role : roles) {
            if (roleCode.equals(role.getRoleCode())) {
                return true;
            }
        }
        return false;
    }

    private SysUser createUser(String username, String rawPassword, String phone,
                               String openid, byte userType, String roleCode) {
        SysUser user = new SysUser();
        user.setUsername(username);
        if (rawPassword != null) {
            user.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt(12)));
        }
        user.setNickname("栗友" + UUID.randomUUID().toString().substring(0, 8));
        user.setAvatar("/static/images/default-avatar.png");
        user.setPhone(phone);
        user.setOpenid(openid);
        user.setUserType(userType);
        user.setIsEnable(CommonStatus.ACTIVE);
        user.setAuthVersion(0L);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        Long roleId = authMapper.roleId(roleCode);
        if (roleId == null) {
            throw new IllegalStateException("请先执行 02-roles.sql 初始化角色");
        }
        authMapper.grantRole(user.getId(), roleId);
        return user;
    }

    @Transactional
    public void register(AuthDtos.Register request) {
        smsService.consume(request.getPhone(), "REGISTER", request.getCode());

        try {
            createUser(request.getUsername(), request.getPassword(), request.getPhone(),
                    null, BUYER_TYPE, BUYER_ROLE);
        } catch (DuplicateKeyException ex) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "无法完成注册");
        }
    }

    //手机号密码登录
    public AuthDtos.Tokens login(AuthDtos.Login request, String device) {
        // 根据手机号查询用户
        SysUser user = findByPhone(request.getPhone());
        // 验证用户是否存在且已启用，密码是否匹配，设备是否允许登录，后面颁发token的时候还需要再校验一次，
        // 因为这两个操作时间有间隔，可能用户已经禁用或者密码已经修改
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_EXIST);
        }
        if (user.getIsEnable() != CommonStatus.ACTIVE
                || !matchesPassword(request.getPassword(), user.getPassword())
                || !canLogin(user, device)) {
            throw new AuthException(ResultCode.LOGIN_ERROR);
        }
        // 更新登录时间
        updateLoginTime(user);
        return tokenService.issue(user.getId(), device, user.getAuthVersion());
    }

    private boolean canLogin(SysUser user, String device) {
        Byte userType = user.getUserType();
        // 用户类型为空 → 禁止登录
        if (userType == null) {
            return false;
        }
        // 场景1：device = BUYER_ROLE（买家APP/买家前端）
        if (BUYER_ROLE.equals(device)) {
            // 必须：用户类型是买家(3) 并且 拥有BUYER角色
            return userType == BUYER_TYPE && hasRole(user.getId(), BUYER_ROLE);
        }
        // 场景2：device = ADMIN_ROLE（管理后台）
        if (ADMIN_ROLE.equals(device)) {
            if (userType == SYS_ADMIN_TYPE) {
                // 超级管理员(type=1)：必须拥有 SYS_ADMIN 角色
                return hasRole(user.getId(), SYS_ADMIN_ROLE);
            }
            // 普通管理员(type=2)：必须拥有 ADMIN 角色
            return userType == ADMIN_TYPE && hasRole(user.getId(), ADMIN_ROLE);
        }
        // device不是BUYER、也不是ADMIN → 不允许登录
        return false;
    }

    private void updateLoginTime(SysUser user) {
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        //创建一个SysUser对象，用于更新用户信息
        SysUser update = new SysUser();
        //设置用户ID
        update.setId(user.getId());
        //设置上次登录时间
        update.setLastLoginTime(now);
        //如果第一次登录，则设置第一次登录时间
        if (user.getFirstLoginTime() == null) {
            update.setFirstLoginTime(now);
        }
        //更新
        userMapper.updateById(update);//空值不更新，只更新非空值上次登录时间
    }

    @Transactional
    public AuthDtos.Tokens wechat(String code) {
        String openid = wechatGateway.openid(code);
        SysUser user = findByOpenid(openid);
        if (user == null) {
            String username = "wx_" + UUID.randomUUID().toString().replace("-", "");
            try {
                user = createUser(username, null, null, openid, BUYER_TYPE, BUYER_ROLE);
            } catch (DuplicateKeyException ex) {
                // 两个相同微信身份的请求同时创建时，使用已创建的账号。
                user = findByOpenid(openid);
            }
        }
        if (user == null || user.getIsEnable() != CommonStatus.ACTIVE
                || !canLogin(user, BUYER_ROLE)) {
            throw new AuthException(ResultCode.LOGIN_ERROR);
        }

        updateLoginTime(user);
        return tokenService.issue(user.getId(), BUYER_ROLE, user.getAuthVersion());
    }

    public String sendSms(String phone, String purpose) {
        return smsService.send(phone, purpose);
    }

    public String verifyReset(String phone, String code) {
        smsService.consume(phone, "RESET_PASSWORD", code);

        SysUser user = findByPhone(phone);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String userId = user == null ? "0" : user.getId().toString();
        stringRedisTemplate.opsForValue().set("lxp:reset:" + ticket, userId, Duration.ofMinutes(10));
        return ticket;
    }

    public void resetPassword(String ticket, String newPassword) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>(
                "return redis.call('GETDEL',KEYS[1])", String.class);
        String userId = stringRedisTemplate.execute(script, List.of("lxp:reset:" + ticket));
        if (userId == null) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "重置凭据无效或已使用");
        }

        long uid = Long.parseLong(userId);
        if (uid == 0) {
            return;
        }
        replacePasswordAndRevoke(uid, newPassword);
    }

    public void changePassword(long uid, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(uid);
        if (user == null || !matchesPassword(oldPassword, user.getPassword())) {
            throw new AuthException(ResultCode.LOGIN_ERROR);
        }
        replacePasswordAndRevoke(uid, newPassword);
    }

    private void replacePasswordAndRevoke(long uid, String newPassword) {
        String passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        authMapper.replacePassword(uid, passwordHash);
        tokenService.revokeAll(uid);
    }

    @Transactional
    public void createAdmin(AuthDtos.CreateAdmin request) {
        try {
            createUser(request.getUsername(), request.getPassword(), request.getPhone(),
                    null, ADMIN_TYPE, ADMIN_ROLE);
        } catch (DuplicateKeyException ex) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "无法创建管理员");
        }
    }

    @Transactional
    public void bootstrapAdmin(String username, String rawPassword, String phone) {
        if (authMapper.systemAdminCount() != 0) {
            return;
        }
        createUser(username, rawPassword, phone, null, SYS_ADMIN_TYPE, SYS_ADMIN_ROLE);
    }

    public void disable(long uid) {
        if (uid == AuthContext.get().getUid()) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "不能禁用当前账号");
        }
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_EXIST);
        }

        SysUser update = new SysUser();
        update.setId(uid);
        update.setIsEnable(CommonStatus.INACTIVE);
        userMapper.updateById(update);
        tokenService.revokeAll(uid);
    }

    public AuthDtos.Tokens refresh(String refreshToken, String device) {
        return tokenService.rotate(refreshToken, device);
    }

    public void logout() {
        tokenService.logout(AuthContext.get());
    }
}
