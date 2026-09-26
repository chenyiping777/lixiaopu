package com.lixiaopu.common.auth.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

/** Configurable HTTPS SMS provider boundary; adapt the payload to the selected provider contract. */
@Component
@Profile("!local-test")
public class HttpSmsGateway implements SmsGateway {
    @Value("${lixiaopu.sms.endpoint:}")
    private String endpoint;
    @Value("${lixiaopu.sms.api-key:}")
    private String apiKey;
    @Value("${lixiaopu.sms.sign:}")
    private String sign;
    @Value("${lixiaopu.sms.template:}")
    private String template;

    private final RestClient http=RestClient.create();
    @Override public void send(String phone, String purpose, String code) {
        if (!endpoint.startsWith("https://") || apiKey.isBlank() || sign.isBlank() || template.isBlank())
            throw new IllegalStateException("SMS provider is not configured");
        http.post().uri(endpoint).header("Authorization","Bearer "+apiKey)
            .body(Map.of("phone",phone,"purpose",purpose,"code",code,"sign",sign,"template",template))
            .retrieve().toBodilessEntity();
    }
}
