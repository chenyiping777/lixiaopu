package com.lixiaopu.mapper;

import com.lixiaopu.pojo.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.pojo.enums.CommonStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;


public interface ProductMapper extends BaseMapper<Product> {
    @Select("SELECT * FROM product WHERE id = #{id} FOR UPDATE")
    Product lockById(@Param("id") Long id);

    /** 不使用版本号：扣减后库存非负才更新，以影响行数判断成功。 */
    @Update("UPDATE product SET stock = stock - #{quantity} " +
            "WHERE id = #{id} AND status = 1 AND stock - #{quantity} >= 0")
    int deductStock(@Param("id") Long id, @Param("quantity") long quantity);

    @Update("UPDATE product SET stock = stock + #{delta} " +
            "WHERE id = #{id} AND stock + #{delta} >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") long delta);

    @Update("UPDATE product SET stock = (SELECT COALESCE(SUM(stock), 0) " +
            "FROM product_spec WHERE product_id = #{id}) WHERE id = #{id}")
    int refreshStockTotal(@Param("id") Long id);

    //游标分页，用于 ES 全量索引同步：
    //查询 `id > afterId` 的商品，按 id 升序，取最多`batchSize`条，
    //下一批把返回列表最后一条 id 当作新的`afterId`继续拉，直到查空。
    default List<Product> selectIndexSyncBatch(long afterId, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return selectList(Wrappers.<Product>lambdaQuery()
                .select(Product::getId, Product::getCategoryId, Product::getName, Product::getImage,
                        Product::getSellPoint, Product::getPrice, Product::getStatus,
                        Product::getViewCount, Product::getSalesCount,
                        Product::getCreateTime, Product::getUpdateTime)
                .gt(Product::getId, afterId)
                .orderByAsc(Product::getId)
                .last("LIMIT " + batchSize));
    }
    //根据商品 id + 用户 id 查商品详情，带图片、规格、是否收藏
    Product selectByProductId(@Param("productId") String productId, @Param("userId") Long userId);

    //根据商品 ID 集合，查询上架状态的商品列表
    default List<Product> getBriefProduct(List<Long> productIdsList) {
        if (productIdsList == null || productIdsList.isEmpty()) {
            return List.of();
        }
        return selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, CommonStatus.ACTIVE)
                .in(Product::getId, productIdsList));
    }
}




