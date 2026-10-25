package com.lixiaopu.aop.aspect;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.CategoryService;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Aspect
@Component
public class UpdateCategoryTreeRedisCacheAspect {

    @Autowired
    private CategoryService categoryService;

    @Pointcut(value = "@annotation(com.lixiaopu.aop.annotation.UpdateCategoryTreeRedisCacheAnnotation)")
    private void pointCut() {
    }

    //“怎么更新分类缓存”属于分类业务逻辑，而切面主要负责“什么时候触发更新”
    @AfterReturning(pointcut = "pointCut()", returning = "result")
    public void afterReturnSuccess(Result result) {
        // 只有当 result 不为空，并且 result.success == true（业务执行成功）的时候，才会执行
        if (Objects.isNull(result) || !Boolean.TRUE.equals(result.getSuccess())) {
            return;
        }
        // 业务成功后从数据库重建 Redis 分类 ID 映射，并使本地分类树失效。
        categoryService.updateCategoryTreeRedisCache();

    }

}
