package com.lixiaopu.infrastructure.caffeine.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.lixiaopu.common.constant.CaffeineConstant;
import com.lixiaopu.mapper.ProductSearchKeywordMapper;
import com.lixiaopu.pojo.entity.Category;
import com.lixiaopu.pojo.entity.ProductSearchKeyword;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.service.impl.CategoryServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地缓存创建及缓存未命中时的加载规则；两个启动类均扫描infrastructure。
 */
@Configuration
public class CaffeineConfig {

    @Bean
    public LoadingCache<String, List<Category>> categoryTreeCache(
            ObjectProvider<CategoryServiceImpl> categoryService) {
        //`@Bean` 方法的参数，默认自动注入，不需要写 `@Autowired`
        //`ObjectProvider<T>` 继承自 `ObjectFactory<T>`，所以它同样有 `getObject()` 方法，功能比 ObjectFactory 更强：
        //- 支持可选 bean（`getIfAvailable()`）
        //- 支持多个同类型 bean（`stream()`遍历）
        //- 同样：调用 getObject () 的时候，才去容器拿 bean 实例，实现延迟查找，解决循环依赖

        // 构建Caffeine缓存对象
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder();
        // 设置初始容量
        caffeineBuilder.initialCapacity(1);
        // 设置最大容量
        caffeineBuilder.maximumSize(1);
        // build 方法传入 CacheLoader，key不存在时执行加载逻辑
        LoadingCache<String, List<Category>>  loadingCache = caffeineBuilder.build(key -> {
            // 判断key是否为分类树缓存key
            if (!CaffeineConstant.CACHE_KEY_CATEGORY_TREE.equals(key)) {
                throw new IllegalArgumentException(CaffeineConstant.CACHE_KEY_NOT_VALID_ERROR);
            }
            // 缓存未命中才获取Service，避免创建缓存时产生循环依赖
            //CategoryServiceImpl依赖CaffeineUtils，CaffeineUtils依赖CaffeineConfig
            //当 Spring 在创建这个 Caffeine LoadingCache Bean 的时候，
            //就需要立刻注入、拿到 `CategoryService`，一旦两者互相依赖，就会出现循环依赖报错。
            //使用 ObjectFactory 延迟获取（方案
            //`ObjectFactory` 是 Spring 提供的延迟查找 bean的接口：
            //- 注入的时候，Spring 只注入一个 `ObjectFactory` 包装代理对象，不会立刻实例化 CategoryService
            //- 只有真正执行缓存加载逻辑（缓存 miss）的时候，才调用 `.getObject()` 去 Spring 容器里实时拿 CategoryService Bean
            //- 这样创建缓存对象阶段不需要实例化 CategoryService，打破循环依赖
            //区分两个完全不一样的 get
            //1. `ObjectFactory#getObject()`：Spring 接口方法，从容器获取 bean 实例，不是你的业务方法
            //2. `CategoryService#getCategoryTreeCache()`：你自己写的业务方法，查分类树
            return categoryService.getObject().getCategoryTreeCache();
        });

        return loadingCache;
    }

    @Bean
    public LoadingCache<String, List<String>> hotProductSearchKeywordCache(
            ProductSearchKeywordMapper keywordMapper) {
        return Caffeine.newBuilder().initialCapacity(1).maximumSize(1)
                .build(key -> {
                    if (!CaffeineConstant.CACHE_KEY_HOT_PRODUCT_SEARCH_KEYWORD.equals(key)) {
                        throw new IllegalArgumentException(CaffeineConstant.CACHE_KEY_NOT_VALID_ERROR);
                    }
                    List<ProductSearchKeyword> keywords = keywordMapper.selectList(
                            Wrappers.lambdaQuery(ProductSearchKeyword.class)
                                    .eq(ProductSearchKeyword::getIsHot, CommonStatus.ACTIVE)
                                    .eq(ProductSearchKeyword::getIsShow, CommonStatus.ACTIVE)
                                    .orderByAsc(ProductSearchKeyword::getId));
                    return new ArrayList<>(keywords.stream()
                            .map(ProductSearchKeyword::getKeyword).toList());
                });
    }
}
