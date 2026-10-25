package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.ScrollQueryDTO;
import com.lixiaopu.service.OrderService;
import com.lixiaopu.service.WxRefundService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** 管理端订单查询、退款审核及状态维护，沿用管理员认证。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderService orders;
    private final WxRefundService refunds;

    /** 按 Tab 查询所有用户的订单，复用用户端的游标分页和明细装配逻辑。 */
    @GetMapping("/list")
    public Result getOrderList(@Valid ScrollQueryDTO query) {
        return orders.getAdminOrderList(query);
    }

    public record RefundReview(@NotNull Boolean approved, @Size(max = 200) String reason) {}

    @PutMapping("/{orderNo}/refund/review")
    public Result reviewRefund(@PathVariable String orderNo, @Valid @RequestBody RefundReview request) {
        return refunds.reviewRefund(orderNo, request.approved(), request.reason());
    }

    @PostMapping("/{orderNo}/refund/retry")
    public Result retryRefund(@PathVariable String orderNo) { return refunds.retryRefund(orderNo); }

    /** status=4：已发货待收货；status=5：已完成。 */
    @PutMapping("/{orderNo}/status")
    public Result status(@PathVariable String orderNo, @RequestParam Integer status) {
        return orders.updateFulfillmentStatus(orderNo, status);
    }
}
