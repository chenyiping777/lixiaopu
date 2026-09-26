package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 栗小铺 order
 * @TableName order
 */
@TableName(value ="order")
@Data
public class Order {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String orderNo;

    /**
     * 
     */
    private Long userId;

    /**
     * 
     */
    private Long addressId;

    /**
     * 
     */
    private BigDecimal totalGoodsAmount;

    /**
     * 
     */
    private BigDecimal freight;

    /**
     * 
     */
    private BigDecimal totalAmount;

    /**
     * 
     */
    private Integer status;

    /**
     * 
     */
    private Integer payType;

    /**
     * 
     */
    private Date payTime;

    /**
     * 
     */
    private Date deliverTime;

    /**
     * 
     */
    private Date receiveTime;

    /**
     * 
     */
    private Date cancelTime;

    /**
     * 
     */
    private String cancelReason;

    /**
     * 
     */
    private String remark;

    /**
     * 
     */
    private Integer isEvaluate;

    /**
     * 
     */
    private Integer isDeleted;

    /**
     * 
     */
    private String logisticsCompany;

    /**
     * 
     */
    private String logisticsNo;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}