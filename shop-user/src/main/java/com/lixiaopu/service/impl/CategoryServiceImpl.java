package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.mapper.CategoryMapper;
import com.lixiaopu.model.entity.Category;
import com.lixiaopu.service.CategoryService;
import org.springframework.stereotype.Service;

/**
* @author Mlpnk
* @description 针对表【category(商品分类)】的数据库操作Service实现
* @createDate 2026-09-22 11:16:17
*/
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category>
    implements CategoryService{

}




