package com.lixiaopu.aop.aspect;

import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import java.util.Objects;
import com.lixiaopu.job.delay.SaveCartRedisCacheDelayJob;


@Aspect
@Component
@Slf4j
public class SaveCartRedisCacheToMysqlAspect {

    @Autowired
    private SaveCartRedisCacheDelayJob saveCartRedisCacheDelayJob;

    @Autowired
    private CartService cartService;


    //切入点（Pointcut）：单独定义，命名复用
    //@Pointcut 注解里那串
    //@annotation(com.lixiaopu.aop.annotation.SaveCartRedisCacheToMysqlAnnotation)
    //才是真正的筛选规则（匹配"所有标了这个注解的方法"）。
    //但 Spring 规定：这个规则要挂在一个方法上，
    //那个方法就是 pointCut()——它方法体为空，没有逻辑，只是给这条规则起个名字，类似"给规则贴个标签"
    @Pointcut(value = "@annotation(com.lixiaopu.aop.annotation.SaveCartRedisCacheToMysqlAnnotation)")
    public void pointCut() {
    }


    /**
     * cart RedisCache 延迟存库
     * @param result
     */
    //通知
    @AfterReturning(pointcut = "pointCut()", returning = "result")
    public void afterReturnSuccess(Result result) {
        if (Objects.isNull(result)) {
            return;
        }
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return;
        }
        long userId = CurrentHolder.getCurrentUser().getId();
        try {
            saveCartRedisCacheDelayJob.setUserIdToDelayedQueue(userId);
        } catch (Exception enqueueError) {
            log.error("购物车延迟同步任务提交失败，立即同步 MySQL，用户ID: {}", userId, enqueueError);
            try {
                cartService.syncCartToMysql(userId);
            } catch (Exception syncError) {
                syncError.addSuppressed(enqueueError);
                throw new IllegalStateException("购物车保存失败，请稍后重试", syncError);
            }
        }
    }
}
