package com.lixiaopu.service;

import com.lixiaopu.pojo.dto.AuthDtos;
import com.lixiaopu.common.exception.AuthException;
import com.lixiaopu.common.context.AuthContext;
import com.lixiaopu.security.token.TokenService;
import com.lixiaopu.infrastructure.sms.SmsService;
import com.lixiaopu.infrastructure.wechat.WechatGateway;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.ResultCode;
import com.lixiaopu.mapper.AuthMapper;
import com.lixiaopu.mapper.SysRoleMapper;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.mapper.SysUserRoleMapper;
import com.lixiaopu.pojo.entity.SysRole;
import com.lixiaopu.pojo.entity.SysUser;
import com.lixiaopu.pojo.entity.SysUserRole;
import com.lixiaopu.pojo.enums.CommonStatus;
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
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService extends ServiceImpl<SysUserMapper, SysUser> {
    private static final byte SYS_ADMIN_TYPE = 1;
    private static final byte ADMIN_TYPE = 2;
    private static final byte BUYER_TYPE = 3;

    private static final String SYS_ADMIN_ROLE = "SYS_ADMIN";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String BUYER_ROLE = "BUYER";

    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private SysRoleMapper roleMapper;
    @Autowired
    private SysUserRoleMapper userRoleMapper;
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
        return getOne(Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getPhone, phone));
    }

    private SysUser findByOpenid(String openid) {
        return getOne(Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getOpenid, openid));
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


    /**
     * 创建用户
     *
     * @param username 用户名
     * @param rawPassword 明文密码
     * @param phone 手机号
     * @param openid 微信openid
     * @param userType 用户类型
     * @param roleCode 角色码
     * @return 用户
     * 注册
     *   createUser(request.getUsername(), request.getPassword(), request.getPhone(),
     *               null, BUYER_TYPE, BUYER_ROLE);
     *
     */
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
        save(user);
        SysRole role = roleMapper.selectOne(Wrappers.lambdaQuery(SysRole.class)
                .select(SysRole::getId)
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getIsEnable, 1));
        if (role == null) {
            throw new IllegalStateException("请先执行 02-roles.sql 初始化角色");
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
        return user;
    }

    @Transactional
    public Result register(AuthDtos.Register request) {
        //校验验证码
        smsService.consume(request.getPhone(), "REGISTER", request.getCode());

        try {
            createUser(request.getUsername(), request.getPassword(), request.getPhone(),
                    null, BUYER_TYPE, BUYER_ROLE);
        } catch (DuplicateKeyException ex) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "无法完成注册");
        }
        return Result.success();
    }

    //手机号密码登录
    public Result login(AuthDtos.Login request, String device) {
        // 根据手机号查询用户
        SysUser user = findByPhone(request.getPhone());
        // 验证用户是否存在且已启用，密码是否匹配，设备是否允许登录，后面颁发token的时候还需要再校验一次，
        // 因为这两个操作时间有间隔，可能用户已经禁用或者密码已经修改
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_EXIST);
        }
        // 验证用户是否已启用
        //从数据库里验证用户是否是前面传回来的device
        if (user.getIsEnable() != CommonStatus.ACTIVE||!canLogin(user, device)) {
            throw new AuthException(ResultCode.LOGIN_ERROR);
        }

        // 确定用户存在并且启用，然后验证密码或验证码
        if(request.getPassword()==null&&request.getCode()!=null){
            // 验证码登录
            smsService.consume(request.getPhone(), "LOGIN", request.getCode());
        }else{
            //验证密码
            if (!matchesPassword(request.getPassword(), user.getPassword())) {
                throw new AuthException(ResultCode.LOGIN_ERROR);
            }
        }

        // 更新登录时间
        updateLoginTime(user);
        return tokenService.issue(user.getId(), device, user.getAuthVersion());
    }

    //验证用户是否允许登录，根据用户类型和设备
    private boolean canLogin(SysUser user, String device) {
        Byte userType = user.getUserType();
        // 用户类型为空，直接禁止登录
        if (userType == null) {
            return false;
        }
        if (BUYER_ROLE.equals(device)) {
            // 买家端登录：用户类型必须是买家，并且拥有买家角色
            return userType == BUYER_TYPE && hasRole(user.getId(), BUYER_ROLE);
        } else if (ADMIN_ROLE.equals(device)) {
            // 管理后台登录分支
            if (userType == SYS_ADMIN_TYPE) {
                // 超级管理员：校验是否拥有超级管理员角色
                return hasRole(user.getId(), SYS_ADMIN_ROLE);
            } else if (userType == ADMIN_TYPE) {
                // 普通管理员：类型匹配 + 拥有管理员角色
                return hasRole(user.getId(), ADMIN_ROLE);
            } else {
                // 其他类型用户（如买家）禁止登录后台
                return false;
            }
        } else {
            // 未知终端，不允许登录
            return false;
        }
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
        updateById(update);//空值不更新，只更新非空值上次登录时间
    }

    @Transactional
    public Result wechat(String code) {
        // 通过微信授权码获取openid
        String openid = wechatGateway.getOpenid(code);
        // 通过openid查询用户
        SysUser user = findByOpenid(openid);
        // 如果用户不存在，则创建用户
        if (user == null) {
            String username = "wx_" + UUID.randomUUID().toString().replace("-", "");
            // 创建用户
            try {
                user = createUser(username, null, null, openid, BUYER_TYPE, BUYER_ROLE);
            } catch (DuplicateKeyException ex) {
                //要插入的这条记录，唯一字段的值已经存在于数据库里，不能重复。
                // 两个相同微信身份的请求同时创建时，使用已创建的账号。
                // 通过openid查询用户
                user = findByOpenid(openid);
            }
        }
        // 验证用户是否已启用且允许登录
        if (user == null || user.getIsEnable() != CommonStatus.ACTIVE
                || !canLogin(user, BUYER_ROLE)) {
            throw new AuthException(ResultCode.LOGIN_ERROR);
        }

        // 更新登录时间
        updateLoginTime(user);
        // 颁发token
        return tokenService.issue(user.getId(), BUYER_ROLE, user.getAuthVersion());
    }

    //发送短信验证码
    public Result sendSms(String phone, String purpose) {
        String code = smsService.send(phone, purpose);
        return Result.success(code==null?null: Map.of("localTestCode",code));
    }

    //验证重置密码的验证码
    public String verifyReset(String phone, String code) {
        // 验证验证码
        smsService.consume(phone, "RESET_PASSWORD", code);
        // 通过手机号查询用户
        SysUser user = findByPhone(phone);
        // 生成重置凭据
        byte[] bytes = new byte[32];
        // 生成32字节的随机字节数组
        secureRandom.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // 将用户ID和凭据关联，并设置有效期为10分钟
        String userId = (user == null) ? "0" : user.getId().toString();
        stringRedisTemplate.opsForValue().set("lxp:reset:" + ticket, userId, Duration.ofMinutes(10));
        return ticket;
    }

    public Result resetPassword(String ticket, String newPassword) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>(
                "return redis.call('GETDEL',KEYS[1])", String.class);
        String userId = stringRedisTemplate.execute(script, List.of("lxp:reset:" + ticket));
        // Redis GETDEL 找不到 key 时返回 null。
        if (userId == null || userId.isBlank()) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "重置凭据无效或已使用");
        }
        long uid = Long.parseLong(userId);
        if (uid == 0) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "重置凭据无效或已使用");
        }
        // 凭据有效，重置密码并撤销所有令牌
        replacePasswordAndRevoke(uid, newPassword);
        return Result.success();
    }

    public Result changePassword(long uid, String oldPassword, String newPassword) {
        SysUser user = getById(uid);
        if (user == null || !matchesPassword(oldPassword, user.getPassword())) {
            throw new AuthException(ResultCode.LOGIN_ERROR);
        }
        replacePasswordAndRevoke(uid, newPassword);
        return Result.success();
    }

    private void replacePasswordAndRevoke(long uid, String newPassword) {
        String passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        if (!lambdaUpdate().eq(SysUser::getId, uid)
                .set(SysUser::getPassword, passwordHash)
                .setIncrBy(SysUser::getAuthVersion, 1)
                .update()) {
            throw new AuthException(ResultCode.USER_NOT_EXIST);
        }
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
        SysUser user = getById(uid);
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_EXIST);
        }

        SysUser update = new SysUser();
        update.setId(uid);
        update.setIsEnable(CommonStatus.INACTIVE);
        updateById(update);
        tokenService.revokeAll(uid);
    }

    public Result refresh(String refreshToken, String device) {
        return Result.success(tokenService.rotate(refreshToken, device));
    }

    public Result logout() {
        tokenService.logout(AuthContext.get());
        return Result.success();
    }
}
