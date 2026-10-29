package com.lixiaopu.infrastructure.netty.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixiaopu.infrastructure.netty.ChatAuthenticator;
import com.lixiaopu.infrastructure.netty.manager.UserChannelManager;
import com.lixiaopu.service.ChatService;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ChatHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final UserChannelManager channels;
    private final ChatService service;
    private final ObjectMapper json;
    private final ChatAuthenticator auth;

    /**
     * WebSocket 长连接一旦建立，TCP 通道会一直保持。
     * 假设：
     * - 用户握手的时候 token 是有效的；
     * - 握手之后，管理员后台把这个账号禁用 / 用户改密码 /token 过期了；
     * - 如果没有 ChatHandler 里这一段二次校验：这条 ws 连接依旧活着，用户可以无限发消息，系统拦不住，直到连接空闲超时断开或者手动踢线。
     * */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // 持续验证会话：过期、退出、禁用、改密后，下一条消息或心跳拒绝并断开。
        try {
            auth.authenticate(ctx.channel().attr(UserChannelManager.TOKEN_KEY).get(),
                    ctx.channel().attr(UserChannelManager.CLIENT_TYPE_KEY).get());
        } catch (Exception invalid) {
            write(ctx.channel(), "ERROR", Map.of("code", "UNAUTHORIZED", "message", "登录已失效，请重新连接"));
            ctx.writeAndFlush(new CloseWebSocketFrame(1008, "Authentication expired")).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (!(frame instanceof TextWebSocketFrame text)) {
            ctx.writeAndFlush(new CloseWebSocketFrame(1003, "Text frames required")).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        try {
            var request = json.readTree(text.text());
            if (request == null || !request.isObject()) throw new IllegalArgumentException("消息须为 JSON 对象");
            var data = request.path("data");
            Long userId = ctx.channel().attr(UserChannelManager.USER_ID_KEY).get();
            switch (request.path("action").asText()) {
                case "SEND_MSG" -> {
                    long to = positiveId(data.get("toUserId"));
                    if (!data.path("content").isTextual() || !data.path("msgType").isIntegralNumber()
                            || !data.path("msgType").canConvertToInt())
                        throw new IllegalArgumentException("content 和 msgType 格式无效");
                    Long product = data.hasNonNull("productId") ? positiveId(data.get("productId")) : null;
                    var saved = service.saveAndGetMessage(userId, to, data.get("content").textValue(), data.get("msgType").intValue(), product);
                    var target = channels.getChannel(to);
                    // ACK 仅表示数据库事务成功，在线推送失败仍可从历史记录取回。
                    if (target != null && target.isActive()) {
                        try {
                            var targetPrincipal = auth.authenticate(target.attr(UserChannelManager.TOKEN_KEY).get(),
                                    target.attr(UserChannelManager.CLIENT_TYPE_KEY).get());
                            if (targetPrincipal.getUid() == to) write(target, "PUSH_MSG", saved);
                        } catch (Exception invalidTarget) { target.close(); }
                    }
                    write(ctx.channel(), "MSG_ACK", Map.of("msgId", saved.getId().toString(), "status", "SUCCESS"));
                }
                case "READ_REPORT" -> service.clearUnread(userId, positiveId(data.get("contactId")));
                case "HEARTBEAT" -> ctx.writeAndFlush(new TextWebSocketFrame("{\"action\":\"PONG\"}"));
                default -> throw new IllegalArgumentException("未知 action");
            }
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException invalid) {
            write(ctx.channel(), "ERROR", Map.of("code", "BAD_REQUEST", "message", "消息格式或内容无效"));
        } catch (Exception failure) {
            log.error("客服消息处理失败，userId={}", ctx.channel().attr(UserChannelManager.USER_ID_KEY).get(), failure);
            write(ctx.channel(), "ERROR", Map.of("code", "SERVER_ERROR", "message", "消息处理失败，请先核对历史记录"));
        }
    }

    private long positiveId(JsonNode value) {
        if (value == null || !(value.isTextual() || value.isIntegralNumber())) throw new IllegalArgumentException("ID 无效");
        long id = Long.parseLong(value.asText());
        if (id <= 0) throw new IllegalArgumentException("ID 无效");
        return id;
    }
    private void write(Channel channel, String action, Object data) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (!channel.isWritable()) { channel.close(); return; }
        channel.writeAndFlush(new TextWebSocketFrame(json.writeValueAsString(Map.of("action", action, "data", data))))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            channels.bind(ctx.channel().attr(UserChannelManager.USER_ID_KEY).get(), ctx.channel());
        } else if (evt instanceof IdleStateEvent) { ctx.close(); }
        super.userEventTriggered(ctx, evt);
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channels.unbind(ctx.channel()); super.channelInactive(ctx);
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { ctx.close(); }
}
