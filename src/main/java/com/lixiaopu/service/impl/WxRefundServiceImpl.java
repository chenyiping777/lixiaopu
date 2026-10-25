package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.mapstruct.CopyMapper;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.mapper.OrderMapper;
import com.lixiaopu.pojo.entity.Order;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import com.lixiaopu.pojo.enums.PayStatusEnum;
import com.lixiaopu.service.InventoryService;
import com.lixiaopu.service.VoucherUseService;
import com.lixiaopu.service.WxPayService;
import com.lixiaopu.service.WxRefundService;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 微信退款领域服务实现。
 * 只围绕订单 payStatus 做状态迁移：
 *   申请(买家) -> REFUND_REQUESTED
 *   审核(商家) -> REFUNDING / REFUND_REJECTED
 *   回调(微信) -> REFUND_SUCCESS（失败保持 REFUNDING，写失败原因）
 * 事务边界：申请/回调走 @Transactional；审核用 TransactionTemplate 包 DB 段，
 *           事务提交后再调微信 HTTP，避免长事务占数据库行锁。
 */
@Service
@Slf4j
public class WxRefundServiceImpl implements WxRefundService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private CopyMapper copyMapper;
    @Autowired
    private WxPayService wxPayService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private VoucherUseService voucherUseService;

    private static ResponseStatusException refundConflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** 加悲观锁 for update 查订单，不存在一律 404。 */
    private Order lockOrder(String orderNo) {
        if (StringUtils.isBlank(orderNo)) throw refundConflict("订单号不能为空");
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo).last("FOR UPDATE"));
        if (order == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        return order;
    }

    /** 正金额核对原微信支付；零元订单核对本地结算时间且没有伪造交易号。 */
    private void checkPaid(Order order) {
        if (order.getPayTime()==null || order.getTotalAmount()==null || order.getTotalAmount().signum()<0)
            throw refundConflict("原支付信息无效，不能退款");
        if (order.getTotalAmount().signum()==0) {
            if (StringUtils.isNotBlank(order.getTransactionId())) throw refundConflict("零元订单不应存在微信交易号");
            return;
        }
        if (StringUtils.isBlank(order.getTransactionId())) throw refundConflict("原支付流水无效，不能退款");
        WxPayServiceImpl.toCents(order.getTotalAmount());
    }

    /** CAS 更新支付状态：where id=? and pay_status=旧值，影响行数!=1 即并发冲突。 */
    private void casRefund(Order order, PayStatusEnum from, PayStatusEnum to,
                           Consumer<LambdaUpdateWrapper<Order>> changes) {
        if (order.getPayStatus() != from)
            throw refundConflict("当前状态不允许此操作，请刷新");
        var w = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getPayStatus, from)
                .set(Order::getPayStatus, to)
                .set(Order::getUpdateTime, LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID));
        changes.accept(w);
        if (orderMapper.update(null, w) != 1) throw refundConflict("订单状态已变化，请刷新");
        evictAfterCommit(order.getOrderNo());
    }

    /** 读完直接返回最新 VO，不加锁。 */
    private Result refundResult(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        return Result.success(copyMapper.orderToOrderWithItemVO(order));
    }

    /** 事务提交后清理订单详情缓存；失败由 TTL 兜底，无事务时立即删除。 */
    private void evictAfterCommit(String orderNo) {
        Assert.hasText(orderNo, "orderNo must not be blank");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { evict(orderNo); }
            });
        } else {
            evict(orderNo);
        }
    }

    private void evict(String orderNo) {
        try {
            RedisConnector.delete(RedisKeyGenerator.orderKey(orderNo));
        } catch (RuntimeException failure) {
            log.warn("订单 {} 详情缓存删除失败，等待 TTL 过期", orderNo, failure);
        }
    }

    // ==================== 接口1：买家申请退款 ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result applyRefund(String orderNo, String reason) {
        var user = CurrentHolder.getCurrentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (StringUtils.isBlank(reason) || reason.length() > 80)
            throw refundConflict("退款原因须为 1 到 80 字");

        Order order = lockOrder(orderNo);
        // 归属不符 OR 已软删（isDeleted=ACTIVE）-> 404，不暴露订单存在性
        if (!Objects.equals(user.getId(), order.getUserId()) || order.getIsDeleted() == CommonStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        checkPaid(order);

        // 只有 已支付 / 上次被拒 能发起；退款中/已退款前端不显示按钮，后端直接返回当前态
        PayStatusEnum cur = order.getPayStatus();
        if (cur != PayStatusEnum.PAID && cur != PayStatusEnum.REFUND_REJECTED)
            return refundResult(orderNo);

        String refundNo = StringUtils.isBlank(order.getRefundNo())
                ? "RF" + UUID.randomUUID().toString().replace("-", "") : order.getRefundNo();

        casRefund(order, cur, PayStatusEnum.REFUND_REQUESTED, u -> u
                .set(Order::getRefundNo, refundNo)
                .set(Order::getRefundReason, reason.trim())
                .set(Order::getRefundFailReason, null));
        return refundResult(orderNo);
    }

    // ==================== 接口2：商家审核退款 ====================
    @Override
    public Result reviewRefund(String orderNo, boolean approved, String reason) {
        if (CurrentHolder.getCurrentUser() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!approved && StringUtils.isBlank(reason)) throw refundConflict("拒绝退款时必须填写原因");
        if (reason != null && reason.length() > 200) throw refundConflict("拒绝原因不能超过 200 字");

        // 事务内只改 DB（持 FOR UPDATE 锁），事务提交后再调微信 HTTP
        Boolean remoteRefund=new TransactionTemplate(transactions).execute(tx -> {
            Order order = lockOrder(orderNo);
            checkPaid(order);
            if (StringUtils.isBlank(order.getRefundNo())) throw refundConflict("退款单号无效，不能处理退款");
            if (approved && order.getTotalAmount().signum()==0) {
                casRefund(order,PayStatusEnum.REFUND_REQUESTED,PayStatusEnum.REFUND_SUCCESS,u -> u
                        .set(Order::getRefundTime,LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID))
                        .set(Order::getRefundFailReason,null));
                releaseRefundEntitlements(order);
                return false;
            }
            casRefund(order, PayStatusEnum.REFUND_REQUESTED,
                    approved ? PayStatusEnum.REFUNDING : PayStatusEnum.REFUND_REJECTED,
                    u -> u.set(Order::getRefundFailReason, approved ? null : reason.trim()));
            return approved;
        });

        if (Boolean.TRUE.equals(remoteRefund)) {
            try { wxPayService.processRefund(orderNo); }
            catch (RuntimeException failure) { log.warn("订单 {} 已同意退款，等待微信回调/重试", orderNo, failure); }
        }
        return refundResult(orderNo);
    }

    @Override
    public Result retryRefund(String orderNo) {
        Order order=orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo,orderNo));
        if (order==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"订单不存在");
        if (order.getPayStatus()==PayStatusEnum.REFUND_SUCCESS) return refundResult(orderNo);
        wxPayService.processRefund(orderNo);
        return refundResult(orderNo);
    }

    // ==================== 接口3：微信退款回调 ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordRefund(Refund result) {
        Order order = lockOrder(result.getOutTradeNo());
        long cents = WxPayServiceImpl.toCents(order.getTotalAmount());
        var amount = result.getAmount();

        // 交易三要素 + 金额币种硬核对，防伪造回调
        if (!Objects.equals(result.getOutRefundNo(), order.getRefundNo())
                || !Objects.equals(result.getTransactionId(), order.getTransactionId())
                || amount == null
                || !Objects.equals(amount.getRefund(), cents)
                || !Objects.equals(amount.getTotal(), cents)
                || !"CNY".equals(amount.getCurrency())
                || StringUtils.isBlank(result.getRefundId())
                || result.getStatus() == null)
            throw refundConflict("退款结果交易号、退款单号、金额或币种不匹配");

        if (order.getPayStatus() == PayStatusEnum.REFUND_SUCCESS) return; // 幂等

        boolean success = result.getStatus() == Status.SUCCESS;
        casRefund(order, PayStatusEnum.REFUNDING,
                success ? PayStatusEnum.REFUND_SUCCESS : PayStatusEnum.REFUNDING, u -> {
            if (success) {
                LocalDateTime paidBack = OffsetDateTime.parse(result.getSuccessTime())
                        .atZoneSameInstant(DatePatternConstants.SYSTEM_ZONE_ID).toLocalDateTime();
                u.set(Order::getRefundTime, paidBack).set(Order::getRefundFailReason, null);
            } else if (result.getStatus() == Status.CLOSED || result.getStatus() == Status.ABNORMAL) {
                // 只有终态失败才写原因；迟到的 PROCESSING 不覆盖已有异常说明
                String error = result.getStatus() == Status.CLOSED
                        ? "微信退款已关闭，请在商户平台核实处理"
                        : "微信退款异常，请在商户平台处理后同步结果";
                u.set(Order::getRefundFailReason, error);
            }
        });

        if (success) releaseRefundEntitlements(order);
    }

    /** 现金退款回调与零元审核通过复用；幂等由订单首次退款成功迁移保证。 */
    private void releaseRefundEntitlements(Order order) {
        if (order.getStatus()==OrderStatusEnum.PENDING_SHIPMENT) inventoryService.release(order.getId());
        if (order.getVoucherOrderId()!=null) voucherUseService.release(order,true);
    }
}
