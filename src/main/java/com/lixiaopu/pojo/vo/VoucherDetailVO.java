package com.lixiaopu.pojo.vo;

import com.lixiaopu.pojo.entity.Voucher;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class VoucherDetailVO extends Voucher {
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
