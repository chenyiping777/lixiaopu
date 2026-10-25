package com.lixiaopu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.lixiaopu.common", "com.lixiaopu.infrastructure", "com.lixiaopu.properties",
        "com.lixiaopu.security", "com.lixiaopu.controller.admin", "com.lixiaopu.controller.tool", "com.lixiaopu.application",
        "com.lixiaopu.service", "com.lixiaopu.aop", "com.lixiaopu.job.init"
})
@MapperScan("com.lixiaopu.mapper")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AdminApplication.class);
        application.setAdditionalProfiles("admin");
        application.run(args);
    }
}
