package com.lixiaopu.infrastructure.es.mapstruct;

import com.lixiaopu.common.constant.DatePatternConstants;
import com.lixiaopu.infrastructure.es.document.ProductDocument;
import com.lixiaopu.pojo.entity.Product;
import com.lixiaopu.pojo.enums.CommonStatus;
import com.lixiaopu.pojo.vo.SimpleProductVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 商品转换规则的唯一入口，不查询数据库，也不发送 ES 请求。
 * `Product（MySQL实体） ↔ ProductDocument（ES文档） → SimpleProductVO（返回前端VO）`
 *  MapStruct 是编译期生成实现类，不是反射。编译的时候自动生成 get/set 代码，运行时直接调用，性能很高。
 * */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EsCopyMapper {

    /** DB 的无时区时间按北京时间解释，ES date 统一毫秒精度。 */
    default Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(DatePatternConstants.SYSTEM_ZONE_ID)
                .toInstant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** 展示 VO 保持北京时间，时间游标不经过此转换。 */
    default LocalDateTime toLocalDateTime(Instant time) {
        return time == null ? null : LocalDateTime.ofInstant(time,
                DatePatternConstants.SYSTEM_ZONE_ID);
    }

    @Mapping(target = "status", source = "status", qualifiedByName = "statusToNumber")
    ProductDocument toDocument(Product product);

    @Mapping(target = "status", source = "status", qualifiedByName = "numberToStatus")
    SimpleProductVO toSimpleProduct(ProductDocument document);

    /** 批量导入与业务补同步共享的空值规则。 */
    @AfterMapping
    default void completeDocument(@MappingTarget ProductDocument document) {
        if (document.getUpdateTime() == null) {
            document.setUpdateTime(document.getCreateTime());
        }
        if (document.getViewCount() == null) {
            document.setViewCount(0L);
        }
        if (document.getSalesCount() == null) {
            document.setSalesCount(0L);
        }
    }

    @Named("statusToNumber")
    default Integer statusToNumber(CommonStatus status) {
        return status == null ? 0 : status.getNumber();
    }

    @Named("numberToStatus")
    default CommonStatus numberToStatus(Integer number) {
        for (CommonStatus status : CommonStatus.values()) {
            if (status.getNumber().equals(number)) {
                return status;
            }
        }
        return null;
    }
}
