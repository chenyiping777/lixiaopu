package com.lixiaopu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.model.dto.UserDetailDTO;
import com.lixiaopu.model.entity.SysUser;

public interface UserService extends IService<SysUser> {
    Result getUserDetailById();

    Result updateUserInfo(UserDetailDTO userDetailDTO);
}
