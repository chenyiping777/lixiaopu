package com.lixiaopu.job;

import com.lixiaopu.infrastructure.rocketmq.seckill.SeckillOutbox;
import com.lixiaopu.mapper.SeckillVoucherMapper;
import com.lixiaopu.pojo.entity.SeckillVoucher;
import com.lixiaopu.service.VoucherOrderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component @Profile("user") @EnableScheduling @RequiredArgsConstructor @Slf4j
public class SeckillRecoveryJob {
    private final SeckillOutbox outbox;
    private final VoucherOrderService orders;
    private final SeckillVoucherMapper vouchers;
    @Scheduled(fixedDelayString="${seckill.dispatch-delay-ms:1000}")
    public void dispatch() { outbox.dispatch(); }
    @Scheduled(fixedDelayString="${seckill.reservation-recovery-ms:10000}")
    public void recover() {
        long cursor = 0;
        while (true) {
            var batch = vouchers.selectList(Wrappers.<SeckillVoucher>lambdaQuery()
                    .gt(SeckillVoucher::getVoucherId, cursor).orderByAsc(SeckillVoucher::getVoucherId).last("LIMIT 100"));
            if (batch.isEmpty()) return;
            for (var voucher : batch) {
                try { orders.recoverReservations(voucher.getVoucherId()); }
                catch (Exception failure) { log.error("秒杀预扣恢复失败，voucherId={}", voucher.getVoucherId(), failure); }
            }
            cursor = batch.get(batch.size()-1).getVoucherId();
        }
    }
}
