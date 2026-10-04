package com.lixiaopu.service;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.AddressDTO;
import com.lixiaopu.pojo.entity.Address;
import com.baomidou.mybatisplus.spring.service.IService;
import jakarta.validation.Valid;

/**
* @author Mlpnk
* @description 针对表【user_address(收货地址，每个用户最多一个默认地址)】的数据库操作Service
* @createDate 2026-09-22 11:17:25
*/
public interface AddressService extends IService<Address> {

    Result getAddressList();

    Result updateAddress(@Valid AddressDTO addressDTO);

    Result insertAddress(@Valid AddressDTO addressDTO);

    Result deleteAddress(long id);
}
