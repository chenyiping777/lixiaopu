package com.lixiaopu.service;

import com.lixiaopu.pojo.dto.VoucherCreateDTO;
import com.lixiaopu.pojo.vo.VoucherDetailVO;
import com.lixiaopu.pojo.entity.Voucher;
import com.baomidou.mybatisplus.spring.service.IService;
import java.util.List;

public interface VoucherService extends IService<Voucher> {
    Long create(VoucherCreateDTO request);
    VoucherDetailVO detail(long id);
    List<VoucherDetailVO> activities(long afterId, int limit);
    List<VoucherDetailVO> activities(long afterId, int limit, int type);
}
