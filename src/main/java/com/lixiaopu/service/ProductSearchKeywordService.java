package com.lixiaopu.service;


import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.entity.ProductSearchKeyword;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface ProductSearchKeywordService extends IService<ProductSearchKeyword> {
    /**
     * 用户获取热门搜索关键词列表
     * @return
     */
    Result getProductSearchKeywordListUser();
    /**
     * 管理员获取搜索关键词列表
     * @return
     */
    Result getProductSearchKeywordListAdmin();
    /**
     * 管理员修改搜索关键词
     * @param productSearchKeywordList
     * @return
     */
    Result updateProductSearchListAdmin(@NotNull List<ProductSearchKeyword> productSearchKeywordList);
}

