package com.lixiaopu.pojo.entity;

import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import com.lixiaopu.pojo.enums.PayStatusEnum;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单表实体（MyBatis-Plus）
 */
@Data
@TableName("`order`")
@Accessors(chain = true)
@FieldNameConstants
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 订单ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单编号（唯一，格式：年月日+随机数）
     */
    @TableField
    private String orderNo;

    /**
     * 关联用户ID（外键）
     * 滚动查询接口会用到。 /api/order/scroll/query/list 直接将 List<Order> 放入返回结果，序列化时会读取这个注解。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 关联地址ID（外键）
     */
    private Long addressId;

    /**
     * 商品总价
     */
    private BigDecimal totalGoodsAmount;

    /**
     * 运费（默认0.00）
     */
    private BigDecimal freight = BigDecimal.ZERO;

    /**
     * 应付/原实付金额（商品原价+运费-券优惠），支付和退款均按此快照。
     */
    private BigDecimal totalAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long voucherOrderId;

    @Builder.Default
    private BigDecimal voucherDiscountAmount = BigDecimal.ZERO;

    /**
     * 订单状态（默认待支付）
     */
    private OrderStatusEnum status = OrderStatusEnum.PENDING_PAYMENT;

    /** 支付及退款状态；status 继续保存履约进度。 */
    @Builder.Default
    private PayStatusEnum payStatus = PayStatusEnum.UNPAID;
    private String refundNo;
    private String refundReason;
    /** 商家拒绝或微信处理异常的原因；结合 payStatus 判断含义。 */
    private String refundFailReason;
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime refundTime;


    /**
     * 支付时间（可为空）
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime payTime;

    private String transactionId;

    private LocalDateTime payExpireTime;

    /**
     * 发货时间（可为空）
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime deliverTime;

    /**
     * 确认收货时间（可为空）
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime receiveTime;

    /**
     * 取消时间（可为空）
     */
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime cancelTime;

    /**
     * 订单取消原因（仅已取消状态有效，可为空）
     */
    private String cancelReason;

    /**
     * 订单备注（用户填写，可为空）
     */
    private String remark;

    /**
     * 是否评价
     */
    private CommonStatus isEvaluate;

    /**
     * 是否删除
     */
    private CommonStatus isDeleted;

    /**
     * 地址相关(收件人,地址...)
     */
    @TableField(exist = false)
    private Address address;

    /**
     * 快递公司（可为空）
     */
    private String logisticsCompany;

    /**
     * 物流单号（可为空）
     */
    private String logisticsNo;

    /**
     * 创建时间（默认当前时间）
     */
    @TableField(fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime createTime;

    /**
     * 更新时间（默认当前时间，更新时自动刷新）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime updateTime;


    @TableField(exist = false)
    private List<OrderItem> orderItems;

    @TableField(exist = false)
    private List<OrderTracking> orderTrackings;

    public BigDecimal getTotalGoodsAmount() {
        return this.totalGoodsAmount;
    }
}
