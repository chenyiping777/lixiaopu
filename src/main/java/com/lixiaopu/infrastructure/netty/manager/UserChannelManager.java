package com.lixiaopu.infrastructure.netty.manager;

import com.lixiaopu.security.ClientType;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

/** 维护【用户 ID ↔ 当前在线 Channel】的映射，并且规则是：同一个用户，只允许同时保留一条在线 WS 连接；新连上，踢掉旧的。 */
@Component
public class UserChannelManager {
    //Channel 本身可以挂一些自定义属性，就像给这条长连接贴标签：贴上用户 ID、token、是买家还是管理员。
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("chat.userId");
    public static final AttributeKey<String> TOKEN_KEY = AttributeKey.valueOf("chat.token");
    public static final AttributeKey<ClientType> CLIENT_TYPE_KEY = AttributeKey.valueOf("chat.clientType");
    private final ConcurrentHashMap<Long, Channel> channels = new ConcurrentHashMap<>();
    //一个 userId，在 map 里面最多存一条 Channel

    //握手成功之后调用
    //1. 先给当前 channel 打上 userId 标签；
    //2. `channels.put(userId, channel)`：
    // 把 userId 对应的连接直接换成这个新 channel；put 会返回这个 userId 原来对应的旧 channel；
    //3. 如果存在旧连接，并且旧连接不等于当前新连接：给旧连接发关闭帧，提示 “被新连接顶替”，然后关掉旧连接。
    public void bind(Long userId, Channel channel) {
        channel.attr(USER_ID_KEY).set(userId);
        Channel previous = channels.put(userId, channel);
        if (previous != null && previous != channel)
            previous.writeAndFlush(new io.netty.handler.codec.http.websocketx.CloseWebSocketFrame(1000, "Replaced by new connection"))
                    .addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }

    //连接断开
    public void unbind(Channel channel) {
        Long id = channel.attr(USER_ID_KEY).get();
        // 旧连接稍后触发 inactive 时，不得移除已经替换成功的新连接。
        if (id != null) channels.remove(id, channel);
    }
    //根据 userId 获取 Channel
    public Channel getChannel(Long id) { return channels.get(id); }
    //判断 userId 是否在线
    public boolean isOnline(Long id) { var ch = channels.get(id); return ch != null && ch.isActive(); }
    //关闭所有连接
    public void closeAll() { channels.values().forEach(Channel::close); channels.clear(); }
}
