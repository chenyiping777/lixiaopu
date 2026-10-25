package com.lixiaopu.pojo.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class ScrollQueryDTO {
    // 首批不传，后续传上一批的 endId。
    @Positive
    private Long beginId;
    @NotBlank
    private String pageName = "allPage";
    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;
}
