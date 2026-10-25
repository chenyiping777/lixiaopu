package com.lixiaopu.infrastructure.mp.util;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

/**
 * 通用MyBatis 批量执行工具
 */

@Component
public class MyBatisBatchExecutor {

    @Autowired
    private  SqlSessionTemplate sqlSessionTemplate;

    /**
     * 批量执行SQL操作（封装BATCH模式）
     * @param action 要执行的批量操作（Function接口，替代匿名内部类）
     * @param <T> 返回值类型
     * @return 执行结果
     *
     * 创建一个 BATCH 模式的 SqlSession。
     * 执行你传入的操作 action，把多条 SQL 放进批处理队列。
     * 调用 commit()，执行队列中的 SQL 并提交。
     * 自动关闭会话；发生异常时，包装成“批量执行失败”异常抛出。
     */
    @Transactional(rollbackFor = Exception.class)
    public <T> T executeBatch(Function<SqlSession, T> action) {
        // 1. 开启BATCH模式SqlSession（不自动提交）
        try (SqlSession batchSession = sqlSessionTemplate.getSqlSessionFactory()
                .openSession(ExecutorType.BATCH, false)) {
            // 2. 执行批量操作
            T result = action.apply(batchSession);
            // 3. 批量提交
            batchSession.commit();
            return result;
        } catch (Exception e) {
            throw new RuntimeException("批量执行失败", e);
        }
    }
}