package com.lixiaopu.common.auth.sms;

import com.lixiaopu.common.auth.security.AuthException;
import com.lixiaopu.common.auth.session.AuthProperties;

import com.lixiaopu.common.result.ResultCode;
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
    private final StringRedisTemplate redis;
    private final SmsGateway gateway;
    private final AuthProperties properties;
    private final SecureRandom random=new SecureRandom();
    public SmsService(StringRedisTemplate redis,SmsGateway gateway,AuthProperties properties) {
        this.redis=redis;this.gateway=gateway;this.properties=properties;
    }
    private String key(String phone,String purpose) { return "lxp:sms:"+purpose+":"+phone; }
    private String hash(String phone,String code) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(
                (properties.getJwtSecret()+":"+phone+":"+code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public String send(String phone,String purpose) {
        String rate="lxp:sms:rate:"+purpose+":"+phone;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(rate,"1",Duration.ofSeconds(60))))
            throw new AuthException(ResultCode.VALIDATE_FAILED,"发送过于频繁");
        String code=String.format("%06d",random.nextInt(1_000_000));
        redis.opsForValue().set(key(phone,purpose),hash(phone,code)+"|5",Duration.ofMinutes(5));
        try { gateway.send(phone,purpose,code); }
        catch (RuntimeException ex) { redis.delete(key(phone,purpose));redis.delete(rate);throw ex; }
        return gateway.localTest()?code:null;
    }
    public void consume(String phone,String purpose,String code) {
        String script="local v=redis.call('GET',KEYS[1]);if not v then return 0 end;"+
            "local sep=string.find(v,'|');local attempts=tonumber(string.sub(v,sep+1));"+
            "if string.sub(v,1,sep-1)==ARGV[1] then redis.call('DEL',KEYS[1]);return 1 end;"+
            "if attempts<=1 then redis.call('DEL',KEYS[1]) else redis.call('SET',KEYS[1],string.sub(v,1,sep)..(attempts-1),'KEEPTTL') end;return 0";
        Long ok=redis.execute(new DefaultRedisScript<>(script,Long.class),List.of(key(phone,purpose)),hash(phone,code));
        if (!Long.valueOf(1).equals(ok)) throw new AuthException(ResultCode.VALIDATE_FAILED,"验证码无效或已过期");
    }
}
