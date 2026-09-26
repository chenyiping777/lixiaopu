package com.lixiaopu.common.auth.security;

import org.apache.shiro.authc.AuthenticationToken;
import lombok.Getter;

@Getter
public class JwtAuthenticationToken implements AuthenticationToken {
    private final String jwt;
    private final String device;

    public JwtAuthenticationToken(String jwt, String device) {
        this.jwt = jwt;
        this.device = device;
    }

    @Override
    public Object getPrincipal() {
        return jwt;
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }
}
