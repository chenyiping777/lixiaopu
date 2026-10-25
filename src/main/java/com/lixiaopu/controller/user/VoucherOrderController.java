package com.lixiaopu.controller.user;

import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.VoucherOrderService;
import com.lixiaopu.service.impl.VoucherPaymentService;
import com.lixiaopu.pojo.entity.VoucherOrder;
import com.lixiaopu.pojo.vo.OwnedVoucherVO;
import com.lixiaopu.common.constant.DatePatternConstants;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀订单相关接口（C 端用户侧）
 *
 * <p>覆盖秒杀下单的完整链路：
 * <ol>
 *     <li>POST /seckill/{voucherId}        —— 提交秒杀请求（同步扣 Redis 库存，返回 orderId）</li>
 *     <li>GET  /result/{orderId}           —— 轮询下单结果（等 MQ 落单完成）</li>
 *     <li>POST /{orderId}/pay              —— 对已生成的订单发起支付预下单</li>
 *     <li>GET  /mine                       —— 我的券包（已购券列表，游标倒序分页）</li>
 * </ol>
 * 用户 id 统一从 CurrentHolder（登录上下文）取，不从前端传，防止越权。</p>
 *
 * 用户点击 "立即抢购" 时先打到 `POST /seckill/{voucherId}`，
 * 也就是 `submit` 方法 —— 它在 Redis 里原子预扣库存、往 `seckill_request` 表插一条 `PENDING` 记录，然后立即返回一个 `orderId`，
 * 但此时数据库里其实还没有真正的 `voucher_order` 订单，
 * 订单是由后续 MQ 消费者异步落库的。正因为落单是异步的，前端拿到 `orderId` 之后不能立刻跳支付，
 * 得先轮询 `GET /result/{orderId}` 直到状态变成 "已下单待支付"，
 * 确认这条订单在库里真的存在了，才能带着这个 `orderId` 去调 `POST /{orderId}/pay`，
 * 也就是 `pay` 方法 —— 它本质上是拿一个已经存在的订单去支付渠道换预支付凭证（比如微信的 `prepay_id`），
 * 前端再拿这个凭证调起收银台完成付款。
 */
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/voucher-order")
@RequiredArgsConstructor
public class VoucherOrderController {

    private final VoucherOrderService voucherOrderService;
    private final VoucherPaymentService voucherPaymentService;
    private final com.lixiaopu.service.NormalVoucherService normalVouchers;

    @PostMapping("/buy/{voucherId}")
    public Result buy(@PathVariable @Positive long voucherId) {
        return Result.success(normalVouchers.buy(voucherId,currentUserId()));
    }

    private long currentUserId() {
        var user=CurrentHolder.getCurrentUser();
        if (user==null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,"请先登录");
        return user.getId();
    }

    /**
     * 提交秒杀下单请求
     *
     * <p>这是秒杀的入口：同步走 Redis 预扣库存 + 落 PENDING 请求，
     * 然后由 MQ 异步真正落订单；前端拿到 orderId 后再轮询 /result 接口。</p>
     *
     * @param voucherId 要抢的券 id，必须为正数
     */
    @PostMapping("/seckill/{voucherId}")
    public Result submit(@PathVariable @Positive long voucherId) {
        // 用户 id 从上下文取，保证无法伪造他人下单
        Long userId = currentUserId();
        return Result.success(voucherOrderService.submit(voucherId, userId));
    }

    /**
     * 查询秒杀下单结果
     *
     * <p>submit 之后订单可能还在 MQ 消费中，前端用 orderId 轮询本接口，
     * 直到返回成功/失败/处理中三种状态之一。</p>
     *
     * @param orderId 下单时返回的订单 id
     */
    @GetMapping("/result/{orderId}")
    public Result result(@PathVariable @Positive long orderId) {
        Long userId = currentUserId();
        // Service 内部会校验订单是否属于当前用户，防止越权查他人订单
        return Result.success(voucherOrderService.result(orderId, userId));
    }

    /**
     * 对指定订单发起支付预下单
     *
     * <p>调用支付渠道生成预支付交易单（如微信 prepay_id），前端拿到后再调起收银台。</p>
     * @param orderId 订单 id，必须为正数
     */
    @PostMapping("/{orderId}/pay")
    public Result pay(@PathVariable @Positive long orderId) {
        // Service 内部会校验订单归属和状态，避免他人订单被支付
        return Result.success(voucherPaymentService.prepay(orderId));
    }

    /**
     * 查询"我的券包"（当前用户已购的券列表）
     *
     * <p>游标倒序分页：和活动列表的正序游标相反，这里按 id 倒序拉，
     * 前端每次把上一页最小 id 作为 beforeId 传回来，查 id < beforeId 的更新记录。</p>
     *
     * @param beforeId 上一页最后一条（最小）id，首次传 0 表示不带上限
     * @param limit    每页条数，默认 20，范围 1~100
     */
    @GetMapping("/mine")
    public Result mine(
            @RequestParam(defaultValue = "0") @PositiveOrZero long beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {

        // 从登录上下文拿当前用户 id，绝不信前端传参
        Long userId = currentUserId();

        // 用系统时区取当前时间，渲染券的"有效期/状态"（未使用/已过期/已使用）
        var now = LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);

        return Result.success(voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)                       // 只查自己的订单
                .lt(beforeId > 0, VoucherOrder::getId, beforeId)          // 游标：beforeId > 0 时才加 id < beforeId 条件
                .orderByDesc(VoucherOrder::getId)                         // 按 id 倒序，最新购券在前
                .last("LIMIT " + limit)
                .list()
                .stream()
                .map(order -> OwnedVoucherVO.from(order, now))             // 顺带算出券当前状态（待使用/已过期等）
                .toList());
    }


}
