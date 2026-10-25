package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value ="voucher")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Voucher {
    
    @TableId(type = IdType.AUTO)
    private Long id;



    private String title;

    private String subTitle;

    private String rules;

    private Long payValue;

    private Long actualValue;

    /** 支付成功后可使用的天数，与抢购时间分开。 */
    private Integer validityDays;

    private Integer type;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
