package com.lixiaopu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.ProductSyncEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

public interface ProductSyncEventMapper extends BaseMapper<ProductSyncEvent> {
    @Select("SELECT id FROM product_sync_outbox WHERE completed_at IS NULL "
            + "AND next_attempt_at <= CURRENT_TIMESTAMP ORDER BY id LIMIT 50")
    List<Long> selectPendingIds();

    /** 必须在消费事务内调用，锁定尚未完成的事件，防止重复消费。 */
    @Select("SELECT product_id FROM product_sync_outbox "
            + "WHERE id=#{eventId} AND completed_at IS NULL FOR UPDATE")
    Long selectPendingProductIdForUpdate(@Param("eventId") Long eventId);

    @Update("UPDATE product_sync_outbox SET attempts=attempts+1, "
            + "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 SECOND) WHERE id=#{eventId}")
    int scheduleNextAttempt(@Param("eventId") Long eventId);

    @Update("UPDATE product_sync_outbox SET completed_at=CURRENT_TIMESTAMP WHERE id=#{eventId}")
    int markCompleted(@Param("eventId") Long eventId);
}
