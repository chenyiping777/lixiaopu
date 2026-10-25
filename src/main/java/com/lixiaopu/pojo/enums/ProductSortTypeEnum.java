package com.lixiaopu.pojo.enums;


import com.lixiaopu.aop.annotation.ParamCheckAnnotation;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.pojo.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;


import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Getter
@AllArgsConstructor
public enum ProductSortTypeEnum {


    // default(默认), priceAsc(价格升序), priceDesc(价格降序), newest(最新上架)
    DEFAULT("default", "sales_count DESC", ProductDocument.Fields.salesCount, CommonSortTypeEnum.DESC, "默认按照销量排行"),
    PRICE_ASC("priceAsc", "price ASC", ProductDocument.Fields.price, CommonSortTypeEnum.ASC, "价格升序"),
    PRICE_DESC("priceDesc", "price DESC", ProductDocument.Fields.price, CommonSortTypeEnum.DESC, "价格降序"),
    NEWEST("newest", "update_time DESC", ProductDocument.Fields.updateTime, CommonSortTypeEnum.DESC, "最新上架");
    /**
     * 前端传的 String
     */
    private final String value;
    /**
     * 拼接的 sql 字段
     */
    private final String dbValue;

    /**
     * 商品文档排序字段
     */
    private final String sortField;

    /**
     * 排序方式
     */
    private final CommonSortTypeEnum commonSortTypeEnum;


    /**
     * 文字描述
     */
    private final String desc;


    /**
     * static 根据 value 返回枚举
     * @param value 前端传参字段
     * @return 枚举
     */
    public static ProductSortTypeEnum getByValue(String value) {
        for (ProductSortTypeEnum productSortTypeEnum : values()) {
            if (productSortTypeEnum.value.equals(value)) {
                return productSortTypeEnum;
            }
        }
        throw new IllegalArgumentException("无效的ProductSortType.value:" + value);
    }

    public static int compare(Product p1, Product p2, ProductSortTypeEnum productSortTypeEnum) {
        switch (productSortTypeEnum) {
            case DEFAULT -> {
                return p2.getSalesCount().compareTo(p1.getSalesCount());
            }
            case PRICE_ASC -> {
                return p1.getPrice().compareTo(p2.getPrice());
            }
            case PRICE_DESC -> {
                return p2.getPrice().compareTo(p1.getPrice());
            }
            case NEWEST -> {
                return p2.getUpdateTime().compareTo(p1.getUpdateTime());
            }
        }
        throw new IllegalArgumentException("无效的ProductSortType: " + productSortTypeEnum);
    }

    /**
     * 根据枚举格式化 游标排序值
     * @param productSortTypeEnum 枚举
     * @param sortValue 游标排序值
     * @return 格式化 游标排序值
     */
    public static String filterFormatSortValue(ProductSortTypeEnum productSortTypeEnum , String sortValue){
        // 空值/空白串直接返回，一般是首页请求，不带游标
        if (StringUtils.isBlank(sortValue)) {
            return sortValue;
        }

        // 只有【最新上架】排序，才需要处理时间格式
        if (productSortTypeEnum == ProductSortTypeEnum.NEWEST) {
            try {
                java.time.OffsetDateTime.parse(sortValue, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException invalid) {
                throw new IllegalArgumentException("日期游标必须包含时区偏移或 Z，请重新从第一页查询", invalid);
            }
        }

        // 非NEWEST排序（DEFAULT销量、PRICE_ASC/PRICE_DESC价格），直接原值返回
        return sortValue;
    }

    @ParamCheckAnnotation
    public static String getSortValueByProductDocument(ProductSortTypeEnum productSortTypeEnum , ProductDocument productDocument){
        if (productDocument.getSortValues() != null && !productDocument.getSortValues().isEmpty()) {
            var value = productDocument.getSortValues().get(0);
            if (value.isString()) return value.stringValue();
            if (value.isLong()) return Long.toString(value.longValue());
            if (value.isDouble()) return Double.toString(value.doubleValue());
        }
        switch (productSortTypeEnum){
            case DEFAULT -> {return productDocument.getSalesCount().toString();}
            case PRICE_ASC,PRICE_DESC -> {return productDocument.getPrice().toString();}
            case NEWEST -> {throw new IllegalStateException("日期游标必须使用 ES 返回的实际 sortValues");}
        }
        throw new RuntimeException("非法 productSortTypeEnum 参数");

    }


}
