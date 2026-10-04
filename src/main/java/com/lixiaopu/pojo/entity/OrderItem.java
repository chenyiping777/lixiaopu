package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 栗小铺 order_item
 * @TableName order_item
 */
@TableName(value ="order_item")
@Data
public class OrderItem {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long orderId;

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
    private String productName;

    /**
     * 
     */
    private String specText;

    /**
     * 
     */
    private String productImage;

    /**
     * 
     */
    private BigDecimal price;

    /**
     * 
     */
    private Integer quantity;

    /**
     * 
     */
    private BigDecimal subtotal;

    /**
     * 
     */
    private Date createTime;
}