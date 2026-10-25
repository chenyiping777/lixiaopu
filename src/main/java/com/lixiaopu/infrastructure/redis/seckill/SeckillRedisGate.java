package com.lixiaopu.infrastructure.redis.seckill;

import cn.hutool.json.JSONUtil;
import com.lixiaopu.infrastructure.rocketmq.seckill.SeckillMessage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SeckillRedisGate {
    private static final long ACCEPTED = 0;//成功，允许进入下一步（发MQ）
    private static final long DUPLICATE = 1;//重复
    private static final long SOLD_OUT = 2;//售罄
    private static final long NOT_OPEN = 3;//未开售
    private static final DefaultRedisScript<Long> RESERVE = script("seckill-reserve.lua");
    private static final DefaultRedisScript<Long> SETTLE = script("seckill-settle.lua");
    private static final DefaultRedisScript<Long> REBUILD = script("seckill-rebuild.lua");

    private final StringRedisTemplate redis;
    public SeckillRedisGate(StringRedisTemplate redis) { this.redis = redis; }
    private static DefaultRedisScript<Long> script(String name) {//lua 脚本文件名（例如`reserve.lua`）
        var script = new DefaultRedisScript<Long>();//泛型`<Long>`代表脚本执行结果是 Long。
        script.setLocation(new ClassPathResource("lua/seckill/" + name));
        script.setResultType(Long.class);
        
        return script;
    }
    private List<String> keys(long voucherId) {
        String prefix = "lixiaopu:seckill:{" + voucherId + "}:";
        return List.of(prefix + "activity", prefix + "buyers", prefix + "reservations");

    }
    public void reserve(SeckillMessage message) {
        long result = redis.execute(RESERVE, keys(message.getVoucherId()),
                Long.toString(message.getUserId()), Long.toString(message.getOrderId()));
        if (result == ACCEPTED) return;
        if (result == DUPLICATE) throw new IllegalArgumentException("已抢购或正在排队");
        if (result == SOLD_OUT) throw new IllegalArgumentException("库存不足");
        if (result == NOT_OPEN) throw new IllegalArgumentException("活动未开始或已结束");
        throw new IllegalStateException("活动尚未预热，请先重建秒杀缓存");
    }

    public void settle(SeckillMessage message, String outcome) {
        redis.execute(SETTLE, keys(message.getVoucherId()),
                Long.toString(message.getUserId()), Long.toString(message.getOrderId()), outcome);
    }

    /**
     * 订单超时关单后的 Redis 库存回补，由 DB 事务提交成功后调用，失败只打日志。
     * 与 settle 的非 SUCCESS 分支一致：仅在活动已预热(ready=1)时 +1；
     * 不 SREM buyers——voucher_order 的 uk(user_id,voucher_id) 已禁止该用户再次下单，
     * 保持其在 buyers 集合中与 ALREADY_ORDERED 语义一致。
     * 未预热时跳过，下次 rebuild 会以 DB 库存为准重建，不会丢库存。
     */
    public void releaseStockOnCancel(long voucherId) {
        var key = keys(voucherId).get(0);
        if (!"1".equals(redis.opsForHash().get(key, "ready"))) return;
        redis.opsForHash().increment(key, "stock", 1);
    }
    
    public List<SeckillMessage> reservations(long voucherId) {
        return redis.opsForHash().entries(keys(voucherId).get(2)).entrySet().stream()
                .map(entry -> new SeckillMessage(Long.parseLong(entry.getKey().toString()),
                        Long.parseLong(entry.getValue().toString()), voucherId)).toList();
    }

    public void rebuild(long voucherId, int stock, long beginTime, long endTime,
                        List<Long> buyers, List<SeckillMessage> pending) {
        Map<String, String> reservations = pending.stream().collect(Collectors.toMap(
                message -> Long.toString(message.getOrderId()), message -> Long.toString(message.getUserId())));
        String snapshot = JSONUtil.toJsonStr(Map.of("stock", stock, "beginTime", beginTime,
                "endTime", endTime, "buyers", buyers.stream().map(String::valueOf).toList(),
                "reservations", reservations));
        redis.execute(REBUILD, keys(voucherId), snapshot);
    }
}
