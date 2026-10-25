package com.lixiaopu.infrastructure.es.repository;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.lixiaopu.infrastructure.es.query.ProductSearchOptions;

import java.util.List;
import java.util.Set;

/** 只构造 ES 请求，不连接 ES，不读取数据库。
 * 商品搜索请求的创建工具
 * */
final class ProductSearchRequestFactory {

    private static final int MAX_PAGE_SIZE = 1000;
    private static final int MAX_RESULT_WINDOW = 10000;
    private static final Set<String> SORT_FIELDS = Set.of(
            "_score", "id", "price", "salesCount", "viewCount", "createTime", "updateTime");

    /**
     * 创建一个分页查询请求
     * - 页码从 1 开始
     * - 每页数量最大值为 1000
     * - 总记录数最大值为 10000
     * */
    SearchRequest createPageRequest(String indexName, ProductSearchOptions options) {
        long offset = ((long) options.getPageNo() - 1) * options.getPageSize();
        if (options.getPageNo() < 1 || offset + options.getPageSize() > MAX_RESULT_WINDOW) {
            throw new IllegalArgumentException("页码超出范围，请使用游标分页");
        }

        return createBaseRequest(indexName, options)
                .from((int) offset)
                .trackTotalHits(total -> total.enabled(true))
                .build();
    }

    /**
     * 专门构建带 `search_after` 的游标分页查询请求
     * - 这个方法只额外处理游标逻辑，不重复写查询条件。
    * */
    SearchRequest createCursorRequest(String indexName, ProductSearchOptions options) {
        SearchRequest.Builder request = createBaseRequest(indexName, options);
        //`createBaseRequest(indexName, options)`：底层方法，先把公共部分（关键词、分类、价格、filter、size、sort）拼装好，返回一个`SearchRequest.Builder`
        boolean hasValue = options.getCursorValue() != null;
        boolean hasId = options.getCursorId() != null;
        if (hasValue != hasId) {
            throw new IllegalArgumentException("游标值和商品 ID 必须同时传入");
        }
        if (hasValue) {
            FieldValue value = parseCursorValue(options.getSortField(), options.getCursorValue());
            List<FieldValue> cursor = options.getSortField().equals("id")
                    ? List.of(value)
                    : List.of(value, FieldValue.of(options.getCursorId().longValue()));
            request.searchAfter(cursor);
        }
        return request.build();
    }

    /**
     * 创建一个基础的查询请求，包含关键词、分类、价格、filter、size、sort
     * - 这个方法只拼装查询条件，不处理分页逻辑。
     * */
    private SearchRequest.Builder createBaseRequest(String indexName, ProductSearchOptions options) {
        validate(options);
        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(indexName)
                .query(createQuery(options))
                .size(options.getPageSize());
        addSort(request, options);
        return request;
    }

    /**
     * 创建一个查询条件
     * - 这个方法只拼装查询条件，不处理分页逻辑。
     * */
    private Query createQuery(ProductSearchOptions options) {
        List<Long> categoryIds = options.getCategoryIds();
        if (categoryIds != null && categoryIds.isEmpty()) {
            return Query.of(query -> query.matchNone(none -> none));
        }

        BoolQuery.Builder conditions = new BoolQuery.Builder();
        if (options.getKeyword() != null && !options.getKeyword().isBlank()) {
            conditions.must(query -> query.match(match -> match
                    .field("name")
                    .query(options.getKeyword())));
        }
        if (options.isActiveOnly()) {
            conditions.filter(query -> query.term(term -> term.field("status").value(1)));
        }
        if (categoryIds != null) {
            List<FieldValue> values = categoryIds.stream()
                    .map(id -> FieldValue.of(id.longValue()))
                    .toList();
            conditions.filter(query -> query.terms(terms -> terms
                    .field("categoryId")
                    .terms(field -> field.value(values))));
        }
        if (options.getMinPrice() != null || options.getMaxPrice() != null) {
            conditions.filter(query -> query.range(range -> range.number(number -> {
                number.field("price");
                if (options.getMinPrice() != null) {
                    number.gte(options.getMinPrice().doubleValue());
                }
                if (options.getMaxPrice() != null) {
                    number.lte(options.getMaxPrice().doubleValue());
                }
                return number;
            })));
        }
        if (options.getAfterId() != null) {
            conditions.filter(query -> query.range(range -> range.number(number -> number
                    .field("id")
                    .gt(options.getAfterId().doubleValue()))));
        }
        return conditions.build()._toQuery();
    }

    /**
     * 添加排序条件
     * - 默认按分数排序
     * - 可以按价格、销量、浏览量、创建时间、更新时间排序
     * - 主排序值相同时，用唯一业务 ID 确定顺序。
     * */
    private void addSort(SearchRequest.Builder request, ProductSearchOptions options) {
        String fieldName = options.getSortField();
        SortOrder order = options.isAscending() ? SortOrder.Asc : SortOrder.Desc;
        request.sort(sort -> sort.field(field -> {
            field.field(fieldName).order(order);
            if (fieldName.equals("createTime") || fieldName.equals("updateTime")) {
                field.format("strict_date_optional_time");
            }
            return field;
        }));
        // 主排序值相同时，用唯一业务 ID 确定顺序。
        if (!fieldName.equals("id")) {
            request.sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Asc)));
        }
    }

    /**
     * 解析游标值
     * - 根据排序字段，将字符串类型的游标值转换为对应的值类型
     * */
    private FieldValue parseCursorValue(String sortField, String value) {
        return switch (sortField) {
            case "price" -> FieldValue.of(Double.parseDouble(value));
            case "salesCount", "viewCount", "id" -> FieldValue.of(Long.parseLong(value));
            case "createTime", "updateTime" -> {
                try {
                    java.time.OffsetDateTime.parse(value, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                } catch (java.time.format.DateTimeParseException invalid) {
                    throw new IllegalArgumentException("日期游标必须包含时区偏移或 Z", invalid);
                }
                yield FieldValue.of(value);
            }
            default -> throw new IllegalArgumentException("该排序字段不支持游标");
        };
    }

    /**
     * 验证查询参数
     * - 页码从 1 开始
     * - 每页数量最大值为 1000
     * - 总记录数最大值为 10000
     * - 排序字段必须在允许的范围内
     * - 最低价不能高于最高价
     * */
    private void validate(ProductSearchOptions options) {
        if (options.getPageSize() < 1 || options.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("查询数量必须为 1 到 1000");
        }
        if (!SORT_FIELDS.contains(options.getSortField())) {
            throw new IllegalArgumentException("不支持的排序字段: " + options.getSortField());
        }
        if (options.getMinPrice() != null && options.getMaxPrice() != null
                && options.getMinPrice().compareTo(options.getMaxPrice()) > 0) {
            throw new IllegalArgumentException("最低价不能高于最高价");
        }
    }
}
