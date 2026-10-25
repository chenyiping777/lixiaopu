package com.lixiaopu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.VoucherOrder;

public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    /** 受理落单时原子复制券模板，避免后续改价改变已购权益。 */
    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO voucher_order
          (id,user_id,voucher_id,status,pay_value,actual_value,seckill_voucher_id,validity_days,pay_expire_time,create_time,update_time)
        SELECT #{id},#{userId},id,1,pay_value,actual_value,id,validity_days,#{deadline},#{now},#{now}
        FROM voucher WHERE id=#{voucherId} AND type=1
        """)
    int insertPurchase(@org.apache.ibatis.annotations.Param("id") long id,
                       @org.apache.ibatis.annotations.Param("userId") long userId,
                       @org.apache.ibatis.annotations.Param("voucherId") long voucherId,
                       @org.apache.ibatis.annotations.Param("now") java.time.LocalDateTime now,
                       @org.apache.ibatis.annotations.Param("deadline") java.time.LocalDateTime deadline);
}

