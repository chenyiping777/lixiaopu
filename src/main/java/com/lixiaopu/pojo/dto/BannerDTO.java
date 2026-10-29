package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** 首页轮播图；新增时 sort 默认 0，status 默认 1。 */
@Data
public class BannerDTO {
    @Positive
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 128)
    private String title;

    @NotBlank(message = "图片地址不能为空")
    @Size(max = 1024)
    private String imageUrl;

    @Size(max = 1024)
    private String linkUrl;

    @Size(max = 32)
    private String linkType;

    @Min(0)
    private Integer sort;

    /** 1=启用，0=禁用。 */
    @Min(0) @Max(1)
    private Integer status;
}
