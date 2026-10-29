package com.lixiaopu.controller.admin;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.util.BloomFilterUtils;
import com.lixiaopu.pojo.entity.ProductSearchKeyword;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.service.ProductService;
import com.lixiaopu.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.lixiaopu.service.ProductSearchKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 后台商品管理入口；用户浏览与评论接口在 controller.user。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products")
public class ProductController {

    @Autowired
    private final ProductSearchKeywordService productSearchKeywordService;
    private final BloomFilterUtils bloomFilterUtils;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final com.lixiaopu.service.admin.ProductRelationsService productRelationsService;

    @GetMapping("/{id}")
    public Result detail(@PathVariable @Positive Long id) {
        return Result.success(productRelationsService.detail(id));
    }

    public record StockAdjustment(
            @NotNull(message = "规格ID不能为空") @Positive(message = "规格ID必须大于0") Long specId,
            @NotNull(message = "库存调整量不能为空") Integer delta) {
        @AssertTrue(message = "库存调整量不能为0")
        public boolean isDeltaNonZero() { return delta == null || delta != 0; }
    }

    @PostMapping("/{id}/stock")
    public Result adjustStock(@PathVariable @Positive(message = "商品ID必须大于0") Long id,
                              @Valid @RequestBody StockAdjustment request) {
        inventoryService.adjust(id, request.specId(), request.delta());
        return Result.success();
    }


    /**
     * 商品新增、修改、删除事务成功后，会立即清理缓存，然后通过同步事件更新 Elasticsearch 和相关缓存
    * */
    @PostMapping
    public Result create(@RequestBody Product product) {
        return Result.success(Map.of("id", productService.createProduct(product)));
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Product product) {
        return Result.success(Map.of("id", productService.updateProduct(id, product)));
    }

    @PutMapping("/{id}/status")
    public Result status(@PathVariable Long id, @RequestParam Integer status) {
        if (status != 0 && status != 1)
            return Result.error(400, "状态必须为 0 或 1");
        var product = new Product();
        product.setStatus(status == 1 ? CommonStatus.ACTIVE : CommonStatus.INACTIVE);
        return update(id, product);
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return Result.success();
        } catch (DataIntegrityViolationException referenced) {
            return Result.error(409, "商品或规格仍被其他数据引用，请改为下架");
        }
    }

    @PostMapping("/bloom-filter/rebuild")
    public Result rebuildBloomFilter() {
        bloomFilterUtils.rebuild();
        return Result.success();
    }

    @GetMapping("/keywords")
    public Result getProductSearchKeywordListAdmin() {
        return productSearchKeywordService.getProductSearchKeywordListAdmin();
    }

    @PutMapping("/keywords")
    public Result updateProductSearchListAdmin(@RequestBody List<ProductSearchKeyword> keywords) {
        return productSearchKeywordService.updateProductSearchListAdmin(keywords);
    }
}
