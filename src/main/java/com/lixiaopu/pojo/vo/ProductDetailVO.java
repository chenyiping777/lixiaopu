package com.lixiaopu.pojo.vo;

import java.math.BigDecimal;
import java.util.List;

/** 用户详情页需要的展示数据，不包含数据库实体及内部维护字段。 */
public record ProductDetailVO(
        Long id, Long categoryId, String name, String image, String sellPoint,
        String description, BigDecimal price, BigDecimal enterprisePrice, Long stock,
        Long viewCount, Long salesCount, Integer isCollection,
        List<String> imageUrls, List<Spec> specList) {
    public ProductDetailVO {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        specList = specList == null ? List.of() : List.copyOf(specList);
        isCollection = isCollection == null ? 0 : isCollection;
    }

    public record Spec(Long id, Long productId, String specText, BigDecimal price,
                       BigDecimal enterprisePrice, Integer stock) { }
}
