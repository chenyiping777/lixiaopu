package com.lixiaopu.infrastructure.redis.generator;

public final class RedisKeyGenerator {
    private static final String CART_KEY_PREFIX = "lxp:cart:";
    private static final String CATEGORY_TREE_KEY = "lxp:category:tree";

    private RedisKeyGenerator() {
    }

    public static String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    public static String cartHashKey(Object productId, Object specId) {
        return productId + ":" + specId;
    }

    /** 分类ID映射的Redis Hash键。 */
    public static String categoryTreeKey() {
        return CATEGORY_TREE_KEY;
    }

    /** Hash字段：一个一级分类对应一个二级分类ID列表。 */
    public static String categoryTreeHashKey(Long firstCategoryId) {
        return "firstCategory:" + firstCategoryId;
    }
}
