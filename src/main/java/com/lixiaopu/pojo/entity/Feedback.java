package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 栗小铺 feedback
 * @TableName feedback
 */
@TableName(value ="feedback")
@Data
public class Feedback {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long userId;

    /**
     * 
     */
    private String content;

    /**
     * 
     */
    private Object imageUrls;

    /**
     * 
     */
    private String contact;

    /**
     * 
     */
    private String replyContent;

    /**
     * 
     */
    private Long replyAdminId;

    /**
     * 
     */
    private Integer status;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date replyTime;
}