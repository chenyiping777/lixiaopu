package com.lixiaopu.frontend;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** 无数据库依赖的前端入口：静态商城、管理台及同源 API 网关。 */
public final class FrontendApplication {
    private FrontendApplication() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(option(args, "port", "8080"));
        String host = option(args, "host", "127.0.0.1");
        URI buyer = origin(option(args, "buyer", "http://127.0.0.1:8081"));
        URI admin = origin(option(args, "admin", "http://127.0.0.1:8082"));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup workers = new NioEventLoopGroup();
        // 类路径资源读取不占用网络事件循环。
        var resources = new io.netty.util.concurrent.DefaultEventExecutorGroup(2);
        try {
            Channel server = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new ReadTimeoutHandler(40));
                            channel.pipeline().addLast(new HttpServerCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(32 * 1024 * 1024));
                            channel.pipeline().addLast(resources, new FrontendHandler(client, buyer, admin));
                        }
                    }).bind(host, port).sync().channel();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "frontend-shutdown"));
            System.out.printf("栗小铺商城 http://%s:%d/  管理台 http://%s:%d/admin/ %n", host, port, host, port);
            server.closeFuture().sync();
        } finally {
            resources.shutdownGracefully().sync();
            workers.shutdownGracefully().sync();
            boss.shutdownGracefully().sync();
        }
    }

    static URI origin(String value) {
        URI uri = URI.create(value);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("后端地址必须为 http(s)://host:port");
        }
        return URI.create(value.replaceAll("/+$", ""));
    }

    private static String option(String[] args, String name, String fallback) {
        for (String arg : args) if (arg.startsWith("--frontend." + name + "="))
            return arg.substring(arg.indexOf('=') + 1);
        return fallback;
    }
}
