package com.lixiaopu.properties;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {
    private boolean enabled = true;
    @Min(1) @Max(65535) private int port = 9090;
    @NotBlank private String host = "0.0.0.0";
    @Min(10) private int idleSeconds = 60;
    @Min(1) @Max(64) private int businessThreads = 8;
    /** 留空时使用当前主机名和 chat.port；生产填写反向代理的 wss 地址，页面按配置的业务端替换为对应握手路径。 */
    private String publicUrl = "";
    @NotEmpty private List<String> allowedOrigins = List.of("http://localhost:8081", "http://localhost:8082",
            "http://127.0.0.1:8081", "http://127.0.0.1:8082", "http://localhost:8080", "http://127.0.0.1:8080");
}
