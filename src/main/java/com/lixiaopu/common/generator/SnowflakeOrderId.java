package com.lixiaopu.common.generator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 进程内唯一的底层雪花序列；每个实例必须配置不同的节点组合，禁止缺省回退。 */
@Component
public class SnowflakeOrderId {
    
    private final static long START_EPOCH = 1609459200000L;

    private final static long MACHINE_BITS = 5L;
    
    private final static long DATACENTER_BITS = 5L;
    
    private final static long SEQUENCE_BITS = 12L;

    private final static long MACHINE_SHIFT = SEQUENCE_BITS;
    
    private final static long DATACENTER_SHIFT = SEQUENCE_BITS + MACHINE_BITS;
    
    private final static long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_BITS + DATACENTER_BITS;

    private final static long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private final long datacenterId;
    private final long machineId;
    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;

    @Autowired
    public SnowflakeOrderId(@Value("${snowflake.datacenterId}") long datacenterId,
                             @Value("${snowflake.machineId}") long machineId) {
        if (datacenterId > ~(-1L << DATACENTER_BITS) || datacenterId < 0) {
            throw new IllegalArgumentException(
                    "datacenterId 不能超过 " + ~(-1L << DATACENTER_BITS) + " 或小于 0");
        }
        if (machineId > ~(-1L << MACHINE_BITS) || machineId < 0) {
            throw new IllegalArgumentException(
                    "machineId 不能超过 " + ~(-1L << MACHINE_BITS) + " 或小于 0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 10) {
                try {
                    wait(offset << 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                currentTimestamp = timestamp();
                if (currentTimestamp < lastTimestamp) {
                    throw new RuntimeException("Clock moved backwards. Refusing to generate id");
                }
            } else {
                throw new RuntimeException("Clock moved backwards too much (超过10ms)");
            }
        }
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;
        return ((currentTimestamp - START_EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (machineId << MACHINE_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = timestamp();
        }
        return timestamp;
    }

    long timestamp() {
        return System.currentTimeMillis();
    }
}
