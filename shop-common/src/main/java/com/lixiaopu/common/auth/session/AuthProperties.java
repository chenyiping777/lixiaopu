package com.lixiaopu.common.auth.session;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "lixiaopu.auth")
public class AuthProperties {
    private String jwtSecret;
    private String issuer = "lixiaopu";
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(7);
    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String value) { jwtSecret=value; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer=value; }
    public Duration getAccessTtl() { return accessTtl; }
    public void setAccessTtl(Duration value) { accessTtl=value; }
    public Duration getRefreshTtl() { return refreshTtl; }
    public void setRefreshTtl(Duration value) { refreshTtl=value; }
}
