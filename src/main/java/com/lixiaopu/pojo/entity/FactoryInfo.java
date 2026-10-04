package com.lixiaopu.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对应 factory_info；业务逻辑按模块逐步实现。 */
@Data
@TableName("factory_info")
public class FactoryInfo {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String image;
    private String factoryName;
    private String introduction;
    private String serviceHotline;
    private String officialWechat;
    private String address;
    private String copyrightInfo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

