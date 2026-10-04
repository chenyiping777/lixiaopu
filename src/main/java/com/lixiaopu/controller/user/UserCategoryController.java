package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.CategoryService;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserCategoryController {

    @Autowired
    private  CategoryService categoryService;


    @GetMapping("/api/category/tree")
    public Result getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /**
     * 用户点击一级分类后，查询该分类下的所有启用子分类。
     *
     * @param categoryId 一级分类的数据库ID，不是列表索引
     * @return Result，成功时data为子分类列表；没有子分类时为空列表
     */
    @GetMapping("/api/category/{categoryId}/children")
    public Result getCategoryChildren(
            @PathVariable("categoryId")
            @Pattern(regexp = "^[1-9][0-9]*$", message = "分类 ID 必须为正整数")
            String categoryId) {
        return categoryService.getCategoryChildren(categoryId);
    }

    @GetMapping("/api/category/product/list/{categoryId}/{beginProductId}")
    public Result getCategoryProductList(@PathVariable String categoryId,
                                         @PathVariable String beginProductId,
                                         @RequestParam(defaultValue = "default") String sortType) {
        return categoryService.getCategoryProductList(categoryId, beginProductId, sortType);
    }
}
