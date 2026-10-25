package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.CategoryDTO;
import com.lixiaopu.service.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdminCategoryController {
    @Autowired
    private  CategoryService categoryService;

    /**
     * 获取分类树
     * @return
     */
    @GetMapping("/category/tree")
    public Result getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /**
     * 添加分类
     *
     * @return
     */
    @PostMapping("/admin/category/add")
    public Result addCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        return categoryService.addCategory(categoryDTO);
    }

    /**
     * 删除分类
     *
     * @param categoryId
     * @return
     */
    @DeleteMapping("/admin/category/{categoryId}")
    public Result deleteCategory(@PathVariable @Pattern(regexp = "^[1-9][0-9]*$", message = "分类 ID 必须为正整数") String categoryId) {
        return categoryService.deleteCategory(categoryId);
    }

    /**
     * 更新分类
     *
     * @param id
     * @return
     */
    @PutMapping("/admin/category/{id}")
    public Result updateCategoryInfo(@PathVariable @Pattern(regexp = "^[1-9][0-9]*$", message = "分类 ID 必须为正整数") String id,
                                     @Valid @RequestBody CategoryDTO categoryDTO) {
        return categoryService.updateCategoryInfo(id, categoryDTO);
    }

    /**
     * 更新分类状态
     *
     * @param id
     * @param status
     * @return
     */
    @PutMapping("/admin/category/{id}/status")
    public Result updateCategoryStatus(@PathVariable @Pattern(regexp = "^[1-9][0-9]*$", message = "分类 ID 必须为正整数") String id,
                                       @RequestParam @Pattern(regexp = "^[01]$", message = "状态只能为 0 或 1") String status) {
        return categoryService.updateCategoryStatus(id, status);
    }


}
