package com.lixiaopu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lixiaopu.common.mapstruct.CopyMapper;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.mapper.FactoryInfoMapper;
import com.lixiaopu.pojo.entity.FactoryInfo;
import com.lixiaopu.service.FactoryInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FactoryInfoServiceImpl implements FactoryInfoService {
    private final FactoryInfoMapper factoryInfoMapper;
    private final CopyMapper copyMapper;

    @Override
    public Result getFactoryInfo() {
        FactoryInfo info = factoryInfoMapper.selectOne(Wrappers.<FactoryInfo>lambdaQuery()
                .orderByAsc(FactoryInfo::getId).last("LIMIT 1"));
        return info == null ? Result.error(404, "暂未配置关于我们")
                : Result.success(copyMapper.factoryInfoToFactoryInfoVO(info));
    }
}
