package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.mapper.*;
import com.lixiaopu.pojo.dto.VoucherCreateDTO;
import com.lixiaopu.pojo.entity.*;
import com.lixiaopu.pojo.vo.VoucherDetailVO;
import com.lixiaopu.service.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * 优惠券（秒杀券）业务实现类
 *
 * <p>继承 MyBatis-Plus 的 ServiceImpl，获得单表 CRUD 能力；
 * 同时组合 SeckillVoucherMapper 操作库存表，组合 VoucherOrderService 做缓存预热。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {

    /** 秒杀券库存表 Mapper：与优惠券一对一，单独存储库存与活动时间 */
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    /** 优惠券订单 Service：事务提交后调用其 rebuildCache 预热 Redis 库存缓存 */
    @Autowired
    private VoucherOrderService voucherOrderService;

    /**
     * 创建秒杀优惠券
     *
     * <p>事务内做两件事：
     * <ol>
     *     <li>保存主表 voucher（基本信息）</li>
     *     <li>保存从表 seckill_voucher（库存 + 活动开始/结束时间）</li>
     * </ol>
     * 事务提交成功后，再异步触发缓存预热；若预热失败不回滚数据库，仅打印日志告警，
     * 避免缓存写入失败拖垮发券流程。</p>
     *
     * @param request 前端传入的建券请求（金额、库存、时间等）
     * @return 新生成的优惠券主键 id
     */
    @Override
    @Transactional
    public Long create(VoucherCreateDTO request) {
        // 校验活动时间：开始时间必须早于结束时间，且结束时间必须晚于当前时间（否则是一张"已过期"的废券）
        if (!request.getBeginTime().isBefore(request.getEndTime()) || !request.getEndTime().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("结束时间必须晚于开始时间及当前时间");

        // 校验金额：购券价（payValue）不能大于抵扣面额（actualValue），否则用户倒贴钱
        if (request.getPayValue() > request.getActualValue())
            throw new IllegalArgumentException("购券金额不能大于抵扣金额");

        // 1. 拷贝 DTO 字段到 Voucher 实体，并补全业务字段
        var voucher = new Voucher();
        BeanUtils.copyProperties(request, voucher);
        voucher.setType(1);// 1 = 秒杀券（与普通券区分）
        voucher.setStatus(1); // 1 = 上架可用

        // 保存主表，影响行数为 0 视为失败（理论上 save() 返回 boolean）
        if (!save(voucher)) throw new IllegalStateException("保存优惠券失败");

        // 2. 组装秒杀库存记录：与 voucher 一对一，共用 voucherId 作为主键关联
        var stock = new SeckillVoucher();
        stock.setVoucherId(voucher.getId());            // 回写刚生成的券 id
        stock.setStock(request.getStock());
        stock.setBeginTime(request.getBeginTime());
        stock.setEndTime(request.getEndTime());


        // 影响行数必须为 1，否则抛异常触发事务回滚
        if (seckillVoucherMapper.insert(stock) != 1) throw new IllegalStateException("保存秒杀库存失败");

        // 3. 注册事务同步回调：等当前事务真正提交（commit）之后再执行缓存预热
        //    好处：如果事务回滚，缓存不会被写入脏数据；
        //    用 afterCommit 而不是方法末尾直接调用，是因为方法末尾事务可能还未提交，
        //    此时 Redis 里写入的库存对其他事务可能不可见。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    voucherOrderService.rebuildCache(voucher.getId());
                } catch (Exception failure) {
                    // 缓存预热失败不影响已经落库的券，仅告警，由运维或手动触发重建
                    log.error("秒杀券已保存，缓存预热失败，请重建缓存，voucherId={}", voucher.getId(), failure);
                }
            }
        });

        return voucher.getId();
    }

    /**
     * 根据 id 查询优惠券详情
     *
     * <p>聚合主表基本信息 + 从表库存/活动时间，返回给前端展示。</p>
     */
    @Override
    public VoucherDetailVO detail(long id) {
        // 主表不存在 → 404
        var voucher = getById(id);
        if (voucher == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "优惠券不存在");

        // 拷贝基本字段
        var result = new VoucherDetailVO();
        BeanUtils.copyProperties(voucher, result);

        if (Integer.valueOf(0).equals(voucher.getType())) {
            return result;
        }

        // 从表：用 voucherId 查库存记录（注意 SeckillVoucher 的主键就是 voucherId）
        var stock = seckillVoucherMapper.selectById(id);
        if (stock == null) throw new IllegalStateException("秒杀券库存记录不存在");

        // 把库存和活动时间补充到 VO
        result.setStock(stock.getStock());
        result.setBeginTime(stock.getBeginTime());
        result.setEndTime(stock.getEndTime());

        return result;
    }

    /**
     * 分页查询正在进行的秒杀活动列表（游标分页）
     *
     * <p>用 afterId 作为上一页最后一条 id，每次只查 id > afterId 的记录，
     * 避免深分页 LIMIT offset 过大带来的性能问题；按 id 升序保证顺序稳定。</p>
     *
     * @param afterId 上一页最大 id，首次传 0
     * @param limit   每页条数
     */
    @Override
    public List<VoucherDetailVO> activities(long afterId, int limit) {
        return activities(afterId,limit,1);
    }

    @Override
    public List<VoucherDetailVO> activities(long afterId, int limit, int type) {
        if (type!=0 && type!=1) throw new IllegalArgumentException("优惠券类型不合法");
        return lambdaQuery()
                .eq(Voucher::getStatus, 1)              // 只查上架的券
                .eq(Voucher::getType, type)
                .gt(Voucher::getId, afterId)            // 游标：id 大于上一页最后一条
                .orderByAsc(Voucher::getId)             // 按 id 升序，保证游标推进方向一致
                .last("LIMIT " + limit)                 // 每页条数（注意此处 limit 是 int，无 SQL 注入风险）
                .list()
                .stream()
                .map(voucher -> detail(voucher.getId())) // 逐条聚合库存/时间详情
                .toList();
    }
}
