package com.lixiaopu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.constant.MessageConstant;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.mapper.AddressMapper;
import com.lixiaopu.pojo.dto.AddressDTO;
import com.lixiaopu.pojo.entity.Address;
import com.lixiaopu.pojo.enums.CommonDefault;
import com.lixiaopu.service.AddressService;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;

@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address>
        implements AddressService {


    private static final String ADDRESS_ID = "addressId";
    private static final String DELETED = "deleted";

    /**
     * 获取地址列表
     */
    @Override
    public Result getAddressList() {
        long userId = CurrentHolder.getCurrentUser().getId();
        List<Address> list = lambdaQuery().eq(Address::getUserId, userId).list();
        return Result.success(list);
    }

    /**
     * 新增地址列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result insertAddress(AddressDTO addressDTO) {
        long userId = CurrentHolder.getCurrentUser().getId();
        // 创建地址对象并复制属性
        Address address = new Address();
        BeanUtil.copyProperties(addressDTO, address);
        address.setUserId(userId);
        //获得当前代理对象
        AddressServiceImpl addressService = (AddressServiceImpl) AopContext.currentProxy();
        addressService.makeOnlyHaveOneDefault(addressDTO, userId);
        // 保存地址
        boolean isSuccess = save(address);
        if (!isSuccess) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        return Result.success(address);
    }

    /**
     * 保证只有一个默认地址
     */
    @Transactional(rollbackFor = Exception.class)
    void makeOnlyHaveOneDefault(AddressDTO addressDTO, long userId) {
        // 如果本次地址要设为默认
        if (addressDTO.getIsDefault()) {
            lambdaUpdate()
                    .eq(Address::getUserId, userId)
                    .ne(Address::getId, addressDTO.getId()) // 排除当前正在修改的地址
                    .set(Address::getIsDefault, CommonDefault.NO_DEFAULT.getNumber())
                    .update();
        }


    }

    /**
     * 修改地址
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result updateAddress(AddressDTO addressDTO) {
        long userId = CurrentHolder.getCurrentUser().getId();

        Address address = new Address();
        BeanUtil.copyProperties(addressDTO, address);
        address.setId(userId);

        AddressServiceImpl addressService = (AddressServiceImpl) AopContext.currentProxy();
        addressService.makeOnlyHaveOneDefault(addressDTO, userId);
        address.setUserId(userId);

        boolean isSuccess = updateById(address);
        if (!isSuccess) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        return Result.success(address);
    }

    /**
     * 删除地址
     */
    @Override
    public Result deleteAddress(long id) {
        boolean isSuccess = removeById(id);
        if (!isSuccess) {
            return Result.error(MessageConstant.DATA_ERROR);
        }
        HashMap<String, Object> map = new HashMap<>(2);
        map.put(ADDRESS_ID, id);
        map.put(DELETED, true);
        return Result.success(map);
    }
}
