package com.lixiaopu.job;

import com.lixiaopu.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 购券订单超时关单兜底扫描。
 * 正常路径由支付到期时间驱动；本任务兜底进程重启、微信关单临时失败等场景。
 * 与 SeckillRecoveryJob 同 profile，只在用户态运行实例执行。
 */
@Slf4j
@Component
@Profile("user")
@EnableScheduling
@RequiredArgsConstructor
public class VoucherOrderTimeoutCancelJob {

    private final VoucherOrderService orders;

    @Scheduled(fixedDelayString = "${voucher.timeout-scan-ms:60000}")
    public void scan() {
        orders.scanTimeoutOrders();
    }
}
