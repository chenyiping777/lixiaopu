package com.lixiaopu.common.config;

import com.lixiaopu.properties.WxPayProperties;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
public class WechatPayConfig {
    @Bean @Lazy
    public RSAAutoCertificateConfig wxPayConfig(WxPayProperties p) {
        return new RSAAutoCertificateConfig.Builder().merchantId(p.getMerchantId())
                .privateKeyFromPath(p.getPrivateKeyPath()).merchantSerialNumber(p.getCertSerialNumber())
                .apiV3Key(p.getApiV3Key()).build();
    }
    @Bean @Lazy
    public JsapiServiceExtension jsapiService(RSAAutoCertificateConfig config) {
        return new JsapiServiceExtension.Builder().config(config).build();
    }
    @Bean @Lazy
    public NotificationParser notificationParser(RSAAutoCertificateConfig config) {
        return new NotificationParser(config);
    }
    @Bean @Lazy
    public com.wechat.pay.java.service.refund.RefundService refundService(RSAAutoCertificateConfig config) {
        return new com.wechat.pay.java.service.refund.RefundService.Builder().config(config).build();
    }
}
