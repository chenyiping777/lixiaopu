package com.lixiaopu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Map;

/** 栗小铺启动入口。在 IDEA 中运行 main 方法即可启动。 */
@SpringBootApplication(scanBasePackages = {
        "com.lixiaopu.common", "com.lixiaopu.infrastructure", "com.lixiaopu.properties",
        "com.lixiaopu.security", "com.lixiaopu.controller.user", "com.lixiaopu.controller.tool",
        "com.lixiaopu.service", "com.lixiaopu.aop", "com.lixiaopu.job"
})
@MapperScan("com.lixiaopu.mapper")
public class LixiaopuApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LixiaopuApplication.class);
        application.setAdditionalProfiles("user");
        application.setDefaultProperties(Map.of(
                "server.port", "8081",
                "spring.web.resources.static-locations", "classpath:/static/"));
        application.run(args);
    }
}
