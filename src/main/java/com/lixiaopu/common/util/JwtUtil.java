package com.lixiaopu.common.util;

import com.lixiaopu.properties.AuthProperties;
import com.lixiaopu.security.token.TokenService;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.lixiaopu.common.exception.AuthException;
import com.lixiaopu.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** 统一签发和校验访问令牌、刷新令牌。Redis 会话校验仍由 TokenService 完成。 */
@Component
public class JwtUtil {
    private static final String ALGORITHM = "HS256";
    @Autowired
    private  AuthProperties authProperties;
    private final byte[] secret;

    public JwtUtil(AuthProperties authProperties) {
        this.authProperties = authProperties;
        String configuredSecret = authProperties.getJwtSecret();
        if (configuredSecret == null || configuredSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("LXP_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String create(long id, String sid, String tokenType, Instant expiresAt) {
        return JWT.create()
                .setSigner(ALGORITHM, secret)
                .setIssuer(authProperties.getIssuer())
                .setExpiresAt(Date.from(expiresAt))
                .setPayload("uid", id)
                .setPayload("sid", sid)
                .setPayload("tokenType", tokenType)
                .sign();
    }

    //解析jwt，parse之后变成对象TokenClaims
    public TokenClaims parse(String token, String expectedType) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            // 校验算法
            if (!ALGORITHM.equals(jwt.getAlgorithm())) {
                throw new IllegalArgumentException("Unexpected JWT algorithm");
            }
            jwt.setSigner(ALGORITHM, secret);
            // 校验签名 + 过期时间
            if (!jwt.validate(0) || jwt.getPayload("exp") == null) {
                throw new IllegalArgumentException("Invalid JWT signature or expiration");
            }
            // 校验签发者 iss 和令牌类型 tokenType（access / refresh）
            if (!authProperties.getIssuer().equals(jwt.getPayload("iss"))
                    || !expectedType.equals(jwt.getPayload("tokenType"))) {
                throw new IllegalArgumentException("Unexpected JWT claims");
            }
            // 取出 uid、sid，做类型和合法性校验
            Object uid = jwt.getPayload("uid");
            Object sid = jwt.getPayload("sid");
            if (!(uid instanceof Number) || ((Number) uid).longValue() <= 0
                    || !(sid instanceof String) || ((String) sid).isBlank()) {
                throw new IllegalArgumentException("Invalid JWT identity");
            }
            // 封装成不可变的 TokenClaims 返回
            return new TokenClaims(((Number) uid).longValue(), (String) sid);
        } catch (Exception ex) {
            // 捕获所有异常，统一抛业务异常
            throw new AuthException("refresh".equals(expectedType)
                    ? ResultCode.REFRESH_TOKEN_EXPIRED : ResultCode.UNAUTHORIZED);
        }
    }



    //用来承载 JWT 令牌解析出来的核心载荷信息。
    public static class TokenClaims {
        private final long uid;//用户ID
        private final String sid;//会话ID
        //构造方法
        public TokenClaims(long uid, String sid) {
            this.uid = uid;
            this.sid = sid;
        }
        //获取用户ID
        public long getUid() { return uid; }
        //获取会话ID
        public String getSid() { return sid; }
    }
}
