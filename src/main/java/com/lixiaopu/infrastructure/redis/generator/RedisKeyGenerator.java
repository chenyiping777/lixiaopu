package com.lixiaopu.infrastructure.redis.generator;

import com.lixiaopu.infrastructure.redis.constant.RedisConstant;

public final class RedisKeyGenerator {
    public static String orderKey(String orderNo) {
        return "order:detail:orderNo:" + orderNo;
    }

    private RedisKeyGenerator() {}

    /**
     * cartKey 用户购物车
     * cart: + user: + {userId}
     * @param userId
     * @return
     */
    public static String cartKey(Long userId) {
        return RedisConstant.PREFIX_CART + RedisConstant.USER + userId;
    }

    /**
     * cartHashKey
     * product: + {productId} + "," + productSpec + {specId}
     * @return
     */
    public static String cartHashKey(Object productId, Object specId) {
        return productId + ":" + specId;
    }






    /**
     * categoryTreeKey
     * category: + {tree}
     * @return
     */
    public static String categoryTreeKey() {
        return RedisConstant.PREFIX_CATEGORY + RedisConstant.TREE;
    }

    /**
     * categoryTreeHashKey
     * firstCategory: + {firstCategoryId};
     * @param firstCategoryId
     * @return
     */
    public static String categoryTreeHashKey(Long firstCategoryId) {
        return "firstCategory:" + firstCategoryId;
    }



    /**
     * es最大商品id缓存
     * product: + maxProductId
     * @return value key
     */
    public static String maxProductId(){
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.MAX_PRODUCT_ID;
    }


    /**
     * productDetail
     * product: + detail: + productId
     * @param productId
     * @return
     */
    public static String productDetail(Long productId) {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.DETAIL + productId;
    }

    /**
     * productCollection
     * product: + collection: + productId
     * @param productId
     * @return
     */
    public static String productCollection(Long productId) {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.COLLECTION + productId;

    }


    /**
     * product: + {productId} + : +commentCount:
     * @param productId
     * @return
     */
    public static String productCommentCount(Long productId) {
        return RedisConstant.PREFIX_PRODUCT + productId + ":" + RedisConstant.COMMENT_COUNT;
    }

    /**
     * firstCommentKey
     * product: + firstComment: + {firstCommentId}
     * @param firstCommentId
     * @return
     */
    public static String firstCommentKey(Long firstCommentId) {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.FIRST_COMMENT + firstCommentId;
    }

    /**
     * secondCommentKey
     * product: + secondComment: + secondCommentId
     * @param secondCommentId
     * @return
     */
    public static String secondCommentKey(Long secondCommentId) {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.SECOND_COMMENT + secondCommentId;
    }


    /**
     * appendCommentKey
     * product: + appendComment: + firstComment: + firstCommentId
     * @param firstCommentId
     * @return
     */
    public static String appendCommentKey(Long firstCommentId) {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.APPEND_COMMENT + RedisConstant.FIRST_COMMENT + firstCommentId;
    }





    /**
     * productCommentUserIdList
     * product: + comment: + commentId + : + user: +idList
     * @param commentId
     * @return
     */
    public static String productCommentUserIdList(Long commentId) {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.COMMENT + commentId + ":" + RedisConstant.USER + RedisConstant.ID_LIST;
    }





    /**
     * hotProduct RedisKey
     * product:hot
     * @return
     */
    public static String hotProductKey() {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.HOT;
    }

    /**
     * hotProduct HashKey
     * id: + hotProductId
     * @param hotProductId
     * @return
     */
    public static String hotProductHashKey(Long hotProductId) {
        return RedisConstant.ID + hotProductId;
    }

    /**
     * hotProductIdList
     * product: + hot: + idList
     * @return
     */
    public static String hotProductIdList() {
        return RedisConstant.PREFIX_PRODUCT + RedisConstant.HOT + ":" + RedisConstant.ID_LIST;
    }


}
