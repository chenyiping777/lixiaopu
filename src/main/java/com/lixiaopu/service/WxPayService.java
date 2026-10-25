package com.lixiaopu.service;

import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface WxPayService {
    PrepayWithRequestPaymentResponse createPrepayOrder(String orderNo);
    void handlePayNotify(HttpServletRequest request, String body);
    boolean closeUnpaidOrder(String orderNo, String reason);
    void processRefund(String orderNo);
    void handleRefundNotify(HttpServletRequest request, String body);
}
