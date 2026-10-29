package com.lixiaopu.frontend;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** 固定上游，保留认证、multipart 和微信验签头；每个连接处理一次请求，避免流水线响应乱序。
 * JS 发起的所有 /api 接口，都先经过网关，网关帮 JS 把请求送到正确的后端服务；JS 需要的页面、脚本、图片，网关直接给。 */
final class FrontendHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Set<String> HOP = Set.of("connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade", "host",
            "content-length", "expect", "http2-settings");
    private final HttpClient client;
    private final URI buyer;
    private final URI admin;
    private boolean handled;

    FrontendHandler(HttpClient client, URI buyer, URI admin) {
        this.client = client; this.buyer = buyer; this.admin = admin;
    }

    @Override protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (handled) return;
        handled = true;
        ctx.channel().config().setAutoRead(false);
        if (!request.decoderResult().isSuccess()) { error(ctx, 400, "无效 HTTP 请求"); return; }
        try {
            URI uri = URI.create(request.uri());
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getFragment() != null) {
                error(ctx, 400, "只接受站内路径"); return;
            }
            String path = uri.getPath();
            if (path == null || !path.startsWith("/") || path.contains("\\") || path.indexOf('\0') >= 0
                    || Arrays.asList(path.split("/", -1)).contains("..")) {
                error(ctx, 400, "无效路径"); return;
            }
            if (path.equals("/admin")) {
                var redirect = response(308, new byte[0]);
                redirect.headers().set(HttpHeaderNames.LOCATION, "/admin/");
                finish(ctx, redirect); return;
            }
            if (path.startsWith("/api/")) { proxy(ctx, request, uri, path); return; }
            if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
                error(ctx, 405, "静态资源仅支持 GET / HEAD"); return;
            }
            String resource = staticResource(path);
            if (resource == null) { error(ctx, 404, "页面不存在"); return; }
            try (var input = FrontendHandler.class.getResourceAsStream(resource)) {
                if (input == null) { error(ctx, 404, "页面不存在"); return; }
                byte[] bytes = input.readAllBytes();
                var response = response(200, request.method().equals(HttpMethod.HEAD) ? new byte[0] : bytes);
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType(resource));
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
                response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
                finish(ctx, response);
            }
        } catch (IllegalArgumentException ex) { error(ctx, 400, "无效请求路径或请求头"); }
        catch (IOException ex) { error(ctx, 500, "读取页面失败"); }
    }

    static String staticResource(String path) {
        boolean management = path.equals("/admin") || path.startsWith("/admin/");
        String relative = management ? path.substring(6) : path;
        if (relative.isEmpty() || relative.equals("/")) relative = management ? "/console.html" : "/index.html";
        // 只开放前端资源，不暴露配置、密钥或类路径中的其他文件。
        if (!relative.matches("/[A-Za-z0-9_./-]+") || relative.contains("..")
                || !relative.matches(".*\\.(html|css|js|svg|png|jpg|jpeg|webp|ico|woff2)$")) return null;
        return (management ? "/admin-static" : "/static") + relative;
    }

    private void proxy(ChannelHandlerContext ctx, FullHttpRequest request, URI uri, String path) {
        URI upstream = path.startsWith("/api/admin/") ? admin : buyer;
        // 拼接固定 origin，禁止 //host 或编码路径改变代理目标；原始查询和 body 不做转换。
        URI target = URI.create(upstream.toString() + uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        byte[] body = new byte[request.content().readableBytes()];
        request.content().getBytes(request.content().readerIndex(), body);
        var builder = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(25))
                .method(request.method().name(), body.length == 0 ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        Set<String> excluded = excluded(request.headers().getAll("connection"));
        request.headers().forEach(entry -> {
            if (!excluded.contains(entry.getKey().toLowerCase(Locale.ROOT))) builder.header(entry.getKey(), entry.getValue());
        });
        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray()).whenComplete((reply, failure) -> {
            if (failure != null) { error(ctx, 502, "后端暂时无法连接，请检查用户端与管理端服务"); return; }
            var response = response(reply.statusCode(), reply.body());
            Set<String> skip = excluded(reply.headers().allValues("connection"));
            reply.headers().map().forEach((key, values) -> {
                if (!skip.contains(key.toLowerCase(Locale.ROOT))) response.headers().set(key, values);
            });
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
            finish(ctx, response);
        });
    }

    private static Set<String> excluded(List<String> connections) {
        Set<String> names = new HashSet<>(HOP);
        connections.forEach(value -> Arrays.stream(value.split(","))
                .forEach(name -> names.add(name.trim().toLowerCase(Locale.ROOT))));
        return names;
    }
    private static DefaultFullHttpResponse response(int status, byte[] body) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(status), Unpooled.wrappedBuffer(body));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        return response;
    }
    private static void finish(ChannelHandlerContext ctx, FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.CONNECTION, "close");
        response.headers().set("X-Content-Type-Options", "nosniff");
        response.headers().set("Referrer-Policy", "same-origin");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    private static void error(ChannelHandlerContext ctx, int status, String message) {
        var response = response(status, ("{\"success\":false,\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=UTF-8");
        finish(ctx, response);
    }
    private static String contentType(String file) {
        String extension = file.substring(file.lastIndexOf('.') + 1);
        return switch (extension) {
            case "html" -> "text/html;charset=UTF-8";
            case "css" -> "text/css;charset=UTF-8";
            case "js" -> "text/javascript;charset=UTF-8";
            case "svg" -> "image/svg+xml";
            case "jpg", "jpeg" -> "image/jpeg";
            case "woff2" -> "font/woff2";
            default -> "image/" + extension;
        };
    }
    @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { ctx.close(); }
}
