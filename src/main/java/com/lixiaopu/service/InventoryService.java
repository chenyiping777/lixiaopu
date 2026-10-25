package com.lixiaopu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.infrastructure.rocketmq.product.ProductSyncOutbox;
import com.lixiaopu.mapper.OrderItemMapper;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.mapper.ProductSpecMapper;
import com.lixiaopu.pojo.dto.OrderItemDTO;
import com.lixiaopu.pojo.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 库存与订单共用本地事务；不加版本号，由扣减后库存非负的原子条件更新判断是否成功。 */
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final ProductMapper products;
    private final ProductSpecMapper specs;
    private final OrderItemMapper orderItems;
    private final ProductSyncOutbox outbox;

    /**
     * 库存预占（扣减主商品总库存 + 规格库存，生成订单商品快照）
     * 事务传播：MANDATORY → 必须在已有事务内执行，不能单独新开事务，和外层 createOrder 共用同一个事务
     * 任意 Exception 都会触发事务回滚，保证库存扣减和订单创建原子性
     * 适配当前购物车业务：购物车条目加入后规格、数量固定，不会出现同一次下单重复specId
     * @param orderItemDTOList 前端传过来的订单明细DTO列表
     * @return 填充好价格、小计、商品快照信息的订单明细实体列表
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<OrderItem> reserve(List<OrderItemDTO> orderItemDTOList) {

        // quantities：外层key=productId，内层key=specId，value=该规格本次要购买数量
        // TreeMap：统一数据库 UPDATE 的行锁获取顺序，降低并发死锁风险；不预先 SELECT FOR UPDATE。
        // 外层：key=productId → TreeMap → 商品ID从小到大遍历
        // 内层：每个product对应的 specMap = new TreeMap<>(); key=specId → 规格ID从小到大遍历
        Map<Long, Map<Long, Integer>> quantities = new TreeMap<>();
        for (OrderItemDTO item : orderItemDTOList) {
            // 按商品分组；业务上不会出现同一个spec重复提交，直接put，不再需要merge累加
            quantities.computeIfAbsent(item.getProductId(), ignored -> new TreeMap<>())
                    .put(item.getSpecId(), item.getQuantity());
        }

        List<OrderItem> result = new ArrayList<>();

        // 锁顺序固定：先按productId升序，再按specId升序操作，防止循环等待产生死锁
        for (var productEntry : quantities.entrySet()) {
            long productId = productEntry.getKey();
            // 汇总该商品下所有规格本次购买总和，扣减商品主表总库存
            long total = productEntry.getValue().values().stream().mapToLong(Integer::longValue).sum();
            // deductStock 返回影响行数 !=1：商品下架 / 主表总库存不足，抛出409冲突异常
            if (products.deductStock(productId, total) != 1) {
                throw conflict("商品未上架或库存不足");
            }
            // 查询商品主表记录（当前事务内，前面扣库存SQL已经命中行锁）
            var product = products.selectById(productId);

            // 遍历该商品下每一个规格，扣规格独立库存
            for (var specEntry : productEntry.getValue().entrySet()) {
                Long specId = specEntry.getKey();
                Integer buyQty = specEntry.getValue();
                // 扣规格库存，SQL条件同时校验：规格存在、归属该商品、规格库存充足
                // 返回行数!=1 → 校验不通过，抛冲突异常
                if (specs.deductStock(productId, specId, buyQty) != 1) {
                    throw conflict("规格不存在、归属不匹配或库存不足");
                }
                // 查询规格数据，事务内持有该行行锁，拿到扣减后的规格最新数据
                var spec = specs.selectById(specId);
                // 校验规格价格合法，不能为空、不能负数
                if (spec.getPrice() == null || spec.getPrice().signum() < 0) {
                    throw new IllegalStateException("规格价格无效");
                }
                // 构建订单明细快照：下单那一刻的商品、规格、价格快照，防止后续改商品价格影响历史订单
                OrderItem snapshot = OrderItem.builder()
                        .productId(productId)
                        .specId(spec.getId())
                        .quantity(buyQty)
                        .price(spec.getPrice())
                        .subtotal(spec.getPrice().multiply(BigDecimal.valueOf(buyQty)))
                        .productName(product.getName())
                        .productImage(product.getImage())
                        .specText(spec.getSpecText())
                        .build();
                result.add(snapshot);

            }
            // 写入事务内 outbox 消息表（事务发件箱模式），记录该商品库存变动事件，用于后续异步通知/同步其他系统
            outbox.record(productId);
        }
        // 返回快照明细集合，交给外层订单创建逻辑保存到order_item表
        return result;
    }



    /** 仅在未支付取消成功、或未发货订单全额退款成功的事务中调用；调用方保证幂等，禁止使用客户端数量回补。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void release(Long orderId) {
        List<OrderItem> saved = orderItems.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId));
        if (saved.isEmpty()) throw new IllegalStateException("订单缺少库存明细，不能释放库存");
        Map<Long, Map<Long, Long>> quantities = new TreeMap<>();
        for (OrderItem item : saved) {
            if (item.getProductId() == null || item.getSpecId() == null
                    || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalStateException("订单库存明细无效");
            }
            quantities.computeIfAbsent(item.getProductId(), ignored -> new TreeMap<>())
                    .merge(item.getSpecId(), item.getQuantity().longValue(), Math::addExact);
        }
        for (var productEntry : quantities.entrySet()) {
            long total = productEntry.getValue().values().stream().mapToLong(Long::longValue).sum();
            if (products.adjustStock(productEntry.getKey(), total) != 1) {
                throw new IllegalStateException("商品库存回补失败");
            }
            for (var specEntry : productEntry.getValue().entrySet()) {
                if (specs.adjustStock(productEntry.getKey(), specEntry.getKey(), specEntry.getValue()) != 1) {
                    throw new IllegalStateException("规格库存回补失败");
                }
            }
            outbox.record(productEntry.getKey());
        }
    }

    /** 后台补货/调减库存使用增量，避免旧编辑页面覆盖下单后的库存。 */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long productId, Long specId, Integer delta) {
        if (products.adjustStock(productId, delta.longValue()) != 1
                || specs.adjustStock(productId, specId, delta.longValue()) != 1) {
            throw conflict("商品或规格不存在，或调整后库存超出范围");
        }
        outbox.record(productId);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
