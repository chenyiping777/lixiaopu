package com.lixiaopu.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wxpay")
public class WxPayProperties {
    private boolean enabled;
    private String merchantId;
    private String apiV3Key;
    private String certSerialNumber;
    private String privateKeyPath;
    private String appId;
    private String notifyUrl;
    private String refundNotifyUrl;
}
