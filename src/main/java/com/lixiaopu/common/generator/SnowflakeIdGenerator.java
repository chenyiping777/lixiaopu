package com.lixiaopu.common.generator;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 商品订单号格式化；商品和购券共用注入的唯一雪花生成器与底层序列。 */
@Component
public class SnowflakeIdGenerator {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);
    private final SnowflakeOrderId ids;
    private final Clock clock;

    @Autowired
    public SnowflakeIdGenerator(SnowflakeOrderId ids) {
        this(ids, Clock.system(ZONE));
    }

    SnowflakeIdGenerator(SnowflakeOrderId ids, Clock clock) {
        this.ids = ids;
        this.clock = clock.withZone(ZONE);
    }

    public long nextId() {
        return ids.nextId();
    }

    public String nextIdStr() {
        return Long.toString(nextId());
    }

    /** 6 位北京时间日期 + 19 位补零的完整雪花 ID，共 25 位；不截断任何 ID 位。 */
    public String generateOrderNo() {
        return LocalDate.now(clock).format(DATE_PREFIX) + String.format(Locale.ROOT, "%019d", nextId());
    }
}
