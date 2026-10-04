package com.lixiaopu.infrastructure.sms;

import com.lixiaopu.common.exception.AuthException;
import com.lixiaopu.properties.AuthProperties;

import com.lixiaopu.common.result.ResultCode;
import com.lixiaopu.common.util.CodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Service
public class SmsService {
    @Autowired
    private  StringRedisTemplate stringRedisTemplate;
    @Autowired
    private  SmsGateway smsGateway;
    @Autowired
    private  AuthProperties authProperties;


    //哈希运算
    private String hash(String phone,String code) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(
                    (authProperties.getJwtSecret()+":"+phone+":"+code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
    //发送验证码（先判断频率）
    public String send(String phone,String purpose) {
        // 验证码发送频率
        String rate="lxp:sms:rate:"+purpose+":"+phone;
        // 60秒内只能发送一次
        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(rate,"1",Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(res)){
            throw new AuthException(ResultCode.VALIDATE_FAILED,"发送过于频繁");
        }
        // 生成验证码
        String code= CodeUtil.generateFiveCode();
        // 存储验证码
        stringRedisTemplate.opsForValue().set("lxp:sms:"+purpose+":"+phone,
                hash(phone,code)+"|5",Duration.ofMinutes(5));
        // 发送验证码
        try {
            smsGateway.send(phone,purpose,code);
        // 验证码发送失败则删除验证码
        }catch (RuntimeException ex) {
            stringRedisTemplate.delete("lxp:sms:"+purpose+":"+phone);
            stringRedisTemplate.delete(rate);throw ex;
        }
        return smsGateway.localTest()?code:null;
    }

    //验证验证码
    public void consume(String phone, String purpose, String code) {
        // 1. 拼装redis key
        String redisKey = "lxp:sms:" + purpose + ":" + phone;

        // 2. 计算hash：phone+code的摘要（和存验证码时的hash方法保持一致！）
        String hashValue = hash(phone, code);

        // 3. 单独定义Lua脚本字符串，换行拆分方便阅读
        String luaScript = """
            local v = redis.call('GET',KEYS[1])
            -- key不存在（过期/未发送验证码），返回0
            if not v then
                return 0
            end
            -- 查找 | 分隔符位置
            local sep = string.find(v,'|')
            -- 截取 | 后面部分，转为数字：剩余尝试次数
            local attempts = tonumber(string.sub(v,sep+1))
            -- 判断哈希是否匹配（验证码正确）
            if string.sub(v,1,sep-1)==ARGV[1] then
                redis.call('DEL',KEYS[1])
                return 1
            end
            -- 验证码错误，剩余次数扣减
            if attempts <= 1 then
                redis.call('DEL',KEYS[1])
            else
                redis.call('SET',KEYS[1], string.sub(v,1,sep)..(attempts-1), 'KEEPTTL')
            end
            return 0
            """;

        // 4. 封装RedisScript对象，返回类型Long
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);

        // 5. 执行脚本：KEYS集合，ARGV参数
        List<String> keyList = List.of(redisKey);
        Long result = stringRedisTemplate.execute(redisScript, keyList, hashValue);
        //`execute` 方法设计上统一接收 `List<K> keys`，不管实际用 1 个还是 N 个 key，都传 List，
        // 所以哪怕只有 1 个 key，也要包装成 List。
        // 6. 判断返回结果，不等于1就抛出异常
        if (!Long.valueOf(1).equals(result)) {
            throw new AuthException(ResultCode.VALIDATE_FAILED, "验证码无效或已过期");
        }
    }
}
