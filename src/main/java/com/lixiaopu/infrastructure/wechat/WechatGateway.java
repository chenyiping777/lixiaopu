package com.lixiaopu.infrastructure.wechat;

//传入微信临时授权 code，返回 openid。实际实现类：WechatCodeGateway
public interface WechatGateway {
    String getOpenid(String temporaryCode);
}
