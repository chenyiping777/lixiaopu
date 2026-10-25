package com.lixiaopu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixiaopu.pojo.entity.ProductSpec;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductSpecMapper extends BaseMapper<ProductSpec> {
    /** 归属匹配且扣减后库存非负才更新，不使用版本号。 */
    @Update("UPDATE product_spec SET stock = stock - #{quantity} " +
            "WHERE id = #{specId} AND product_id = #{productId} " +
            "AND stock - #{quantity} >= 0")
    int deductStock(@Param("productId") Long productId, @Param("specId") Long specId,
                    @Param("quantity") int quantity);

    @Update("UPDATE product_spec SET stock = stock + #{delta} " +
            "WHERE id = #{specId} AND product_id = #{productId} " +
            "AND CAST(stock AS DECIMAL(20,0)) + #{delta} BETWEEN 0 AND 2147483647")
    int adjustStock(@Param("productId") Long productId, @Param("specId") Long specId,
                    @Param("delta") long delta);
}
