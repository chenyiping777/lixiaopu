package com.lixiaopu.infrastructure.mp.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;
import com.lixiaopu.common.constant.DatePatternConstants;

/**
 * 基于 mybatis-plus 实现在实体类进行时间填充
 */
@Configuration
public class MyMetaObjectHandlerConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 插入时自动填充创建时间和更新时间
        var now = LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时自动填充更新时间
        var now = LocalDateTime.now(DatePatternConstants.SYSTEM_ZONE_ID);
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, now);
    }
}
