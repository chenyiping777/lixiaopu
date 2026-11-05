package com.lixiaopu.infrastructure.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixiaopu.common.exception.AuthException;
import com.lixiaopu.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 使用小程序临时 code 换取 OpenID；微信可能以 text/plain 返回 JSON。 */
@Component
@Profile("!local-test")
@Slf4j
public class WechatCodeGateway implements WechatGateway {
    @Value("${lixiaopu.wechat.app-id:}")
    private String appId;
    @Value("${lixiaopu.wechat.app-secret:}")
    private String appSecret;
    private final RestClient http;
    private final ObjectMapper json;

    public WechatCodeGateway(RestClient.Builder builder, ObjectMapper json) {
        this.http = builder.build();
        this.json = json;
    }

    @Override
    public String getOpenid(String code) {
        if (appId.isBlank() || appSecret.isBlank())
            throw new AuthException(ResultCode.ERROR, "微信登录配置缺失，请联系管理员");
        String body;
        try {
            body = http.get().uri(builder -> builder.scheme("https").host("api.weixin.qq.com")
                    .path("/sns/jscode2session").queryParam("appid", appId)
                    .queryParam("secret", appSecret).queryParam("js_code", code)
                    .queryParam("grant_type", "authorization_code").build())
                    .retrieve().body(String.class);
        } catch (RestClientException ex) {
            // 异常消息可能带有含 AppSecret 的请求 URL，不记录异常原文。
            log.warn("微信登录接口请求失败，异常类型={}", ex.getClass().getSimpleName());
            throw new AuthException(ResultCode.ERROR, "微信登录接口连接失败，请稍后重试");
        }
        JsonNode response;
        try {
            response = body == null ? null : json.readTree(body);
        } catch (java.io.IOException ex) {
            throw new AuthException(ResultCode.ERROR, "微信登录接口返回格式异常，请稍后重试");
        }
        if (response == null || !response.isObject())
            throw new AuthException(ResultCode.ERROR, "微信登录接口返回为空，请稍后重试");
        int error = response.path("errcode").asInt(0);
        if (error != 0) {
            log.warn("微信登录凭证校验失败，errcode={}", error);
            String message = switch (error) {
                case 40029, 40163 -> "微信登录凭证无效或已使用，请重新点击微信登录";
                case 40013, 40125, 40001 -> "微信登录配置校验失败，请联系管理员核对 AppID 和 AppSecret";
                case 45011 -> "微信登录过于频繁，请稍后重试";
                default -> "微信登录失败，请稍后重试（错误码：" + error + "）";
            };
            throw new AuthException(ResultCode.LOGIN_ERROR, message);
        }
        JsonNode openid = response.get("openid");
        if (openid == null || !openid.isTextual() || openid.asText().isBlank())
            throw new AuthException(ResultCode.LOGIN_ERROR, "微信登录响应缺少用户标识，请重新登录");
        return openid.asText();
    }
}
