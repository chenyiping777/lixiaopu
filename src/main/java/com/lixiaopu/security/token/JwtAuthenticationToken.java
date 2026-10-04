package com.lixiaopu.security.token;

import org.apache.shiro.authc.AuthenticationToken;
import lombok.Getter;

@Getter
public class JwtAuthenticationToken implements AuthenticationToken {
    //authenticationToken是shiro原生顶层接口，定义了登录令牌该有的行为：获取凭证，获取主体等
    //内置实现类：UsernamePasswordToken：账号+密码
    //自定义JwtAuthenticationToken，用jwt登录

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
