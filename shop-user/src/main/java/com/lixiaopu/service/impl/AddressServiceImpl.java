package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.mapper.AddressMapper;
import com.lixiaopu.model.entity.Address;
import com.lixiaopu.service.AddressService;
import org.springframework.stereotype.Service;

/**
* @author Mlpnk
* @description 针对表【user_address(收货地址，每个用户最多一个默认地址)】的数据库操作Service实现
* @createDate 2026-09-22 11:17:25
*/
@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address>
    implements AddressService {

}




