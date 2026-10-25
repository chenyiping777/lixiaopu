package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.common.generator.SnowflakeOrderId;
import com.lixiaopu.pojo.entity.SeckillRequest;
import com.lixiaopu.pojo.entity.SeckillVoucher;
import com.lixiaopu.pojo.entity.VoucherOrder;
import com.lixiaopu.mapper.SeckillRequestMapper;
import com.lixiaopu.mapper.SeckillVoucherMapper;
import com.lixiaopu.mapper.VoucherOrderMapper;
import com.lixiaopu.infrastructure.rocketmq.seckill.SeckillMessage;
import com.lixiaopu.infrastructure.rocketmq.seckill.SeckillOutbox;
import com.lixiaopu.infrastructure.redis.seckill.SeckillRedisGate;
import com.lixiaopu.properties.WxPayProperties;
import com.lixiaopu.service.VoucherOrderService;
import com.lixiaopu.service.VoucherUseService;
import com.lixiaopu.pojo.vo.Ticket;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.lixiaopu.common.constant.SeckillState.*;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements VoucherOrderService {

    private static final String ALREADY_ORDERED = "ALREADY_ORDERED";
    private static final String SOLD_OUT = "SOLD_OUT";
    private static final int LOCK_WAIT_SECONDS = 2;
    private final SeckillRequestMapper requests;
    private final SeckillVoucherMapper vouchers;
    private final RedissonClient redisson;
    private final SnowflakeOrderId ids;
    private final SeckillRedisGate gate;
    private final SeckillOutbox outbox;
    private final TransactionTemplate transaction;
    // 超时关单所需的微信关单客户端与支付回调入口。
    private final WxPayProperties wxPayProperties;
    private final ObjectProvider<JsapiServiceExtension> jsapi;
    private final VoucherPaymentService voucherPaymentService;

    @org.springframework.beans.factory.annotation.Value("${order.payment-timeout-seconds:3600}")
    private long paymentTimeoutSeconds = 3600;

    public VoucherOrderServiceImpl(VoucherOrderMapper orders, SeckillRequestMapper requests,
            SeckillVoucherMapper vouchers, RedissonClient redisson, SnowflakeOrderId ids,
            SeckillRedisGate gate, SeckillOutbox outbox, PlatformTransactionManager manager,
            WxPayProperties wxPayProperties, ObjectProvider<JsapiServiceExtension> jsapi,
            VoucherPaymentService voucherPaymentService) {
        this.baseMapper = orders;
        this.requests = requests;
        this.vouchers = vouchers;
        this.redisson = redisson;
        this.ids = ids;
        this.gate = gate;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(manager);
        this.wxPayProperties = wxPayProperties;
        this.jsapi = jsapi;
        this.voucherPaymentService = voucherPaymentService;
    }

    @Override
    public Ticket submit(long voucherId, long userId) {
        return withLock(activityLock(voucherId, false), () -> {
            var message = new SeckillMessage(ids.nextId(), userId, voucherId);
            
            gate.reserve(message);// Set 去重 + Redis 库存预扣 + 留下恢复凭据。
            ensureRequest(message);
            outbox.publish(message);//发送消息到 MQ（生产者)
            return new Ticket(Long.toString(message.getOrderId()), PENDING, null);
        });

    }

    @Override
    public Ticket result(long orderId, long userId) {
        var request = requests.selectOne(Wrappers.<SeckillRequest>lambdaQuery()
                .eq(SeckillRequest::getId, orderId).eq(SeckillRequest::getUserId, userId));
        if (request == null) throw new IllegalArgumentException("订单不存在");
        return new Ticket(request.getId().toString(), request.getState(), request.getReason());
    }

    @Override
    public void consume(SeckillMessage message) {
        withLock(activityLock(message.getVoucherId(), false), () ->
            withLock(redisson.getLock("lixiaopu:lock:seckill:" + message.getVoucherId() + ":" + message.getUserId()), () -> {
                try {
                    transaction.executeWithoutResult(status -> createOrder(message));

                } catch (DuplicateKeyException duplicate) {
                    if (!lambdaQuery().eq(VoucherOrder::getUserId, message.getUserId())
                            .eq(VoucherOrder::getVoucherId, message.getVoucherId()).exists()) throw duplicate;
                    transaction.executeWithoutResult(status -> requests.update(null,
                            Wrappers.<SeckillRequest>lambdaUpdate()
                                    .eq(SeckillRequest::getId, message.getOrderId())
                                    .eq(SeckillRequest::getState, PENDING)
                                    .set(SeckillRequest::getState, FAILED)
                                    .set(SeckillRequest::getReason, ALREADY_ORDERED)));
                }
                settle(requests.selectById(message.getOrderId()));
                return null;
            }));
    }
    
    private void createOrder(SeckillMessage message) {

        int claimed = requests.update(null, Wrappers.<SeckillRequest>lambdaUpdate()
                .eq(SeckillRequest::getId, message.getOrderId())
                .eq(SeckillRequest::getUserId, message.getUserId())
                .eq(SeckillRequest::getVoucherId, message.getVoucherId())
                .eq(SeckillRequest::getState, PENDING)
                .set(SeckillRequest::getState, PROCESSING));
        
        if (claimed == 0) {
            var request = requests.selectById(message.getOrderId());
            if (request != null && (SUCCESS.equals(request.getState()) || FAILED.equals(request.getState()))) return;
            throw new IllegalStateException("请求未准备好，稍后重试");
        }
        int deducted = vouchers.update(null, Wrappers.<SeckillVoucher>lambdaUpdate()
                .eq(SeckillVoucher::getVoucherId, message.getVoucherId())
                .gt(SeckillVoucher::getStock, 0).setSql("stock = stock - 1"));
        if (deducted == 0) {
            finish(message.getOrderId(), FAILED, SOLD_OUT);
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (baseMapper.insertPurchase(message.getOrderId(), message.getUserId(), message.getVoucherId(),
                now, now.plusSeconds(paymentTimeoutSeconds)) != 1)
            throw new IllegalStateException("创建购券订单失败，券模板不存在");
        finish(message.getOrderId(), SUCCESS, null);
    }

    private void finish(long orderId, String state, String reason) {
        requests.update(null, Wrappers.<SeckillRequest>lambdaUpdate()
                .eq(SeckillRequest::getId, orderId).set(SeckillRequest::getState, state)
                .set(SeckillRequest::getReason, reason).setSql("updated_at = CURRENT_TIMESTAMP"));
    }

    private void settle(SeckillRequest request) {
        if (request != null && (SUCCESS.equals(request.getState()) || FAILED.equals(request.getState()))) {
            gate.settle(new SeckillMessage(request.getId(), request.getUserId(), request.getVoucherId()),
                    SUCCESS.equals(request.getState()) ? SUCCESS : request.getReason());
        }
    }

    private void ensureRequest(SeckillMessage message) {
        if (requests.selectById(message.getOrderId()) != null) return;
        var request = new SeckillRequest();
        request.setId(message.getOrderId());
        request.setUserId(message.getUserId());
        request.setVoucherId(message.getVoucherId());
        request.setState(PENDING);
        request.setNextAttemptAt(LocalDateTime.now());
        request.setUpdatedAt(request.getNextAttemptAt());
        try {
            transaction.executeWithoutResult(status -> requests.insert(request));
        } catch (DuplicateKeyException duplicate) {
            if (requests.selectById(message.getOrderId()) == null) throw duplicate;
        }
    }

    @Override
    public void recoverReservations(long voucherId) {
        withLock(activityLock(voucherId, false), () -> {
            recover(voucherId);
            return null;
        });
    }

    private void recover(long voucherId) {
        for (var message : gate.reservations(voucherId)) {
            ensureRequest(message);
            settle(requests.selectById(message.getOrderId()));
        }
    }

    /**
     * 重建指定秒杀券的 Redis 活动缓存
     * 触发时机：
     * <ul>
     *     <li>后台新建秒杀券（事务提交后调用，见 VoucherServiceImpl#create）</li>
     *     <li>Redis 故障 / 缓存丢失后的人工恢复</li>
     *     <li>库存或活动时间发生变更后手动刷新</li>
     * </ul>
     *
     * <p>重建逻辑必须在分布式锁内执行，避免与正在进行的下单请求并发导致缓存被覆盖。
     * 重建后的缓存包含四要素：
     * <ol>
     *     <li>剩余可售库存（总库存 - 已下单数 - 待确认预扣数）</li>
     *     <li>活动开始/结束时间戳（毫秒，Asia/Shanghai 时区）</li>
     *     <li>全部已下单用户 id 集合（用于防重复下单）</li>
     *     <li>所有仍处于 PENDING 状态的预扣消息（待消息确认后落单）</li>
     * </ol>
     * </p>
     */
    @Override
    public void rebuildCache(long voucherId) {
        // 对这张券加分布式锁：true 表示加锁失败时阻塞等待，避免重建与并发下单互相覆盖
        withLock(activityLock(voucherId, true), () -> {

            // 第一步：先做"孤立预扣"回收——把数据库里状态异常但仍占用库存的预扣单持久化，
            // 防止它们在后续算库存时被漏掉，导致超卖。
            recover(voucherId);

            // 第二步：查主表拿最新券信息（库存、活动时间），缓存必须以数据库为准
            var voucher = vouchers.selectById(voucherId);
            if (voucher == null) throw new IllegalArgumentException("秒杀券不存在");

            // 第三步：捞出所有 PENDING（已扣 Redis 库存、但订单还没落库）的预扣请求
            // 这些用户已经"抢到"资格，重建缓存时必须把他们当成已购用户，否则会重复放行导致超卖
            var pending = requests.selectList(Wrappers.<SeckillRequest>lambdaQuery()
                            .eq(SeckillRequest::getVoucherId, voucherId)
                            .eq(SeckillRequest::getState, PENDING))
                    .stream()
                    .map(request -> new SeckillMessage(request.getId(), request.getUserId(), voucherId))
                    .toList();

            // 第四步：已成功下单的用户 id 集合（来自 voucher_order 表）
            // 把"已下单用户"和"PENDING 预扣用户"合并，作为缓存里的黑名单/已购名单
            var buyers = new ArrayList<>(lambdaQuery()
                    .eq(VoucherOrder::getVoucherId, voucherId)
                    .list()
                    .stream()
                    .map(VoucherOrder::getUserId)
                    .toList());
            // PENDING 用户也算"已占名额"，追加进 buyers，避免同一用户被再次放行
            pending.forEach(message -> buyers.add(message.getUserId()));

            // 第五步：调用网关把所有要素一次性写入 Redis
            //   剩余库存 = 总库存 - PENDING 数量（已下单的在落单时已经扣过 stock，这里只减待确认的）
            //   活动时间转成 Asia/Shanghai 时区的毫秒时间戳，前端和网关判断活动开始/结束用
            gate.rebuild(
                    voucherId,
                    voucher.getStock() - pending.size(),
                    voucher.getBeginTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
                    voucher.getEndTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
                    buyers,
                    pending
            );

            return null;
        });
    }


    /** 普通受理/消费持读锁；重建持写锁，避免覆盖正在处理的预扣。 */
    private RLock activityLock(long voucherId, boolean rebuilding) {
        var lock = redisson.getReadWriteLock("lixiaopu:lock:seckill:activity:" + voucherId);
        return rebuilding ? lock.writeLock() : lock.readLock();
    }

    private <T> T withLock(RLock lock, Supplier<T> action) {
        boolean acquired = false;//记录当前线程有没有成功拿到锁，初始 false
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS); // watchdog 续期
            
            if (!acquired) throw new IllegalStateException("活动处理中，请稍后重试");
            return action.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求已中断", interrupted);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()){
                
                lock.unlock();
            }

        }
    }

    // ===== 购券订单超时关单与秒杀库存回补 =====

    /** 事务结果：决定提交后是否回补 Redis 库存。 */
    private enum Outcome { SKIPPED, PAID, CANCELLED, NORMAL_CANCELLED }

    /** 扫描所有已过支付截止时间仍待支付的购券订单，逐条关单；单条失败不影响其他订单。 */
    @Override
    public void scanTimeoutOrders() {
        var now = LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);
        var expired = baseMapper.selectList(Wrappers.<VoucherOrder>lambdaQuery()
                .eq(VoucherOrder::getStatus, VoucherUseService.UNPAID)
                .le(VoucherOrder::getPayExpireTime, now)
                .isNotNull(VoucherOrder::getPayExpireTime)
                .orderByAsc(VoucherOrder::getPayExpireTime)
                .last("LIMIT 200"));
        for (var order : expired) {
            try {
                cancelOne(order.getId(), order.getVoucherId());
            } catch (RuntimeException ex) {
                log.warn("购券订单 {} 超时关单失败，下轮重试", order.getId(), ex);
            }
        }
    }

    /**
     * 关单入口。事务内完成锁单→微信关单→本地状态与库存；事务提交成功后才回补 Redis。
     * @return true 本地订单已取消并回补库存；false 订单已支付/已取消，无需处理。
     */
    private boolean cancelOne(long orderId, long voucherId) {
        Outcome outcome = transaction.execute(status -> {
            // 1. FOR UPDATE 锁单，与支付回调 accept() 的锁互斥：先到者决定结果。
            VoucherOrder order = baseMapper.selectOne(Wrappers.<VoucherOrder>lambdaQuery()
                    .eq(VoucherOrder::getId, orderId).last("FOR UPDATE"));
            if (order == null || !Objects.equals(order.getStatus(), VoucherUseService.UNPAID))
                return Outcome.SKIPPED;

            // 2. 先调微信关单；若微信侧已支付，查单并走支付成功逻辑，提交事务但不回补库存。
            if (closeOnWechat(orderId)) return Outcome.PAID;

            // 3. 条件更新订单为已取消。affected=1 才继续，这是幂等闸门。
            int changed = baseMapper.update(null, Wrappers.<VoucherOrder>lambdaUpdate()
                    .eq(VoucherOrder::getId, orderId)
                    .eq(VoucherOrder::getStatus, VoucherUseService.UNPAID)
                    .set(VoucherOrder::getStatus, VoucherUseService.CANCELLED)
                    .set(VoucherOrder::getUpdateTime,
                            LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID)));
            if (changed != 1) return Outcome.SKIPPED;

            // 4. DB 库存 +1。能走到这里说明本事务是第一个把订单从 UNPAID 改成 CANCELLED 的。
            if (order.getSeckillVoucherId()==null) return Outcome.NORMAL_CANCELLED;
            int restored=vouchers.update(null, Wrappers.<SeckillVoucher>lambdaUpdate()
                    .eq(SeckillVoucher::getVoucherId, order.getSeckillVoucherId())
                    .setSql("stock = stock + 1"));
            if (restored!=1) throw new IllegalStateException("秒杀券库存回补失败");
            return Outcome.CANCELLED;
        });

        // 5. DB 事务提交成功后，才做 Redis 库存回补；失败只打日志，不阻塞主流程。
        if (outcome == Outcome.CANCELLED) {
            try {
                gate.releaseStockOnCancel(voucherId);
            } catch (RuntimeException ex) {
                log.warn("购券订单 {} 关单后 Redis 库存回补失败，等待 rebuild 对齐", orderId, ex);
            }
            return true;
        }
        return outcome == Outcome.NORMAL_CANCELLED;
    }

    /**
     * 调微信关单。
     * @return true 微信侧已支付（已查单并落库支付状态，调用方应提交事务但不回补库存）；
     *         false 关单成功或微信侧无此预支付记录，可继续本地关单。
     * @throws ServiceException 微信返回其它错误，抛出由外层回滚并下轮重试。
     */
    private boolean closeOnWechat(long orderId) {
        if (!wxPayProperties.isEnabled()) return false;
        var outTradeNo = VoucherPaymentService.PREFIX + orderId;
        var request = new CloseOrderRequest();
        request.setMchid(wxPayProperties.getMerchantId());
        request.setOutTradeNo(outTradeNo);
        try {
            jsapi.getObject().closeOrder(request);
            return false;
        } catch (ServiceException ex) {
            if ("ORDERPAID".equals(ex.getErrorCode())) {
                var query = new QueryOrderByOutTradeNoRequest();
                query.setMchid(wxPayProperties.getMerchantId());
                query.setOutTradeNo(outTradeNo);
                Transaction remote = jsapi.getObject().queryOrderByOutTradeNo(query);
                if (remote.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
                    // MANDATORY 传播，加入当前事务；内部再次 FOR UPDATE 同行（同事务可重入）。
                    voucherPaymentService.accept(remote);
                }
                return true;
            }
            // ORDER_NOT_EXIST：微信侧无预支付记录，直接继续本地关单。
            if ("ORDER_NOT_EXIST".equals(ex.getErrorCode())) return false;
            throw ex;
        }
    }
}

