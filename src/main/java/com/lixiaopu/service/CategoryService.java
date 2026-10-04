package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.CategoryDTO;
import com.lixiaopu.pojo.entity.Category;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author Mlpnk
* @description 针对表【category(商品分类)】的数据库操作Service
* @createDate 2026-09-22 11:16:17
*/
public interface CategoryService extends IService<Category> {

    Result getCategoryTree();
    Result getCategoryChildren(String categoryId);
    Result addCategory(CategoryDTO categoryDTO);
    Result deleteCategory(String categoryId);
    Result updateCategoryInfo(String id,CategoryDTO categoryDTO);
    Result updateCategoryStatus(String id, String status);
    void updateCategoryTreeRedisCache();
}
