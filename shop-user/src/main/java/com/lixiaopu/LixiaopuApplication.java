package com.lixiaopu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 栗小铺启动入口。在 IDEA 中运行 main 方法即可启动。 */
@SpringBootApplication
@MapperScan("com.lixiaopu.mapper")
public class LixiaopuApplication {
    public static void main(String[] args) {
        SpringApplication.run(LixiaopuApplication.class, args);
    }
}
