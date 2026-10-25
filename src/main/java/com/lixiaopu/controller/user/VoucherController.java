package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.service.VoucherService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/api/vouchers")
public class VoucherController {
    @Autowired
    private VoucherService vouchers;
    /**
     * 分页查询秒杀活动列表
     *
     * <p>游标分页：前端上拉加载时把上一页最后一条 id 作为 afterId 传回来，
     * 服务端只查 id > afterId 的记录，避免深分页性能问题。</p>
     *
     * @param afterId 上一页最大 id，首次请求传 0（默认值），必须非负
     * @param limit   每页条数，默认 20，范围 1~100（防止一次拉太多拖垮 DB）
     */
    @GetMapping
    public Result list(@RequestParam(defaultValue = "0") @PositiveOrZero long afterId,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "1") @Min(0) @Max(1) int type) {
        // 直接透传 Service，Service 内部会逐券聚合库存/活动时间
        return Result.success(vouchers.activities(afterId, limit, type));
    }

    /**
     * 查询单张秒杀券详情
     *
     * @param id 优惠券主键，必须为正数（@Positive 拦截 0 和负数）
     * @return 包含基本信息 + 剩余库存 + 活动起止时间的 VO
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable @Positive long id) {
        // Service 层会在券不存在时抛 ResponseStatusException(404)，
        // 在库存记录缺失时抛 IllegalStateException(500)，由全局异常处理统一转 Result
        return Result.success(vouchers.detail(id));
    }
}
