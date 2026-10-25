package com.lixiaopu.infrastructure.redis.connect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.Assert;

public final class RedisConnector {

    // 由 RedisConfig 初始化，业务代码通过类名直接调用。
    private static RedisTemplate<String, Object> template;
    private static ObjectMapper objectMapper;

    private RedisConnector() {
    }

    public static void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        Assert.notNull(redisTemplate, "RedisTemplate must not be null");
        RedisConnector.template = redisTemplate;
    }

    /** 初始化对象与 Hash 转换使用的 ObjectMapper。 */
    public static void initHashMapper(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");
        RedisConnector.objectMapper = objectMapper;
    }

    /** 将对象字段合并到 Hash，已有的其他字段不会被删除。 */
    public static void setHashObject(String key, Object object) {
        if (object == null) {
            return;
        }
        Map<String, Object> map = objectMapper.convertValue(object, new TypeReference<>() {});
        if (!map.isEmpty()) {
            opsForHash().putAll(key, map);
        }
    }

    public static void setHashObject(String key, Object object, long ttlSeconds) {
        Map<String, Object> map = objectMapper.convertValue(object, new TypeReference<>() {});
        setHashWithTtl(key, map, ttlSeconds);
    }

    /** 完整替换 Hash，与设置 TTL 在同一 Redis 事务中完成。 */
    public static void setHashWithTtl(String key, Map<String, Object> values, long ttlSeconds) {
        template.execute(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(org.springframework.data.redis.core.RedisOperations<K, V> operations) {
                operations.multi();
                operations.delete((K) key);
                operations.opsForHash().putAll((K) key, values);
                operations.expire((K) key, ttlSeconds, TimeUnit.SECONDS);
                return operations.exec();
            }
        });
    }

    /** 将 Hash 转为对象，键不存在时返回 null。 */
    public static <T> T getHashObject(String key, Class<T> clazz) {
        Map<String, Object> map = opsForHash().entries(key);
        return map.isEmpty() ? null : objectMapper.convertValue(map, clazz);
    }

    /** 获取单个字段，字段不存在或类型转换失败时返回 null。 */
    public static <T> T getHashField(String key, String field, Class<T> targetClass) {
        Object value = opsForHash().get(key, field);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, targetClass);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 获取单个字段，支持 List 等复杂泛型。 */
    public static <T> T getHashField(String key, String field, TypeReference<T> typeReference) {
        Object value = opsForHash().get(key, field);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, typeReference);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** key 为空或没有待添加的元素时，跳过 Set 写入。 */
    public static void safeAddToSet(String key, Object... values) {
        if (key == null || key.isBlank() || values == null || values.length == 0) {
            return;
        }
        opsForSet().add(key, values);
    }

    public static List<Object> executePipelined(SessionCallback<?> sessionCallback) {
        return template.executePipelined(sessionCallback);
    }

    public static ValueOperations<String, Object> opsForValue() {
        return template.opsForValue();
    }

    public static HashOperations<String, String, Object> opsForHash() {
        return template.opsForHash();
    }

    public static ListOperations<String, Object> opsForList() {
        return template.opsForList();
    }

    public static SetOperations<String, Object> opsForSet() {
        return template.opsForSet();
    }

    public static ZSetOperations<String, Object> opsForZSet() {
        return template.opsForZSet();
    }

    // 设置过期时间
    public static Boolean expire(String key, long ttl, TimeUnit unit) {
        return template.expire(key, ttl, unit);
    }

    /** 给历史永久缓存补 TTL；已有过期时间的缓存不续期。 */
    public static void expireIfPersistent(String key, long ttlSeconds) {
        if (Long.valueOf(-1).equals(template.getExpire(key))) {
            template.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }
    // 删除键

    public static Boolean delete(String key) {
        return template.delete(key);
    }

    public static Boolean hasKey(String key) {
        return template.hasKey(key);
    }

    /** 先写完整临时 Hash，再用 RENAME 原子替换；写入失败时保留旧缓存。 */
    public static void replaceHash(String key, Map<String, Object> values) {
        if (values.isEmpty()) {
            template.delete(key);
            return;
        }
        String temporaryKey = key + ":building:" + java.util.UUID.randomUUID();
        try {
            opsForHash().putAll(temporaryKey, values);
            template.rename(temporaryKey, key);
        } finally {
            template.delete(temporaryKey);
        }
    }
}
