package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.OrderDTO;
import com.lixiaopu.pojo.dto.ScrollQueryDTO;
import com.lixiaopu.service.OrderService;
import com.lixiaopu.service.WxRefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private WxRefundService wxRefundService;

    /** 仅申请整单退款；金额由原订单实付确定，不接收商品、数量或退款金额。 */
    public record RefundRequest(@NotBlank @Size(max = 80) String reason) {}

    @PostMapping("/{orderNo}/refund")
    public Result applyRefund(@PathVariable String orderNo, @Valid @RequestBody RefundRequest request) {
        return wxRefundService.applyRefund(orderNo, request.reason());
    }

    /**
     * 创建订单
     * 按商品/规格和购买数量占用库存，价格及商品快照由数据库生成。
     */
    @PostMapping("/create")
    public Result insertOrder(@Valid @RequestBody OrderDTO orderDTO) {
        return orderService.insertOrder(orderDTO);
    }

    /**
     * 获取订单列表
     */
    @GetMapping("/list")
    public Result getOrderList(@Valid ScrollQueryDTO query) {
        return orderService.getOrderList(query);
    }

    /**
     * 查看订单详情
     * @param orderNo
     * @return
     */
    @GetMapping("/detail")
    public Result getOrderDesc(@RequestParam @NotBlank String orderNo) {
        return orderService.getOrderDesc(orderNo);
    }


    /**
     * 取消订单
     * @param orderNo
     * @return
     */
    @PutMapping("/cancel")
    public Result cancelOrder(@RequestParam @NotBlank String orderNo, String cancelReason) {
        return orderService.cancelOrder(orderNo, cancelReason);
    }

    /**
     * 确认收货
     * @param orderNo
     * @return
     */
    @PutMapping("/confirmReceipt")
    public Result confirmOrderReceipt(@RequestParam @NotBlank String orderNo) {
        return orderService.confirmOrderReceipt(orderNo);
    }

    /**
     * 删除订单
     * @param orderNo
     * @return
     */
    @DeleteMapping("/delete")
    public Result deleteOrder(@RequestParam @NotBlank String orderNo){
        return orderService.deleteOrder(orderNo);
    }

    /**
     * 计算运费
     * @param productIds
     * @param addressId
     * @return
     */
    @GetMapping("/freight")
    public Result getOrderFreight(@RequestParam @NotBlank String productIds, @RequestParam @NotBlank String addressId) {
        return orderService.getOrderFreight(productIds, addressId);
    }

    /**
     * 获取物流信息
     * @param orderNo
     * @return
     */
    @GetMapping("/logistics")
    public Result getOrderLogistics(@RequestParam @NotBlank String orderNo) {
        return orderService.getOrderLogistics(orderNo);
    }

    /**
     * 条件搜索订单,前端传字符串,后端判断类型
     * @param searchCondition
     * @return
     */
    @GetMapping("/search")
    public Result searchOrderByCondition(@RequestParam @NotBlank String searchCondition) {
        return orderService.searchOrderByCondition(searchCondition);
    }

}
