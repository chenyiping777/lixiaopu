package com.lixiaopu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Map;

@SpringBootApplication(scanBasePackages = {
        "com.lixiaopu.common", "com.lixiaopu.infrastructure", "com.lixiaopu.properties",
        "com.lixiaopu.security", "com.lixiaopu.controller.admin", "com.lixiaopu.application",
        "com.lixiaopu.service", "com.lixiaopu.aop"
})
@MapperScan("com.lixiaopu.mapper")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AdminApplication.class);
        application.setAdditionalProfiles("admin");
        application.setDefaultProperties(Map.of(
                "server.port", "8082",
                "spring.web.resources.static-locations", "classpath:/admin-static/"));
        application.run(args);
    }
}
