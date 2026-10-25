package com.lixiaopu.infrastructure.rocketmq.seckill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage {
    private long orderId;
    private long userId;
    private long voucherId;
 }
