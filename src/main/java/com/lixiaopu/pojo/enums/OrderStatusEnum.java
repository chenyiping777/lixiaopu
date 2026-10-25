package com.lixiaopu.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 订单状态枚举
 * 对接前端传参、JSON序列化、MyBatis-Plus数据库存储
 */
@Getter
public enum OrderStatusEnum {
    /**
     * 待支付
     */
    PENDING_PAYMENT("pendingPayment", 1, "待支付", 2),
    /**
     * 待确认
     */
    PENDING_CONFIRM("pendingConfirm", 2, "待确认", 1),
    /**
     * 待发货
     */
    PENDING_SHIPMENT("pendingShipment", 3, "待发货", 3),
    /**
     * 待收货
     */
    PENDING_RECEIPT("pendingReceipt", 4, "待收货", 4),
    /**
     * 已完成
     */
    COMPLETED("completed", 5, "已完成", 5),
    /**
     * 已取消
     */
    CANCELLED("cancelled", 6, "已取消", 1),
    /**
     * 售后
     */
    AFTER_SALE("afterSale", 7, "售后", 5),
    /**
     * 已评价
     */
    EVALUATED("evaluated", 8, "已评价", 1),
    /**
     * 已追评
     */
    REVIEWED("reviewed", 9, "已追评", 1),
    /** 兼容历史订单；新退款结果使用 order.pay_status，保留原履约状态。 */
    REFUNDED("refunded", 10, "已退款", 1);

    /**
     * @JsonValue：前端交互值，JSON序列化向外输出这个字符串（前端展示/传参用）
     */
    @JsonValue
    private final String value;

    /**
     * @EnumValue：MyBatis-Plus存入数据库的数字编码，数据库status字段存这个int值
     */
    @EnumValue
    private final int code;

    /**
     * 中文描述，用于后端提示、日志展示
     */
    private final String desc;

    /**
     * 页面编码，关联 OrderPageEnum，用来区分我的订单标签页归类
     */
    private final int pageCode;

    OrderStatusEnum(String value, int code, String desc, int pageCode) {
        this.value = value;
        this.code = code;
        this.desc = desc;
        this.pageCode = pageCode;
    }

    /**
     * @JsonCreator：Jackson反序列化，前端传value字符串，转换成对应枚举
     * @param value 前端传入的状态字符串，例如 pendingPayment
     * @return 匹配的订单状态枚举
     */
    @JsonCreator
    public static OrderStatusEnum getByValue(String value) {
        for (OrderStatusEnum orderStatusEnum : OrderStatusEnum.values()) {
            if (StringUtils.equals(orderStatusEnum.value, value)) {
                return orderStatusEnum;
            }
        }
        throw new IllegalArgumentException("无效的OrderStatusEnum.value:" + value);
    }
}
