package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.VoucherCreateDTO;
import com.lixiaopu.service.VoucherService;
import com.lixiaopu.service.VoucherOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/api/admin/vouchers")
public class AdminVoucherController {
    @Autowired
    private VoucherService voucherService;
    @Autowired
    private VoucherOrderService voucherOrderService;
    @Autowired
    private com.lixiaopu.service.NormalVoucherService normalVouchers;

    @PostMapping("/normal")
    public Result createNormal(@Valid @RequestBody com.lixiaopu.pojo.dto.NormalVoucherCreateDTO request) {
        return Result.success(normalVouchers.create(request));
    }

    @PutMapping("/{id}/status")
    public Result normalStatus(@PathVariable @Positive long id, @RequestParam int status) {
        normalVouchers.setStatus(id,status);
        return Result.success();
    }
    /**
     * 新增优惠券
     * @param request 优惠券创建参数
     * @return 操作结果，返回新增优惠券信息
     */
    @PostMapping
    public Result create(@Valid @RequestBody VoucherCreateDTO request) {
        return Result.success(voucherService.create(request));
    }

    /**
     * 重建指定优惠券订单缓存
     * @param id 优惠券订单id（必须为正数）
     * @return 操作结果
     */
    @PostMapping("/{id}/cache/rebuild")
    public Result rebuild(@PathVariable @Positive long id) {
        voucherOrderService.rebuildCache(id);
        return Result.success();
    }

}
