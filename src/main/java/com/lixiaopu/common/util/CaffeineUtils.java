package com.lixiaopu.common.util;


import com.lixiaopu.common.constant.CaffeineConstant;
import com.lixiaopu.pojo.entity.Category;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public  class CaffeineUtils {

    @Autowired
    private LoadingCache<String, List<String>> hotProductSearchKeywordCache;

    @Autowired
    private LoadingCache<String, List<Category>> categoryTreeCache;

    //读取和清除本地缓存的工具类。它管理两份缓存：热门搜索关键词、分类树。

    /**
     * 查询热门搜索关键词
     */
    public  List<String> getHotProductSearchKeyword() {
        return hotProductSearchKeywordCache.get(CaffeineConstant.CACHE_KEY_HOT_PRODUCT_SEARCH_KEYWORD);
    }

    /**
     * 清除热门搜索关键词
     */
    public void invalidateHotProductSearchKeywordCache() {
        hotProductSearchKeywordCache.invalidate(CaffeineConstant.CACHE_KEY_HOT_PRODUCT_SEARCH_KEYWORD);
    }

    /**
     * 查询分类树
     */
    public List<Category> getCategoryTree() {
        return categoryTreeCache.get(CaffeineConstant.CACHE_KEY_CATEGORY_TREE);
    }

    /**
     * 删除分类树
     */
    public void invalidateCategoryTree() {
        categoryTreeCache.invalidate(CaffeineConstant.CACHE_KEY_CATEGORY_TREE);
    }




}
