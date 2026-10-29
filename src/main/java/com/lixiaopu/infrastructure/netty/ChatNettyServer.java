package com.lixiaopu.infrastructure.netty;

import com.lixiaopu.infrastructure.netty.handler.ChatHandler;
import com.lixiaopu.infrastructure.netty.handler.JwtAuthHandler;
import com.lixiaopu.infrastructure.netty.manager.UserChannelManager;
import com.lixiaopu.properties.ChatProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** 买家和管理端共用用户进程的客服网关，避免两个进程的本地连接表彼此隔离。 */
@Slf4j
@Component
@Profile("user")
@ConditionalOnProperty(name = "chat.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ChatNettyServer implements SmartLifecycle {
    //`SmartLifecycle`：Spring 的生命周期接口。
    // Spring 启动完会自动调用 `start()` 启动 Netty 聊天服务；
    // Spring 关闭的时候自动调用 `stop()` 停掉 Netty、释放端口、断开所有在线用户。
    private final ChatProperties properties;
    private final JwtAuthHandler authentication;
    private final ChatHandler chat;
    private final UserChannelManager channels;
    private EventLoopGroup boss, workers;
    private DefaultEventExecutorGroup business;
    private Channel server;
    private final DefaultChannelGroup connections = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private volatile boolean running;


    /**
     * HTTP -> HttpServerCodec 编解码
     * -> 聚合器 -> 空闲检测（超时踢下线）-> 业务线程池执行 JWT 鉴权
     * -> WebSocket 握手协议处理器 -> WebSocket 帧聚合
     * -> 业务线程池执行 ChatHandler 聊天逻辑。
    * */
    @Override
    public synchronized void start() {
        if (running) return;
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup(2);
        // 数据库和 Redis 操作离开 I/O 线程；有界队列避免无限积压。
        business = new DefaultEventExecutorGroup(properties.getBusinessThreads(), new DefaultThreadFactory("chat-business"),
                1024, RejectedExecutionHandlers.reject());
        try {
            server = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024).childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32768, 65536))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override // 每来一个新客户端连接，就走这里，组装这条连接的处理器链 pipeline
                        protected void initChannel(SocketChannel ch) {
                            connections.add(ch);
                            ch.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(16384),
                                    new IdleStateHandler(properties.getIdleSeconds(), 0, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(business, "jwt", authentication);
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler(WebSocketServerProtocolConfig.newBuilder()
                                    // JwtAuthHandler 已将外部入口严格限制为买家和管理端两条路径。
                                    .websocketPath("/ws").checkStartsWith(true).allowExtensions(false).maxFramePayloadLength(16384)
                                    .handshakeTimeoutMillis(10000).build()), new WebSocketFrameAggregator(16384));
                            ch.pipeline().addLast(business, "chat", chat);
                        }
                    }).bind(properties.getHost(), properties.getPort()).sync().channel();
            running = true;
            log.info("客服 WebSocket 已启动，端口 {}，路径 /ws/buyer/chat、/ws/admin/chat", boundPort());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            stop();
            throw new IllegalStateException("客服 WebSocket 启动失败", failure);
        }
    }
    @Override
    public synchronized void stop() {

        //先停止接收新连接、踢掉在线用户，再慢慢关闭业务线程池，最后关闭接待线程。
        running = false;
        if (server != null) server.close().awaitUninterruptibly();
        channels.closeAll();
        connections.close().awaitUninterruptibly();
        // Pipeline 销毁会在业务线程和 I/O 线程间切换，两组同时留出安静期处理移除回调。
        var workerStop = workers == null ? null : workers.shutdownGracefully(200, 5000, TimeUnit.MILLISECONDS);
        var businessStop = business == null ? null : business.shutdownGracefully(200, 5000, TimeUnit.MILLISECONDS);
        if (workerStop != null) workerStop.awaitUninterruptibly();
        if (businessStop != null) businessStop.awaitUninterruptibly();
        if (boss != null) boss.shutdownGracefully(0, 5, TimeUnit.SECONDS).awaitUninterruptibly();
    }
    @Override
    public boolean isRunning() { return running; }
    int boundPort() { return ((InetSocketAddress) server.localAddress()).getPort(); }
}
