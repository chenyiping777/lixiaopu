package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.mapper.OrderMapper;
import com.lixiaopu.pojo.entity.Order;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import com.lixiaopu.pojo.enums.PayStatusEnum;
import com.lixiaopu.properties.WxPayProperties;
import com.lixiaopu.service.WxPayService;
import com.lixiaopu.service.WxRefundService;
import com.lixiaopu.service.InventoryService;
import com.lixiaopu.service.OrderService;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;

/**
 * 微信支付业务实现类
 * 能力：创建JSAPI预支付、支付回调处理、关闭未支付订单及关单时补记支付结果
 * 说明：使用行锁 FOR UPDATE 控制订单并发，防止支付/取消并发冲突；回调验签依赖微信官方SDK
 */
@Service
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class WxPayServiceImpl implements WxPayService {

    private final WxPayProperties wxPayProperties;
    private final OrderMapper orderMapper;
    private final InventoryService inventoryService;
    // ObjectProvider 延迟获取，避免配置未开启时直接注入报错
    private final ObjectProvider<JsapiServiceExtension> jsapi;
    private final ObjectProvider<NotificationParser> parser;
    private final ObjectProvider<RefundService> refundClient;
    // 延迟获取订单服务，避免 WxPayService -> OrderService 的循环依赖（仅用于 evictAfterCommit）。
    private final ObjectProvider<OrderService> orderServices;
    // 退款回调结果落到 WxRefundService；同样用 ObjectProvider 打破 WxRefund -> WxPay -> WxRefund 环。
    private final ObjectProvider<WxRefundService> wxRefundServices;

    @org.springframework.beans.factory.annotation.Autowired
    private com.lixiaopu.service.VoucherUseService voucherUseService;
    @org.springframework.beans.factory.annotation.Autowired
    private VoucherPaymentService voucherPaymentService;

    /**
     * 获取微信JSAPI客户端，校验微信支付开关是否开启
     * @return JsapiServiceExtension 微信支付SDK客户端
     */
    private JsapiServiceExtension client() {
        if (!wxPayProperties.isEnabled()) {
            throw new IllegalStateException("微信支付尚未启用");
        }
        return jsapi.getObject();
    }

    /**
     * 通过订单号加行锁查询订单
     * 与取消操作共用行锁，避免取消后又创建新的微信预支付会话，解决并发竞争
     * @param no 订单号
     * @return 订单实体
     */
    private Order lockOrder(String no) {
        if (!StringUtils.hasText(no)) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        // FOR UPDATE 行锁，事务内锁住这条订单，其他事务阻塞
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, no)
                .last("FOR UPDATE"));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    /**
     * 权限校验：校验当前登录用户是否为订单归属人，同时校验订单未删除
     * @param order 订单
     */
    private void requireOwner(Order order) {
        var user = CurrentHolder.getCurrentUser();
        if (user == null
                || !Objects.equals(user.getId(), order.getUserId())
                || order.getIsDeleted() == CommonStatus.ACTIVE) {
            throw new IllegalArgumentException("订单不存在或无权操作");
        }
    }

    /**
     * 金额转换：元 -> 分，微信支付单位为分
     * @param amount 元，BigDecimal
     * @return 分为单位的int
     */
    static int toCents(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("订单金额无效");
        }
        try {
            // 小数点右移两位，精确转int，溢出/小数过多抛异常
            return amount.movePointRight(2).intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("订单金额精度或范围无效", ex);
        }
    }

    /**
     * 创建微信JSAPI预支付订单，返回前端调起支付需要的参数
     * @param orderNo 订单号
     * @return 预支付响应，内含payment参数给前端拉起微信支付
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepayWithRequestPaymentResponse createPrepayOrder(String orderNo) {
        Order order = lockOrder(orderNo);
        requireOwner(order);

        // 校验订单状态：待支付、且未超时
        if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT
                || order.getPayStatus() != PayStatusEnum.UNPAID
                || order.getPayExpireTime() == null
                || !order.getPayExpireTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) {
            throw new IllegalArgumentException("订单已过期或状态不允许支付");
        }

        String openid = CurrentHolder.getCurrentUser().getOpenid();
        if (!StringUtils.hasText(openid)) {
            throw new IllegalArgumentException("请先使用微信登录");
        }

        // 组装预支付请求
        PrepayRequest request = new PrepayRequest();
        request.setAppid(wxPayProperties.getAppId());
        request.setMchid(wxPayProperties.getMerchantId());
        request.setNotifyUrl(wxPayProperties.getNotifyUrl());
        request.setOutTradeNo(orderNo);
        request.setDescription("栗小铺订单-" + orderNo);
        // 支付过期时间，转为OffsetDateTime字符串
        request.setTimeExpire(order.getPayExpireTime()
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toOffsetDateTime()
                .toString());

        Amount amount = new Amount();
        amount.setTotal(toCents(order.getTotalAmount()));
        amount.setCurrency("CNY");
        request.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        return client().prepayWithRequestPayment(request);
    }

    /**
     * 微信支付异步通知入口
     * 完成签名校验，解析报文，交给accept处理支付成功逻辑
     * @param request HttpServletRequest，用于获取微信签名头
     * @param body 回调原始报文
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayNotify(HttpServletRequest request, String body) {
        if (!wxPayProperties.isEnabled()) {
            throw new IllegalStateException("微信支付尚未启用");
        }
        // 组装验签参数，从header读取微信签名相关字段
        RequestParam param = new RequestParam.Builder()
                .serialNumber(request.getHeader("Wechatpay-Serial"))
                .nonce(request.getHeader("Wechatpay-Nonce"))
                .signature(request.getHeader("Wechatpay-Signature"))
                .timestamp(request.getHeader("Wechatpay-Timestamp"))
                .body(body)
                .build();
        // SDK内部自动验签，解析成Transaction对象
        accept(parser.getObject().parse(param, Transaction.class));
    }

    /**
     * 统一处理支付成功交易报文（支付回调、关单发现已支付后的查单都会走到这里）
     * 幂等逻辑：已经记录transactionId则不再重复更新；校验金额、商户、appid
     * @param tx 微信交易报文
     */
    private void accept(Transaction tx) {
        // 校验appid、商户号，防止回调伪造
        if (!Objects.equals(wxPayProperties.getAppId(), tx.getAppid())
                || !Objects.equals(wxPayProperties.getMerchantId(), tx.getMchid())) {
            throw new IllegalArgumentException("支付通知商户信息不匹配");
        }
        // 非成功状态直接返回，不处理
        if (tx.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            return;
        }

        //优惠券分流
        if (tx.getOutTradeNo() != null && tx.getOutTradeNo().startsWith(VoucherPaymentService.PREFIX)) {
            voucherPaymentService.accept(tx);
            return;
        }

        Order order = lockOrder(tx.getOutTradeNo());
        // 校验流水号、金额、币种
        if (!StringUtils.hasText(tx.getTransactionId())
                || tx.getAmount() == null
                || !Objects.equals(tx.getAmount().getTotal(), toCents(order.getTotalAmount()))
                || !"CNY".equals(tx.getAmount().getCurrency())) {
            throw new IllegalArgumentException("支付通知金额或流水无效");
        }

        // 幂等：已经存在交易流水
        if (order.getTransactionId() != null) {
            if (!order.getTransactionId().equals(tx.getTransactionId())) {
                throw new IllegalArgumentException("支付流水不匹配");
            }
        } else {
            // 流水为空，说明首次收到支付成功，校验状态必须是待支付
            if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT) {
                throw new IllegalStateException("订单状态不允许确认支付");
            }
            // 微信成功时间转北京时间LocalDateTime
            LocalDateTime paid = OffsetDateTime.parse(tx.getSuccessTime())
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();

            // 条件更新：id、状态、transactionId为空，防止并发重复更新
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, OrderStatusEnum.PENDING_PAYMENT)
                    .isNull(Order::getTransactionId)
                    .set(Order::getStatus, OrderStatusEnum.PENDING_SHIPMENT)
                    .set(Order::getPayStatus, PayStatusEnum.PAID)
                    .set(Order::getPayTime, paid)
                    .set(Order::getTransactionId, tx.getTransactionId()));
            if (updated != 1) {
                throw new IllegalStateException("支付状态更新失败，请重试");
            }
            if (order.getVoucherOrderId() != null) voucherUseService.consume(order, paid);
        }
        // 事务提交后删除订单缓存
        orderServices.getObject().evictAfterCommit(order.getOrderNo());
    }



    /**
     * 关闭未支付订单：调用微信关单接口 + 修改本地订单为已取消
     * 兼容场景：微信提示ORDERPAID（实际已经支付），自动走accept完成支付流程
     * @param orderNo 订单号
     * @param reason 取消原因
     * @return true 本地订单成功取消；false 订单状态不允许关闭或者已经支付
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeUnpaidOrder(String orderNo, String reason) {
        Order order = lockOrder(orderNo);
        // 只有待支付才允许关闭
        if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT) {
            return false;
        }

        if (wxPayProperties.isEnabled()) {
            CloseOrderRequest request = new CloseOrderRequest();
            request.setMchid(wxPayProperties.getMerchantId());
            request.setOutTradeNo(orderNo);
            try {
                client().closeOrder(request);
            } catch (ServiceException ex) {
                // 错误码ORDERPAID：微信侧该单已经支付成功，查单并执行支付成功逻辑
                if ("ORDERPAID".equals(ex.getErrorCode())) {
                    QueryOrderByOutTradeNoRequest query = new QueryOrderByOutTradeNoRequest();
                    query.setMchid(wxPayProperties.getMerchantId());
                    query.setOutTradeNo(orderNo);
                    Transaction tx = client().queryOrderByOutTradeNo(query);
                    if (tx.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
                        throw ex;
                    }
                    accept(tx);
                    return false;
                }
                // ORDER_NOT_EXIST：微信没有该笔预支付记录，允许直接取消本地订单，不抛异常
                if (!"ORDER_NOT_EXIST".equals(ex.getErrorCode())) {
                    throw ex;
                }
            }
        }

        // 更新本地订单为已取消，条件更新防并发
        boolean updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatusEnum.PENDING_PAYMENT)
                .set(Order::getStatus, OrderStatusEnum.CANCELLED)
                .set(Order::getCancelTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                .set(Order::getCancelReason, reason)) == 1;

        if (updated) {
            inventoryService.release(order.getId());
            if (order.getVoucherOrderId() != null) voucherUseService.release(order, false);
        }
        orderServices.getObject().evictAfterCommit(orderNo);
        return updated;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SignedRefundNotification extends RefundNotification {
        private String mchid;
    }

    /** 包括审核提交后进程中断的订单；只扫描订单表中的“退款中”。 */
    @Scheduled(fixedDelayString = "${wxpay.refund-scan-ms:60000}")
    public void scanRefunds() {
        if (!wxPayProperties.isEnabled() || !StringUtils.hasText(wxPayProperties.getRefundNotifyUrl())) return;
        for (Order order : orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getPayStatus, PayStatusEnum.REFUNDING)
                .le(Order::getUpdateTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(1))
                .orderByAsc(Order::getUpdateTime).last("LIMIT 100"))) {
            try { processRefund(order.getOrderNo()); }
            catch (RuntimeException ex) { log.warn("订单 {} 退款同步失败，稍后继续查询", order.getOrderNo(), ex); }
        }
    }

    /** 不开启数据库事务；先查后退，超时重试仍使用同一退款单号。 */
    @Override
    public void processRefund(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (order.getPayStatus() == PayStatusEnum.REFUND_SUCCESS) return;
        if (order.getPayStatus() != PayStatusEnum.REFUNDING || !StringUtils.hasText(order.getRefundNo()))
            throw new IllegalStateException("只有商家同意后的退款可执行或同步");
        try {
            if (!wxPayProperties.isEnabled() || !StringUtils.hasText(wxPayProperties.getRefundNotifyUrl()))
                throw new IllegalStateException("请启用微信支付并配置退款通知地址");
            RefundService client = refundClient.getObject();
            QueryByOutRefundNoRequest query = new QueryByOutRefundNoRequest();
            query.setOutRefundNo(order.getRefundNo());
            Refund result;
            try {
                result = client.queryByOutRefundNo(query);
            } catch (ServiceException ex) {
                // 仅明确查无此单才提交；网络错误、已关闭或异常退款都不能换单重退。
                if (!"RESOURCE_NOT_EXISTS".equals(ex.getErrorCode())) throw ex;
                CreateRequest request = new CreateRequest();
                request.setTransactionId(order.getTransactionId()); request.setOutRefundNo(order.getRefundNo());
                request.setReason(order.getRefundReason()); request.setNotifyUrl(wxPayProperties.getRefundNotifyUrl());
                long cents = toCents(order.getTotalAmount());
                AmountReq amount = new AmountReq(); amount.setTotal(cents); amount.setRefund(cents); amount.setCurrency("CNY");
                request.setAmount(amount);
                result = client.create(request);
            }
            wxRefundServices.getObject().recordRefund(result);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? "微信退款请求失败" : ex.getMessage();
            orderMapper.update(null, new LambdaUpdateWrapper<Order>().eq(Order::getId, order.getId())
                    .eq(Order::getPayStatus, PayStatusEnum.REFUNDING)
                    .set(Order::getRefundFailReason, message.substring(0, Math.min(500, message.length())))
                    .set(Order::getUpdateTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai"))));
            orderServices.getObject().evictAfterCommit(orderNo);
            throw ex;
        }
    }

    @Override
    public void handleRefundNotify(HttpServletRequest request, String body) {
        if (!wxPayProperties.isEnabled()) throw new IllegalStateException("微信支付尚未启用");
        RequestParam param = new RequestParam.Builder().serialNumber(request.getHeader("Wechatpay-Serial"))
                .nonce(request.getHeader("Wechatpay-Nonce")).signature(request.getHeader("Wechatpay-Signature"))
                .timestamp(request.getHeader("Wechatpay-Timestamp")).body(body).build();
        SignedRefundNotification notice = parser.getObject().parse(param, SignedRefundNotification.class);
        if (!Objects.equals(wxPayProperties.getMerchantId(), notice.getMchid()))
            throw new IllegalArgumentException("退款通知商户不匹配");
        Refund result = new Refund(); result.setOutTradeNo(notice.getOutTradeNo()); result.setOutRefundNo(notice.getOutRefundNo());
        result.setTransactionId(notice.getTransactionId()); result.setRefundId(notice.getRefundId());
        result.setStatus(notice.getRefundStatus()); result.setSuccessTime(notice.getSuccessTime());
        // 官方退款通知的金额对象可不带币种，本项目仅接入人民币交易。
        if (notice.getAmount() != null && notice.getAmount().getCurrency() == null) notice.getAmount().setCurrency("CNY");
        result.setAmount(notice.getAmount());
        wxRefundServices.getObject().recordRefund(result);
    }
}
