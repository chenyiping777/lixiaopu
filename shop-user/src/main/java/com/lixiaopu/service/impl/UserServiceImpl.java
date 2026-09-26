package com.lixiaopu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lixiaopu.common.constant.MessageConstant;
import com.lixiaopu.common.context.CurrentHolder;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.mapper.SysUserMapper;
import com.lixiaopu.model.dto.UserDetailDTO;
import com.lixiaopu.model.entity.SysUser;
import com.lixiaopu.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements UserService {
    @Override
    public Result getUserDetailById() {
        Long currentUserId = CurrentHolder.getCurrentUser().getId();
        SysUser user = lambdaQuery().eq(SysUser::getId,currentUserId).one();
        UserDetailDTO userDetailDTO = new UserDetailDTO();
        BeanUtil.copyProperties(user,userDetailDTO);
        return Result.success(userDetailDTO);
    }

    //更新用户的头像，昵称，电话
    @Override
    public Result updateUserInfo(UserDetailDTO userDetailDTO) {
        Long currentUserId = CurrentHolder.getCurrentUser().getId();
        //更新头像涉及到文件上传，阿里云文件上传
        //业务流程拆分：
        //1. 头像上传接口（单独写）
        //前端上传图片文件 → 后端接收 MultipartFile → 上传到对象存储 / 本地 → 获取图片 url 地址 → 返回 url 给前端。
        //2. 更新用户信息接口（ updateUserDetail）
        //前端拿到上传成功后的图片 url，把 url 放到 userDetailDTO 的 avatar 字段，调用这个更新接口，把 url 存入数据库。

        // 为空代表不改变，还是前端直接全部传回来，旧的没改的也传回来一起覆盖
        //全量覆盖
        boolean isSuccess = lambdaUpdate()
                .eq(SysUser::getId, currentUserId)
                .set(SysUser::getNickname, userDetailDTO.getNickname())
                .set(SysUser::getAvatar, userDetailDTO.getAvatar())
                .update();
        if (!isSuccess) {
            return Result.error(MessageConstant.SQL_MESSAGE_SAVE_ERROR);
        }
        //同时更新线程里的用户信息
        CurrentHolder.getCurrentUser().setNickname(userDetailDTO.getNickname());
        CurrentHolder.getCurrentUser().setAvatar(userDetailDTO.getAvatar());
        //source 里面是 null 的字段，也会把 target 对应字段覆盖成 null！
        //靠前端传过来的全量字段
        return Result.success();
    }
}
