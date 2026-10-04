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
        // 原业务方法已清除本地旧树，此处重新加载并同步Redis分类ID映射。
        categoryService.updateCategoryTreeRedisCache();

    }

}
