package com.lixiaopu.common.mapstruct;

import com.lixiaopu.common.result.UserInfo;
import com.lixiaopu.pojo.dto.*;
import com.lixiaopu.pojo.entity.*;
import com.lixiaopu.pojo.enums.CommonDefault;
import com.lixiaopu.pojo.vo.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",//把生成的实现类交给spring
        unmappedTargetPolicy = ReportingPolicy.IGNORE, // 忽略字段不匹配警告
        unmappedSourcePolicy = ReportingPolicy.IGNORE)  // 忽略源对象多余字段)
//标记接口，告诉 MapStruct：这是一个对象映射接口，
//编译阶段自动生成接口实现类（生成的代码就是一堆 get/set，和手动写的转换代码一样，没有反射，性能很高）
public interface CopyMapper {
    default CommonDefault map(Boolean value) {
        return value == null ? null : CommonDefault.getByIsDefault(value);
    }

    UserInfo sysUserToUserInfo(SysUser sysUser);

    Banner bannerDTOToBanner(BannerDTO bannerDTO);

    CartItem cartProductDTOToCartItem(CartProductDTO cartProductDTO);

    Category categoryDTOToCategory(CategoryDTO categoryDTO);

    Notice noticeDTOToNotice(NoticeDTO noticeDTO);


    ProductDetailVO productToProductDetailVO(Product product);

    ProductDetailVO.Spec productSpecToDetailSpec(ProductSpec productSpec);

    UserDetailVO sysUserToUserDetailVO(SysUser sysUser);

    Address addressDTOToAddress(AddressDTO addressDTO);

    @Mapping(source = "id", target = "id")
    SimpleProductVO productToSimpleProductVO(Product product);

    Order orderDTOToOrder(OrderDTO orderDTO);

    OrderItem orderItemDTOToOrderItem(OrderItemDTO orderItemDTO);

    @Mapping(source = "id", target = "orderId")
    @Mapping(source = "totalAmount", target = "payAmount")
    @Mapping(target = "paymentDeadline", ignore = true)
    OrderVO orderToOrderVO(Order order);

    OrderWithItemVO orderToOrderWithItemVO(Order order);

    @Mapping(source = "id", target = "orderId")
    OrderWithTrackingVO orderToOrderWithTrackingVO(Order order);
    OrderTrackingVO orderTrackingToOrderTrackingVO(OrderTracking tracking);
    OrderAddressVO addressToOrderAddressVO(Address address);

    OrderItemVO orderItemToOrderItemVO(OrderItem orderItem);

    FactoryInfoVO factoryInfoToFactoryInfoVO(FactoryInfo factoryInfo);

    ProductComment firstProductCommentDTOToProductComment(FirstProductCommentDTO firstProductCommentDTO);

    ProductComment secondProductCommentDTOToProductComment(SecondProductCommentDTO secondProductCommentDTO);

    ProductCommentAppend appendProductFirstCommentDTOToProductCommentAppend(AppendProductFirstCommentDTO appendProductFirstCommentDTO);

    ProductFirstCommentVO productCommentToProductFirstCommentVO(ProductComment productComment);

    ProductSecondCommentVO productCommentToProductSecondCommentVO(ProductComment productComment);

    ProductAppendCommentVO productCommentAppendToProductCommentAppendVO(ProductCommentAppend productCommentAppend);

}
