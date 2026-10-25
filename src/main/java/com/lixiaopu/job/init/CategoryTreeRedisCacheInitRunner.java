package com.lixiaopu.job.init;

import com.lixiaopu.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 与栗子商城保持一致：应用启动时初始化分类 Redis 映射。 */
@Component
@RequiredArgsConstructor
public class CategoryTreeRedisCacheInitRunner implements ApplicationRunner {
    private final CategoryService categoryService;

    @Override
    public void run(ApplicationArguments args) {
        categoryService.updateCategoryTreeRedisCache();
    }
}
