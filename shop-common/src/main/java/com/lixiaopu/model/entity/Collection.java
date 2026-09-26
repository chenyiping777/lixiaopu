package com.lixiaopu.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 栗小铺 collection
 * @TableName collection
 */
@TableName(value ="collection")
@Data
public class Collection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;
    
    private Date createTime;
}