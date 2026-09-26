package com.lixiaopu.common.auth.session;

import com.lixiaopu.common.auth.dto.AuthDtos;
import com.lixiaopu.common.auth.security.AuthException;

import com.lixiaopu.common.result.ResultCode;
import com.lixiaopu.model.entity.SysUser;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.model.enums.CommonStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class TokenService {
    @Data
    @AllArgsConstructor

    public static class Principal {
        private long uid;
        private String sid;
        private String device;
    }

    private final AuthProperties authProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final SecureRandom random = new SecureRandom();

    public TokenService(AuthProperties authProperties, StringRedisTemplate stringRedisTemplate,
                        SysUserMapper userMapper, JwtUtil jwtUtil) {
        this.authProperties = authProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    private String randomId() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String digest(String token) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private String sessionKey(String sid) { return "lxp:auth:session:"+sid; }
    private String indexKey(long uid) { return "lxp:auth:user:"+uid; }
    private String value(long uid, String device, long version, String refresh) {
        return uid+"|"+device+"|"+version+"|"+digest(refresh);
    }
    private SysUser active(long uid) {
        SysUser user=userMapper.selectById(uid);
        if (user==null || user.getIsEnable()!=CommonStatus.ACTIVE)
            throw new AuthException(ResultCode.UNAUTHORIZED);
        return user;
    }
    /**
     * 颁发登录令牌（accessToken + refreshToken）
     * @param uid 用户ID
     * @param device 登录设备标识 BUYER / ADMIN
     * @param expectedVersion 用户授权版本号 authVersion
     * @return Tokens 令牌对象，包含access、refresh、uid、会话sid
     */
    public AuthDtos.Tokens issue(Long uid, String device, Long expectedVersion) {
        // 1. 查询启用状态的用户，校验授权版本号
        SysUser user = active(uid);
        // expectedVersion 是签发token时传入的authVersion，和数据库最新authVersion比对
        // 版本不一致，代表用户触发过全量令牌吊销（改密码/禁用账号等），拒绝颁发令牌
        if (expectedVersion == null || !expectedVersion.equals(user.getAuthVersion())) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }

        // 2. 生成唯一会话ID sid，用于标记本次登录会话
        String sid = randomId();
        Instant now = Instant.now();

        // 3. 生成accessToken（短期业务访问令牌）、refreshToken（长期刷新令牌）
        String access = jwtUtil.create(uid, sid, "access", now.plus(authProperties.getAccessTtl()));
        String refresh = jwtUtil.create(uid, sid, "refresh", now.plus(authProperties.getRefreshTtl()));

        // refreshToken 的过期秒数
        long ttl = authProperties.getRefreshTtl().toSeconds();

        // 4. Redis Lua脚本：原子完成会话存储 + 用户会话索引维护 + 清理过期会话
        // KEYS[1] = sessionKey(sid)  单个会话的key，存储会话详情
        // KEYS[2] = indexKey(uid)   用户维度zset索引，保存该用户所有会话sid，score=过期时间戳
        // ARGV[1] = 会话序列化value(uid,device,expectedVersion,refresh)
        // ARGV[2] = 会话key的EX过期时间（秒）
        // ARGV[3] = ZADD score：sid的过期时间戳
        // ARGV[4] = sid
        // ARGV[5] = ZREMRANGEBYSCORE：清理所有score小于当前时间戳的过期sid
        // ARGV[6] = 用户会话zset的过期时间（比refresh有效期多3600s，兜底）
        // 所有 sid 对应的 session 字符串 key 一定先过期、消失，之后，才允许 zset 索引被 Redis 自动删除。
        //当这个用户所有会话全部过期，并且用户再也没有新登录，超过这个时间，整个 zset 直接被 Redis 自动删除，防止永久僵尸 key
        String script = """
            redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[2]);
            redis.call('ZADD',KEYS[2],ARGV[3],ARGV[4]);
            redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',ARGV[5]);
            redis.call('EXPIRE',KEYS[2],ARGV[6]);
            return 1
            """;

        // 执行Lua脚本，传入key列表和参数
        Long written = stringRedisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                List.of(sessionKey(sid), indexKey(uid)),
                value(uid, device, expectedVersion, refresh),
                Long.toString(ttl),
                Long.toString(now.getEpochSecond() + ttl),
                sid,
                Long.toString(now.getEpochSecond()),
                Long.toString(ttl + 3600)
        );

        // Lua脚本返回值不等于1，代表脚本执行异常，抛未授权异常
        if (!Long.valueOf(1).equals(written)) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }

        // 5. 二次校验authVersion（防并发：签发令牌过程中，authVersion被其他线程修改）
        // 如果在上面Lua执行期间，用户改密码导致authVersion+1，直接拒绝返回令牌
        if (!expectedVersion.equals(active(uid).getAuthVersion())) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }

        // 6. 返回令牌封装对象
        return new AuthDtos.Tokens(access, refresh, uid, sid);
    }

    public Principal verifyAccess(String token, String expectedDevice) {
        JwtUtil.TokenClaims claims = jwtUtil.parse(token, "access");
        long uid = claims.getUid(); String sid = claims.getSid();
        String session=stringRedisTemplate.opsForValue().get(sessionKey(sid));
        SysUser user=active(uid);
        if (session==null || !session.startsWith(uid+"|"+expectedDevice+"|"+user.getAuthVersion()+"|"))
            throw new AuthException(ResultCode.UNAUTHORIZED);
        return new Principal(uid,sid,expectedDevice);
    }
    public AuthDtos.Tokens rotate(String token, String device) {
        // 先校验刷新令牌及账号状态，再为新会话生成一对令牌。
        JwtUtil.TokenClaims claims = jwtUtil.parse(token, "refresh");
        long uid = claims.getUid();
        String oldSid = claims.getSid();
        SysUser user = active(uid);
        Long authVersion = user.getAuthVersion();
        String newSid = randomId();
        Instant now = Instant.now();
        long refreshTtlSeconds = authProperties.getRefreshTtl().toSeconds();

        String accessToken = jwtUtil.create(uid, newSid, "access", now.plus(authProperties.getAccessTtl()));
        String refreshToken = jwtUtil.create(uid, newSid, "refresh", now.plus(authProperties.getRefreshTtl()));
        String oldSessionValue = value(uid, device, authVersion, token);
        String newSessionValue = value(uid, device, authVersion, refreshToken);

        // KEYS: 旧会话、新会话、用户会话索引。
        // ARGV: 旧会话值、新会话值、会话 TTL、旧 sid、新会话过期时间、新 sid、当前时间、索引 TTL。
        // 比较旧会话值和替换会话必须原子执行，避免同一刷新令牌被并发使用两次。
        String script = """
                if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                    return 0
                end
                redis.call('DEL', KEYS[1])
                redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
                redis.call('ZREM', KEYS[3], ARGV[4])
                redis.call('ZADD', KEYS[3], ARGV[5], ARGV[6])
                redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[7])
                redis.call('EXPIRE', KEYS[3], ARGV[8])
                return 1
                """;

        Long rotated = stringRedisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                List.of(sessionKey(oldSid), sessionKey(newSid), indexKey(uid)),
                oldSessionValue,
                newSessionValue,
                Long.toString(refreshTtlSeconds),
                oldSid,
                Long.toString(now.getEpochSecond() + refreshTtlSeconds),
                newSid,
                Long.toString(now.getEpochSecond()),
                Long.toString(refreshTtlSeconds + 3600)
        );
        if (!Long.valueOf(1).equals(rotated)) {
            throw new AuthException(ResultCode.REFRESH_TOKEN_EXPIRED);
        }

        // 刷新期间若发生改密或禁用，拒绝把新令牌返回给客户端。
        if (!authVersion.equals(active(uid).getAuthVersion())) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }
        return new AuthDtos.Tokens(accessToken, refreshToken, uid, newSid);
    }
    public void logout(Principal principal) {
        String script = "redis.call('DEL',KEYS[1]);redis.call('ZREM',KEYS[2],ARGV[1]);return 1";
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(script,Long.class),
            List.of(sessionKey(principal.getSid()),
                    indexKey(principal.getUid())),
                    principal.getSid());
    }
    public void revokeAll(long uid) {
        String script="local ids=redis.call('ZRANGE',KEYS[1],0,-1);for _,sid in ipairs(ids) do redis.call('DEL',ARGV[1]..sid) end" +
                ";redis.call('DEL',KEYS[1]);return #ids";
        stringRedisTemplate.execute(new DefaultRedisScript<>(script,Long.class),List.of(indexKey(uid)),"lxp:auth:session:");
    }
}
