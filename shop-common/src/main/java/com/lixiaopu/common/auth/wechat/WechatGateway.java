package com.lixiaopu.common.auth.wechat;

public interface WechatGateway {
    String openid(String temporaryCode);
}
