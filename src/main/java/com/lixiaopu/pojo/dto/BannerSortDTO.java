package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BannerSortDTO {
    @NotNull @Positive
    private Long id;
    @NotNull @Min(0)
    private Integer sort;
}
