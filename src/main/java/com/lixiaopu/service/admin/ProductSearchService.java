package com.lixiaopu.service.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.mapper.CategoryMapper;
import com.lixiaopu.pojo.entity.Category;
import com.lixiaopu.pojo.enums.CommonSortTypeEnum;
import com.lixiaopu.pojo.enums.ProductSortTypeEnum;
import com.lixiaopu.infrastructure.es.mapstruct.EsCopyMapper;
import com.lixiaopu.pojo.vo.SimpleProductVO;
import com.lixiaopu.infrastructure.es.query.ProductSearchOptions;
import com.lixiaopu.infrastructure.es.repository.ProductSearchRepository;
import com.lixiaopu.pojo.query.ProductQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 将页面参数转换为查询条件，并组装页面需要的分页结果。 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductSearchRepository productSearchRepository;
    private final CategoryMapper categoryMapper;
    private final EsCopyMapper esCopyMapper;

    //解释页面参数，计算页数
    // 将页面参数转换为查询条件，并组装页面需要的分页结果。
    public SearchResult search(ProductQuery query) {
        var sort = query.getSortType() == null || query.getSortType().isBlank()
                ? ProductSortTypeEnum.DEFAULT
                : ProductSortTypeEnum.getByValue(query.getSortType());
        List<Long> categoryIds = query.getCategoryId() == null ? null : List.of(query.getCategoryId());
        if (query.getCategoryId() != null && query.isFirstCategoryId()) {
            categoryIds = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                    .eq(Category::getParentId, query.getCategoryId())).stream().map(Category::getId).toList();
            if (categoryIds.isEmpty()) {
                return new SearchResult(List.of(), 0, 0, query.getPageNo(), query.getPageSize());
            }
        }
        ProductSearchOptions options = ProductSearchOptions.builder()
                .keyword(query.getKeyword())
                .categoryIds(categoryIds)
                .minPrice(query.getMinPrice())
                .maxPrice(query.getMaxPrice())
                .pageNo(query.getPageNo())
                .pageSize(query.getPageSize())
                .sortField(sort.getSortField())
                .ascending(sort.getCommonSortTypeEnum() == CommonSortTypeEnum.ASC)
                .build();

        ProductSearchRepository.Page page = productSearchRepository.searchPage(options);
        long pages = (page.total() + query.getPageSize() - 1) / query.getPageSize();
        return new SearchResult(page.list().stream().map(esCopyMapper::toSimpleProduct).toList(), page.total(), pages, query.getPageNo(), query.getPageSize());
    }

    public record SearchResult(
            List<SimpleProductVO> list,
            long total,
            long pages,
            int pageNo,
            int pageSize) {
    }
}
