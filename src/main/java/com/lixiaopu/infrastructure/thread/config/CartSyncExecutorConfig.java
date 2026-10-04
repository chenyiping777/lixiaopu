package com.lixiaopu.infrastructure.thread.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CartSyncExecutorConfig {

    @Bean(name = "saveCartRedisCacheToMysqlThreadPool", destroyMethod = "shutdownNow")
    //`destroyMethod = "shutdownNow"`
    //Spring 容器销毁（项目停止）的时候，自动调用线程池的`shutdownNow()`方法，尝试停止里面正在运行的线程，释放资源。
    public ExecutorService cartSyncExecutor() {
        //创建一个单线程的线程池
        //这个池子里面，永远只维护 1 条工作线程，名字叫`cart-sync-consumer`
        //你往这个池子`execute()`提交任何任务，全部都交给这唯一一条线程串行执行
        //如果提交多个任务，任务会放在池子内部的队列排队，一个做完再跑下一个
        //重点：这个池子的 1 个线程，**只属于这个池子**，和 Tomcat 线程池不是同一个东西。
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "cart-sync-consumer");
            thread.setDaemon(true);
            //设置为守护线程
            //用户线程：只要还有一个活着，JVM 就不会关闭。
            //守护线程：只是辅助后台干活；一旦所有用户线程结束，守护线程会被 JVM 直接粗暴终止，不管有没有做完。
            return thread;
        });//task是thread对象
    }
}
