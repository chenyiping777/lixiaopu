package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** 普通付费代金券，不设购买库存或活动时间；金额单位为分。 */
@Data
public class NormalVoucherCreateDTO {
    @NotBlank @Size(max=128) private String title;
    @Size(max=255) private String subTitle;
    @Size(max=2000) private String rules;
    @NotNull @Positive @Max(2147483647L) private Long payValue;
    @NotNull @Positive private Long actualValue;
    @NotNull @Min(1) @Max(3650) private Integer validityDays = 30;
}
