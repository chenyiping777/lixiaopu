package com.lixiaopu.pojo.dto;

import lombok.Data;

@Data
public class NoticeDTO {

    private String id;
    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;


    private Integer status;
}
