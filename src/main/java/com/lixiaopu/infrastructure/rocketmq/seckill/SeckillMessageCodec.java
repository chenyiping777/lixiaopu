package com.lixiaopu.infrastructure.rocketmq.seckill;

import cn.hutool.json.JSONUtil;
import java.nio.charset.StandardCharsets;

public final class SeckillMessageCodec {
    private SeckillMessageCodec() { }
    public static byte[] encode(SeckillMessage message) {
        return JSONUtil.createObj().set("orderId", message.getOrderId())
                .set("userId", message.getUserId()).set("voucherId", message.getVoucherId())
                .toString().getBytes(StandardCharsets.UTF_8);
    }
    public static SeckillMessage decode(byte[] body) {
        var json = JSONUtil.parseObj(new String(body, StandardCharsets.UTF_8));
        return new SeckillMessage(json.getLong("orderId"), json.getLong("userId"), json.getLong("voucherId"));
    }
}
