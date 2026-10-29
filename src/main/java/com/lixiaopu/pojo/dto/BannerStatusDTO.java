package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BannerStatusDTO {
    @NotNull @Positive
    private Long id;
    @NotNull @Min(0) @Max(1)
    private Integer status;
}
