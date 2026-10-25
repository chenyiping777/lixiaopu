package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 金额单位为分；本模块发布限量、一人一单的秒杀代金券。 */
/**
 * 创建秒杀优惠券的请求 DTO
 *
 * <p>用于后台运营新增秒杀券时接收前端参数，配合 jakarta.validation 在 Controller 层做参数校验。
 * 金额单位统一为「分」（Long），避免浮点精度问题；有效期用天数字段兜底，时间范围用 beginTime/endTime 精确控制。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoucherCreateDTO {

    /** 券标题，列表页主标题，必填，最长 128 字 */
    @NotBlank
    @Size(max = 128)
    private String title;

    /** 副标题/活动卖点，可空，最长 255 字 */
    @Size(max = 255)
    private String subTitle;

    /** 使用规则文案（如"满 100 可用，不与其他优惠同享"），可空，最长 2000 字 */
    @Size(max = 2000)
    private String rules;

    /** 购券价格（用户实际付的钱），单位：分，必填，必须为正数，上限用 Integer.MAX_VALUE 兜底防止溢出 */
    @NotNull
    @Positive
    @Max(2147483647L)
    private Long payValue;

    /** 抵扣面额（券值），单位：分，必填，必须为正数；业务上要求 actualValue >= payValue */
    @NotNull
    @Positive
    private Long actualValue;

    /** 领券后有效天数，默认 30 天，范围 1~3650（约 10 年） */
    @NotNull
    @Min(1)
    @Max(3650)
    private Integer validityDays = 30;

    /** 发放库存总数，必填，必须为正整数 */
    @NotNull
    @Positive
    private Integer stock;

    /** 秒杀活动开始时间，必填，到秒级精度 */
    @NotNull
    private LocalDateTime beginTime;

    /** 秒杀活动结束时间，必填，业务上要求晚于 beginTime 且晚于当前时间 */
    @NotNull
    private LocalDateTime endTime;

}
