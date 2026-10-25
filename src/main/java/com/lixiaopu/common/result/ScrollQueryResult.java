package com.lixiaopu.common.result;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;
@Data
@Builder
public class ScrollQueryResult {
    // 订单 ID 以字符串返回，避免浏览器丢失 Long 精度。
    @JsonSerialize(using = ToStringSerializer.class)
    private Long endId;
    private boolean hasMore;
    List<?> list;
}
