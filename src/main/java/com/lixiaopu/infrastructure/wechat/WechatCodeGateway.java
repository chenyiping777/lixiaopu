package com.lixiaopu.infrastructure.wechat;

import com.lixiaopu.common.exception.AuthException;

import com.lixiaopu.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

/**
 * 小程序前端调用`wx.login()`拿到`js_code`（临时 code）传给后端；
 * 后端拿这个 code，加上 appId、appSecret，
 * 请求微信官方接口，换取用户 openid。
 * 微信接口地址：`https://api.weixin.qq.com/sns/jscode2session`
 * <a href="https://api.weixin.qq.com/sns/jscode2session?appid=xxx&secret=yyy">...</a>
 *           └──协议┘└──域名──────────┘└──path────────────┘ └──查询参数────────────┘
 *            scheme    host            path                queryParam
 *            `uri` 是完整资源定位器（包含协议、域名、端口、路径、参数）
 *  */

//真实调用微信官方接口，用前端拿到的`code`换取微信`openid`
@Component
@Profile("!local-test")
public class WechatCodeGateway implements WechatGateway {
    @Value("${lixiaopu.wechat.app-id:}")
    private String appId;
    @Value("${lixiaopu.wechat.app-secret:}")
    private String appSecret;

    private final RestClient http=RestClient.create();
    @Override
    public String getOpenid(String code) {
        //校验配置，如果没填小程序账号密钥，直接抛异常，不让启动 / 执行。
        if (appId.isBlank() || appSecret.isBlank())
            throw new IllegalStateException("WeChat credentials missing");

        Map<?,?> response=http.get()
                .uri(
                        builder->builder.scheme("https").host("api.weixin.qq.com")
                        .path("/sns/jscode2session")
                        .queryParam("appid",appId)
                        .queryParam("secret",appSecret)
                        .queryParam("js_code",code)
                        .queryParam("grant_type","authorization_code")
                                //`grant_type` 是微信 jscode2session 接口强制要求的参数，
                                // 固定写死值 `authorization_code`，代表：用授权码模式换取会话信息，微信文档规定这个参数不能省略、值固定。
                                //这个接口就是用小程序 wx.login 拿到的 js_code（临时授权码）来换取 openid，所以 grant_type 固定填这个字符串。
                        .build()
                )
                .retrieve().body(Map.class);
        /*
        *retrieve()发起 HTTP 请求，拿到响应结果。
        *前面的`.get()`只是定义请求信息（地址、参数），
        *调用 retrieve 才真正发送 http 请求，等待微信服务器返回数据。
        *
        *`.body(Map.class)`
        * 把微信返回的 JSON 响应体，自动解析成 Java 的`Map`对象。
        * */


        if (response==null
                || !(response.get("openid") instanceof String id)
                || id.isBlank())
            //1. 取出response map 里 key 为`openid`的值
            //2. 判断这个值是不是 String 类型；
            //3. 如果是，自动把这个值强制转型，赋值给新变量 id。
            throw new AuthException(ResultCode.UNAUTHORIZED,"微信登录失败");
        return id;
    }
}
