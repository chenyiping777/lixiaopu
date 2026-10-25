package com.lixiaopu.pojo.vo;

import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.pojo.enums.OrderStatusEnum;
import com.lixiaopu.pojo.enums.PayStatusEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderWithItemVO {
    private String orderNo;
    private String userId;
    private String addressId;
    private String totalGoodsAmount;
    private String totalAmount;
    private String voucherOrderId;
    private String voucherDiscountAmount;
    private String freight;
    private String remark;
    private OrderStatusEnum status;
    private PayStatusEnum payStatus;
    private String refundNo;
    private String refundReason;
    private String refundFailReason;
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime refundTime;
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime payTime;
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime createTime;
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime updateTime;
    private List<OrderItemVO> orderItems;
    private OrderAddressVO address;
}
