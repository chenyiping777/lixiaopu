package com.lixiaopu.common.util;

import com.lixiaopu.properties.AuthProperties;

import com.lixiaopu.common.exception.AuthException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private final AuthProperties properties = new AuthProperties();

    private JwtUtil jwtUtil() {
        properties.setJwtSecret("this-is-a-long-random-test-secret-of-32-bytes");
        return new JwtUtil(properties);
    }

    @Test void validatesSignatureExpirationAndTokenType() {
        JwtUtil jwt = jwtUtil();
        String token = jwt.create(7L, "session-1", "access", Instant.now().plusSeconds(60));

        assertEquals(7L, jwt.parse(token, "access").getUid());
        assertEquals("session-1", jwt.parse(token, "access").getSid());
        assertThrows(AuthException.class, () -> jwt.parse(token, "refresh"));

        String changed = token.substring(0, token.length() - 2) + "xx";
        assertThrows(AuthException.class, () -> jwt.parse(changed, "access"));

        String expired = jwt.create(7L, "session-1", "access", Instant.now().minusSeconds(60));
        assertThrows(AuthException.class, () -> jwt.parse(expired, "access"));
    }

    @Test void requiresLongEnoughSecret() {
        properties.setJwtSecret("short");
        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }
}
