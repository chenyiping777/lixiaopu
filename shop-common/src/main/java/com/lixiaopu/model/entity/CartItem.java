package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 购物车条目，同一用户的同一商品合并数量
 * @TableName cart_item
 */
@TableName(value ="cart")
@Data
public class CartItem {
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
    private Long specId;

    /**
     * 
     */
    private Integer quantity;

    /**
     * 是否勾选
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
