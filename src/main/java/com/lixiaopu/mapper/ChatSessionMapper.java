package com.lixiaopu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.ChatSession;
import com.lixiaopu.pojo.vo.ChatSessionVO;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    List<ChatSessionVO> selectSessionList(@Param("userId") Long userId);
    int recordMessage(@Param("a") long a, @Param("b") long b,
                      @Param("content") String content, @Param("time") LocalDateTime time,
                      @Param("unreadA") int unreadA, @Param("unreadB") int unreadB);
}
