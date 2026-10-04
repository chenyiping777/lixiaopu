package com.lixiaopu.aop.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SaveCartRedisCacheToMysqlAnnotation {
    // 该注解用于标识需要将购物车缓存保存到MySQL的方法
}
