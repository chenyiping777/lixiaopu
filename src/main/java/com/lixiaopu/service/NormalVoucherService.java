package com.lixiaopu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.common.generator.SnowflakeOrderId;
import com.lixiaopu.mapper.VoucherMapper;
import com.lixiaopu.mapper.VoucherOrderMapper;
import com.lixiaopu.pojo.dto.NormalVoucherCreateDTO;
import com.lixiaopu.pojo.entity.Voucher;
import com.lixiaopu.pojo.entity.VoucherOrder;
import com.lixiaopu.pojo.vo.OwnedVoucherVO;
import com.lixiaopu.properties.OrderProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 普通代金券同步创建待支付订单，付款与抵扣复用现有链路，不进入秒杀机制。 */
@Service
@RequiredArgsConstructor
public class NormalVoucherService {
    private final VoucherMapper vouchers;
    private final VoucherOrderMapper orders;
    private final SnowflakeOrderId ids;
    private final OrderProperties properties;

    public long create(NormalVoucherCreateDTO request) {
        if (request.getPayValue()>request.getActualValue())
            throw new IllegalArgumentException("购券金额不能大于抵扣面额");
        var voucher=new Voucher(); BeanUtils.copyProperties(request,voucher);
        voucher.setType(0); voucher.setStatus(1);
        if (vouchers.insert(voucher)!=1) throw new IllegalStateException("普通券发布失败");
        return voucher.getId();
    }

    public void setStatus(long voucherId,int status) {
        if (status!=0 && status!=1) throw new IllegalArgumentException("状态只能是0下架或1上架");
        if (vouchers.update(null,Wrappers.<Voucher>lambdaUpdate().eq(Voucher::getId,voucherId)
                .eq(Voucher::getType,0).set(Voucher::getStatus,status))!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"普通券不存在");
    }

    @Transactional(rollbackFor=Exception.class)
    public OwnedVoucherVO buy(long voucherId,long userId) {
        var voucher=vouchers.selectById(voucherId);
        if (voucher==null || !Integer.valueOf(0).equals(voucher.getType()) || !Integer.valueOf(1).equals(voucher.getStatus()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"普通券不存在或已下架");
        var now=LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);
        var order=new VoucherOrder();
        order.setId(ids.nextId()); order.setUserId(userId); order.setVoucherId(voucherId);
        order.setStatus(VoucherUseService.UNPAID);
        order.setPayValue(voucher.getPayValue()); order.setActualValue(voucher.getActualValue());
        order.setValidityDays(voucher.getValidityDays());
        order.setPayExpireTime(now.plusSeconds(properties.getPaymentTimeoutSeconds()));
        order.setCreateTime(now); order.setUpdateTime(now);
        if (orders.insert(order)!=1) throw new IllegalStateException("普通券购买订单创建失败");
        return OwnedVoucherVO.from(order,now);
    }
}
