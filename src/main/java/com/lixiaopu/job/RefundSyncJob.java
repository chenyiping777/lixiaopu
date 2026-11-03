package com.lixiaopu.job;

import com.lixiaopu.service.impl.WxPayServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 用户端负责退款兜底扫描；管理端仍保留支付、退款业务服务。 */
@Component
@Profile("user")
@RequiredArgsConstructor
public class RefundSyncJob {
    private final WxPayServiceImpl payments;

    @Scheduled(fixedDelayString = "${wxpay.refund-scan-ms:60000}")
    public void scan() {
        payments.scanRefunds();
    }
}
