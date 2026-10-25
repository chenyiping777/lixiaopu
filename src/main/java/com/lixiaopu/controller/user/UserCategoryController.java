package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserCategoryController {

    @Autowired
    private  CategoryService categoryService;


    @GetMapping("/api/category/tree")
    public Result getCategoryTree() {
        return categoryService.getCategoryTree();
    }


}
