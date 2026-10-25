package com.lixiaopu.pojo.vo;

import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.pojo.enums.OrderTrackingStatusEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class OrderTrackingVO {
    private OrderTrackingStatusEnum logisticsStatus;
    private String location;
    private String description;


    @DateTimeFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    @JsonFormat(pattern = DatePatternConstants.DATE_TIME_FORM)
    private LocalDateTime createTime;
}
