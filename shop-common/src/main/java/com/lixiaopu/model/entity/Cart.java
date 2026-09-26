package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 栗小铺 cart
 * @TableName cart
 */
@TableName(value ="cart")
@Data
public class Cart {
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
    private Long productId;

    /**
     * 
     */
    private Long specId;

    /**
     * 
     */
    private Integer quantity;

    /**
     * 
     */
    private Integer checked;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}