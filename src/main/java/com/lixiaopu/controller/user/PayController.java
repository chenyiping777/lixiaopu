package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.WxPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
@Slf4j
public class PayController {
    private final WxPayService wxPayService;
    public record PayRequest(@NotBlank String orderNo) {}

    @PostMapping("/refund/notify")
    public ResponseEntity<Map<String, String>> refundNotify(HttpServletRequest request, @RequestBody String body) {
        try {
            wxPayService.handleRefundNotify(request, body);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "OK"));
        } catch (RuntimeException ex) {
            log.warn("微信退款通知处理失败", ex);
            return ResponseEntity.status(500).body(Map.of("code", "FAIL", "message", "退款通知处理失败"));
        }
    }

    @PostMapping("/wxpay")
    public Result wxPay(@Valid @RequestBody PayRequest request) {
        return Result.success(wxPayService.createPrepayOrder(request.orderNo()));
    }

    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> notify(HttpServletRequest request, @RequestBody String body) {
        try {
            wxPayService.handlePayNotify(request, body);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "OK"));
        } catch (RuntimeException ex) {
            log.warn("微信支付通知处理失败", ex);
            return ResponseEntity.status(500).body(Map.of("code", "FAIL", "message", "支付通知处理失败"));
        }
    }
}
