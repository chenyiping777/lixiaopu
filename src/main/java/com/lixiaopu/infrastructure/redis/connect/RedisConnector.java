package com.lixiaopu.infrastructure.redis.connect;

import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

@Component
public final class RedisConnector {

    private final RedisTemplate<String, Object> template;
    //可以直接用RedisTemplate，
    //`RedisTemplate`默认序列化器是 JDK 序列化，
    //存进去是二进制乱码，Redis 客户端看不了，而且要求实体实现 Serializable，缺点很多。
    //当前在`RedisConnector`的构造器里面一次性配置好：
    //- key：String 序列化
    //- hashKey：String 序列化
    //- hashValue：Jackson JSON 序列化，直接存 Java 对象 CartItem
    //`StringRedisTemplate` 只能value 存字符串；
    public RedisConnector(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> configured = new RedisTemplate<>();
        // 配置 Redis 连接工厂
        configured.setConnectionFactory(connectionFactory);
        // 配置 key 序列化器
        configured.setKeySerializer(new StringRedisSerializer());
        // 配置 hashKey 序列化器
        configured.setHashKeySerializer(new StringRedisSerializer());
        // 配置 hashValue 序列化器
        configured.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        // 初始化配置
        configured.afterPropertiesSet();
        // 返回配置好的 RedisTemplate
        this.template = configured;
    }

    // Hash 操作
    public HashOperations<String, String, Object> opsForHash() {
        return template.opsForHash();
    }

    // 设置过期时间
    public void expire(String key, long ttl, TimeUnit unit) {
        template.expire(key, ttl, unit);
    }
    // 删除键

    public void delete(String key) {
        template.delete(key);
    }
}
