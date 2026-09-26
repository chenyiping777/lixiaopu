package com.lixiaopu.common.auth.wechat;

import com.lixiaopu.common.auth.security.AuthException;

import com.lixiaopu.common.result.ResultCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-test")
public class LocalWechatGateway implements WechatGateway {
    @Override public String openid(String code) {
        if (!code.startsWith("local:") || code.length()<7) throw new AuthException(ResultCode.UNAUTHORIZED);
        return "local-openid-"+code.substring(6);
    }
}
