package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.util.CaffeineUtils;
import com.lixiaopu.mapper.ProductSearchKeywordMapper;
import com.lixiaopu.pojo.entity.ProductSearchKeyword;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.service.ProductSearchKeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.ArrayList;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProductSearchKeywordServiceImpl extends ServiceImpl<ProductSearchKeywordMapper, ProductSearchKeyword>
        implements ProductSearchKeywordService {

    @Autowired
    private CaffeineUtils caffeineUtils;//查询热门搜索词和清除热门搜索词

    /**
     * 用户获取热门搜索关键词列表
     * 面向普通网站用户
     * - 数据源：优先读取 Caffeine 缓存（就是前面那个`hotProductSearchKeywordCache`），只有缓存失效的时候才会触发 CacheLoader 去查数据库
     * - 查询条件：只取 `isHot=启用 && isShow=启用` 的关键词（只拿配置为热门、允许展示的词）
     * - 业务处理：拿到关键词列表后随机打乱顺序，截取最多 5 条返回
     * - 目的：页面展示热门搜索，每次刷新顺序不一样，只展示 5 个，性能高（走本地缓存）
     */
    @Override
    public Result getProductSearchKeywordListUser() {
        List<String> hotProductSearchKeyword = new ArrayList<>(caffeineUtils.getHotProductSearchKeyword());
        Collections.shuffle(hotProductSearchKeyword);//随机打乱顺序
        List<String> resultList = hotProductSearchKeyword.stream().limit(5).toList();
        return Result.success(resultList);
    }

    /**
     * 这是底层 DB 查询方法：根据条件查询【启用展示 + 热门】的关键词，只提取关键词字符串返回。
     * `Caffeine LoadingCache` 的 CacheLoader 里面执行的查询逻辑
    * */
    public List<String> getHotProductSearchKeywordListUser(String cacheKey) {
        List<ProductSearchKeyword> list = lambdaQuery()
                .eq(ProductSearchKeyword::getIsShow, CommonStatus.ACTIVE.getNumber())
                .eq(ProductSearchKeyword::getIsHot, CommonStatus.ACTIVE.getNumber()).list();
        if (Objects.isNull(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(ProductSearchKeyword::getKeyword).collect(Collectors.toList());
    }

    /**
     * 管理员获取搜索关键词列表
     * 面向后台管理员
     * - 数据源：直接查询数据库，不走缓存
     * - 查询条件：无任何过滤，返回表里面全部记录，不管是否是热门、是否开启展示
     * - 返回内容：返回完整实体对象（包含 id、isHot、isShow 等所有字段），不是单纯关键词字符串
     * - 目的：后台管理页面，管理员要看到全部关键词，用来编辑、修改、全量覆盖更新
     */
    @Override
    public Result getProductSearchKeywordListAdmin() {
        List<ProductSearchKeyword> resultList = lambdaQuery().list();
        return Result.success(resultList);
    }

    /**
     * 管理员修改搜索关键词
     * @param productSearchKeywordList
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateProductSearchListAdmin(List<ProductSearchKeyword> productSearchKeywordList) {
        // 1. 删除数据库里全部的搜索关键词记录
        remove(new LambdaQueryWrapper<>());
        // 2. 批量插入前端传过来的新关键词列表
        if (!productSearchKeywordList.isEmpty()) {
            saveBatch(productSearchKeywordList);
        }
        // 3. 清除本地缓存中的热门搜索关键词缓存
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    caffeineUtils.invalidateHotProductSearchKeywordCache();
                }
            });
        } else {
            caffeineUtils.invalidateHotProductSearchKeywordCache();
        }
        return Result.success();
    }
}
