package com.lixiaopu.infrastructure.netty.handler;

import com.lixiaopu.infrastructure.netty.ChatAuthenticator;
import com.lixiaopu.infrastructure.netty.manager.UserChannelManager;
import com.lixiaopu.properties.ChatProperties;
import com.lixiaopu.security.ClientType;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
//只会在 WebSocket 握手那一次 HTTP 请求过来的时候执行。
public class JwtAuthHandler extends ChannelInboundHandlerAdapter {
    private final ChatAuthenticator auth;
    private final ChatProperties properties;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 【判断】如果不是HTTP请求（就是握手之后的ws消息），直接放行，不鉴权
        if (!(msg instanceof FullHttpRequest request)) { ctx.fireChannelRead(msg); return; }
        try {
            var query = new QueryStringDecoder(request.uri());
            ClientType clientType = ClientType.fromChatPath(query.path());
            if (!request.decoderResult().isSuccess() || clientType == null || request.method() != HttpMethod.GET) {
                reject(ctx, HttpResponseStatus.BAD_REQUEST); return;
            }
            String origin = request.headers().get(HttpHeaderNames.ORIGIN);
            // 无 Origin 的非浏览器客户端依靠 JWT 校验；浏览器必须在明确的来源白名单内。
            if (origin != null && !properties.getAllowedOrigins().contains(origin)) {
                reject(ctx, HttpResponseStatus.FORBIDDEN); return;
            }
            String token = query.parameters().getOrDefault("token", List.of("")).get(0);
            String header = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) token = header.substring(7);
            var principal = auth.authenticate(token, clientType);
            ctx.channel().attr(UserChannelManager.USER_ID_KEY).set(principal.getUid());
            ctx.channel().attr(UserChannelManager.TOKEN_KEY).set(token);
            ctx.channel().attr(UserChannelManager.CLIENT_TYPE_KEY).set(principal.getClientType());
            // 移除凭证查询参数，只将已通过精确路径白名单校验的请求传给协议升级处理器。
            request.setUri(clientType.getChatPath());
            ctx.fireChannelRead(request.retain());
        } catch (Exception ignored) {
            // 不记录原始 URI、token 或异常细节，避免凭证进入日志。
            reject(ctx, HttpResponseStatus.UNAUTHORIZED);
        } finally { ReferenceCountUtil.release(request); }
    }

    private void reject(ChannelHandlerContext ctx, HttpResponseStatus status) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
