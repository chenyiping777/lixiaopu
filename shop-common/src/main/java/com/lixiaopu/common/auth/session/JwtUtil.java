package com.lixiaopu.common.auth.session;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.lixiaopu.common.auth.security.AuthException;
import com.lixiaopu.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** 统一签发和校验访问令牌、刷新令牌。Redis 会话校验仍由 TokenService 完成。 */
@Component
public class JwtUtil {
    private static final String ALGORITHM = "HS256";
    private final AuthProperties authProperties;
    private final byte[] secret;

    public JwtUtil(AuthProperties authProperties) {
        this.authProperties = authProperties;
        String configuredSecret = authProperties.getJwtSecret();
        if (configuredSecret == null || configuredSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("LXP_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String create(long uid, String sid, String tokenType, Instant expiresAt) {
        return JWT.create()
                .setSigner(ALGORITHM, secret)
                .setIssuer(authProperties.getIssuer())
                .setExpiresAt(Date.from(expiresAt))
                .setPayload("uid", uid)
                .setPayload("sid", sid)
                .setPayload("tokenType", tokenType)
                .sign();
    }

    public TokenClaims parse(String token, String expectedType) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            if (!ALGORITHM.equals(jwt.getAlgorithm())) {
                throw new IllegalArgumentException("Unexpected JWT algorithm");
            }
            jwt.setSigner(ALGORITHM, secret);
            if (!jwt.validate(0) || jwt.getPayload("exp") == null) {
                throw new IllegalArgumentException("Invalid JWT signature or expiration");
            }
            if (!authProperties.getIssuer().equals(jwt.getPayload("iss"))
                    || !expectedType.equals(jwt.getPayload("tokenType"))) {
                throw new IllegalArgumentException("Unexpected JWT claims");
            }
            Object uid = jwt.getPayload("uid");
            Object sid = jwt.getPayload("sid");
            if (!(uid instanceof Number) || ((Number) uid).longValue() <= 0
                    || !(sid instanceof String) || ((String) sid).isBlank()) {
                throw new IllegalArgumentException("Invalid JWT identity");
            }
            return new TokenClaims(((Number) uid).longValue(), (String) sid);
        } catch (Exception ex) {
            throw new AuthException("refresh".equals(expectedType)
                    ? ResultCode.REFRESH_TOKEN_EXPIRED : ResultCode.UNAUTHORIZED);
        }
    }

    public static class TokenClaims {
        private final long uid;
        private final String sid;

        public TokenClaims(long uid, String sid) {
            this.uid = uid;
            this.sid = sid;
        }

        public long getUid() { return uid; }
        public String getSid() { return sid; }
    }
}
