package com.lixiaopu.service.impl;

import com.lixiaopu.properties.OrderProperties;
import com.lixiaopu.common.constant.DataConstant;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.common.constant.MessageConstant;
import com.lixiaopu.common.constant.RegexConstants;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.mapper.OrderItemMapper;
import com.lixiaopu.pojo.vo.OrderItemVO;
import com.lixiaopu.service.WxPayService;
import com.lixiaopu.service.InventoryService;
import com.lixiaopu.common.generator.SnowflakeIdGenerator;
import com.lixiaopu.common.mapstruct.CopyMapper;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.result.ScrollQueryResult;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.infrastructure.redis.properties.RedisCacheTtlProperties;
import com.lixiaopu.job.delay.CancelUnpaidOrderDelayJob;
import com.lixiaopu.mapper.OrderMapper;
import com.lixiaopu.pojo.dto.OrderDTO;
import com.lixiaopu.pojo.dto.OrderItemDTO;
import com.lixiaopu.pojo.dto.ScrollQueryDTO;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.enums.OrderPageEnum;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import com.lixiaopu.pojo.enums.PayStatusEnum;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.lixiaopu.pojo.entity.Address;
import com.lixiaopu.pojo.entity.Order;
import com.lixiaopu.pojo.entity.OrderItem;
import com.lixiaopu.pojo.vo.OrderVO;
import com.lixiaopu.pojo.vo.OrderWithItemVO;
import com.lixiaopu.pojo.vo.OrderWithTrackingVO;
import com.lixiaopu.service.AddressService;
import com.lixiaopu.service.OrderItemService;
import com.lixiaopu.service.OrderService;
import com.lixiaopu.service.CartService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private CopyMapper copyMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private RedisCacheTtlProperties redisCacheTtlProperties;

    @Autowired
    private OrderProperties orderProperties;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private AddressService addressService;

    @Autowired
    private CancelUnpaidOrderDelayJob cancelUnpaidOrderDelayJob;

    @Autowired
    private WxPayService wxPayService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CartService cartService;

    @Autowired
    private com.lixiaopu.service.VoucherUseService voucherUseService;

    private static final Order emptyOrder = Order.builder().id(DataConstant.ZERO_LONG).build();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateFulfillmentStatus(String no, Integer status) {
        if (status == null || (status != 4 && status != 5))
            return Result.error(400, "状态只能为 4（待收货）或 5（已完成）");
        Order order = lambdaQuery().eq(Order::getOrderNo, no).one();
        if (order == null) return Result.error(404, "订单不存在");
        OrderStatusEnum next = status == 4 ? OrderStatusEnum.PENDING_RECEIPT : OrderStatusEnum.COMPLETED;
        if (!updateOrderStatus(order.getId(), no, next)) return Result.error("订单状态更新失败");
        // 缓存在事务提交后失效，响应直接读库，避免返回更新前的缓存。
        return Result.success(copyMapper.orderToOrderWithItemVO(orderMapper.getOrderDesc(no)));
    }

    private static final String PRODUCT_IDS = "productIds";


    /**
     *
     *
     * 创建订单
     * @param orderDTO 订单入参DTO，包含订单基础信息以及订单明细列表
     * @return 创建订单结果，data 为 OrderVO（订单ID、订单号、支付金额、支付截止时间）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result insertOrder(OrderDTO orderDTO) {

        if (orderDTO.getOrderItems() == null || orderDTO.getOrderItems().isEmpty())
            throw new IllegalArgumentException("订单明细不能为空");
        requireOwnedAddress(orderDTO.getAddressId());

        // 获取订单明细集合
        List<OrderItemDTO> orderItems = orderDTO.getOrderItems();
        // 原子扣减库存，同时从数据库生成价格和商品快照；失败抛异常回滚整单。
        List<OrderItem> orderItemList = inventoryService.reserve(orderItems);
        //把订单明细列表里每一条的 `subtotal`（单行小计）全部相加，得到整个订单商品总金额 goodsAmount。
        BigDecimal goodsAmount = orderItemList
                .stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 获取当前登录用户ID
        Long userId = CurrentHolder.getCurrentUser().getId();
        // 雪花算法生成唯一订单号
        String orderNo = snowflakeIdGenerator.generateOrderNo();
        // 获取订单运费
        BigDecimal freight = orderProperties.getFreight();

        Order order = copyMapper.orderDTOToOrder(orderDTO);
        // 设置用户ID、订单号
        order.setUserId(userId)
                .setOrderNo(orderNo)
                .setStatus(OrderStatusEnum.PENDING_PAYMENT)
                .setPayStatus(PayStatusEnum.UNPAID)
                .setIsDeleted(CommonStatus.INACTIVE)
                .setIsEvaluate(CommonStatus.INACTIVE)
                .setFreight(freight)
                .setTotalGoodsAmount(goodsAmount)
                .setTotalAmount(goodsAmount.add(freight));
        // 固定本地与微信支付的同一截止时间，避免配置变化延长旧订单。
        order.setPayExpireTime(LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID)
                .plusSeconds(orderProperties.getPaymentTimeoutSeconds()));
        // 保存订单主表数据
        if (!save(order)) throw new IllegalStateException("订单保存失败");

        if (orderDTO.getVoucherOrderId() != null) {
            BigDecimal discount = voucherUseService.lock(orderDTO.getVoucherOrderId(), order);
            order.setVoucherOrderId(orderDTO.getVoucherOrderId())
                    .setVoucherDiscountAmount(discount)
                    .setTotalAmount(goodsAmount.add(freight).subtract(discount));
        }

        if (order.getTotalAmount().signum()<0) throw new IllegalArgumentException("订单应付金额不能为负数");
        boolean zeroAmount=order.getTotalAmount().signum()==0;
        if (zeroAmount) {
            var paidAt=LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);
            order.setStatus(OrderStatusEnum.PENDING_SHIPMENT).setPayStatus(PayStatusEnum.PAID)
                    .setPayTime(paidAt).setPayExpireTime(null);
            if (order.getVoucherOrderId()!=null) voucherUseService.consume(order,paidAt);
        }
        if (orderDTO.getVoucherOrderId()!=null || zeroAmount) {
            boolean updated=lambdaUpdate().eq(Order::getId,order.getId())
                    .set(Order::getTotalAmount,order.getTotalAmount())
                    .set(Order::getVoucherOrderId,order.getVoucherOrderId())
                    .set(Order::getVoucherDiscountAmount,order.getVoucherDiscountAmount())
                    .set(Order::getStatus,order.getStatus()).set(Order::getPayStatus,order.getPayStatus())
                    .set(Order::getPayTime,order.getPayTime()).set(Order::getPayExpireTime,order.getPayExpireTime()).update();
            if (!updated) throw new IllegalStateException("订单结算保存失败");
        }

        // 订单明细DTO批量转为实体，绑定订单ID
        orderItemList.forEach(item -> item.setOrderId(order.getId()));
        // 批量保存订单明细
        if (!orderItemService.saveBatch(orderItemList)) throw new IllegalStateException("订单明细保存失败");

        // 转换创建订单响应，支付有效期沿用延迟取消任务的配置
        OrderVO orderVO = copyMapper.orderToOrderVO(order);
        orderVO.setPaymentDeadline(order.getPayExpireTime());
        // 获取订单来源
        String source = orderDTO.getSource();
        boolean fromCart = "CART".equals(source);
        if (fromCart) orderVO.setCartCleared(false);

        // 事务提交后：投递超时关单任务，并按需清理购物车（子步骤失败不回滚已提交订单）
        registerOrderAfterCommit(orderNo, order.getPayExpireTime(), fromCart, orderItemList, userId, orderVO);

        // 返回支付所需的订单信息
        return Result.success(orderVO);
    }

    /**
     * 订单事务提交后执行：
     * 1. 投递超时未支付自动关单延迟任务，失败由超时扫描兜底；
     * 2. 若为购物车下单，清理已下单商品并把结果回填到 orderVO.cartCleared。
     * 任意子步骤失败都只打日志，不回滚已提交的订单，避免把后置动作的失败误报为下单失败导致重复购买。
     */
    private void registerOrderAfterCommit(String orderNo, LocalDateTime payExpireTime,
                                          boolean fromCart, List<OrderItem> orderItemList,
                                          Long userId, OrderVO orderVO) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 投递延迟关单任务；失败由超时扫描任务兜底
                try {
                    if (payExpireTime!=null) cancelUnpaidOrderDelayJob.setUnpaidOrderNoToDelayQueue(orderNo, payExpireTime);
                } catch (RuntimeException failure) {
                    log.warn("订单 {} 已提交，延迟任务投递失败，由超时扫描继续处理", orderNo, failure);
                }

                // 购物车来源：清理已下单商品，清理失败不影响已创建的订单
                if (fromCart) {
                    try {
                        Result cleanup = cartService.removeOrderedProducts(orderItemList);
                        orderVO.setCartCleared(cleanup != null && Boolean.TRUE.equals(cleanup.getSuccess()));
                    } catch (RuntimeException failure) {
                        // 订单已经提交；返回订单号及清理结果，避免把清理失败报告为下单失败导致重复购买。
                        log.warn("订单 {} 已创建，购物车清理未完成，用户 {}", orderNo, userId, failure);
                    }
                }
            }
        });
    }

    /**
     * 按 Tab 分类及订单 ID 倒序游标查询，批量装配本批商品明细。
     */
    @Override
    public Result getOrderList(ScrollQueryDTO query) {
        return queryOrderList(query, CurrentHolder.getCurrentUser().getId());
    }

    @Override
    public Result getAdminOrderList(ScrollQueryDTO query) {
        return queryOrderList(query, null);
    }

    /** userId 为空仅供管理端查询全部用户；买家入口固定使用当前登录用户。 */
    private Result queryOrderList(ScrollQueryDTO query, Long userId) {
        OrderPageEnum tab = OrderPageEnum.getByPageKey(query.getPageName());
        List<Integer> statuses = tab == OrderPageEnum.ALL ? null : Arrays.stream(OrderStatusEnum.values())
                .filter(status -> status.getPageCode() == tab.getPageCode())
                .map(OrderStatusEnum::getCode)
                .toList();
        int pageSize = query.getPageSize();
        // 多取一条判断是否还有下一批，仅为实际返回的订单加载明细。
        List<Order> candidates = orderMapper.getOrderBatch(userId, statuses, query.getBeginId(), pageSize + 1);
        boolean hasMore = candidates.size() > pageSize;
        List<Order> orderRecords = hasMore ? candidates.subList(0, pageSize) : candidates;
        if (orderRecords.isEmpty()) {
            return Result.success(ScrollQueryResult.builder().list(Collections.emptyList())
                    .endId(query.getBeginId()).hasMore(false).build());
        }

        //2.收集本批所有订单id
        List<Long> orderIdList = orderRecords.stream()
                .map(Order::getId)
                .toList();

        //3.一次性in查询本批全部订单明细
        List<OrderItem> orderItemList = orderItemMapper.selectByOrderIdList(orderIdList);

        //4.先把 OrderItem 实体 -> OrderItemVO，再分组
        List<OrderItemVO> orderItemVOList = orderItemList.stream()
                .map(copyMapper::orderItemToOrderItemVO)
                .toList();

        //5.明细VO按orderId分组
        Map<Long, List<OrderItemVO>> itemGroupMap = orderItemVOList.stream()
                .collect(Collectors.groupingBy(OrderItemVO::getOrderId));

        //6.实体转VO，装配明细VO
        List<OrderWithItemVO> orderWithItemVOS = orderRecords.stream()
                .map(order -> {
                    OrderWithItemVO vo = copyMapper.orderToOrderWithItemVO(order);
                    vo.setOrderItems(itemGroupMap.getOrDefault(order.getId(), Collections.emptyList()));
                    return vo;
                }).toList();

        Long endId = orderRecords.get(orderRecords.size() - 1).getId();
        return Result.success(ScrollQueryResult.builder().list(orderWithItemVOS)
                .endId(endId).hasMore(hasMore).build());
    }

    /**
     * 查看订单详情
     * 先从Redis缓存读取订单，缓存不存在再查询数据库；查询成功后写入缓存并设置过期时间，减少DB压力
     * @param orderNo 订单编号
     * @return 订单详情VO，包含订单主信息、商品明细、收货地址
     */
    @Override
    public Result getOrderDesc(String orderNo) {
        // 生成订单对应的Redis key
        String key = RedisKeyGenerator.orderKey(orderNo);
        // 从Redis Hash中读取订单缓存
        Order order = RedisConnector.getHashObject(key, Order.class);
        // 缓存未命中，去数据库查询
        if (Objects.isNull(order)) {
            order = orderMapper.getOrderDesc(orderNo);
            // 如果数据库查不到订单，赋值空订单占位，防止缓存穿透（避免大量不存在的订单号反复打DB）
            order = Objects.isNull(order) ? emptyOrder : order;
            // 完整替换 Hash 并设置 TTL；空订单使用独立的短 TTL。
            long ttl = Objects.equals(order.getId(), DataConstant.ZERO_LONG)
                    ? redisCacheTtlProperties.getOrderEmptyTtl() : redisCacheTtlProperties.getOrderDetailTtl();
            RedisConnector.setHashObject(key, order, ttl);
        }
        // 判断是占位空订单 → 订单不存在，返回错误
        if (Objects.equals(order.getId(), DataConstant.ZERO_LONG)) {
            return Result.error(MessageConstant.ORDER_NOT_FOUND);
        }
        // 订单实体转为前端展示的复合VO（订单+订单项+地址）
        OrderWithItemVO orderWithItemVO = copyMapper.orderToOrderWithItemVO(order);
        return Result.success(orderWithItemVO);
    }


    /**
     * 取消订单
     */
    @Override
    public Result cancelOrder(String orderNo, String cancelReason) {
        Order owned = lambdaQuery().eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, CurrentHolder.getCurrentUser().getId()).one();
        if (owned == null) return Result.error("订单不存在");
        boolean isSuccess = cancelOrderCommon(orderNo, cancelReason);
        if (!isSuccess) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        Map<String, String> map = new HashMap<>(2);
        map.put(Order.Fields.orderNo, orderNo);
        map.put(Order.Fields.status, OrderStatusEnum.CANCELLED.getValue());
        return Result.success(map);
    }

    public boolean cancelOrderCommon(String orderNo, String cancelReason) {
        return wxPayService.closeUnpaidOrder(orderNo, cancelReason);
    }




    /**
     * 删除订单
     * 逻辑为软删除，只允许删除【已完成、已取消、已评价、已追评】状态的订单；同时校验只能删除当前登录用户自己的订单
     * @param orderNo 订单编号
     * @return 成功返回订单号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteOrder(String orderNo) {
        // 软删除：把isDeleted标记置为有效删除标识；
        // 条件：订单号匹配、归属是当前登录用户、订单状态属于允许删除的状态集合
        boolean isSuccess = lambdaUpdate().set(Order::getIsDeleted, CommonStatus.ACTIVE.getNumber())
                .eq(Order::getOrderNo, orderNo).eq(Order::getUserId, CurrentHolder.getCurrentUser().getId())
                .notIn(Order::getPayStatus, PayStatusEnum.REFUND_REQUESTED, PayStatusEnum.REFUNDING)
                .and(w -> w.in(Order::getStatus, OrderStatusEnum.COMPLETED, OrderStatusEnum.CANCELLED,
                        OrderStatusEnum.EVALUATED, OrderStatusEnum.REVIEWED, OrderStatusEnum.REFUNDED)
                        .or().eq(Order::getPayStatus, PayStatusEnum.REFUND_SUCCESS)).update();
        if (!isSuccess) {
            return Result.error(MessageConstant.DELETE_ERROR);
        }
        evictAfterCommit(orderNo);
        return Result.success(orderNo);
    }


    /**
     * 计算运费
     */
    @Override
    public Result getOrderFreight(String productIds, String addressId) {
        requireOwnedAddress(addressId);
        String[] productIdsArray = StringUtils.split(productIds, ",");
        HashMap<String, Object> map = new HashMap<>(2);
        map.put(Order.Fields.freight, orderProperties.getFreight());
        map.put(PRODUCT_IDS, productIdsArray);
        return Result.success(map);
    }

    private void requireOwnedAddress(String addressId) {
        if (addressId == null || !addressId.matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("请选择收货地址");
        Address address = addressService.getById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), CurrentHolder.getCurrentUser().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "收货地址不存在");
    }

    /**
     * 获取物流信息
     * 根据订单号查询订单物流相关数据，转换为物流VO返回给前端
     * @param orderNo 订单编号
     * @return 包含物流轨迹的订单VO
     */
    @Override
    public Result getOrderLogistics(String orderNo) {
        // 通过mapper查询订单以及对应的物流信息
        Order order = orderMapper.getOrderLogistics(orderNo);
        // 查询不到订单，返回订单不存在错误
        if (Objects.isNull(order)) {
            return Result.error(MessageConstant.ORDER_NOT_FOUND);
        }
        // 将订单实体转换为专门承载物流信息的VO，VO里面包含物流单号、物流轨迹等字段
        OrderWithTrackingVO orderWithTrackingVO = copyMapper.orderToOrderWithTrackingVO(order);
        return Result.success(orderWithTrackingVO);
    }

    /**
     * 条件搜索订单
     * 前端传入单个搜索关键词，后端用正则自动分类检索条件，动态拼接 SQL 查询当前用户订单的检索方案，属于单输入框多维度搜索。
     */
    @Override
    public Result searchOrderByCondition(String searchCondition) {
        Long userId = CurrentHolder.getCurrentUser().getId();
        String orderNo = null;
        String logisticsNo = null;
        String productName = null;
        if (RegexConstants.isOrderNo(searchCondition)) {
            orderNo = searchCondition;
        } else if (RegexConstants.isLogisticsNo(searchCondition)) {
            logisticsNo = searchCondition;
        } else {
            productName = searchCondition;

        }
        List<Order> orderList = orderMapper.searchOrderByCondition(orderNo, logisticsNo, productName, userId);
        if (orderList.isEmpty()) {
            return Result.success();

        }
        List<OrderWithItemVO> orderWithItemVOs = orderList.stream().map(order -> copyMapper.orderToOrderWithItemVO(order)).toList();
        return Result.success(orderWithItemVOs);
    }



    /**
     * 确定收货
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result confirmOrderReceipt(String orderNo) {
        Long userId = CurrentHolder.getCurrentUser().getId();
        Order order = lambdaQuery().eq(Order::getUserId, userId).eq(Order::getOrderNo, orderNo)
                .eq(Order::getIsDeleted, CommonStatus.INACTIVE).one();
        if (order == null) return Result.error("订单不存在");
        if (!updateOrderStatus(order.getId(), orderNo, OrderStatusEnum.COMPLETED)) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        HashMap<String, Object> map = new HashMap<>(3);
        map.put(Order.Fields.orderNo, orderNo);
        map.put(Order.Fields.status, OrderStatusEnum.COMPLETED.getValue());
        map.put(Order.Fields.receiveTime, getById(order.getId()).getReceiveTime());
        return Result.success(map);
    }


    /**
     * 根据订单ID 或 订单号来修改订单状态
     * 可以其中以个参数传null
     * @param orderId          订单 ID
     * @param orderNo         订单号
     * @param orderStatusEnum 订单修改后的状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateOrderStatus(Long orderId , String orderNo, OrderStatusEnum orderStatusEnum) {
        if (orderStatusEnum == null || (orderId == null && orderNo == null)) return false;
        // 支付、取消及恢复待支付只能由各自的库存/支付事务处理，通用入口禁止修改。
        OrderStatusEnum previous = switch (orderStatusEnum) {
            case PENDING_RECEIPT -> OrderStatusEnum.PENDING_SHIPMENT;
            case COMPLETED -> OrderStatusEnum.PENDING_RECEIPT;
            case EVALUATED -> OrderStatusEnum.COMPLETED;
            case REVIEWED -> OrderStatusEnum.EVALUATED;
            default -> null;
        };
        if (previous == null) return false;
        Order order = lambdaQuery().eq(orderId != null, Order::getId, orderId)
                .eq(orderNo != null, Order::getOrderNo, orderNo).one();
        if (order == null) return false;
        if (order.getPayStatus() != null && order.getPayStatus().blocksFulfillment()) return false;
        if (order.getStatus() == orderStatusEnum) {
            evictAfterCommit(order.getOrderNo());
            return true;
        }
        if (order.getStatus() != previous) return false;
        var update = lambdaUpdate().eq(Order::getId, order.getId()).eq(Order::getStatus, previous)
                .notIn(Order::getPayStatus, PayStatusEnum.REFUND_REQUESTED, PayStatusEnum.REFUNDING, PayStatusEnum.REFUND_SUCCESS)
                .set(Order::getStatus, orderStatusEnum);
        LocalDateTime now = LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);
        if (orderStatusEnum == OrderStatusEnum.PENDING_RECEIPT) update.set(Order::getDeliverTime, now);
        if (orderStatusEnum == OrderStatusEnum.COMPLETED) update.set(Order::getReceiveTime, now);
        boolean updated = update.update();
        if (updated) evictAfterCommit(order.getOrderNo());
        return updated;
    }


    /** 提交后清理详情缓存；失败由 TTL 兜底，无事务时立即删除。 */
    @Override
    public void evictAfterCommit(String orderNo) {
        Assert.hasText(orderNo, "orderNo must not be blank");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(orderNo);
                }
            });
        } else {
            evict(orderNo);
        }
    }

    private void evict(String orderNo) {
        try {
            // key 已不存在也视为成功；下次详情查询会查库并重建缓存。
            RedisConnector.delete(RedisKeyGenerator.orderKey(orderNo));
        } catch (RuntimeException failure) {
            log.warn("订单 {} 详情缓存删除失败，等待 TTL 过期", orderNo, failure);
        }
    }
}
