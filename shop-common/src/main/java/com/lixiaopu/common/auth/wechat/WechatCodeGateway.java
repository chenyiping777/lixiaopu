package com.lixiaopu.common.auth.wechat;

import com.lixiaopu.common.auth.security.AuthException;

import com.lixiaopu.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

/** WeChat Mini Program wx.login code exchange. Client-supplied openid is never accepted. */
@Component
@Profile("!local-test")
public class WechatCodeGateway implements WechatGateway {
    @Value("${lixiaopu.wechat.app-id:}") private String appId;
    @Value("${lixiaopu.wechat.app-secret:}") private String appSecret;
    private final RestClient http=RestClient.create();
    @Override public String openid(String code) {
        if (appId.isBlank() || appSecret.isBlank()) throw new IllegalStateException("WeChat credentials missing");
        Map<?,?> response=http.get().uri(builder->builder.scheme("https").host("api.weixin.qq.com")
            .path("/sns/jscode2session").queryParam("appid",appId).queryParam("secret",appSecret)
            .queryParam("js_code",code).queryParam("grant_type","authorization_code").build())
            .retrieve().body(Map.class);
        if (response==null || !(response.get("openid") instanceof String id) || id.isBlank())
            throw new AuthException(ResultCode.UNAUTHORIZED,"微信登录失败");
        return id;
    }
}
