package com.lixiaopu.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 仅指定的用户端实例启用周期调度；不影响业务 Bean 和启动初始化。 */
@Configuration(proxyBeanMethods = false)
@Profile("user")
@ConditionalOnProperty(name = "jobs.enabled", havingValue = "true", matchIfMissing = false)
@EnableScheduling
public class JobSchedulingConfiguration {
}
