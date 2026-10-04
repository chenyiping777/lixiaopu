package com.lixiaopu.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CategoryDTO {
    /**
     * 分类名称
     */
    private String name;
    /**
     * 父分类 id
     */
    @Pattern(regexp = "^(0|[1-9][0-9]*)$", message = "父分类 ID 必须为非负整数")
    //字符串做正则
    private String parentId;
    /**
     * 排序
     */
    @Min(value = 0, message = "排序不能小于 0")
    private Integer sort;//越小越靠前

    private String iconUrl;// 分类图标
    /**
     * 1-启用 0-禁用
     */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
