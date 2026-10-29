package com.lixiaopu.service.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.mapper.ProductSpecMapper;
import com.lixiaopu.mapper.ProductMapper;
import com.lixiaopu.mapper.OrderItemMapper;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.entity.ProductSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.lixiaopu.mapper.ProductImageMapper;
import com.lixiaopu.mapper.CollectionMapper;
import com.lixiaopu.pojo.entity.ProductImage;
import com.lixiaopu.pojo.entity.CollectionProduct;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashSet;

/** 图册与规格保存，复用商品管理事务。null 表示不修改，空列表表示清空。
 * 专门处理商品附属数据：商品图册、商品规格
 * */
@Service
@RequiredArgsConstructor
public class ProductRelationsService {

    private final ProductSpecMapper productSpecMapper;
    private final ProductImageMapper productImageMapper;
    private final CollectionMapper collectionMapper;
    private final ProductMapper productMapper;
    private final OrderItemMapper orderItemMapper;

    public void save(Product product) {
        saveImages(product);
        saveSpecs(product);
    }

    /** 管理台编辑读取完整快照，包含已下架商品，避免列表摘要覆盖描述和图册。 */
    public Product detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        product.setSpecList(productSpecMapper.selectList(Wrappers.<ProductSpec>lambdaQuery()
                .eq(ProductSpec::getProductId, id).orderByAsc(ProductSpec::getId)));
        product.setImageUrls(productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id).orderByAsc(ProductImage::getSort, ProductImage::getId))
                .stream().map(ProductImage::getImageUrl).toList());
        return product;
    }

    private void saveImages(Product product) {
        if (product.getImageUrls() == null) return;
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, product.getId()));
        int sort = 0;
        for (String url : product.getImageUrls()) {
            if (url == null || url.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品图片地址不能为空");
            }
            var image = new ProductImage();
            image.setProductId(product.getId());
            image.setImageUrl(url);
            image.setSort(sort++);
            productImageMapper.insert(image);
        }
    }

    /**
     * 商品规格逻辑比较复杂，差量更新
     *
     * 前端传 specList = null → 规格【不修改，保留原有规格】
     * 前端传 specList = [] → 规格【清空，删除所有规格】
     * {"id":101,"specText":"蓝色 XL码","price":99}, // 修改旧规格B，库存走独立增量接口
     * {"id":null,"specText":"白色 XS码","price":88,"stock":5}  // 新增规格D
     */
    private void saveSpecs(Product product) {
        // specList == null → 直接return，规格【不修改，保留原有规格】
        if (product.getSpecList() == null) return;

        // 查询数据库里，当前商品已经存在的旧规格列表
        var old = productSpecMapper.selectList(Wrappers.<ProductSpec>lambdaQuery().eq(ProductSpec::getProductId, product.getId()));
        // kept集合：用来保存【本次要保留/更新的规格id】
        var kept = new HashSet<Long>();

        // 遍历前端传过来的新规格列表
        for (var spec : product.getSpecList()) {
            validateSpec(spec); // 校验规格：规格文本、价格、库存不能非法
            spec.setProductId(product.getId()); // 绑定当前商品id

            if (spec.getId() == null) {
                // 规格id为null：代表【新增一条规格】
                productSpecMapper.insert(spec);
            } else {
                // 规格id不为null：代表【修改这条旧规格】
                // 两个错误判断：
                // 1. old旧规格里找不到这个id → 这个规格id不属于当前商品
                // 2. kept.add(id)失败，说明id已经在集合里 → 前端传了重复的规格id
                if (old.stream().noneMatch(item -> item.getId().equals(spec.getId())) || !kept.add(spec.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "规格 ID 不属于此商品或重复");
                }
                spec.setStock(null); // 普通资料编辑不允许覆盖并发下单后的库存。
                if (productSpecMapper.updateById(spec) != 1) throw new IllegalStateException("规格保存失败");
            }
        }

        // 遍历数据库旧规格
        for (var spec : old) {
            // 如果旧规格id不在kept集合里面：说明前端这次没有传这条规格 → 需要删掉
            if (!kept.contains(spec.getId())) {
                if (orderItemMapper.countPendingSpec(spec.getId()) > 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "规格有待支付订单占用，不能删除");
                }
                productSpecMapper.deleteById(spec.getId());
            }
        }
        if (productMapper.refreshStockTotal(product.getId()) != 1) throw new IllegalStateException("商品总库存更新失败");
    }

    /**
     * - 规格对象不能为 null
     * - 规格名称（specText）不能为空、不能只有空格
     * - 规格价格不能为 null，不能负数
     * - 新增规格必须提供非负初始库存，已有规格的库存字段忽略
     * 不满足直接抛出 400 参数异常。
    * */
    private void validateSpec(ProductSpec spec) {
        if (spec == null || spec.getSpecText() == null || spec.getSpecText().isBlank()
                || spec.getPrice() == null || spec.getPrice().signum() < 0
                || (spec.getId() == null && (spec.getStock() == null || spec.getStock() < 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "规格名称、价格或库存不合法");
        }
    }


    /**
     * 商品删除时调用：一次性删除三件附属数据
     * 1. 这个商品的收藏记录 collection
     * 2. 商品图片 product_image
     * 3. 商品规格 product_spec
    * */
    public void delete(Long productId) {
        if (orderItemMapper.countPendingProduct(productId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品有待支付订单占用，请改为下架");
        }
        collectionMapper.delete(Wrappers.<CollectionProduct>lambdaQuery().eq(CollectionProduct::getProductId, productId));
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, productId));
        productSpecMapper.delete(Wrappers.<ProductSpec>lambdaQuery().eq(ProductSpec::getProductId, productId));
    }
}
