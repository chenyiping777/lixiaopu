package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 栗小铺 coupon
 * @TableName coupon
 */
@TableName(value ="coupon")
@Data
public class Coupon {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String couponNo;

    /**
     * 
     */
    private String activityName;

    /**
     * 
     */
    private Integer couponType;

    /**
     * 
     */
    private BigDecimal faceValue;

    /**
     * 
     */
    private BigDecimal discountRate;

    /**
     * 
     */
    private BigDecimal maxDiscount;

    /**
     * 
     */
    private BigDecimal minSpend;

    /**
     * 
     */
    private Integer totalQuota;

    /**
     * 
     */
    private Integer usedQuota;

    /**
     * 
     */
    private Integer receiveQuota;

    /**
     * 
     */
    private Integer validMode;

    /**
     * 
     */
    private Date validStart;

    /**
     * 
     */
    private Date validEnd;

    /**
     * 
     */
    private Integer receiveValidDays;

    /**
     * 
     */
    private Integer limitPerPerson;

    /**
     * 
     */
    private Integer userLimitType;

    /**
     * 
     */
    private Integer useScope;

    /**
     * 
     */
    private Long mutexGroupCode;

    /**
     * 
     */
    private Integer status;

    /**
     * 
     */
    private Integer isElimination;

    /**
     * 
     */
    private Date releaseTime;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}